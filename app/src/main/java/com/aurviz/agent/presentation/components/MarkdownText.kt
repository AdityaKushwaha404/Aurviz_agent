package com.aurviz.agent.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Renders the Markdown subset produced by the agent using the active theme colours. */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = annotated(block.spans, codeBackground),
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                )
                is MdBlock.Paragraph -> Text(
                    text = annotated(block.spans, codeBackground),
                    color = color,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                is MdBlock.ListItem -> Row(modifier = Modifier.padding(start = (block.indent * 14).dp)) {
                    Text(
                        text = block.marker,
                        color = color.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        modifier = Modifier.widthIn(min = 22.dp)
                    )
                    Text(
                        text = annotated(block.spans, codeBackground),
                        color = color,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

private fun annotated(spans: List<MdSpan>, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            background = if (span.code) codeBackground else Color.Unspecified
        )
        withStyle(style) { append(span.text) }
    }
}
