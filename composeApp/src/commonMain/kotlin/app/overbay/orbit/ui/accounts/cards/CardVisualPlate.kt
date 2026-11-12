package app.overbay.orbit.ui.accounts.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
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

    Surface(
        modifier = modifier.orbitTactilePress(),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.2f))
    ) {
        if (artworkBitmap != null) {
            Image(
                bitmap = artworkBitmap,
                contentDescription = name.ifBlank { "Credit Card" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(startColor, endColor)))
                    .padding(10.dp)
            ) {
                Text(
                    text = (issuer?.takeIf { it.isNotBlank() } ?: "ORBIT").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    color = textColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(width = 24.dp, height = 18.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(textColor.copy(alpha = 0.3f))
                )
                Text(
                    text = last4?.takeIf { it.isNotBlank() }?.let { "•••• $it" } ?: name.ifBlank { "Credit Card" },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
    }
}
