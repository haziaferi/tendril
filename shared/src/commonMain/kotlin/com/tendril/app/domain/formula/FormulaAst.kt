package com.tendril.app.domain.formula

/**
 * §5.4/DB3 — the parsed shape of a formula. [PropertyRef] is its own node rather than a generic
 * [Call] whose name happens to be `"prop"`, because two other pieces need to find every property
 * reference in an expression without knowing anything about function-call syntax in general:
 * [topologicallySortFormulas] (which references land on another `COMPUTED` property, for cycle
 * detection) and the type checker (each reference's declared type, from the property it names).
 * Every node keeps the source [position] the parser saw it at, so a type or reference error can
 * point at the exact character rather than "somewhere in this formula."
 */
sealed class FormulaAst {
    abstract val position: Int

    data class NumberLit(val value: Double, override val position: Int) : FormulaAst()
    data class StringLit(val value: String, override val position: Int) : FormulaAst()
    data class BoolLit(val value: Boolean, override val position: Int) : FormulaAst()

    /** `prop("Name")` — [propertyName] is a display name, not a uid, matching §5.4's own
     * example (`prop("A") + prop("B")`) and the fact a formula is meant to be typed and read by
     * a person, not addressed the way sync-layer records are. The real cost of that choice —
     * renaming a property silently breaks every formula naming its old name — is the same
     * tradeoff Notion's own formula language makes, not an oversight; §5.4 wrote the example
     * this way rather than `prop(uid)`. */
    data class PropertyRef(val propertyName: String, override val position: Int) : FormulaAst()

    data class Call(val functionName: String, val args: List<FormulaAst>, override val position: Int) : FormulaAst()
    data class UnaryOp(val op: String, val operand: FormulaAst, override val position: Int) : FormulaAst()
    data class BinaryOp(val op: String, val left: FormulaAst, val right: FormulaAst, override val position: Int) : FormulaAst()
}

/** Every `prop(...)` reference in an expression, depth-first — used by both
 * [topologicallySortFormulas] and [checkAllFormulas] so neither has to walk the tree itself. */
fun FormulaAst.propertyReferences(): List<FormulaAst.PropertyRef> = when (this) {
    is FormulaAst.PropertyRef -> listOf(this)
    is FormulaAst.Call -> args.flatMap { it.propertyReferences() }
    is FormulaAst.UnaryOp -> operand.propertyReferences()
    is FormulaAst.BinaryOp -> left.propertyReferences() + right.propertyReferences()
    is FormulaAst.NumberLit, is FormulaAst.StringLit, is FormulaAst.BoolLit -> emptyList()
}
