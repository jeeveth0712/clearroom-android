"""Sort a Kotlin file's import block the ktlint way: everything else alphabetically, then java, javax, kotlin."""
import pathlib
import sys


def group(line):
    name = line[len("import "):]
    for rank, prefix in enumerate(("java.", "javax.", "kotlin.")):
        if name.startswith(prefix):
            return (rank + 1, name)
    return (0, name)


for path in sys.argv[1:]:
    p = pathlib.Path(path)
    lines = p.read_text(encoding="utf-8").split("\n")
    idx = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if not idx:
        continue
    first, last = idx[0], idx[-1]
    block = lines[first:last + 1]
    assert all(l.startswith("import ") for l in block), f"{path}: non-import line inside the import block"
    ordered = sorted(set(block), key=group)
    p.write_text("\n".join(lines[:first] + ordered + lines[last + 1:]), encoding="utf-8")
    print("sorted", path)
