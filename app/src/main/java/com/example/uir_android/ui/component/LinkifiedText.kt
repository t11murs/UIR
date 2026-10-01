package com.example.uir_android.ui.component

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

private const val LinkTag = "external_link"
private val LinkPattern = Regex(
    "https?://[^\\s<>]+|www\\.[^\\s<>]+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|@[A-Za-z0-9_]{5,32}",
    RegexOption.IGNORE_CASE
)
private val TrailingPunctuation = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}')

@Composable
fun LinkifiedText(
    text: String,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val annotatedText = remember(text, linkColor) {
        buildLinkifiedText(text, linkColor)
    }
    @Suppress("DEPRECATION")
    ClickableText(
        text = annotatedText,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
        onClick = { offset ->
            annotatedText.getStringAnnotations(LinkTag, offset, offset)
                .firstOrNull()
                ?.item
                ?.let(onOpenLink)
        }
    )
}

internal fun buildLinkifiedText(text: String, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        LinkPattern.findAll(text).forEach { match ->
            val displayed = match.value.trimEnd { it in TrailingPunctuation }
            if (displayed.isEmpty()) return@forEach
            val end = match.range.first + displayed.length
            val target = when {
                displayed.startsWith("@") -> "https://t.me/${displayed.drop(1)}"
                '@' in displayed && !displayed.startsWith("http", true) -> "mailto:$displayed"
                displayed.startsWith("www.", true) -> "https://$displayed"
                else -> displayed
            }
            addStringAnnotation(LinkTag, target, match.range.first, end)
            addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                match.range.first,
                end
            )
        }
    }
}
