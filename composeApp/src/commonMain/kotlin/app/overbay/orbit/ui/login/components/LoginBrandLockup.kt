package app.overbay.orbit.ui.login.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitSprings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import orbitmoney.composeapp.generated.resources.Res
import orbitmoney.composeapp.generated.resources.orbit_logo
import org.jetbrains.compose.resources.painterResource

/**
 * Animated brand lockup for the Orbit Money onboarding & login card.
 * Displays the authentic Orbit logo icon with spring settle physics
 * and staggered two-tone wordmark reveal.
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
        modifier = modifier,
        shape = shape,
        color = Color(0xFF50C878)
    ) {
        Image(
            painter = painterResource(Res.drawable.orbit_logo),
            contentDescription = "Orbit Money Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
