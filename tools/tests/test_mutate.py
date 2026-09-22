"""`mutate`'s pure parts — run by the audit workflow (`python -m pytest tools/tests`).

Nothing here starts Gradle. What is tested is everything that decides *what* gets mutated, which
is where the mistakes were: a blanker that lost line numbers made one function's body swallow the
next three, and matching prose instead of code listed a `?: return` written in a sentence.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import mutate as mu


# --- the blanker --------------------------------------------------------------

def test_every_line_in_is_a_line_out():
    # The bug this exists for. `audit.strip_literals` eats a block comment's newlines, so
    # indexing its output against the raw lines drifts, and brace depth is read off the wrong
    # line — addEdge's body ran on past three later functions.
    src = [
        "fun a() {",
        "    /* two",
        "       lines */",
        "    doThing()",
        "}",
    ]
    assert len(mu.code_only(src)) == len(src)


def test_a_block_comment_is_blanked_across_lines():
    src = ["/* {{{ ", " still comment { ", " */ val x = 1"]
    out = mu.code_only(src)
    assert "{" not in out[0] and "{" not in out[1]
    assert "val x = 1" in out[2]


def test_a_line_comment_and_a_string_are_blanked():
    out = mu.code_only(['val s = "a { b"  // and { here', "fun f() {"])
    assert "{" not in out[0], out[0]
    assert "{" in out[1]


def test_a_char_literal_quote_does_not_swallow_the_line():
    out = mu.code_only(["val q = trim('\"') ; fun g() {"])
    assert "{" in out[0]


# --- what counts as a guard ---------------------------------------------------

def test_the_guard_shapes_this_repo_actually_uses():
    assert mu.GUARD.match("        if (locked()) return")
    assert mu.GUARD.match("    if (entryId < 0 || action == null) return")
    assert mu.GUARD.match("            if (x) return@launchAndTouch")
    assert mu.GUARD.match("        if (page.uid !in wonUids) continue")
    assert mu.GUARD.match("        if (a) break")
    assert mu.GUARD.match("        if (locked()) return  // §3.1.2")


def test_a_multi_line_guard_is_not_matched():
    # Deleting `if (cond) {` alone leaves an orphan block and a compile error, which would be
    # reported as INVALID and tell nobody anything.
    assert not mu.GUARD.match("        if (locked()) {")
    assert mu.UNSUPPORTED.search("        if (locked()) {")


def test_an_elvis_return_is_flagged_unsupported_rather_than_mutated():
    # Deleting the line would remove a binding the rest of the body uses.
    assert not mu.GUARD.match("        val id = canvas.value?.id ?: return")
    assert mu.UNSUPPORTED.search("        val id = canvas.value?.id ?: return")


# --- finding the declaration and its body -------------------------------------

def test_a_body_ends_at_its_own_closing_brace(tmp_path):
    p = tmp_path / "X.kt"
    p.write_text(
        "fun first() {\n"
        "    if (a) return\n"
        "}\n"
        "\n"
        "fun second() {\n"
        "    if (b) return\n"
        "}\n",
        encoding="utf-8",
    )
    lines = p.read_text(encoding="utf-8").split("\n")
    blanked = mu.code_only(lines)
    depth, started, end = 0, False, None
    for j in range(0, len(lines)):
        depth += blanked[j].count("{") - blanked[j].count("}")
        if "{" in blanked[j]:
            started = True
        if started and depth <= 0:
            end = j
            break
    assert end == 2, "first()'s body must close at its own brace, not at second()'s"


def test_a_brace_in_a_string_does_not_hold_a_body_open():
    lines = ['fun f() {', '    val s = "{"', "}"]
    blanked = mu.code_only(lines)
    assert blanked[1].count("{") == 0


def test_a_brace_in_kdoc_does_not_open_a_body():
    lines = ["/** see {@link X} and { */", "val top = 1"]
    blanked = mu.code_only(lines)
    assert blanked[0].count("{") == 0


# --- against the real tree ----------------------------------------------------

def test_the_real_lock_guard_is_found_and_the_kdoc_around_it_is_not():
    hit = mu.find_declaration("launchAndTouch")
    assert hit, "CanvasViewModel.launchAndTouch went missing"
    path, start, end = hit
    guards, skipped = mu.guards_in(path, start, end)
    assert [t for _, t in guards] == ["if (locked()) return"], guards
    # The body is a handful of lines; a range that swallowed the next functions would drag in
    # their elvis returns as "skipped".
    assert end - start < 15, "the body range is over-running again (%d lines)" % (end - start)


def test_a_symbol_resolves_to_the_test_classes_that_name_it():
    unit, _instrumented = mu.test_classes_naming("launchAndTouch")
    assert any(c.endswith("ViewOnlySurfacesGuardTest") for c in unit), unit
    assert all("." in c for c in unit), "classes must be fully qualified for --tests"


def test_a_symbol_nothing_names_returns_no_classes():
    assert mu.test_classes_naming("aSymbolNoTestCouldPossiblyMention") == ([], [])


def test_an_instrumented_only_symbol_is_not_reported_as_unit_coverage():
    # The distinction this split exists for. `OverdueAlarmReceiver` is named by
    # `AlarmSchedulerInstrumentedTest` and by nothing in the JVM suite, so `testDebugUnitTest`
    # cannot kill a mutation in it. Reporting a flat "no tests" would be wrong, and reporting it
    # as covered would be worse — a SURVIVED would then look like a finding when it is an
    # artefact of which suite was run.
    unit, instrumented = mu.test_classes_naming("OverdueAlarmReceiver")
    assert unit == [], unit
    assert any(c.endswith("AlarmSchedulerInstrumentedTest") for c in instrumented), instrumented


def test_a_symbol_with_neither_is_distinguishable_from_one_with_instrumented_only():
    # `BootCompletedReceiver` has nothing in either suite — §9.7's boot re-arm, the thing that
    # stops every reminder silently dying at the next restart, is named by no test at all.
    assert mu.test_classes_naming("BootCompletedReceiver") == ([], [])


def test_the_notification_action_path_is_no_longer_uncovered():
    # This asserted `EntryActionReceiver` had nothing anywhere, which was true when it was
    # written and is the reason §3.6's hole survived from 2026-09-06: nothing could have caught
    # it. Kept, inverted, as the regression guard — if this list empties again, the App Lock
    # refusal on the overdue notification has lost its only test.
    unit, _ = mu.test_classes_naming("EntryActionReceiver")
    assert any(c.endswith("NotificationActionAppLockTest") for c in unit), unit


# --- guards that return a value -----------------------------------------------

def test_a_guard_may_return_a_value():
    # `if (appLockEnabled) return false` planned nothing while GUARD required a bare `return`,
    # and the tool said "no single-line guard in its body" — a blind spot dressed as a clean
    # result. Every shape below is a real guard somewhere in this repo.
    assert mu.GUARD.match("    if (appLockEnabled) return false")
    assert mu.GUARD.match("        val x = 1; if (a) return null" .replace("val x = 1; ", ""))
    assert mu.GUARD.match("        if (a) return emptyList()")
    assert mu.GUARD.match("            if (a) return@launch null")


def test_a_returning_guard_is_still_distinguished_from_a_block():
    assert not mu.GUARD.match("        if (locked()) {")
    assert not mu.GUARD.match("        if (a) returnSomething()")


# --- the owner, for scoping ---------------------------------------------------

def test_the_owner_is_the_enclosing_type_not_the_first_in_the_file():
    # SnapshotSyncOrchestrator.kt opens with `data class SnapshotMergeResult`. Taking the first
    # top-level declaration attributed every symbol in the file to that, so the scope missed the
    # suites that construct the orchestrator.
    hit = mu.find_declaration("mergeEntryContent")
    assert hit, "the merge helper went missing"
    path, start, _end = hit
    assert mu.owner_of(path, start) == "SnapshotSyncOrchestrator"


def test_a_private_helper_is_scoped_through_its_owner():
    # The bug that produced a false SURVIVED for the last-write-wins guard: the tests that
    # exercise a private helper call the public entry point and never name the helper.
    hit = mu.find_declaration("mergeEntryContent")
    path, start, _end = hit
    own, _ = mu.test_classes_naming("mergeEntryContent")
    via_owner, _ = mu.test_classes_naming(mu.owner_of(path, start))
    combined = set(own) | set(via_owner)
    assert len(combined) > len(own), "the owner added nothing; scoping is back to the helper alone"
    assert any(c.endswith("MergeTouchedEntriesTest") for c in combined), sorted(combined)
