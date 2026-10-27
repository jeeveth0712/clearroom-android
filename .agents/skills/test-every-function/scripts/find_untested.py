"""List Kotlin functions and classes in production code that no test mentions.

A function counts as "referenced" when its name appears as an identifier in any
test source file (unit or instrumented). It's a name-based heuristic: it finds
functions with no test at all, but can't prove that a referenced function is
well tested. Composables are listed separately because they're covered by UI
tests or by testing their model mappers.

Usage (from the project root, e.g. ClearRoom/):
    python -I find_untested.py                      # defaults: app/src/main -> app/src/test + app/src/androidTest
    python -I find_untested.py --include-private    # also list private functions
    python -I find_untested.py --strict             # exit code 1 if anything is untested (for hooks/CI)
"""
import argparse
import re
import sys
from pathlib import Path

MODIFIERS = (
    "public|internal|private|protected|override|suspend|inline|operator|infix|"
    "tailrec|external|open|abstract|final|actual|expect"
)
FUN_RE = re.compile(
    r"^\s*((?:(?:" + MODIFIERS + r")\s+)*)fun\s+(?:<[^>]*>\s+)?(?:[\w.<>?,* ]+\.)?(`[^`]+`|\w+)\s*\("
)
CLASS_RE = re.compile(
    r"^\s*(?:(?:public|internal|private|data|sealed|abstract|open|enum|value|inner)\s+)*"
    r"(class|object|interface)\s+(\w+)"
)
ANNOTATION_RE = re.compile(r"^\s*@(\w+)")
SKIPPED_NAMES = {"main", "toString", "equals", "hashCode"}


def kotlin_files(root: Path):
    return sorted(p for p in root.rglob("*.kt") if p.is_file()) if root.exists() else []


def test_identifiers(test_roots):
    words = set()
    for root in test_roots:
        for path in kotlin_files(root):
            words.update(re.findall(r"`[^`]+`|\b\w+\b", path.read_text(encoding="utf-8", errors="replace")))
    return words


def scan(main_root: Path, words, include_private: bool, include_overrides: bool):
    untested, composables, classes_without_test = [], [], []
    for path in kotlin_files(main_root):
        rel = path.relative_to(main_root)
        pending_annotations = []
        for line_no, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            annotation = ANNOTATION_RE.match(line)
            if annotation and "fun " not in line:
                pending_annotations.append(annotation.group(1))
                continue
            klass = CLASS_RE.match(line)
            if klass and "private" not in line.split(klass.group(1))[0]:
                name = klass.group(2)
                if name not in words and f"{name}Test" not in words:
                    classes_without_test.append((rel, line_no, name))
            fun = FUN_RE.match(line)
            if fun:
                modifiers, name = fun.group(1).split(), fun.group(2)
                annotations = pending_annotations + re.findall(r"@(\w+)", line.split("fun ")[0])
                pending_annotations = []
                if "Preview" in annotations or name in SKIPPED_NAMES:
                    continue
                if "private" in modifiers and not include_private:
                    continue
                if "override" in modifiers and not include_overrides:
                    continue
                if "Composable" in annotations:
                    if name not in words:
                        composables.append((rel, line_no, name))
                    continue
                if name not in words:
                    untested.append((rel, line_no, name))
            elif line.strip() and not line.strip().startswith(("//", "*", "/*")):
                pending_annotations = []
    return untested, composables, classes_without_test


def report(title, rows):
    print(f"\n{title}: {len(rows)}")
    for rel, line_no, name in rows:
        print(f"  {rel}:{line_no}  {name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--main", default="app/src/main", help="production source root")
    parser.add_argument("--tests", nargs="+", default=["app/src/test", "app/src/androidTest"])
    parser.add_argument("--include-private", action="store_true")
    parser.add_argument("--include-overrides", action="store_true")
    parser.add_argument("--strict", action="store_true", help="exit 1 when anything is untested")
    args = parser.parse_args()

    main_root = Path(args.main)
    if not main_root.exists():
        sys.exit(f"Production source root not found: {main_root.resolve()}")
    words = test_identifiers([Path(t) for t in args.tests])
    untested, composables, classes = scan(main_root, words, args.include_private, args.include_overrides)

    report("Functions no test mentions", untested)
    report("Composables no UI test mentions (test their model mapper or add a UI test)", composables)
    report("Classes/objects no test mentions", classes)
    total = len(untested) + len(classes)
    print(f"\nSummary: {total} untested function(s)/class(es), {len(composables)} composable(s) without UI tests.")
    if args.strict and (total or composables):
        sys.exit(1)


if __name__ == "__main__":
    main()
