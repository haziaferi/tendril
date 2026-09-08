package com.tendril.app.domain.formula

import kotlin.math.abs
import kotlin.math.round

/** Resolves one `prop("Name")` reference to a row's actual value. Deliberately narrow — no
 * database access, no coroutines — so [evaluateFormula] stays a pure function callable straight
 * from a unit test with a hand-built map, the same separation [FormulaValue]'s own file note
 * describes for the value/type layer versus the ViewModel that will supply real resolvers. */
fun interface FormulaPropertyResolver {
    fun resolve(propertyName: String): FormulaValue
}

/**
 * §5.4/DB3 — evaluates a parsed formula against one row via [resolver]. Never throws: an
 * operator applied to a value it cannot use, a reference to a name [resolver] does not
 * recognise, division by zero — every one of these becomes [FormulaValue.Empty] rather than an
 * exception. A formula is one column among many on a row; one bad cell degrading to blank is
 * [checkAllFormulas]'s job to have already caught at save time, not a reason to blank the
 * entire row's other columns or crash the table render, the same tolerant-degrade posture used
 * everywhere else a stored value might not resolve cleanly (§9.4's quarantine, a relation uid
 * with no local row).
 */
fun evaluateFormula(ast: FormulaAst, resolver: FormulaPropertyResolver): FormulaValue = when (ast) {
    is FormulaAst.NumberLit -> FormulaValue.Number(ast.value)
    is FormulaAst.StringLit -> FormulaValue.Text(ast.value)
    is FormulaAst.BoolLit -> FormulaValue.Bool(ast.value)
    is FormulaAst.PropertyRef -> resolver.resolve(ast.propertyName)
    is FormulaAst.UnaryOp -> evaluateUnary(ast, resolver)
    is FormulaAst.BinaryOp -> evaluateBinary(ast, resolver)
    is FormulaAst.Call -> evaluateCall(ast, resolver)
}

private fun evaluateUnary(ast: FormulaAst.UnaryOp, resolver: FormulaPropertyResolver): FormulaValue {
    val operand = evaluateFormula(ast.operand, resolver)
    return when (ast.op) {
        "-" -> (operand as? FormulaValue.Number)?.let { FormulaValue.Number(-it.value) } ?: FormulaValue.Empty
        "not" -> (operand as? FormulaValue.Bool)?.let { FormulaValue.Bool(!it.value) } ?: FormulaValue.Empty
        else -> FormulaValue.Empty
    }
}

/** A value's string form for `+`-as-concatenation — the same whole-number-drops-its-decimal
 * shape [com.tendril.app.data.pagedatabase.formatRollupNumber] already uses for a rollup result,
 * so `"Total: " + prop("Score")` reads `"Total: 3"` rather than `"Total: 3.0"`. */
private fun FormulaValue.asDisplayText(): String = when (this) {
    is FormulaValue.Text -> value
    is FormulaValue.Number -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    is FormulaValue.Bool -> value.toString()
    is FormulaValue.DateValue -> value.toString()
    FormulaValue.Empty -> ""
}

private fun evaluateBinary(ast: FormulaAst.BinaryOp, resolver: FormulaPropertyResolver): FormulaValue {
    // `and`/`or` short-circuit: the right side is only ever evaluated if the left side did not
    // already decide the result, so a formula like `prop("Done") and prop("Total") > 0` never
    // evaluates the right side of `and` when a row isn't done — matters once a resolver call can
    // itself walk a chain of other formulas (a later PR's relation traversal).
    if (ast.op == "and" || ast.op == "or") {
        val left = evaluateFormula(ast.left, resolver) as? FormulaValue.Bool ?: return FormulaValue.Empty
        if (ast.op == "and" && !left.value) return FormulaValue.Bool(false)
        if (ast.op == "or" && left.value) return FormulaValue.Bool(true)
        val right = evaluateFormula(ast.right, resolver) as? FormulaValue.Bool ?: return FormulaValue.Empty
        return right
    }

    val left = evaluateFormula(ast.left, resolver)
    val right = evaluateFormula(ast.right, resolver)

    if (ast.op == "==") return FormulaValue.Bool(left.structurallyEquals(right))
    if (ast.op == "!=") return FormulaValue.Bool(!left.structurallyEquals(right))

    if (ast.op == "+") {
        return when {
            left is FormulaValue.Number && right is FormulaValue.Number -> FormulaValue.Number(left.value + right.value)
            left is FormulaValue.Text || right is FormulaValue.Text -> FormulaValue.Text(left.asDisplayText() + right.asDisplayText())
            else -> FormulaValue.Empty
        }
    }

    if (ast.op in setOf("-", "*", "/")) {
        if (left !is FormulaValue.Number || right !is FormulaValue.Number) return FormulaValue.Empty
        return when (ast.op) {
            "-" -> FormulaValue.Number(left.value - right.value)
            "*" -> FormulaValue.Number(left.value * right.value)
            else -> if (right.value == 0.0) FormulaValue.Empty else FormulaValue.Number(left.value / right.value)
        }
    }

    // comparisons: Number-Number or Date-Date only
    val comparison = when {
        left is FormulaValue.Number && right is FormulaValue.Number -> left.value.compareTo(right.value)
        left is FormulaValue.DateValue && right is FormulaValue.DateValue -> left.value.compareTo(right.value)
        else -> return FormulaValue.Empty
    }
    return FormulaValue.Bool(
        when (ast.op) {
            "<" -> comparison < 0
            "<=" -> comparison <= 0
            ">" -> comparison > 0
            ">=" -> comparison >= 0
            else -> return FormulaValue.Empty
        }
    )
}

private fun FormulaValue.structurallyEquals(other: FormulaValue): Boolean = when {
    this is FormulaValue.Number && other is FormulaValue.Number -> value == other.value
    this is FormulaValue.Text && other is FormulaValue.Text -> value == other.value
    this is FormulaValue.Bool && other is FormulaValue.Bool -> value == other.value
    this is FormulaValue.DateValue && other is FormulaValue.DateValue -> value == other.value
    this is FormulaValue.Empty && other is FormulaValue.Empty -> true
    else -> false
}

private fun evaluateCall(ast: FormulaAst.Call, resolver: FormulaPropertyResolver): FormulaValue {
    val args = ast.args.map { evaluateFormula(it, resolver) }
    return when (ast.functionName) {
        "if" -> {
            val cond = args.getOrNull(0) as? FormulaValue.Bool ?: return FormulaValue.Empty
            if (args.size != 3) return FormulaValue.Empty
            if (cond.value) args[1] else args[2]
        }
        "abs" -> (args.singleOrNull() as? FormulaValue.Number)?.let { FormulaValue.Number(abs(it.value)) } ?: FormulaValue.Empty
        "round" -> (args.singleOrNull() as? FormulaValue.Number)?.let { FormulaValue.Number(round(it.value)) } ?: FormulaValue.Empty
        "min" -> args.map { it as? FormulaValue.Number ?: return FormulaValue.Empty }.minByOrNull { it.value } ?: FormulaValue.Empty
        "max" -> args.map { it as? FormulaValue.Number ?: return FormulaValue.Empty }.maxByOrNull { it.value } ?: FormulaValue.Empty
        else -> FormulaValue.Empty
    }
}
