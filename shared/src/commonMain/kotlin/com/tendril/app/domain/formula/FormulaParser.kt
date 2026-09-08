package com.tendril.app.domain.formula

/**
 * §5.4/DB3 — recursive-descent parser over [tokenizeFormula]'s output, one function per
 * precedence level, lowest to highest: `or`, `and`, equality (`==`/`!=`), comparison
 * (`<`/`<=`/`>`/`>=`), additive (`+`/`-`), multiplicative (`*`/`/`), unary (`-`/`not`), primary
 * (literals, `prop(...)`, a function call, or a parenthesised sub-expression). Each level calls
 * the next tighter one for its operands, which is what gives `2 + 3 * 4` its usual grouping
 * without an explicit precedence table.
 *
 * Throws [FormulaSyntaxError] on the first malformed input rather than trying to recover and
 * report several — §5.4's "errors are caught when the formula is written" needs one clear
 * error at the exact character, not a list a person has to reconcile against a still-broken
 * expression.
 */
fun parseFormula(source: String): FormulaAst {
    val tokens = tokenizeFormula(source)
    val parser = Parser(tokens)
    val ast = parser.parseOr()
    parser.expectEnd()
    return ast
}

private class Parser(private val tokens: List<FormulaToken>) {
    private var pos = 0
    private fun peek(): FormulaToken = tokens[pos]
    private fun advance(): FormulaToken = tokens[pos].also { pos++ }

    private fun isSymbol(text: String): Boolean = (peek() as? FormulaToken.Symbol)?.text == text
    private fun isKeyword(name: String): Boolean = (peek() as? FormulaToken.Ident)?.name == name

    private fun expectSymbol(text: String) {
        if (!isSymbol(text)) throw FormulaSyntaxError("expected '$text'", peek().position)
        advance()
    }

    fun expectEnd() {
        if (peek() !is FormulaToken.End) throw FormulaSyntaxError("unexpected trailing input", peek().position)
    }

    fun parseOr(): FormulaAst {
        var left = parseAnd()
        while (isKeyword("or")) {
            val pos = advance().position
            left = FormulaAst.BinaryOp("or", left, parseAnd(), pos)
        }
        return left
    }

    private fun parseAnd(): FormulaAst {
        var left = parseEquality()
        while (isKeyword("and")) {
            val pos = advance().position
            left = FormulaAst.BinaryOp("and", left, parseEquality(), pos)
        }
        return left
    }

    private fun parseEquality(): FormulaAst {
        var left = parseComparison()
        while (isSymbol("==") || isSymbol("!=")) {
            val op = (advance() as FormulaToken.Symbol)
            left = FormulaAst.BinaryOp(op.text, left, parseComparison(), op.position)
        }
        return left
    }

    private fun parseComparison(): FormulaAst {
        var left = parseAdditive()
        while (isSymbol("<") || isSymbol("<=") || isSymbol(">") || isSymbol(">=")) {
            val op = (advance() as FormulaToken.Symbol)
            left = FormulaAst.BinaryOp(op.text, left, parseAdditive(), op.position)
        }
        return left
    }

    private fun parseAdditive(): FormulaAst {
        var left = parseMultiplicative()
        while (isSymbol("+") || isSymbol("-")) {
            val op = (advance() as FormulaToken.Symbol)
            left = FormulaAst.BinaryOp(op.text, left, parseMultiplicative(), op.position)
        }
        return left
    }

    private fun parseMultiplicative(): FormulaAst {
        var left = parseUnary()
        while (isSymbol("*") || isSymbol("/")) {
            val op = (advance() as FormulaToken.Symbol)
            left = FormulaAst.BinaryOp(op.text, left, parseUnary(), op.position)
        }
        return left
    }

    private fun parseUnary(): FormulaAst {
        if (isSymbol("-")) {
            val pos = advance().position
            return FormulaAst.UnaryOp("-", parseUnary(), pos)
        }
        if (isKeyword("not")) {
            val pos = advance().position
            return FormulaAst.UnaryOp("not", parseUnary(), pos)
        }
        return parsePrimary()
    }

    private fun parsePrimary(): FormulaAst {
        val token = peek()
        return when (token) {
            is FormulaToken.Number -> { advance(); FormulaAst.NumberLit(token.value, token.position) }
            is FormulaToken.Str -> { advance(); FormulaAst.StringLit(token.value, token.position) }
            is FormulaToken.Symbol -> if (token.text == "(") {
                advance()
                val inner = parseOr()
                expectSymbol(")")
                inner
            } else {
                throw FormulaSyntaxError("unexpected '${token.text}'", token.position)
            }
            is FormulaToken.Ident -> parseIdentStart(token)
            is FormulaToken.End -> throw FormulaSyntaxError("expected an expression", token.position)
        }
    }

    private fun parseIdentStart(token: FormulaToken.Ident): FormulaAst {
        advance()
        return when (token.name) {
            "true" -> FormulaAst.BoolLit(true, token.position)
            "false" -> FormulaAst.BoolLit(false, token.position)
            "and", "or", "not" -> throw FormulaSyntaxError("'${token.name}' is not a value here", token.position)
            "prop" -> {
                expectSymbol("(")
                val nameToken = peek()
                if (nameToken !is FormulaToken.Str) {
                    throw FormulaSyntaxError("prop(...) needs a property name in quotes, like prop(\"Status\")", nameToken.position)
                }
                advance()
                expectSymbol(")")
                FormulaAst.PropertyRef(nameToken.value, token.position)
            }
            else -> {
                if (!isSymbol("(")) {
                    throw FormulaSyntaxError("'${token.name}' is not a known name — did you mean prop(\"${token.name}\")?", token.position)
                }
                advance()
                val args = mutableListOf<FormulaAst>()
                if (!isSymbol(")")) {
                    args += parseOr()
                    while (isSymbol(",")) { advance(); args += parseOr() }
                }
                expectSymbol(")")
                FormulaAst.Call(token.name, args, token.position)
            }
        }
    }
}
