package com.onikki.app.domain.notes

data class ChecklistItem(val text: String, val done: Boolean)

/**
 * A checklist note keeps its items in `Note.content`, one per line: "☐ text" or "☑ text".
 * Plain text so search, sharing and old app versions still see readable content.
 */
object Checklist {
    private const val OPEN = "☐ "
    private const val DONE = "☑ "
    private val BULLET = Regex("""^\s*(?:[-*•]\s+|\d+[.)]\s+|\[\s?]\s*|\[[xX]]\s*|✓\s*)""")

    fun parse(content: String): List<ChecklistItem> =
        content.lines().filter { it.isNotBlank() }.map { line ->
            when {
                line.startsWith(DONE) -> ChecklistItem(line.removePrefix(DONE), true)
                line.startsWith(OPEN) -> ChecklistItem(line.removePrefix(OPEN), false)
                line.startsWith("☑") -> ChecklistItem(line.removePrefix("☑").trim(), true)
                line.startsWith("☐") -> ChecklistItem(line.removePrefix("☐").trim(), false)
                else -> ChecklistItem(line.trim(), false)
            }
        }

    fun serialize(items: List<ChecklistItem>): String =
        items.filter { it.text.isNotBlank() }.joinToString("\n") { (if (it.done) DONE else OPEN) + it.text.trim() }

    fun toggle(content: String, index: Int): String {
        val items = parse(content)
        if (index !in items.indices) return content
        return serialize(items.mapIndexed { i, item -> if (i == index) item.copy(done = !item.done) else item })
    }

    /** Free text → items: one per non-blank line, list markers ("- ", "1. ", "[x]") understood. */
    fun fromPlain(text: String): String = serialize(
        text.lines().filter { it.isNotBlank() }.map { line ->
            val done = line.trimStart().let { it.startsWith("[x]", true) || it.startsWith("✓") }
            ChecklistItem(line.replace(BULLET, "").trim(), done)
        }
    )

    /** Items → free text; finished ones keep a "✓ " so nothing is lost. */
    fun toPlain(content: String): String =
        parse(content).joinToString("\n") { (if (it.done) "✓ " else "") + it.text }
}
