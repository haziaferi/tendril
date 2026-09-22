"""`spec_trace`'s two hard parts — run by the audit workflow (`python -m pytest tools/tests`).

The hard parts are not the counting. They are (1) a stripper that has to keep comments while
blanking strings, in a codebase where a KDoc line carrying one unbalanced quote is ordinary,
and (2) an exclusion list that must drop history without dropping promises.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import spec_trace as st

SECTION = "§"


def cited(src: str):
    return [m.group(1) for m in st.CITATION.finditer(st.strip_strings(src))]


# --- the stripper -------------------------------------------------------------

def test_a_citation_in_a_kdoc_block_counts():
    # Where almost all 1,852 of them live.
    assert cited("/** %s9.7 — every write that moves the next due time re-arms. */\nfun f() {}" % SECTION) == ["9.7"]


def test_a_citation_in_a_line_comment_counts():
    assert cited("fun f() {} // %s3.2 the Provider half" % SECTION) == ["3.2"]


def test_a_citation_inside_a_runtime_string_does_not():
    # A user-facing message naming a section is not the code implementing it.
    assert cited('val msg = "see %s9.4 for details"' % SECTION) == []


def test_an_unbalanced_quote_in_a_comment_does_not_swallow_the_file():
    # PageCanvas.kt, Habit.kt, Block.kt and five others each have a line of this shape.
    # A stripper that did not skip comments would read that quote as opening a literal
    # and lose every citation after it.
    src = (
        '/** structure" (%s5.2\'s Database is the same shape) */\n'
        "fun a() {}\n"
        "/** %s6.1 — the dividing test. */\n"
        "fun b() {}\n" % (SECTION, SECTION)
    )
    assert cited(src) == ["5.2", "6.1"]


def test_a_char_literal_quote_does_not_swallow_the_file():
    # The `trim('"')` case that once made every declaration after Ics.kt read as dead.
    src = "val q = trim('\"')\n/** %s9.10 the migration policy. */\nfun f() {}" % SECTION
    assert cited(src) == ["9.10"]


def test_a_raw_string_is_blanked_but_what_follows_survives():
    src = 'val sql = """SELECT %s1"""\n// %s5.5.1 trash\n' % (SECTION, SECTION)
    assert cited(src) == ["5.5.1"]


# --- claims and sections ------------------------------------------------------

def test_normative_words_are_recognised_and_discussion_is_not():
    assert st.NORMATIVE.search("the app must arm the alarm")
    assert st.NORMATIVE.search("a phantom occurrence is never emitted")
    assert st.NORMATIVE.search("every write re-arms")
    # "should" and "may" are discussion; a spec this long is full of both.
    assert not st.NORMATIVE.search("this should probably be revisited")
    assert not st.NORMATIVE.search("we may want a second view here")


def test_the_real_spec_parses_and_carries_claims():
    secs = st.read_sections()
    by_num = {s.num: s for s in secs}
    assert "9.7" in by_num, "the notification-reliability section went missing"
    assert by_num["9.7"].claims, "a section with a stated invariant parsed with no claims"
    assert by_num["9.7"].spec.endswith("tendril-spec.md")


def test_a_fenced_code_block_is_not_counted_as_claims():
    # Otherwise a section is weighted by how much code it quotes.
    secs = st.read_sections()
    gate = [s for s in secs if s.num == "9.2"]
    assert gate, "section 9.2 vanished"
    for _, text in gate[0].claims:
        assert not text.startswith("```")


# --- the exclusion list -------------------------------------------------------

def test_document_notes_is_excluded():
    secs = st.read_sections()
    assert [s for s in secs if s.num == "11"][0].meta


def test_open_items_is_excluded_and_so_are_its_children():
    assert "0.10" in st.META
    secs = {s.num: s for s in st.read_sections()}
    assert secs["0.10"].meta


def test_decisions_of_2026_09_11_is_deliberately_kept():
    # The carve-out that matters. Its rows carry "Acceptance:" criteria and are the most
    # mechanically checkable text in the document; excluding the whole of section 0 to be
    # tidy would throw away the best material in it.
    secs = {s.num: s for s in st.read_sections()}
    assert not secs["0.6"].meta
    assert secs["0.6"].claims


def test_meta_sections_are_kept_out_of_the_worklist_but_reachable():
    secs = st.read_sections()
    src, test = st.Counter(), st.Counter()
    default = {r["section"] for r in st.rows(secs, src, test)}
    with_meta = {r["section"] for r in st.rows(secs, src, test, keep_meta=True)}
    assert "11" not in default
    assert "11" in with_meta


# --- rolling up ---------------------------------------------------------------

def test_a_child_citation_counts_for_its_parent():
    c = st.Counter({"9.4.1": 3, "9.4": 1, "9.41": 99})
    assert st.roll(c, "9.4") == 4, "9.41 is a different section, not a child of 9.4"
    assert st.roll(c, "9.4.1") == 3
