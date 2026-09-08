package com.tendril.app.domain.formula

import java.time.LocalDate

/**
 * §5.4/DB3 — a formula's runtime result, or an intermediate value while evaluating one.
 *
 * Distinct from [FormulaType] (the *declared* type used for write-time checking, before any row
 * exists to evaluate against): this is what a formula actually produces once every
 * [FormulaAst.PropertyRef] resolves to a real row's stored value.
 */
sealed class FormulaValue {
    data class Number(val value: Double) : FormulaValue()
    data class Text(val value: String) : FormulaValue()
    data class Bool(val value: Boolean) : FormulaValue()
    data class DateValue(val value: LocalDate) : FormulaValue()

    /** An unset cell, or a value an operator could not coerce into what it needed. Propagates
     * rather than throwing — the same tolerant-degrade posture already used everywhere else a
     * value might not resolve (§9.4's quarantine holding a record rather than crashing, a
     * relation uid with no local row rendering as absent). A formula referencing a blank cell
     * should read as blank, not abort every other cell in the column. */
    object Empty : FormulaValue()
}

/**
 * The type a formula infers for a [FormulaAst.PropertyRef] before ever evaluating a row —
 * §5.4's "errors are caught when the formula is written, not when a cell renders" needs a type
 * to check operators *against*, and the only one available pre-row is the referenced property's
 * own declared `PropertyType`. [ANY] means "not statically knowable" (a `COMPUTED` reference
 * whose own type could not yet be resolved, most often because it is itself invalid) and
 * disables type checking for the expression that touches it rather than rejecting it outright —
 * a real dynamic-typing gap, not a bug, matching how PropertyValue's own storage is untyped text
 * throughout this schema.
 */
enum class FormulaType { NUMBER, TEXT, BOOLEAN, DATE, ANY }
