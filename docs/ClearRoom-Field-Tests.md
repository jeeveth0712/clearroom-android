# ClearRoom field tests (M13)

How to run the requirements doc's testing table in real rooms, with ClearRoom doing the arithmetic.
Every test shows its own pass rule on screen; copy what the app says into the table at the end.

## What to bring

- The phone with ClearRoom (debug or release build), charged, with Wi-Fi off if you like: the app never goes online.
- Someone to clap (a balloon is even better), a tape measure, 2–3 bedsheets or curtains, and the 20-word list below.
- For the listening check: 2–3 volunteers with paper and pens.

## In each room

1. **Room tab:** enter the room's length, width and height, the rows and seats per row, and the students usually present. Save it under a clear name ("Seminar room 2").
2. **Measure tab, self-test first:** scroll to **Check the app itself** and tap **Run the self-test**. Expect **All checks passed: 30 of 30**. Note the slowest clap time (it must be under 5 s).
3. **Echo repeatability** (testing table: CV ≤ 10 % at 500 Hz–2 kHz)
   - Pick 3 seats: front, middle and back.
   - At each seat, do **5 claps** (keep tapping **Another clap** after the third).
   - Under the echo times, read **Repeatable: these 5 claps agree within N %**, or the warning if they spread more.
4. **Background noise:** at the same 3 seats, with the fans as they usually are. Tell the app the fan setting.
5. **Speaker's voice at 1 m:** the speaker reads the paragraph on screen in their normal teaching voice.
6. **Distance model** (mean error ≤ 3 dB)
   - In **Field test: voice by distance**, choose 3 m, hold the phone 3 m straight in front of the speaker at mouth height, and record the same paragraph. Repeat at 5 m and 8 m.
   - Read **Off by X dB on average**.
7. **Fan effect** (noise and clarity move the expected way)
   - At one seat, measure the noise with the fans **off**. On Results, note the seat's clarity and main cause.
   - Then **Re-measure after a change**, measure the same seat with the fans at **full speed** (clap and noise), and read the before/after card.
   - Expect: noise up, clarity down, and the main cause turning to "background noise" if the fans are loud.
   - Tap **Stop comparing** when done.
8. **What-if realism** (predicted echo-time change within ±25 % of measured)
   - With the room measured, tap **Re-measure after a change** on Results.
   - Hang the sheets or curtains and estimate their area (width × drop, in m²).
   - Measure the same seats again: claps and noise.
   - In **Before and after**, set **Curtains or sheets hung (m²)**, then read the predicted and measured echo times and the verdict.
9. **Listening check** (seats rated poorer get lower word scores)
   - Seat one volunteer at the front and one at the back.
   - The speaker reads the 20 words below once, at normal voice, from the usual spot. The volunteers write down what they hear.
   - Count each volunteer's correct words. On Results, tap their seat on the seat map and enter the count under **Field test: listening check**.
   - Read the verdict in the **Field test: listening check** card.
10. **Report:** tap **Share report (PDF)** and keep the PDF for the room. Its "How it was measured" lists the conditions the table needs.

**Once, in a classroom:** a **10-minute day log** (Log tab → Start the day log; phone on its charger at a back seat). It needs the timeline, the noise landscape and the labelled events.

**Usability** (≤ 10 minutes, NFR-1): give the phone to 3 people new to the app and time them measuring one room with on-screen guidance only. Afterwards, ask each to say the top fix in their own words.

## 20 words for the listening check

Short, common and different-sounding (read one every 2–3 seconds, without repeating):

> ship · moon · lamp · fork · rain · desk · bell · shoe · gold · path · tree · cup · nine · fish · wall · road · leaf · coat · pen · bird

## Results

Copy the app's numbers. A test that fails is still a result: write what happened.

| Room | Date | Self-test | Echo repeatability (worst CV, 3 seats) | Distance model (mean error) | Fan effect (cause flips?) | What-if realism (predicted vs measured change) | Listening check (agrees?) | Usability (time, top fix said?) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | | |
| | | | | | | | | |
| | | | | | | | | |
| | | | | | | | | |

Conditions for every row: phone (Samsung Galaxy S24 Ultra, SM-S928B), microphone input and sample rate (on the PDF), and whether the voice was measured or assumed.

**Before recording the demo video:** don't screen-record with the phone's microphone on. Android then gives ClearRoom silence, and every measurement fails.
