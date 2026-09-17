package com.tendril.app.domain.formula

import com.tendril.app.domain.plural

/** One type or reference problem, at the exact character [position] the parser or checker saw
 * it — never a list a person has to reconcile against a still-broken expression on their own, in
 * the sense that each [FormulaError] stands alone and points somewhere specific. */
data class FormulaError(val message: String, val position: Int)

/** What a `prop("Name")` reference resolves to, for type-checking purposes, when it names
 * something *outside* the set of formulas being checked together — the mapping from that
 * property's own declared `PropertyType` (`NUMBER` to [FormulaType.NUMBER], `CHECKBOX` to
 * [FormulaType.BOOLEAN], and so on) is the caller's to build, since [checkAllFormulas] takes no
 * dependency on `Property`/`PropertyType` at all — see this file's own package note on why. A
 * reference to another `COMPUTED` property *inside* the set being checked together is never
 * expressed through this interface either: see [checkAllFormulas]'s own note on why that would
 * be redundant with what it already knows from its own `nodes` list. */
sealed class FormulaPropertyKind {
    data class Typed(val formulaType: FormulaType) : FormulaPropertyKind()

    /** Relation traversal (aggregating across the related rows) is not built yet — see
     * [FormulaAst.PropertyRef]'s own note and `docs/scope-decisions.md`'s DB3 entry. Referencing
     * a `RELATION` property bare is therefore a genuine write-time error today, not a stand-in
     * for "not implemented": there is no sensible single value a bare relation reference could
     * produce, the same way `prop("SomeRow")` referencing a whole row would not either. */
    object Relation : FormulaPropertyKind()
}

fun interface FormulaPropertyTypeLookup {
    /** `null` means no property by this name exists on the database being checked against. */
    fun lookup(propertyName: String): FormulaPropertyKind?
}

/** One formula's check result: every property this database has by this name resolves to
 * exactly one of these, never both — [type] is [FormulaType.ANY] whenever [errors] is
 * non-empty, since an invalid formula has no type another formula could safely build on. */
data class FormulaCheckResult(val type: FormulaType, val errors: List<FormulaError>)

/**
 * §5.4 — "errors are caught when the formula is written, not when a cell renders." Checks every
 * formula in [nodes] against [lookup], in the dependency order [topologicallySortFormulas]
 * produces, so a formula referencing another `COMPUTED` property sees that property's own
 * *checked* inferred type rather than guessing. A cycle anywhere in [nodes] is reported against
 * every property named in it, since from inside the cycle there is no well-defined "first"
 * offender — whichever one a person opens next is exactly as responsible as the others.
 *
 * A `prop()` reference is resolved against [nodes] itself first — every name in there is, by
 * definition, a `COMPUTED` property being checked in this same pass — and only falls through to
 * [lookup] for a name [nodes] does not contain. [lookup] therefore never needs a "this is
 * computed" case of its own: the caller already told this function which names are computed by
 * what it put in [nodes], and repeating that through [lookup] as well would be one more place
 * for the two to silently disagree. In real use, that means every `COMPUTED` property on a
 * database — not just the one being edited — belongs in [nodes] for a check to be accurate.
 */
fun checkAllFormulas(nodes: List<FormulaNode>, lookup: FormulaPropertyTypeLookup): Map<String, FormulaCheckResult> {
    val ordered = try {
        topologicallySortFormulas(nodes)
    } catch (cycle: FormulaCycleException) {
        val message = cycle.message ?: "circular formula reference"
        return cycle.cyclePath.toSet().associateWith { FormulaCheckResult(FormulaType.ANY, listOf(FormulaError(message, 0))) }
    }

    val computedNames = nodes.map { it.propertyName }.toSet()
    val results = mutableMapOf<String, FormulaCheckResult>()
    val computedTypes = mutableMapOf<String, FormulaType>()
    for (node in ordered) {
        val errors = mutableListOf<FormulaError>()
        val type = inferAndCheck(node.ast, lookup, computedNames, computedTypes, errors)
        val finalType = if (errors.isEmpty()) type else FormulaType.ANY
        results[node.propertyName] = FormulaCheckResult(finalType, errors)
        computedTypes[node.propertyName] = finalType
    }
    return results
}

private fun inferAndCheck(
    ast: FormulaAst,
    lookup: FormulaPropertyTypeLookup,
    computedNames: Set<String>,
    computedTypes: Map<String, FormulaType>,
    errors: MutableList<FormulaError>,
): FormulaType = when (ast) {
    is FormulaAst.NumberLit -> FormulaType.NUMBER
    is FormulaAst.StringLit -> FormulaType.TEXT
    is FormulaAst.BoolLit -> FormulaType.BOOLEAN

    is FormulaAst.PropertyRef -> if (ast.propertyName in computedNames) {
        // Topological order guarantees this was already checked, unless a cycle already
        // rejected the whole batch before this loop ever ran — so a missing entry here would
        // mean that guarantee broke, not that the reference is somehow invalid.
        computedTypes[ast.propertyName] ?: FormulaType.ANY
    } else when (val kind = lookup.lookup(ast.propertyName)) {
        null -> {
            errors += FormulaError("no property named \"${ast.propertyName}\" on this database", ast.position)
            FormulaType.ANY
        }
        is FormulaPropertyKind.Typed -> kind.formulaType
        FormulaPropertyKind.Relation -> {
            errors += FormulaError(
                "\"${ast.propertyName}\" is a relation — formulas can't traverse relations yet, only reference plain properties",
                ast.position,
            )
            FormulaType.ANY
        }
    }

    is FormulaAst.UnaryOp -> {
        val operandType = inferAndCheck(ast.operand, lookup, computedNames, computedTypes, errors)
        val expected = if (ast.op == "not") FormulaType.BOOLEAN else FormulaType.NUMBER
        if (operandType != FormulaType.ANY && operandType != expected) {
            errors += FormulaError("'${ast.op}' needs a ${expected.name.lowercase()}, got ${operandType.name.lowercase()}", ast.position)
        }
        expected
    }

    is FormulaAst.BinaryOp -> checkBinary(ast, lookup, computedNames, computedTypes, errors)

    is FormulaAst.Call -> checkCall(ast, lookup, computedNames, computedTypes, errors)
}

private fun checkBinary(
    ast: FormulaAst.BinaryOp,
    lookup: FormulaPropertyTypeLookup,
    computedNames: Set<String>,
    computedTypes: Map<String, FormulaType>,
    errors: MutableList<FormulaError>,
): FormulaType {
    val left = inferAndCheck(ast.left, lookup, computedNames, computedTypes, errors)
    val right = inferAndCheck(ast.right, lookup, computedNames, computedTypes, errors)
    val unknown = left == FormulaType.ANY || right == FormulaType.ANY // one side already broken; don't pile on a second error

    return when (ast.op) {
        "and", "or" -> {
            if (!unknown && (left != FormulaType.BOOLEAN || right != FormulaType.BOOLEAN)) {
                errors += FormulaError("'${ast.op}' needs two booleans, got ${left.name.lowercase()} and ${right.name.lowercase()}", ast.position)
            }
            FormulaType.BOOLEAN
        }
        "==", "!=" -> FormulaType.BOOLEAN // well-defined across any pair of types — mismatched types are simply unequal
        "<", "<=", ">", ">=" -> {
            if (!unknown && !(left == FormulaType.NUMBER && right == FormulaType.NUMBER) && !(left == FormulaType.DATE && right == FormulaType.DATE)) {
                errors += FormulaError("'${ast.op}' compares two numbers or two dates, got ${left.name.lowercase()} and ${right.name.lowercase()}", ast.position)
            }
            FormulaType.BOOLEAN
        }
        "+" -> when {
            unknown -> FormulaType.ANY
            left == FormulaType.NUMBER && right == FormulaType.NUMBER -> FormulaType.NUMBER
            left == FormulaType.TEXT || right == FormulaType.TEXT -> FormulaType.TEXT // string concatenation
            else -> {
                errors += FormulaError("'+' needs two numbers, or a text on one side to concatenate — got ${left.name.lowercase()} and ${right.name.lowercase()}", ast.position)
                FormulaType.ANY
            }
        }
        "-", "*", "/" -> {
            if (!unknown && (left != FormulaType.NUMBER || right != FormulaType.NUMBER)) {
                errors += FormulaError("'${ast.op}' needs two numbers, got ${left.name.lowercase()} and ${right.name.lowercase()}", ast.position)
            }
            FormulaType.NUMBER
        }
        else -> FormulaType.ANY
    }
}

private fun checkCall(
    ast: FormulaAst.Call,
    lookup: FormulaPropertyTypeLookup,
    computedNames: Set<String>,
    computedTypes: Map<String, FormulaType>,
    errors: MutableList<FormulaError>,
): FormulaType {
    val argTypes = ast.args.map { inferAndCheck(it, lookup, computedNames, computedTypes, errors) }

    fun requireArity(min: Int, max: Int = min) {
        if (ast.args.size < min || ast.args.size > max) {
            val expected = if (min == max) plural(min, "argument") else "$min to $max arguments"
            errors += FormulaError("${ast.functionName}(...) needs $expected, got ${ast.args.size}", ast.position)
        }
    }

    fun requireNumbers() {
        argTypes.forEachIndexed { i, t ->
            if (t != FormulaType.ANY && t != FormulaType.NUMBER) {
                errors += FormulaError("${ast.functionName}(...) argument ${i + 1} must be a number, got ${t.name.lowercase()}", ast.args[i].position)
            }
        }
    }

    return when (ast.functionName) {
        "if" -> {
            requireArity(3)
            if (argTypes.isNotEmpty() && argTypes[0] != FormulaType.ANY && argTypes[0] != FormulaType.BOOLEAN) {
                errors += FormulaError("if(...)'s first argument must be a boolean condition, got ${argTypes[0].name.lowercase()}", ast.args[0].position)
            }
            if (argTypes.size == 3) {
                val thenType = argTypes[1]
                val elseType = argTypes[2]
                if (thenType != FormulaType.ANY && elseType != FormulaType.ANY && thenType != elseType) {
                    errors += FormulaError("if(...)'s two branches must be the same type, got ${thenType.name.lowercase()} and ${elseType.name.lowercase()}", ast.position)
                }
                if (thenType != FormulaType.ANY) thenType else elseType
            } else FormulaType.ANY
        }
        "abs", "round" -> { requireArity(1); requireNumbers(); FormulaType.NUMBER }
        "min", "max" -> { requireArity(1, Int.MAX_VALUE); requireNumbers(); FormulaType.NUMBER }
        else -> {
            errors += FormulaError("unknown function \"${ast.functionName}\"", ast.position)
            FormulaType.ANY
        }
    }
}
