#!/usr/bin/env python3
"""Guard the repository language policy written down in AGENTS.md.

Docs stay Chinese; source paths and git history stay English. Checking it here
keeps the split from drifting back into a mix: a Chinese commit subject or a
renamed source directory fails the build instead of being noticed months later
in a pull request.

Exit code 0 means the policy holds, 1 means it does not, 2 means the script
could not do its job (a git call failed), which must not be reported as a pass.
"""

import argparse
import re
import subprocess
import sys

# The ranges cover CJK unified ideographs plus the compatibility and extension A
# blocks, because a "Chinese" file name can also use the rarer forms.
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")

# Source trees and build metadata: everything except docs/, which is Chinese by
# design, and the generated or local directories excluded from checks below.
CHECKED_PREFIXES = ("src/", "tools/", "gradle/", ".github/")
CHECKED_ROOT_FILES = (
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
)


def git(*args):
    result = subprocess.run(
        ["git", *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        print(f"git {' '.join(args)} failed: {result.stderr.strip()}", file=sys.stderr)
        raise SystemExit(2)
    return result.stdout


def is_checked_path(path):
    return path.startswith(CHECKED_PREFIXES) or path in CHECKED_ROOT_FILES


def check_paths():
    problems = []
    # core.quotepath=false keeps CJK names in their readable form; with the
    # default they arrive octal-escaped and would slip past the pattern.
    for path in git("-c", "core.quotepath=false", "ls-files", "-z").split("\0"):
        if path and is_checked_path(path) and CJK.search(path):
            problems.append(path)
    return [f"source path is not English: {path}" for path in problems]


def check_messages(rev_range):
    # Fields are separated by \x1f and records by \x1e so that subjects and
    # bodies can hold any whitespace without breaking the parse.
    raw = git("log", "--format=%H%x1f%s%x1f%b%x1e", rev_range)
    problems = []
    for record in raw.split("\x1e"):
        record = record.strip("\n")
        if not record:
            continue
        full_sha, subject, body = (record.split("\x1f") + ["", ""])[:3]
        short = full_sha[:12]
        if CJK.search(subject):
            problems.append(f"commit {short} has a non-English subject: {subject}")
        for line in body.splitlines():
            if CJK.search(line):
                problems.append(f"commit {short} has a non-English body line: {line}")
                break
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--range",
        dest="rev_range",
        default="",
        help="git revision range to check commit messages in, e.g. abc123..HEAD",
    )
    parser.add_argument(
        "--paths-only",
        action="store_true",
        help="skip the commit message check, useful where history is not available",
    )
    args = parser.parse_args()

    problems = check_paths()
    checked_range = args.rev_range
    if not args.paths_only and checked_range:
        problems.extend(check_messages(checked_range))

    for problem in problems:
        print(problem, file=sys.stderr)
    if problems:
        print(
            f"{len(problems)} language policy violation(s); see AGENTS.md, section 语言约定",
            file=sys.stderr,
        )
        return 1
    scope = "source paths" if args.paths_only or not checked_range else f"source paths and commits in {checked_range}"
    print(f"language policy holds for {scope}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
