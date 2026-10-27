"""Independent reference implementation of the ClearRoom indirect STI chain.

Used to produce golden values for the Kotlin unit tests and the in-app
self-test screen (NFR-4). It mirrors the formulas in the requirements doc,
not the Kotlin code, so a bug in one is unlikely to be copied into the other.

Usage:
    python sti_reference.py                 # print the golden table
    python sti_reference.py --t60 0.8 --snr 10
    python sti_reference.py --t60 0.5,0.6,0.7,0.8,0.8,0.7,0.6 --snr 20,18,15,12,10,8,6
    python sti_reference.py --t60 1.0 --snr inf --direct 0.5   # direct-to-reverberant energy ratio

--direct adds the direct sound (Houtgast & Steeneken): the reverberant modulation transfer
1/(1 + jx), x = 2*pi*F*T/13.8, is summed with the unsmeared direct part d and normalised by (d + 1).
d = 0 (the default) is exactly the requirements doc's formula.
"""
import argparse
import math

BANDS_HZ = [125, 250, 500, 1000, 2000, 4000, 8000]
MOD_FREQS_HZ = [0.63, 0.8, 1, 1.25, 1.6, 2, 2.5, 3.15, 4, 5, 6.3, 8, 10, 12.5]
# Male weights from the requirements doc (verify against IEC 60268-16:2020).
ALPHA = [0.085, 0.127, 0.230, 0.233, 0.309, 0.224, 0.173]
BETA = [0.085, 0.078, 0.065, 0.011, 0.047, 0.095]
SNR_APP_LIMIT_DB = 15.0


def modulation_index(f_mod, t60_s, snr_db, direct=0.0):
    x = 2 * math.pi * f_mod * t60_s / 13.8
    reverb = abs(direct + 1 / (1 + 1j * x)) / (direct + 1)  # complex sum, written independently of the Kotlin
    noise = 1.0 if math.isinf(snr_db) and snr_db > 0 else 1.0 / (1.0 + 10 ** (-snr_db / 10))
    return reverb * noise


def transmission_index(m):
    if m >= 1.0:
        snr_app = SNR_APP_LIMIT_DB
    elif m <= 0.0:
        snr_app = -SNR_APP_LIMIT_DB
    else:
        snr_app = 10 * math.log10(m / (1 - m))
    snr_app = max(-SNR_APP_LIMIT_DB, min(SNR_APP_LIMIT_DB, snr_app))
    return (snr_app + SNR_APP_LIMIT_DB) / (2 * SNR_APP_LIMIT_DB)


def band_mti(t60_s, snr_db, direct=0.0):
    tis = [transmission_index(modulation_index(f, t60_s, snr_db, direct)) for f in MOD_FREQS_HZ]
    return sum(tis) / len(tis)


def sti(t60_per_band, snr_per_band, direct_per_band=None):
    direct_per_band = direct_per_band or [0.0] * 7
    mti = [band_mti(t, s, d) for t, s, d in zip(t60_per_band, snr_per_band, direct_per_band)]
    value = sum(a * m for a, m in zip(ALPHA, mti))
    value -= sum(b * math.sqrt(mti[k] * mti[k + 1]) for k, b in enumerate(BETA))
    return max(0.0, min(1.0, value)), mti


def parse_list(text, label):
    if text.lower() in ("inf", "+inf"):
        return [math.inf] * 7
    values = [float(v) for v in text.split(",")]
    if len(values) == 1:
        return values * 7
    if len(values) != 7:
        raise SystemExit(f"--{label} needs 1 or 7 comma-separated values")
    return values


GOLDEN_CASES = [
    ("perfect: T60=0, no noise", 0.0, math.inf),
    ("noise only: T60=0, SNR=0 dB", 0.0, 0.0),
    ("noise only: T60=0, SNR=+15 dB", 0.0, 15.0),
    ("noise only: T60=0, SNR=-15 dB", 0.0, -15.0),
    ("noise only: T60=0, SNR=+6 dB", 0.0, 6.0),
    ("echo only: T60=0.3 s", 0.3, math.inf),
    ("echo only: T60=0.5 s", 0.5, math.inf),
    ("echo only: T60=0.6 s", 0.6, math.inf),
    ("echo only: T60=1.0 s", 1.0, math.inf),
    ("echo only: T60=1.5 s", 1.5, math.inf),
    ("echo only: T60=2.0 s", 2.0, math.inf),
    ("mixed: T60=0.6 s, SNR=10 dB", 0.6, 10.0),
    ("mixed: T60=1.0 s, SNR=5 dB", 1.0, 5.0),
    ("mixed: T60=1.5 s, SNR=0 dB", 1.5, 0.0),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--t60", help="seconds; one value or 7 comma-separated (125..8k)")
    parser.add_argument("--snr", help="dB; one value, 7 comma-separated, or inf")
    parser.add_argument("--direct", default="0", help="direct-to-reverberant energy ratio; one value or 7")
    args = parser.parse_args()

    if args.t60 is not None and args.snr is not None:
        value, mti = sti(parse_list(args.t60, "t60"), parse_list(args.snr, "snr"), parse_list(args.direct, "direct"))
        for band, m in zip(BANDS_HZ, mti):
            print(f"  MTI {band:>5} Hz = {m:.4f}")
        print(f"STI = {value:.4f}")
        return

    print(f"{'case (same T60 and SNR in all 7 bands)':<42} {'STI':>7}")
    for name, t60, snr in GOLDEN_CASES:
        value, _ = sti([t60] * 7, [snr] * 7)
        print(f"{name:<42} {value:7.4f}")


if __name__ == "__main__":
    main()
