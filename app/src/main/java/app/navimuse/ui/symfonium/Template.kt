package app.navimuse.ui.symfonium

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Symfonium's custom string templates (v12+), as used by now-playing styles:
 *
 *  - `%field%` inserts a value; `%field|condition%` only when the condition holds;
 *    `%|condition%` adds nothing but can gate a block.
 *  - `{ … }` shows its contents only when its first placeholder has a value (or is true).
 *  - Conditions: `==`, `!=`, `+=` (contains), `-=` (doesn't contain), `>`, `<`, `[]"a".."b"`,
 *    `==empty` / `!=empty`, joined with `&&`, `||` and parentheses. The right side is a field
 *    name or a "quoted literal".
 *  - Markup: `**bold**`, `__underline__`, `~~strike~~`, `//italics//`, `^^CAPS^^`.
 *  - `%lb%` and `\n` (inside blocks) break lines; `\t` splits a line into parts spread across
 *    the width; `\o` (a text backdrop) is ignored.
 *
 * Unknown fields are empty, so a template written for Symfonium shows what NaviMuse knows.
 */
object Template {
    /** One rendered template: lines, each split into parts at `\t`. */
    data class Rendered(val lines: List<List<AnnotatedString>>) {
        val isBlank: Boolean get() = lines.all { line -> line.all { it.text.isBlank() } }
    }

    fun render(template: String, fields: (String) -> String?): Rendered {
        val flat = expand(template, fields)
        return Rendered(markup(flat))
    }

    /** Plain text of a template, for places that can't show styling. */
    fun plain(template: String, fields: (String) -> String?): String =
        render(template, fields).lines.joinToString("\n") { parts -> parts.joinToString("  ") { it.text } }.trim()

    // --- blocks and placeholders --------------------------------------------------------------

    private fun expand(t: String, fields: (String) -> String?): String {
        val out = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            when {
                c == '\\' && i + 1 < t.length -> {
                    when (t[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'o' -> Unit
                        else -> out.append(t[i + 1])
                    }
                    i += 2
                }
                c == '{' -> {
                    val end = matching(t, i)
                    if (end < 0) {
                        out.append(c)
                        i++
                    } else {
                        out.append(block(t.substring(i + 1, end), fields))
                        i = end + 1
                    }
                }
                c == '%' -> {
                    val end = t.indexOf('%', i + 1)
                    if (end < 0) {
                        out.append(c)
                        i++
                    } else {
                        out.append(placeholder(t.substring(i + 1, end), fields).orEmpty())
                        i = end + 1
                    }
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun matching(t: String, open: Int): Int {
        var depth = 0
        var i = open
        var inPlaceholder = false
        while (i < t.length) {
            val c = t[i]
            when {
                c == '\\' -> i++
                c == '%' -> inPlaceholder = !inPlaceholder
                !inPlaceholder && c == '{' -> depth++
                !inPlaceholder && c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    /** A `{ … }` block: shown when its first placeholder has a value or a true condition. */
    private fun block(inner: String, fields: (String) -> String?): String {
        val first = Regex("%([^%]*)%").find(inner)
        if (first != null) {
            val p = first.groupValues[1]
            val shown = if (p.startsWith("|")) condition(p.drop(1), fields) else !placeholder(p, fields).isNullOrEmpty()
            if (!shown) return ""
        }
        return expand(inner, fields)
    }

    /** `field`, `field|condition`, `|condition` or `lb`. */
    private fun placeholder(p: String, fields: (String) -> String?): String? {
        val bar = p.indexOf('|')
        val name = (if (bar < 0) p else p.substring(0, bar)).trim()
        if (bar >= 0 && !condition(p.substring(bar + 1), fields)) return null
        if (name.isEmpty()) return ""
        if (name == "lb") return "\n"
        if (name.startsWith("icon:")) return null
        return fields(name)?.takeIf { it.isNotEmpty() }
    }

    // --- conditions ---------------------------------------------------------------------------

    fun condition(expr: String, fields: (String) -> String?): Boolean =
        runCatching { Cond(tokens(expr), fields).parse() }.getOrDefault(false)

    private sealed interface Tok {
        data class Word(val text: String) : Tok
        data class Lit(val text: String) : Tok
        data class Op(val text: String) : Tok
    }

    private val ops = listOf("&&", "||", "==", "!=", "+=", "-=", "[]", "..", ">", "<", "(", ")")

    private fun tokens(s: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '"' -> {
                    val end = s.indexOf('"', i + 1).let { if (it < 0) s.length else it }
                    out += Tok.Lit(s.substring(i + 1, end))
                    i = end + 1
                }
                else -> {
                    val op = ops.firstOrNull { s.startsWith(it, i) }
                    if (op != null) {
                        out += Tok.Op(op)
                        i += op.length
                    } else {
                        val start = i
                        while (i < s.length && !s[i].isWhitespace() && s[i] != '"' && ops.none { s.startsWith(it, i) }) i++
                        out += Tok.Word(s.substring(start, i))
                    }
                }
            }
        }
        return out
    }

    private class Cond(val t: List<Tok>, val fields: (String) -> String?) {
        var pos = 0
        fun peek(): Tok? = t.getOrNull(pos)
        fun isOp(s: String) = (peek() as? Tok.Op)?.text == s

        fun parse(): Boolean = or()

        fun or(): Boolean {
            var v = and()
            while (isOp("||")) {
                pos++
                val r = and()
                v = v || r
            }
            return v
        }

        fun and(): Boolean {
            var v = unit()
            while (isOp("&&")) {
                pos++
                val r = unit()
                v = v && r
            }
            return v
        }

        fun unit(): Boolean {
            if (isOp("(")) {
                pos++
                val v = or()
                if (isOp(")")) pos++
                return v
            }
            val left = value(t.getOrNull(pos++))
            val op = (peek() as? Tok.Op)?.text
            if (op == null || op in listOf("&&", "||", ")")) return !left.isNullOrEmpty() && left != "false"
            pos++
            if (op == "[]") {
                val lo = value(t.getOrNull(pos++))
                if (isOp("..")) pos++
                val hi = value(t.getOrNull(pos++))
                val x = left?.toDoubleOrNull() ?: return false
                return x >= (lo?.toDoubleOrNull() ?: return false) && x <= (hi?.toDoubleOrNull() ?: return false)
            }
            val rightTok = t.getOrNull(pos++)
            if (rightTok is Tok.Word && rightTok.text == "empty") {
                return when (op) {
                    "==" -> left.isNullOrEmpty()
                    "!=" -> !left.isNullOrEmpty()
                    else -> false
                }
            }
            val l = left.orEmpty()
            val r = value(rightTok).orEmpty()
            return when (op) {
                "==" -> l.equals(r, ignoreCase = true)
                "!=" -> !l.equals(r, ignoreCase = true)
                "+=" -> l.contains(r, ignoreCase = true)
                "-=" -> !l.contains(r, ignoreCase = true)
                ">" -> compare(l, r) > 0
                "<" -> compare(l, r) < 0
                else -> false
            }
        }

        fun compare(a: String, b: String): Int {
            val x = a.toDoubleOrNull()
            val y = b.toDoubleOrNull()
            return if (x != null && y != null) x.compareTo(y) else a.compareTo(b, ignoreCase = true)
        }

        fun value(tok: Tok?): String? = when (tok) {
            is Tok.Lit -> tok.text
            is Tok.Word -> fields(tok.text)
            else -> null
        }
    }

    // --- markup -------------------------------------------------------------------------------

    private enum class Mark(val token: String) { BOLD("**"), UNDERLINE("__"), STRIKE("~~"), ITALIC("//"), CAPS("^^") }

    private fun markup(flat: String): List<List<AnnotatedString>> =
        flat.split('\n').map { line -> line.split('\t').map(::styled) }.let { lines ->
            // Drop empty lines Symfonium wouldn't show.
            lines.filter { parts -> parts.any { it.text.isNotBlank() } }
        }

    private fun styled(s: String): AnnotatedString = buildAnnotatedString {
        val on = HashSet<Mark>()
        var i = 0
        val text = StringBuilder()
        fun flush() {
            if (text.isEmpty()) return
            val chunk = if (Mark.CAPS in on) text.toString().uppercase() else text.toString()
            withStyle(
                SpanStyle(
                    fontWeight = if (Mark.BOLD in on) FontWeight.Bold else null,
                    fontStyle = if (Mark.ITALIC in on) FontStyle.Italic else null,
                    textDecoration = TextDecoration.combine(
                        listOfNotNull(
                            TextDecoration.Underline.takeIf { Mark.UNDERLINE in on },
                            TextDecoration.LineThrough.takeIf { Mark.STRIKE in on },
                        ),
                    ),
                ),
            ) { append(chunk) }
            text.clear()
        }
        while (i < s.length) {
            // "//" right after ":" is a link (https://), not italics.
            val mark = Mark.entries.firstOrNull { s.startsWith(it.token, i) && !(it == Mark.ITALIC && i > 0 && s[i - 1] == ':') }
            if (mark != null) {
                flush()
                if (!on.remove(mark)) on += mark
                i += mark.token.length
            } else {
                text.append(s[i])
                i++
            }
        }
        flush()
    }
}
