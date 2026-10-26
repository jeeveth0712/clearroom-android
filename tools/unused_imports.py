"""Remove Kotlin imports whose name never appears in the rest of the file. Delegate operators are kept."""
import pathlib
import re
import sys

KEEP = {"getValue", "setValue", "provideDelegate"}

for path in sys.argv[1:]:
    p = pathlib.Path(path)
    lines = p.read_text(encoding="utf-8").split("\n")
    body = "\n".join(l for l in lines if not l.startswith("import "))
    kept, removed = [], []
    for line in lines:
        if line.startswith("import "):
            name = line.split()[-1].split(".")[-1]
            if name not in KEEP and not re.search(r"\b" + re.escape(name) + r"\b", body):
                removed.append(name)
                continue
        kept.append(line)
    p.write_text("\n".join(kept), encoding="utf-8")
    print(path, "removed:", ", ".join(removed) or "nothing")
