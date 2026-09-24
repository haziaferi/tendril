"""`audit.py` check 10 — the write-only entity field and its baseline.

The baseline's rule was "this set may only SHRINK — an entry goes when its field gains a
reader", and nothing enforced it: the baseline was consulted only for a field with no reads, so
an entry whose field had gained one was invisible by construction. By 2026-09-24 three of its
four entries described fields five screens were reading ("nothing draws it", "renders
nowhere"). These cases pin both directions, plus the name collision that makes the read test
coarse.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import audit

ENTITY = '''@Entity(tableName = "labels")
data class Label(
    @PrimaryKey val id: Long,
    val name: String,
    val color: Int,
)
'''


def run(srcs, baseline=frozenset()):
    return audit.write_only_fields(srcs, baseline=set(baseline), off_language=set())


def test_an_unread_field_is_a_finding():
    found, stale = run({"shared/data/Label.kt": ENTITY,
                        "shared/ui/A.kt": "fun a(l: Label) = l.name + l.id"})
    assert found == ["shared/data/Label.kt  Label.color"]
    assert stale == []


def test_a_baselined_unread_field_is_not_a_finding():
    found, stale = run({"shared/data/Label.kt": ENTITY,
                        "shared/ui/A.kt": "fun a(l: Label) = l.name + l.id"}, baseline={"Label.color"})
    assert found == [] and stale == []


def test_a_baselined_field_that_gained_a_reader_is_reported_stale():
    # The case the old code could not see.
    found, stale = run({"shared/data/Label.kt": ENTITY,
                        "shared/ui/A.kt": "fun a(l: Label) = l.name + l.id + l.color"},
                       baseline={"Label.color"})
    assert found == []
    assert stale == ["Label.color"]


def test_a_baseline_entry_naming_no_entity_field_is_reported_stale():
    # A renamed or deleted field leaves an entry that silences nothing.
    found, stale = run({"shared/data/Label.kt": ENTITY,
                        "shared/ui/A.kt": "fun a(l: Label) = l.name + l.id + l.color"},
                       baseline={"Label.colour"})
    assert stale == ["Label.colour"]


def test_reads_in_tests_and_mappers_do_not_count():
    found, _ = run({"shared/data/Label.kt": ENTITY,
                    "shared/test/LabelTest.kt": "fun t(l: Label) = l.color",
                    "shared/sync/SnapshotMappers.kt": "fun m(l: Label) = l.color",
                    "shared/ui/A.kt": "fun a(l: Label) = l.name + l.id"})
    assert found == ["shared/data/Label.kt  Label.color"]


def test_the_real_baseline_has_no_stale_entry():
    # The tree itself: every baselined field is still write-only by the check's own measure.
    files = audit.walk((".kt", ".kts"))
    srcs = {audit.rel(f): open(f, encoding="utf-8").read() for f in files}
    _, stale = audit.write_only_fields(srcs)
    assert stale == []


# --- check 6 and check 19: spellings that used to slip past ---------------------------------

def test_a_leaked_state_flow_is_caught_with_an_exposing_modifier():
    for line in ("    val items: StateFlow<List<Item>> = _items",
                 "    override val items: StateFlow<List<Item>> = _items",
                 "    internal val items: StateFlow<Int> = _items",
                 "    public open val items: StateFlow<Int> = _items"):
        assert audit.LEAKED_FLOW.match(line), line


def test_a_private_or_wrapped_state_flow_is_not_a_leak():
    for line in ("    private val items: StateFlow<Int> = _items",
                 "    val items: StateFlow<Int> = _items.asStateFlow()"):
        assert not audit.LEAKED_FLOW.match(line), line


def test_max_lines_one_is_found_however_it_is_spaced():
    for text in ("maxLines = 1", "maxLines=1", "maxLines =1", "maxLines  =  1"):
        assert audit.MAX_LINES_ONE.search(text), text
    assert not audit.MAX_LINES_ONE.search("maxLines = 12")


# --- check 9: the imported function used as a value --------------------------------------------

IMPORT = "import androidx.lifecycle.viewmodel.compose.viewModel\n"


def shadowed(src):
    return audit.imported_name_shadowed(src, audit.strip_literals(src))


def test_top_level_block_function_is_caught():
    # The 2026-09-04 shape: the one this check was written for.
    assert shadowed(IMPORT + "fun Row(ids: List<Long>) {\n    viewModel.deleteForever(ids)\n}\n") == [(2, "viewModel")]


def test_member_function_is_caught():
    src = IMPORT + "class Screen {\n    fun onDelete(ids: List<Long>) {\n        viewModel.deleteForever(ids)\n    }\n}\n"
    assert shadowed(src) == [(3, "viewModel")]


def test_expression_bodied_function_is_caught():
    src = IMPORT + "class Screen {\n    override fun onDelete(ids: List<Long>) =\n        viewModel.deleteForever(ids)\n}\n"
    assert shadowed(src) == [(3, "viewModel")]


def test_a_parameter_a_local_or_a_lambda_binding_is_not_shadowed():
    assert shadowed(IMPORT + "fun Row(viewModel: Vm) {\n    viewModel.deleteForever(ids)\n}\n") == []
    assert shadowed(IMPORT + "fun Row() {\n    val viewModel = vm()\n    viewModel.deleteForever(ids)\n}\n") == []
    assert shadowed(IMPORT + "fun Row() {\n    vms.forEach { viewModel -> viewModel.go() }\n}\n") == []


def test_a_class_property_or_constructor_parameter_is_in_scope_for_members():
    prop = IMPORT + "class Screen {\n    private val viewModel = Vm()\n    fun onDelete() {\n        viewModel.go()\n    }\n}\n"
    ctor = IMPORT + "class Screen(private val viewModel: Vm) {\n    fun onDelete() = viewModel.go()\n}\n"
    assert shadowed(prop) == []
    assert shadowed(ctor) == []


def test_a_call_of_the_imported_function_is_not_a_value_use():
    assert shadowed(IMPORT + "fun Row() {\n    val vm: Vm = viewModel()\n    vm.go()\n}\n") == []


def test_an_imported_extension_property_is_not_a_function():
    # `viewModelScope` is imported lowercase but is a property: the tree never calls it bare,
    # so it is not considered. `viewModel` is called bare, so it still is.
    src = ("import androidx.lifecycle.viewModelScope\n" + IMPORT +
           "class Vm {\n    fun go() {\n        viewModelScope.launch { }\n        viewModel.x()\n    }\n}\n")
    called = audit.bare_calls(audit.strip_literals("val a = viewModel()\n// viewModelScope(\n"))
    assert "viewModelScope" not in called
    assert audit.imported_name_shadowed(src, audit.strip_literals(src), called) == [(4, "viewModel")]
