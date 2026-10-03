package com.dexter.data

/**
 * Metadata about a library series that the library itself does not store, for the [LibraryQuery]
 * field prefixes. It comes from the series details the app has cached on the device, so fields can
 * be empty for series you have never opened.
 */
data class LibrarySeriesMeta(
    val author: String? = null,
    val genre: String? = null,
    val tags: List<String> = emptyList(),
    /** The publication status, such as "ongoing" or "completed", when MangaDex names one. */
    val status: String? = null,
    val source: String? = null,
)

/** One parsed library search query. [matches] decides whether a series stays in the list. */
sealed interface LibraryQuery {
    fun matches(series: SavedSeries, meta: LibrarySeriesMeta?): Boolean
}

/** Matches everything. What an empty query parses to. */
private data object MatchAll : LibraryQuery {
    override fun matches(series: SavedSeries, meta: LibrarySeriesMeta?): Boolean = true
}

/**
 * One word, optionally with a field prefix such as `genre:`. Without a prefix the word matches the
 * title, author, genre, tags, and statuses. The prefixes are `title:`, `author:`, `genre:`,
 * `status:`, and `source:`. `status:` matches your reading-list status ("Reading", "Completed",
 * ...) and the publication status together.
 */
private data class Term(val field: String?, val value: String) : LibraryQuery {
    override fun matches(series: SavedSeries, meta: LibrarySeriesMeta?): Boolean {
        val needle = value.lowercase()
        fun has(haystack: String?): Boolean = haystack?.lowercase()?.contains(needle) == true
        return when (field) {
            null -> has(series.title) || has(meta?.author) || has(meta?.genre) ||
                meta?.tags?.any(::has) == true || has(series.status?.label) || has(meta?.status)
            "title" -> has(series.title)
            "author" -> has(meta?.author)
            "genre" -> has(meta?.genre) || meta?.tags?.any(::has) == true
            "status" -> has(series.status?.label) || has(series.status?.name) || has(meta?.status)
            "source" -> has(meta?.source)
            // An unknown prefix still searches the title, so a stray colon cannot empty the list.
            else -> has(series.title)
        }
    }
}

private data class And(val left: LibraryQuery, val right: LibraryQuery) : LibraryQuery {
    override fun matches(series: SavedSeries, meta: LibrarySeriesMeta?): Boolean =
        left.matches(series, meta) && right.matches(series, meta)
}

private data class Or(val left: LibraryQuery, val right: LibraryQuery) : LibraryQuery {
    override fun matches(series: SavedSeries, meta: LibrarySeriesMeta?): Boolean =
        left.matches(series, meta) || right.matches(series, meta)
}

private data class Not(val inner: LibraryQuery) : LibraryQuery {
    override fun matches(series: SavedSeries, meta: LibrarySeriesMeta?): Boolean = !inner.matches(series, meta)
}

private sealed interface Token {
    data object And : Token
    data object Or : Token
    data object Not : Token
    data object Open : Token
    data object Close : Token
    data class Word(val text: String) : Token
}

/** Splits a query into operators, parentheses, and words. `"quoted words"` stay one word. */
private fun tokenize(text: String): List<Token> {
    val tokens = ArrayList<Token>()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c.isWhitespace() -> i++
            text.startsWith("&&", i) -> { tokens += Token.And; i += 2 }
            text.startsWith("||", i) -> { tokens += Token.Or; i += 2 }
            // A dash starts an exclusion only at a word boundary, so "sci-fi" stays one word.
            c == '-' && (i == 0 || text[i - 1].isWhitespace() || text[i - 1] in "(&|") -> { tokens += Token.Not; i++ }
            c == '(' -> { tokens += Token.Open; i++ }
            c == ')' -> { tokens += Token.Close; i++ }
            c == '"' -> {
                val end = text.indexOf('"', i + 1).takeIf { it >= 0 } ?: text.length
                tokens += Token.Word(text.substring(i + 1, end))
                i = if (end < text.length) end + 1 else end
            }
            else -> {
                var j = i
                while (j < text.length && !text[j].isWhitespace() && text[j] !in "()\"") {
                    if (text.startsWith("&&", j) || text.startsWith("||", j)) break
                    j++
                }
                tokens += Token.Word(text.substring(i, j))
                i = j
            }
        }
    }
    // A prefix glued to a quoted value, as in `title:"one piece"`, reads as one word.
    return tokens.fold(ArrayList<Token>()) { merged, token ->
        val last = merged.lastOrNull()
        if (last is Token.Word && last.text.endsWith(':') && token is Token.Word) {
            merged[merged.lastIndex] = Token.Word(last.text + token.text)
        } else {
            merged += token
        }
        merged
    }
}

/** A tiny recursive-descent parser: `||` binds loosest, then `&&` (also implied between words), then `-`, then parentheses. */
private class Parser(private val tokens: List<Token>) {
    private var at = 0
    private fun peek(): Token? = tokens.getOrNull(at)

    fun parse(): LibraryQuery {
        if (tokens.isEmpty()) return MatchAll
        val query = parseOr()
        return query
    }

    private fun parseOr(): LibraryQuery {
        var left = parseAnd()
        while (peek() == Token.Or) {
            at++
            left = Or(left, parseAnd())
        }
        return left
    }

    private fun parseAnd(): LibraryQuery {
        var left = parseUnary()
        while (true) {
            val next = peek()
            // Two words side by side mean AND, so `isekai action` needs no operator.
            if (next == Token.And || next is Token.Word || next == Token.Not || next == Token.Open) {
                if (next == Token.And) at++
                left = And(left, parseUnary())
            } else {
                return left
            }
        }
    }

    private fun parseUnary(): LibraryQuery = when (val token = peek()) {
        Token.Not -> { at++; Not(parseUnary()) }
        Token.Open -> {
            at++
            val inner = parseOr()
            if (peek() == Token.Close) at++ // A missing ")" still parses, instead of failing.
            inner
        }
        is Token.Word -> { at++; termOf(token.text) }
        // A stray operator or ")" where a word belongs matches nothing, rather than failing.
        else -> { at++; MatchNothing }
    }
}

/** Matches nothing. What a dangling operator parses to, so the query still runs. */
private data object MatchNothing : LibraryQuery {
    override fun matches(series: SavedSeries, meta: LibrarySeriesMeta?): Boolean = false
}

/** Splits `field:value` into its field and value. A word without a colon has no field. */
private fun termOf(text: String): LibraryQuery {
    val colon = text.indexOf(':')
    return if (colon > 0) Term(text.substring(0, colon).lowercase(), text.substring(colon + 1)) else Term(null, text)
}

/**
 * Parses a library search query. Supports `&&`, `||`, `-` for exclusion, parentheses, and the
 * field prefixes `title:`, `author:`, `genre:`, `status:`, and `source:`. Quoted words keep their
 * spaces, as in `title:"one piece"`. An empty query matches everything, and a query that cannot
 * be parsed falls back to a plain title search so the list never goes blank from a typo.
 */
fun parseLibraryQuery(text: String): LibraryQuery {
    if (text.isBlank()) return MatchAll
    return runCatching { Parser(tokenize(text)).parse() }.getOrDefault(Term(null, text.trim()))
}

/**
 * True when [query] uses the query language: an operator, parentheses, or a field prefix. Plain
 * words keep the old contains-the-title behavior.
 */
fun isAdvancedQuery(query: String): Boolean =
    ADVANCED_QUERY.containsMatchIn(query.trim())

private val ADVANCED_QUERY = Regex("""&&|\|\||[()]|(^|\s)-(?=\S)|(?i)\b(title|author|genre|status|source):""")
