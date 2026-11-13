package com.aurviz.agent.presentation.components

/** One run of inline text with its formatting. */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false
)

sealed class MdBlock {
    data class Heading(val spans: List<MdSpan>) : MdBlock()
    data class Paragraph(val spans: List<MdSpan>) : MdBlock()
    /** [marker] is "•" for bullets or "1." style for numbered items. */
    data class ListItem(val marker: String, val spans: List<MdSpan>, val indent: Int) : MdBlock()
}

/**
 * Small, dependency-free parser for the Markdown subset Gemini emits in chat answers:
 * headings, bullet and numbered lists, **bold**, *italic* and `code`. Anything else is
 * shown as plain text, so malformed Markdown can never crash the UI.
 */
object MarkdownParser {

    private val HEADING = Regex("^#{1,6}\\s+(.*)$")
    private val BULLET = Regex("^(\\s*)[-*•]\\s+(.*)$")
    private val NUMBERED = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
    private val INLINE = Regex("(\\*\\*[^*\\n]+\\*\\*)|(`[^`\\n]+`)|(\\*[^*\\s][^*\\n]*\\*)|(__[^_\\n]+__)")

    fun parse(markdown: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        for (rawLine in markdown.replace("\r\n", "\n").lines()) {
            if (rawLine.isBlank()) continue
            val heading = HEADING.matchEntire(rawLine.trim())
            val bullet = BULLET.matchEntire(rawLine)
            val numbered = NUMBERED.matchEntire(rawLine)
            blocks += when {
                heading != null -> MdBlock.Heading(inline(heading.groupValues[1]))
                bullet != null -> MdBlock.ListItem("•", inline(bullet.groupValues[2]), bullet.groupValues[1].length / 2)
                numbered != null -> MdBlock.ListItem(
                    "${numbered.groupValues[2]}.", inline(numbered.groupValues[3]), numbered.groupValues[1].length / 2
                )
                else -> MdBlock.Paragraph(inline(rawLine.trim()))
            }
        }
        return blocks
    }

    fun inline(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        var cursor = 0
        for (match in INLINE.findAll(text)) {
            if (match.range.first > cursor) spans += MdSpan(text.substring(cursor, match.range.first))
            val token = match.value
            spans += when {
                token.startsWith("**") -> MdSpan(token.removeSurrounding("**"), bold = true)
                token.startsWith("__") -> MdSpan(token.removeSurrounding("__"), bold = true)
                token.startsWith("`") -> MdSpan(token.removeSurrounding("`"), code = true)
                else -> MdSpan(token.removeSurrounding("*"), italic = true)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) spans += MdSpan(text.substring(cursor))
        return spans.ifEmpty { listOf(MdSpan(text)) }
    }
}
