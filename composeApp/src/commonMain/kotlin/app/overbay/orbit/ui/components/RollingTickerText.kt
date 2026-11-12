package app.overbay.orbit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
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
 * Uses tabular numerals ('tnum'), right-anchored place-value keys so digit count changes
 * (`$999.00` -> `$1,000.00`) keep trailing digits aligned, overall numeric direction hints,
 * and unclipped spring overshoot (`SizeTransform(clip = false)`).
 *
 * Pass [animate] = false during high-frequency pointer scrubbing to render a single static [Text].
 */
@Composable
fun RollingTickerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    animate: Boolean = true,
    rollDirectionHint: Int = 0
) {
    val tnumStyle = remember(style) { style.copy(fontFeatureSettings = "tnum") }
    if (!animate) {
        Text(
            text = text,
            modifier = modifier,
            style = tnumStyle,
            color = color
        )
        return
    }

    var previousText by remember { mutableStateOf(text) }
    val inferredDirection = remember(text, rollDirectionHint) {
        val dir = if (rollDirectionHint != 0) {
            rollDirectionHint
        } else {
            compareNumericMagnitude(text, previousText)
        }
        previousText = text
        dir
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        text.forEachIndexed { index, char ->
            val offsetFromEnd = text.length - 1 - index
            key(offsetFromEnd) {
                if (char.isDigit()) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            val slideUp = if (inferredDirection != 0) {
                                inferredDirection > 0
                            } else {
                                targetState >= initialState
                            }
                            val enter = slideInVertically(
                                animationSpec = OrbitSprings.TickerOffsetSpring,
                                initialOffsetY = { if (slideUp) it else -it }
                            ) + fadeIn(OrbitSprings.FloatEffectsFast)
                            val exit = slideOutVertically(
                                animationSpec = OrbitSprings.TickerOffsetSpring,
                                targetOffsetY = { if (slideUp) -it else it }
                            ) + fadeOut(OrbitSprings.FloatEffectsFast)
                            (enter togetherWith exit).using(SizeTransform(clip = false))
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
    showSign: Boolean = false,
    animate: Boolean = true
) {
    var previousCents by remember { mutableLongStateOf(cents) }
    val direction = remember(cents) {
        val cmp = cents.compareTo(previousCents)
        previousCents = cents
        cmp
    }
    val formatted = MoneyUtils.formatCents(cents, showCents = true, signed = showSign)
    RollingTickerText(
        text = formatted,
        modifier = modifier,
        style = style,
        color = color,
        animate = animate,
        rollDirectionHint = direction
    )
}

private fun compareNumericMagnitude(next: String, prev: String): Int {
    if (next == prev) return 0
    return extractSignedDigits(next).compareTo(extractSignedDigits(prev))
}

private fun extractSignedDigits(raw: String): Long {
    var negative = false
    var acc = 0L
    for (ch in raw) {
        if (ch == '-' && acc == 0L) {
            negative = true
        } else if (ch.isDigit()) {
            acc = acc * 10L + (ch - '0')
        }
    }
    return if (negative) -acc else acc
}
