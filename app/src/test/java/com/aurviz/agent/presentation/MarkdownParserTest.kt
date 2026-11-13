package com.aurviz.agent.presentation

import com.aurviz.agent.presentation.components.MarkdownParser
import com.aurviz.agent.presentation.components.MdBlock
import com.aurviz.agent.presentation.components.MdSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun boldItalicAndCodeBecomeSpansWithoutMarkers() {
        val spans = MarkdownParser.inline("**Subject:** Hello *there* `a@b.com`")
        assertEquals(MdSpan("Subject:", bold = true), spans[0])
        assertEquals(MdSpan(" Hello "), spans[1])
        assertEquals(MdSpan("there", italic = true), spans[2])
        assertEquals(MdSpan("a@b.com", code = true), spans.last())
        assertTrue(spans.none { it.text.contains("*") || it.text.contains("`") })
    }

    @Test
    fun listsAndHeadingsAreRecognised() {
        val blocks = MarkdownParser.parse("# Summary\n1. **LinkedIn** (Thu)\n   - Subject: hi\n- plain item\n\nDone.")
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals("1.", (blocks[1] as MdBlock.ListItem).marker)
        val nested = blocks[2] as MdBlock.ListItem
        assertEquals("•", nested.marker)
        assertEquals(1, nested.indent)
        assertTrue(blocks.last() is MdBlock.Paragraph)
    }

    @Test
    fun unbalancedMarkersFallBackToPlainText() {
        val spans = MarkdownParser.inline("5 * 3 = 15 and **unclosed")
        assertEquals("5 * 3 = 15 and **unclosed", spans.joinToString("") { it.text })
    }
}
