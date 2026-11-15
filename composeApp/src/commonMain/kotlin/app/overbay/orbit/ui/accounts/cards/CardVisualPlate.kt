package app.overbay.orbit.ui.accounts.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.CreditCardTemplates
import app.overbay.orbit.theme.CardArtPalette
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.components.orbitTactilePress

/**
 * Fixed-aspect-ratio (1.586f) credit card visual plate used in Card Wallet rows and
 * the Credit Card detail header so card visuals never stretch or squish alongside text.
 */
@Composable
fun CardVisualPlate(
    name: String,
    issuer: String?,
    artKey: String?,
    artworkUrl: String? = null,
    last4: String? = null,
    modifier: Modifier = Modifier
) {
    val artworkBitmap = rememberCardArtworkBitmap(artworkUrl, name, issuer)
    val effectiveArtKey = remember(artKey, name, issuer) {
        artKey?.takeIf { it.isNotBlank() } ?: CreditCardTemplates.resolveTemplate(name, issuer)?.imageUrl
    }
    val (targetStart, targetEnd, targetText) = CardArtPalette.resolveColors(
        key = effectiveArtKey,
        fallbackStart = MaterialTheme.colorScheme.primary,
        fallbackEnd = MaterialTheme.colorScheme.tertiary,
        fallbackText = MaterialTheme.colorScheme.onPrimary
    )
    val startColor by animateColorAsState(targetStart, OrbitSprings.ColorEffectsDefault, label = "plateStart")
    val endColor by animateColorAsState(targetEnd, OrbitSprings.ColorEffectsDefault, label = "plateEnd")
    val textColor by animateColorAsState(targetText, OrbitSprings.ColorEffectsDefault, label = "plateText")
    val isExpectingRemoteArtwork = !artworkUrl.isNullOrBlank() && artworkBitmap == null

    Surface(
        modifier = modifier.orbitTactilePress(),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.24f))
    ) {
        if (artworkBitmap != null) {
            Image(
                bitmap = artworkBitmap,
                contentDescription = name.ifBlank { "Credit Card" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FallbackCardPlate(
                name = name,
                issuer = issuer,
                last4 = last4,
                startColor = startColor,
                endColor = endColor,
                textColor = textColor,
                shimmerLoading = isExpectingRemoteArtwork
            )
        }
    }
}

@Composable
private fun FallbackCardPlate(
    name: String,
    issuer: String?,
    last4: String?,
    startColor: Color,
    endColor: Color,
    textColor: Color,
    shimmerLoading: Boolean
) {
    val shimmerOffset = if (shimmerLoading) {
        val transition = rememberInfiniteTransition(label = "cardPlateShimmer")
        val offset by transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "cardPlateShimmerX"
        )
        offset
    } else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(startColor, endColor)))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, textColor.copy(alpha = 0.14f))
                ),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(9.dp)
    ) {
        if (shimmerOffset != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                            start = Offset(shimmerOffset * 220f, 0f),
                            end = Offset(shimmerOffset * 220f + 160f, 160f)
                        )
                    )
            )
        }

        Text(
            text = (issuer?.takeIf { it.isNotBlank() } ?: "ORBIT").uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = textColor.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart)
        )

        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            EmvContactChip(modifier = Modifier.size(width = 24.dp, height = 17.dp))
            ContactlessWaves(color = textColor.copy(alpha = 0.72f), modifier = Modifier.size(13.dp))
        }

        Text(
            text = last4?.takeIf { it.isNotBlank() }?.let { "•••• $it" } ?: name.ifBlank { "Credit Card" },
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun EmvContactChip(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(3.5.dp))) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFFEBD7A3), Color(0xFFC8A96A), Color(0xFFB3914F))
            ),
            cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
        )
        val lineColor = Color(0xFF4A3D22).copy(alpha = 0.45f)
        val stroke = 0.9.dp.toPx()
        drawLine(lineColor, Offset(w * 0.36f, 0f), Offset(w * 0.36f, h), strokeWidth = stroke)
        drawLine(lineColor, Offset(w * 0.64f, 0f), Offset(w * 0.64f, h), strokeWidth = stroke)
        drawLine(lineColor, Offset(0f, h * 0.36f), Offset(w * 0.36f, h * 0.36f), strokeWidth = stroke)
        drawLine(lineColor, Offset(w * 0.64f, h * 0.36f), Offset(w, h * 0.36f), strokeWidth = stroke)
        drawLine(lineColor, Offset(0f, h * 0.64f), Offset(w * 0.36f, h * 0.64f), strokeWidth = stroke)
        drawLine(lineColor, Offset(w * 0.64f, h * 0.64f), Offset(w, h * 0.64f), strokeWidth = stroke)
    }
}

@Composable
private fun ContactlessWaves(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
        val originX = -size.width * 0.25f
        val centerY = size.height / 2f
        listOf(0.45f, 0.72f, 1.0f).forEach { radiusFactor ->
            val radius = size.width * radiusFactor
            drawArc(
                color = color,
                startAngle = -42f,
                sweepAngle = 84f,
                useCenter = false,
                topLeft = Offset(originX + size.width * 0.2f - radius * 0.35f, centerY - radius * 0.65f),
                size = Size(radius * 1.15f, radius * 1.3f),
                style = stroke
            )
        }
    }
}
