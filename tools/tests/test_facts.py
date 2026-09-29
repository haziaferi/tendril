"""`facts.py` — the figures the docs cite, measured instead of remembered.

Each counter is tested on a fixture whose answer is known, including the case that fooled a
person before: a CRLF file among LF ones, a row whose status is "reported, not changed" (proved
but unfixed — neither open nor fixed), and a JUnit file with failures.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import facts


def write(root, rel, data: bytes):
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "wb") as f:
        f.write(data)
    return rel


def test_line_endings_split_lf_from_crlf_and_skip_other_extensions(tmp_path):
    root = str(tmp_path)
    files = [
        write(root, "a.kt", b"fun a() {}\nfun b() {}\n"),
        write(root, "b.md", b"# t\r\nline\r\n"),
        write(root, "gradlew.bat", b"@echo off\r\n"),          # not a counted extension
        write(root, "c.py", b"x = 1"),                           # no newline at all: not CRLF
    ]
    lf, crlf = facts.line_endings(root, files)
    assert lf == ["a.kt", "c.py"]
    assert crlf == ["b.md"]


def test_audit_rows_separate_unproven_from_proved_but_unfixed(tmp_path):
    doc = tmp_path / "audit.md"
    doc.write_text(
        "| # | Where | Finding | Status | Proof |\n"
        "|---|---|---|---|---|\n"
        "| 1.1 | `a` | x | executed — fixed | t |\n"
        "| 1.2 | `b` | y | hypothesis (low) | walk |\n"
        "| 1.3 | `c` | z | executed — reported, not changed | cmd |\n"
        "| 1.4 | `d` | w | executed — INFO, not changed | cmd |\n"
        "| 2.1 | `e` | v | **executed — confirmed; fixed** | t |\n"
        "not a row | 9.9 |\n",
        encoding="utf-8")
    rows = facts.audit_rows(str(doc))
    assert rows["total"] == 5
    assert rows["hypothesis"] == ["1.2"]
    assert rows["not_changed"] == ["1.3", "1.4"]
    assert rows["fixed_or_clean"] == 2


def test_a_hypothesis_table_is_read_by_its_finding_not_by_what_would_settle_it(tmp_path):
    # Found 2026-09-29: an audit's hypothesis table has no Status column — its fourth column is
    # "Would settle it" — so reading that column as the status counted every open hypothesis as
    # fixed, and a remedy that said "not changed from here" as proved-but-unfixed.
    doc = tmp_path / "audit.md"
    doc.write_text(
        "| # | Where | Finding | Status | Proof |\n"
        "|---|---|---|---|---|\n"
        "| 1.1 | `a` | x | executed — fixed | t |\n"
        "\n"
        "| # | Where | Finding | Would settle it |\n"
        "|---|---|---|---|\n"
        "| 5.1 | `b` | y. **Fixed 2026-09-26:** z | a test |\n"
        "| 5.2 | `c` | open, nothing done | a walk (the setting is not changed from here) |\n"
        "| 5.3 | `d` | **Pinned, not changed:** the ratio | a test |\n"
        "| 5.4 | `e` | **Fixed:** w. Recorded, not changed: v | t |\n"
        # Found the same day: a hypothesis whose prose recalls an earlier flake that was "fixed"
        # read as fixed. The status is the bold marker a row is amended with, not any word in it.
        "| 5.5 | `f` | red once; an earlier flake was fixed by a reset | a loop |\n",
        encoding="utf-8")
    rows = facts.audit_rows(str(doc))
    assert rows["total"] == 6
    assert rows["hypothesis"] == ["5.2", "5.5"]
    assert rows["not_changed"] == ["5.3"]
    assert rows["fixed_or_clean"] == 3


def test_junit_counts_sum_tests_and_failures(tmp_path):
    root = str(tmp_path)
    write(root, "r/TEST-a.xml", b'<testsuite name="a" tests="3" skipped="0" failures="1" errors="0">')
    write(root, "r/TEST-b.xml", b'<testsuite name="b" tests="4" skipped="0" failures="0" errors="0">')
    files, tests, failures, newest = facts.junit_counts(os.path.join(root, "r", "*.xml"))
    assert (files, tests, failures) == (2, 7, 1)
    assert newest > 0


def test_junit_counts_on_a_missing_directory_is_zero_not_an_error(tmp_path):
    assert facts.junit_counts(os.path.join(str(tmp_path), "none", "*.xml"))[:3] == (0, 0, 0)


def test_schema_version_reads_the_database_annotation(tmp_path):
    kt = tmp_path / "Db.kt"
    kt.write_text('@Database(\n    entities = [A::class],\n    version = 25, // v25 block_fts\n)\n',
                  encoding="utf-8")
    assert facts.schema_version(str(kt)) == 25


def test_the_real_tree_answers(tmp_path):
    # Not vacuous: the repository's own figures resolve, and the two that have been
    # misquoted in prose (tracked files, LF files) are positive and consistent.
    f = facts.gather(facts.ROOT)
    assert f["tracked"] > 0
    assert len(f["lf"]) + len(f["crlf"]) <= f["tracked"]
    assert f["schema_version"] >= 25
    assert f["audit"]["total"] > 0
