"""The classifier's rules, one fixture each — run by the audit workflow (`python -m pytest tools/tests`)."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import type_sites as ts


def sites(src: str):
    out = ts._scan(src, "x.kt")
    for s in out: s.cls = ts.classify(s)
    return out


def one(src: str):
    got = sites(src)
    assert len(got) == 1, got
    return got[0]


def test_chip_label_is_a_slot():
    s = one('FilterChip(selected = true, onClick = {}, label = { Text("Compact") })')
    assert s.cls == "SLOT_CHIP" and s.style is None and s.slot == "FilterChip.label"


def test_menu_item_and_dialog_title_are_slots():
    got = sites('DropdownMenuItem(text = { Text("History") }, onClick = {})\nAlertDialog(title = { Text("Add task") }, text = { Text("Body") }, onDismissRequest = {})')
    assert [s.cls for s in got] == ["SLOT_MENU", "SLOT_DIALOG_TITLE", "SLOT_DIALOG_TEXT"]


def test_row_title_and_meta():
    src = '''Row { Text(entry.title, style = MaterialTheme.typography.body)
    Text(subtitle, style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }'''
    a, b = sites(src)
    assert (a.cls, a.index) == ("TITLE", 0) and (b.cls, b.index, b.color) == ("META", 1, "onSurfaceVariant")


def test_second_line_in_a_column_is_meta_and_keeps_its_raw_role():
    src = '''Column { Text(page.title, style = MaterialTheme.typography.bodyMedium)
    Text("edited " + relativeTime(page.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }'''
    a, b = sites(src)
    assert a.raw_style == "bodyMedium" and b.cls == "META" and b.content == "TIME_DATE"


def test_explainer_empty_state_and_grey_label():
    src = '''Column {
    Text("Every measurement scales with the window's shorter side; this sets how much room a row gets.", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
Box { Text("Nothing unscheduled", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
Box { Text("Title", style = MaterialTheme.typography.label, color = MaterialTheme.colorScheme.onSurfaceVariant) }'''
    assert [s.cls for s in sites(src)] == ["EXPLAINER", "EMPTY_STATE", "GREY_LABEL"]


def test_eyebrow_by_case_and_by_uppercase_call():
    got = sites('Column { Text("STEPS", style = MaterialTheme.typography.eyebrow)\nText(label.uppercase(), style = MaterialTheme.typography.eyebrow) }')
    assert [s.cls for s in got] == ["EYEBROW", "EYEBROW"]


def test_subsection_heading_and_sheet_header():
    src = '''Column { Text("Register", style = MaterialTheme.typography.labelLarge)
    Text("Opens on", style = MaterialTheme.typography.heading) }
Row { Text("Trash", style = MaterialTheme.typography.titleMedium); IconButton(onClick = {}) {} }'''
    assert [s.cls for s in sites(src)] == ["SUBSECTION", "SECTION_HEADING", "SHEET_HEADER"]


def test_explicit_annotation_wins_and_fields_are_fields():
    src = '''Row { Text(timer.title, style = MaterialTheme.typography.body, color = colour) // type: ICON_LABEL
}
BasicTextField(value = v, onValueChange = {}, textStyle = MaterialTheme.typography.bodyMedium.copy(color = c))'''
    a, b = sites(src)
    assert a.cls == "ICON_LABEL" and a.explicit and b.cls == "FIELD" and b.kind == "field" and b.raw_style == "bodyMedium"


def test_literal_choice_reads_as_a_literal():
    s = one('Column { Text(if (viewOnly) "Card" else "Edit card", style = MaterialTheme.typography.heading) }')
    assert s.content == "LITERAL" and s.cls == "SECTION_HEADING"


def test_violations_against_a_table():
    src = '''Row { Text(entry.title, style = MaterialTheme.typography.body)
    Text(meta, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
FilterChip(selected = true, onClick = {}, label = { Text("Compact") })'''
    got = sites(src)
    v = ts.violations(got, {"TITLE": "body", "META": "caption", "SLOT_CHIP": "label"})
    assert len(v) == 1 and "META takes caption" in v[0]
