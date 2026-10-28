package app.overbay.orbit.ui.login.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitSprings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Immich-style animated brand lockup for the Orbit Money login card.
 * Emulates `main` branch's `.login-brand-lockup` with an icon spring settle
 * and staggered wordmark reveal.
 */
@Composable
fun LoginBrandLockup(
    modifier: Modifier = Modifier
) {
    val iconScale = remember { Animatable(1.32f) }
    val iconOffsetY = remember { Animatable(-18f) }
    val iconAlpha = remember { Animatable(0f) }

    val wordmarkAlpha = remember { Animatable(0f) }
    val wordmarkOffsetY = remember { Animatable(8f) }

    LaunchedEffect(Unit) {
        launch {
            iconAlpha.animateTo(1f, OrbitSprings.effectsDefault())
        }
        launch {
            iconScale.animateTo(1f, OrbitSprings.spatialDefault())
        }
        launch {
            iconOffsetY.animateTo(0f, OrbitSprings.spatialDefault())
        }
        launch {
            delay(140)
            launch {
                wordmarkAlpha.animateTo(1f, OrbitSprings.effectsDefault())
            }
            launch {
                wordmarkOffsetY.animateTo(0f, OrbitSprings.spatialDefault())
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LoginBrandIcon(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                    translationY = iconOffsetY.value * density
                    alpha = iconAlpha.value
                }
        )

        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold
                    )
                ) {
                    append("Orbit ")
                }
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("Money")
                }
            },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 28.sp,
                letterSpacing = (-0.6).sp
            ),
            modifier = Modifier.graphicsLayer {
                alpha = wordmarkAlpha.value
                translationY = wordmarkOffsetY.value * density
            }
        )
    }
}

@Composable
private fun LoginBrandIcon(
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.shadow(
            elevation = 14.dp,
            shape = shape,
            ambientColor = Color(0xFF047857),
            spotColor = Color(0xFF047857)
        ),
        shape = shape,
        color = Color(0xFF052E24)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val center = Offset(w * 0.5f, h * 0.5f)

                // Deep emerald ambient background gradient
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF064E3B),
                            Color(0xFF042F24),
                            Color(0xFF021C15)
                        ),
                        start = Offset.Zero,
                        end = Offset(w, h)
                    )
                )

                // Soft radial core glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF10B981).copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = w * 0.46f
                    ),
                    radius = w * 0.46f,
                    center = center
                )

                // Back half of tilted orbital ring
                withTransform({
                    rotate(degrees = -24f, pivot = center)
                }) {
                    drawArc(
                        color = Color(0xFF34D399).copy(alpha = 0.45f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.10f, h * 0.34f),
                        size = Size(w * 0.80f, h * 0.32f),
                        style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
                    )
                }

                // Central planet sphere
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6EE7B7),
                            Color(0xFF10B981),
                            Color(0xFF047857)
                        ),
                        start = Offset(w * 0.3f, h * 0.28f),
                        end = Offset(w * 0.7f, h * 0.72f)
                    ),
                    radius = w * 0.23f,
                    center = center
                )

                // Front half of tilted orbital ring + gold satellite node
                withTransform({
                    rotate(degrees = -24f, pivot = center)
                }) {
                    drawArc(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF6EE7B7),
                                Color(0xFFFCD34D),
                                Color(0xFF34D399)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.10f, h * 0.34f),
                        size = Size(w * 0.80f, h * 0.32f),
                        style = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
                    )

                    drawCircle(
                        color = Color(0xFFFDE68A),
                        radius = w * 0.055f,
                        center = Offset(w * 0.82f, h * 0.58f)
                    )
                }
            }
        }
    }
}
