package app.overbay.orbit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings

/**
 * Animated character-by-character odometer ticker for numbers and balances.
 * Uses tabular numerals ('tnum') and vertical spring physics reels.
 * Renders a lightweight single [Text] node on initial mount and activates per-digit
 * spring reels only once [text] changes after first composition.
 */
@Composable
fun RollingTickerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val tnumStyle = remember(style) { style.copy(fontFeatureSettings = "tnum") }
    val initialText = remember { text }
    var hasChangedSinceMount by remember { mutableStateOf(false) }
    if (!hasChangedSinceMount && text != initialText) {
        hasChangedSinceMount = true
    }

    if (!hasChangedSinceMount) {
        Text(
            text = text,
            modifier = modifier,
            style = tnumStyle,
            color = color
        )
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (char in text) {
            if (char.isDigit()) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val slideUp = targetState > initialState
                        if (slideUp) {
                            slideInVertically(
                                animationSpec = OrbitSprings.TickerOffsetSpring,
                                initialOffsetY = { it }
                            ) togetherWith slideOutVertically(
                                animationSpec = OrbitSprings.TickerOffsetSpring,
                                targetOffsetY = { -it }
                            )
                        } else {
                            slideInVertically(
                                animationSpec = OrbitSprings.TickerOffsetSpring,
                                initialOffsetY = { -it }
                            ) togetherWith slideOutVertically(
                                animationSpec = OrbitSprings.TickerOffsetSpring,
                                targetOffsetY = { it }
                            )
                        }
                    },
                    label = "TickerDigit"
                ) { digitChar ->
                    Text(
                        text = digitChar.toString(),
                        style = tnumStyle,
                        color = color
                    )
                }
            } else {
                Text(
                    text = char.toString(),
                    style = tnumStyle,
                    color = color
                )
            }
        }
    }
}

/**
 * High-level helper for formatting and animating raw integer [cents] with [RollingTickerText].
 */
@Composable
fun RollingTickerCents(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    showSign: Boolean = false
) {
    val formatted = MoneyUtils.formatCents(cents, showCents = true, signed = showSign)
    RollingTickerText(
        text = formatted,
        modifier = modifier,
        style = style,
        color = color
    )
}
