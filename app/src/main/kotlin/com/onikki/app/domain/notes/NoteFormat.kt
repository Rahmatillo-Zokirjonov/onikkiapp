package com.onikki.app.domain.notes

/**
 * Light formatting kept as plain text (so search, sharing and AI see readable words):
 * `# Sarlavha`, `## Kichik sarlavha`, `- band`, `> iqtibos` at line start; `**qalin**`, `*kursiv*`,
 * `~~o'chirilgan~~` inline. The editor styles these live without hiding the markers.
 */
object NoteFormat {
    enum class Style { H1, H2, BULLET, QUOTE, BOLD, ITALIC, STRIKE, MARKER }

    data class Span(val start: Int, val end: Int, val style: Style)

    private val BOLD = Regex("""\*\*(.+?)\*\*""")
    private val ITALIC = Regex("""(?<![*\w])\*(?!\s)([^*\n]+?)(?<!\s)\*(?![*\w])""")
    private val STRIKE = Regex("""~~(.+?)~~""")

    fun spans(text: String): List<Span> {
        val out = mutableListOf<Span>()
        var lineStart = 0
        text.split('\n').forEach { line ->
            val end = lineStart + line.length
            fun prefixed(prefix: String, style: Style) {
                out += Span(lineStart, lineStart + prefix.length, Style.MARKER)
                out += Span(lineStart + prefix.length, end, style)
            }
            when {
                line.startsWith("## ") -> prefixed("## ", Style.H2)
                line.startsWith("# ") -> prefixed("# ", Style.H1)
                line.startsWith("- ") || line.startsWith("• ") -> out += Span(lineStart, lineStart + 1, Style.BULLET)
                line.startsWith("> ") -> prefixed("> ", Style.QUOTE)
            }
            lineStart = end + 1
        }
        fun inline(regex: Regex, marker: Int, style: Style) {
            regex.findAll(text).forEach { m ->
                out += Span(m.range.first, m.range.first + marker, Style.MARKER)
                out += Span(m.range.first + marker, m.range.last + 1 - marker, style)
                out += Span(m.range.last + 1 - marker, m.range.last + 1, Style.MARKER)
            }
        }
        inline(BOLD, 2, Style.BOLD)
        inline(ITALIC, 1, Style.ITALIC)
        inline(STRIKE, 2, Style.STRIKE)
        return out.filter { it.end > it.start }
    }

    /** Text without the markers — for list previews, search and AI. */
    fun plain(text: String): String = text.split('\n').joinToString("\n") { line ->
        val body = when {
            line.startsWith("## ") -> line.drop(3)
            line.startsWith("# ") -> line.drop(2)
            line.startsWith("> ") -> line.drop(2)
            line.startsWith("- ") -> "• " + line.drop(2)
            else -> line
        }
        body.replace(BOLD, "$1").replace(STRIKE, "$1").replace(ITALIC, "$1")
    }

    data class Edit(val text: String, val selStart: Int, val selEnd: Int)

    /** Wraps the selection in [marker] (or unwraps it if it already is); with no selection, inserts a pair. */
    fun toggleWrap(text: String, selStart: Int, selEnd: Int, marker: String): Edit {
        val a = minOf(selStart, selEnd).coerceIn(0, text.length)
        val b = maxOf(selStart, selEnd).coerceIn(0, text.length)
        val m = marker.length
        if (a >= m && b + m <= text.length && text.substring(a - m, a) == marker && text.substring(b, b + m) == marker) {
            return Edit(text.removeRange(b, b + m).removeRange(a - m, a), a - m, b - m)
        }
        val selected = text.substring(a, b)
        if (selected.length >= 2 * m && selected.startsWith(marker) && selected.endsWith(marker)) {
            val inner = selected.substring(m, selected.length - m)
            return Edit(text.replaceRange(a, b, inner), a, a + inner.length)
        }
        return Edit(text.replaceRange(a, b, marker + selected + marker), a + m, b + m)
    }

    /** Adds [prefix] to every line touched by the selection, or removes it if all of them have it. */
    fun toggleLinePrefix(text: String, selStart: Int, selEnd: Int, prefix: String): Edit {
        val a = minOf(selStart, selEnd).coerceIn(0, text.length)
        val b = maxOf(selStart, selEnd).coerceIn(0, text.length)
        val first = text.lastIndexOf('\n', a - 1).let { if (it < 0) 0 else it + 1 }
        val last = text.indexOf('\n', b).let { if (it < 0) text.length else it }
        val lines = text.substring(first, last).split('\n')
        val others = listOf("# ", "## ", "- ", "> ").filter { it != prefix }
        val allHave = lines.all { it.startsWith(prefix) }
        val changed = lines.map { line ->
            if (allHave) line.removePrefix(prefix)
            else prefix + (others.firstOrNull { line.startsWith(it) }?.let { line.removePrefix(it) } ?: line)
        }
        val block = changed.joinToString("\n")
        val delta = block.length - (last - first)
        return Edit(text.replaceRange(first, last, block), (a + if (allHave) -prefix.length else prefix.length).coerceIn(first, first + block.length), (b + delta).coerceIn(first, first + block.length))
    }
}

data class NoteTemplate(val icon: String, val name: String, val title: String, val content: String, val checklist: Boolean = false, val tags: List<String> = emptyList())

object NoteTemplates {
    val all = listOf(
        NoteTemplate("🛒", "Xarid ro'yxati", "Xaridlar", "☐ Non\n☐ Sut\n☐ Meva", checklist = true, tags = listOf("xarid")),
        NoteTemplate(
            "🤝", "Uchrashuv", "Uchrashuv: ",
            "**Kim bilan:** \n**Qachon:** \n\n## Muhokama\n- \n\n## Kelishilgan ishlar\n- ",
            tags = listOf("uchrashuv")
        ),
        NoteTemplate("💡", "G'oya", "G'oya: ", "## Muammo\n\n## Yechim\n\n## Birinchi qadam\n- ", tags = listOf("g'oya")),
        NoteTemplate(
            "📖", "Kitob konspekti", "Kitob: ",
            "**Muallif:** \n\n## Asosiy fikrlar\n- \n\n## Iqtiboslar\n> \n\n## Hayotimda qo'llash\n- ",
            tags = listOf("kitob")
        ),
        NoteTemplate(
            "🗓", "Haftalik reja", "Hafta rejasi",
            "## Asosiy 3 ta maqsad\n- \n- \n- \n\n## Kunlar\n**Du:** \n**Se:** \n**Ch:** \n**Pa:** \n**Ju:** \n**Sh:** \n**Ya:** ",
            tags = listOf("reja")
        ),
        NoteTemplate("✅", "Ishlar ro'yxati", "Qilinadigan ishlar", "☐ ", checklist = true)
    )

    /** Kundalik (journal) prompts for a new day. */
    const val JOURNAL = "## Bugun nima yaxshi bo'ldi?\n\n\n## Nimani o'rgandim?\n\n\n## Nimaga minnatdorman?\n\n\n## Ertaga eng muhim ish\n"
}
