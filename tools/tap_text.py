"""Tap the on-screen element whose text matches, using a fresh uiautomator dump.

Usage: python -I tools/tap_text.py "Load sample room"   (the phone must be connected over adb)
"""
import os
import re
import subprocess
import sys
import time

# adb from ANDROID_HOME (the owner's is D:/Android/Sdk); falls back to adb on PATH.
ADB = os.path.join(os.environ["ANDROID_HOME"], "platform-tools", "adb") if "ANDROID_HOME" in os.environ else "adb"
env = dict(os.environ, MSYS_NO_PATHCONV="1")


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", env=env).stdout


def find(text):
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    for node in re.findall(r"<node [^>]*>", xml):
        if f'text="{text}"' in node:
            x0, y0, x1, y1 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
            return (x0 + x1) // 2, (y0 + y1) // 2
    return None


def clearroom_in_front() -> bool:
    """Only ever tap or scroll inside ClearRoom, never in whatever else the owner has open."""
    activities = adb("shell", "dumpsys", "activity", "activities")
    top = next((line for line in activities.splitlines() if "topResumedActivity" in line), "")
    return "com.clearroom.app/" in top


def main():
    target = sys.argv[1]
    if not clearroom_in_front():
        print("ClearRoom isn't the app in front; start it first (am start -n com.clearroom.app/.MainActivity)")
        sys.exit(2)
    for _ in range(6):  # scroll down a little between tries
        spot = find(target)
        if spot:
            adb("shell", "input", "tap", str(spot[0]), str(spot[1]))
            print(f"tapped '{target}' at {spot}")
            time.sleep(1.5)
            return
        adb("shell", "input", "swipe", "720", "1400", "720", "2000", "300")  # scroll up towards the top
        time.sleep(1)
    print(f"'{target}' not found")
    sys.exit(1)


main()
