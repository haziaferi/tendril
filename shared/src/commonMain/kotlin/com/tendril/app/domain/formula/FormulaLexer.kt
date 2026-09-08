package com.tendril.app.domain.formula

/**
 * §5.4/DB3 — tokens for the formula language. A small, fixed set: two literal kinds, one
 * identifier kind (covers keywords `and`/`or`/`not`/`true`/`false` and function/argument names
 * alike — the parser decides which is which by context, not the lexer), and the punctuation the
 * grammar in [parseFormula] actually uses. Nothing here is Compose- or Room-aware: this file
 * has no dependency beyond Kotlin's stdlib, so it runs identically on Android and desktop —
 * §5.4's determinism requirement starts at the token level.
 */
sealed class FormulaToken {
    abstract val position: Int

    data class Number(val value: Double, override val position: Int) : FormulaToken()
    data class Str(val value: String, override val position: Int) : FormulaToken()
    data class Ident(val name: String, override val position: Int) : FormulaToken()
    data class Symbol(val text: String, override val position: Int) : FormulaToken()
    data class End(override val position: Int) : FormulaToken()
}

class FormulaSyntaxError(message: String, val position: Int) : Exception(message)

private val SYMBOLS = listOf("==", "!=", "<=", ">=", "(", ")", ",", "+", "-", "*", "/", "<", ">")

/** Splits a formula's source text into [FormulaToken]s, left to right, stopping at the first
 * character it cannot classify — an unterminated string or a character no operator or
 * identifier starts with. [FormulaSyntaxError.position] is the byte offset into the *original*
 * source, not a token index, so a caller can point at the exact character in an editor. */
fun tokenizeFormula(source: String): List<FormulaToken> {
    val tokens = mutableListOf<FormulaToken>()
    var i = 0
    while (i < source.length) {
        val c = source[i]
        when {
            c.isWhitespace() -> i++

            c == '"' -> {
                val start = i
                val sb = StringBuilder()
                i++
                while (i < source.length && source[i] != '"') {
                    if (source[i] == '\\' && i + 1 < source.length) {
                        sb.append(
                            when (val esc = source[i + 1]) {
                                '"' -> '"'; '\\' -> '\\'; 'n' -> '\n'; 't' -> '\t'
                                else -> throw FormulaSyntaxError("unknown escape \\$esc in string", i)
                            }
                        )
                        i += 2
                    } else {
                        sb.append(source[i]); i++
                    }
                }
                if (i >= source.length) throw FormulaSyntaxError("unterminated string starting here", start)
                i++ // closing quote
                tokens += FormulaToken.Str(sb.toString(), start)
            }

            c.isDigit() -> {
                val start = i
                while (i < source.length && source[i].isDigit()) i++
                if (i < source.length && source[i] == '.' && i + 1 < source.length && source[i + 1].isDigit()) {
                    i++
                    while (i < source.length && source[i].isDigit()) i++
                }
                val text = source.substring(start, i)
                tokens += FormulaToken.Number(text.toDouble(), start)
            }

            c.isLetter() || c == '_' -> {
                val start = i
                while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                tokens += FormulaToken.Ident(source.substring(start, i), start)
            }

            else -> {
                val symbol = SYMBOLS.firstOrNull { source.startsWith(it, i) }
                    ?: throw FormulaSyntaxError("unexpected character '$c'", i)
                tokens += FormulaToken.Symbol(symbol, i)
                i += symbol.length
            }
        }
    }
    tokens += FormulaToken.End(source.length)
    return tokens
}
