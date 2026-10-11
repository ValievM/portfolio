package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.example.phoneagent.ui.designsystem.theme.AppCodeTextStyle
import com.example.phoneagent.ui.designsystem.theme.AppMonospace
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes

private const val DARK_BACKGROUND_LUMINANCE = 0.5f

/**
 * The only place that talks to the markdown library (docs/design/components.md). The reply is
 * untrusted model output, so:
 * - images are never loaded: no image module is on the classpath and the transformer is a no-op;
 * - links are never opened here: every tap goes to [onLinkClick], which must ask the user first
 *   (see [evaluateLink] and [LinkConfirmationSheet]);
 * - HTML is not rendered (the parser treats it as text).
 * Text arrives in chunks while streaming, so the previous render stays on screen while the new
 * text is parsed, and an unclosed code fence simply renders as code up to the current end.
 */
@Composable
fun AssistantMarkdown(
    text: String,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val linkHandler =
        remember {
            object : UriHandler {
                override fun openUri(uri: String) = currentOnLinkClick(uri)
            }
        }
    val markdownState = rememberMarkdownState(content = closeUnclosedFence(text), retainState = true)

    CompositionLocalProvider(LocalUriHandler provides linkHandler) {
        SelectionContainer(modifier = modifier) {
            Markdown(
                markdownState = markdownState,
                colors = assistantMarkdownColors(),
                typography = assistantMarkdownTypography(),
                modifier = Modifier.fillMaxWidth(),
                imageTransformer = NoOpImageTransformerImpl(),
                components = assistantMarkdownComponents(),
            )
        }
    }
}

@Composable
private fun assistantMarkdownColors(): MarkdownColors {
    val colors = MaterialTheme.colorScheme
    return markdownColor(
        text = colors.onSurface,
        codeBackground = colors.surfaceContainerHigh,
        inlineCodeBackground = colors.surfaceContainerHigh,
        dividerColor = colors.outlineVariant,
        tableBackground = colors.surfaceContainer,
    )
}

@Composable
private fun assistantMarkdownTypography(): MarkdownTypography {
    val typography = MaterialTheme.typography
    return markdownTypography(
        h1 = typography.titleLarge,
        h2 = typography.titleMedium,
        h3 = typography.titleSmall,
        h4 = typography.titleSmall,
        h5 = typography.labelLarge,
        h6 = typography.labelLarge,
        code = AppCodeTextStyle,
        inlineCode = typography.bodyLarge.copy(fontFamily = AppMonospace, fontSize = TextUnit.Unspecified),
        textLink =
            TextLinkStyles(
                style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
            ),
    )
}

/** Code blocks are highlighted for the current theme; the checkbox is the m3 default. */
@Composable
private fun assistantMarkdownComponents(): MarkdownComponents {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < DARK_BACKGROUND_LUMINANCE
    val highlights = remember(darkTheme) { Highlights.Builder().theme(SyntaxThemes.default(darkMode = darkTheme)) }
    return markdownComponents(
        codeBlock = {
            MarkdownHighlightedCodeBlock(
                content = it.content,
                node = it.node,
                style = it.typography.code,
                highlightsBuilder = highlights,
            )
        },
        codeFence = {
            MarkdownHighlightedCodeFence(
                content = it.content,
                node = it.node,
                style = it.typography.code,
                highlightsBuilder = highlights,
            )
        },
        checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
    )
}
