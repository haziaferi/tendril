package com.tendril.app.domain.code

/**
 * §3.1.1's code block, highlighted (§10's "nice-to-have", built 2026-09-22). No library is in the
 * offline build's cache, and none is needed: four kinds of token are what a reader's eye uses —
 * **keywords, strings, comments, numbers** — and a small grammar per language names them. The
 * named set (decided with the user) carries keywords: Kotlin, Python, JavaScript / TypeScript,
 * JSON, Bash, SQL, Markdown; every other tag gets the generic pass (strings and numbers, and the
 * comment shape of its family where one is known). Pure and total: any text, any tag, a list of
 * non-overlapping ranges in order — the field's transformation colours them from the token map.
 */
enum class TokenKind { KEYWORD, STRING, COMMENT, NUMBER }

data class CodeToken(val start: Int, val end: Int, val kind: TokenKind)

/** What a language looks like to the tokenizer: its comment shapes, string delimiters and keywords. */
data class Grammar(
    val keywords: Set<String> = emptySet(),
    val lineComment: List<String> = emptyList(),
    val blockComment: Pair<String, String>? = null,
    /** Delimiters in order of trial — a longer one (`"""`) before the one it starts with. */
    val strings: List<String> = listOf("\"", "'"),
    val caseInsensitive: Boolean = false,
    /** Markdown only: a `#` heading line is a keyword, a backtick span a string. */
    val markdown: Boolean = false,
)

private val C_LIKE = Grammar(lineComment = listOf("//"), blockComment = "/*" to "*/")
private val HASH = Grammar(lineComment = listOf("#"))

private val KOTLIN = C_LIKE.copy(
    strings = listOf("\"\"\"", "\"", "'"),
    // The hard keywords and the modifiers that rarely name anything; the soft ones that double as
    // identifiers (`open`, `set`, `get`, `field`, `it`, `value`, `by`, `where`, `out`) stay plain —
    // a tokenizer without a parser cannot tell `fun open()` from `open class`.
    keywords = setOf("as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null", "object",
        "package", "return", "super", "this", "throw", "true", "try", "typealias", "val", "var", "when", "while", "catch", "constructor",
        "finally", "import", "init", "abstract", "actual", "annotation", "companion", "const", "crossinline", "data", "enum", "expect",
        "external", "final", "infix", "inline", "inner", "internal", "lateinit", "noinline", "operator", "override", "private", "protected",
        "public", "reified", "sealed", "suspend", "tailrec", "vararg"),
)
private val PYTHON = HASH.copy(
    strings = listOf("\"\"\"", "'''", "\"", "'"),
    keywords = setOf("False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else",
        "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
        "try", "while", "with", "yield", "self", "print"),
)
private val JAVASCRIPT = C_LIKE.copy(
    strings = listOf("\"", "'", "`"),
    keywords = setOf("async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else",
        "export", "extends", "false", "finally", "for", "function", "if", "import", "in", "instanceof", "let", "new", "null", "of", "return",
        "static", "super", "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "with", "yield",
        // TypeScript's
        "abstract", "any", "as", "boolean", "declare", "enum", "implements", "interface", "keyof", "namespace", "never", "number", "private",
        "protected", "public", "readonly", "string", "type", "unknown"),
)
private val JSON = Grammar(strings = listOf("\""), keywords = setOf("true", "false", "null"))
private val BASH = HASH.copy(
    keywords = setOf("if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until", "case", "esac", "function", "return",
        "local", "export", "exit", "echo", "cd", "set", "source", "select", "time"),
)
private val SQL = Grammar(
    lineComment = listOf("--"), blockComment = "/*" to "*/", strings = listOf("'", "\""), caseInsensitive = true,
    keywords = setOf("select", "from", "where", "and", "or", "not", "in", "is", "null", "as", "join", "left", "right", "inner", "outer", "on",
        "group", "by", "order", "asc", "desc", "limit", "offset", "insert", "into", "values", "update", "set", "delete", "create", "table",
        "index", "unique", "primary", "key", "foreign", "references", "drop", "alter", "add", "column", "if", "exists", "begin", "commit",
        "rollback", "transaction", "with", "case", "when", "then", "else", "end", "distinct", "having", "union", "all", "like", "between",
        "integer", "text", "real", "blob", "boolean", "default", "cascade", "pragma", "view", "trigger", "count", "sum", "avg", "min", "max"),
)
private val MARKDOWN = Grammar(strings = listOf("`"), markdown = true)

/** The grammar for a tag as `Block.codeLanguage` stores it (free-form; the picker's spellings and a few aliases). */
fun grammarFor(language: String?): Grammar = when (language?.trim()?.lowercase()) {
    "kotlin", "kt" -> KOTLIN
    "python", "py" -> PYTHON
    "javascript", "js", "typescript", "ts", "jsx", "tsx" -> JAVASCRIPT
    "json" -> JSON
    "bash", "sh", "shell", "zsh" -> BASH
    "sql", "sqlite" -> SQL
    "markdown", "md" -> MARKDOWN
    "java", "swift", "c", "cpp", "c++", "csharp", "c#", "go", "rust", "php", "css", "scala", "dart" -> C_LIKE
    "yaml", "yml", "ruby", "rb", "toml", "ini", "perl", "r" -> HASH
    else -> Grammar()
}

fun highlight(code: String, language: String?): List<CodeToken> = highlight(code, grammarFor(language))

fun highlight(code: String, g: Grammar): List<CodeToken> {
    val out = mutableListOf<CodeToken>()
    val n = code.length
    var i = 0
    fun startsAt(at: Int, s: String) = code.startsWith(s, at)
    while (i < n) {
        val c = code[i]
        // A heading line in Markdown: from a leading `#` to the line's end.
        if (g.markdown && c == '#' && (i == 0 || code[i - 1] == '\n')) {
            val end = code.indexOf('\n', i).let { if (it < 0) n else it }
            out += CodeToken(i, end, TokenKind.KEYWORD); i = end; continue
        }
        val block = g.blockComment
        if (block != null && startsAt(i, block.first)) {
            val closeAt = code.indexOf(block.second, i + block.first.length)
            val end = if (closeAt < 0) n else closeAt + block.second.length
            out += CodeToken(i, end, TokenKind.COMMENT); i = end; continue
        }
        val line = g.lineComment.firstOrNull { startsAt(i, it) }
        if (line != null) {
            val end = code.indexOf('\n', i).let { if (it < 0) n else it }
            out += CodeToken(i, end, TokenKind.COMMENT); i = end; continue
        }
        // An apostrophe glued to a word (don't) is prose, not a string's start.
        val delimiter = g.strings.firstOrNull { startsAt(i, it) }?.takeIf { !(it == "'" && i > 0 && isWordChar(code[i - 1])) }
        if (delimiter != null) {
            var j = i + delimiter.length
            while (j < n) {
                if (code[j] == '\\' && delimiter.length == 1) { j += 2; continue }
                if (startsAt(j, delimiter)) { j += delimiter.length; break }
                if (code[j] == '\n' && delimiter.length == 1 && delimiter != "`") break   // a single-line string runs to its line
                j++
            }
            val end = minOf(j, n)
            out += CodeToken(i, end, TokenKind.STRING); i = end; continue
        }
        if (c.isDigit() && (i == 0 || !isWordChar(code[i - 1]))) {
            var j = i + 1
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
            out += CodeToken(i, j, TokenKind.NUMBER); i = j; continue
        }
        if (isWordStart(c)) {
            var j = i + 1
            while (j < n && isWordChar(code[j])) j++
            val word = code.substring(i, j).let { if (g.caseInsensitive) it.lowercase() else it }
            if (word in g.keywords) out += CodeToken(i, j, TokenKind.KEYWORD)
            i = j; continue
        }
        i++
    }
    return out
}

private fun isWordStart(c: Char) = c.isLetter() || c == '_'
private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'
