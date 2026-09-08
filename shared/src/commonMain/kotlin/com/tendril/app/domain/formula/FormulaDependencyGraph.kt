package com.tendril.app.domain.formula

/** One `COMPUTED` property's identity for graph purposes: its own display name (what
 * `prop("Name")` in another formula would name to reach it) and its parsed formula. */
data class FormulaNode(val propertyName: String, val ast: FormulaAst)

/**
 * §5.4 — "composition is a real dependency graph with cycle rejection that names the path, not
 * an opaque depth budget." [cyclePath] is that path, in reference order and closed on itself —
 * `["Total", "Subtotal", "Total"]` for `Total` referencing `Subtotal` referencing `Total` — so
 * the error a person sees names exactly which formulas to break the loop between, rather than
 * Notion's own "exceeded 15 layers" with no indication of where the loop actually is.
 */
class FormulaCycleException(val cyclePath: List<String>) :
    Exception("circular formula reference: ${cyclePath.joinToString(" -> ")}")

/**
 * Topologically sorts [nodes] so every formula precedes anything that references it — the order
 * [checkAllFormulas] needs to resolve a chained `prop()` reference's type from a formula
 * already checked, rather than one not yet visited. A reference naming something outside
 * [nodes] (a plain stored property, or — not yet supported — a `COMPUTED` property on a
 * *different* database) is not an edge this graph knows about; it is a leaf, resolved directly
 * by a [FormulaPropertyResolver] rather than walked here.
 *
 * Classic DFS-with-a-stack cycle detection, chosen over Kahn's algorithm specifically because
 * Kahn's reports *that* a cycle exists (some nodes never reach in-degree zero) but not *which*
 * nodes — recovering the path afterward means a second pass. Carrying the recursion stack here
 * makes the path available for free at the point the cycle closes.
 */
fun topologicallySortFormulas(nodes: List<FormulaNode>): List<FormulaNode> {
    val byName = nodes.associateBy { it.propertyName }
    val visited = mutableSetOf<String>()
    val onStack = LinkedHashSet<String>()
    val result = mutableListOf<FormulaNode>()

    fun visit(name: String) {
        if (name in visited) return
        if (name in onStack) {
            val path = onStack.dropWhile { it != name } + name
            throw FormulaCycleException(path)
        }
        val node = byName[name] ?: return
        onStack += name
        for (ref in node.ast.propertyReferences()) visit(ref.propertyName)
        onStack -= name
        visited += name
        result += node
    }

    for (node in nodes) visit(node.propertyName)
    return result
}
