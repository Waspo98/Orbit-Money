package app.overbay.orbit.ui.accounts.cards

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.CreditCardTemplates
import app.overbay.orbit.theme.CardArtPalette
import app.overbay.orbit.ui.transactions.components.MerchantLogoImageCache

@Composable
internal fun rememberCardArtworkBitmap(
    artworkUrl: String?,
    name: String,
    issuer: String?
): ImageBitmap? {
    val cleanArtworkUrl = remember(artworkUrl, name, issuer) {
        CreditCardTemplates.resolveArtworkUrl(artworkUrl, name, issuer)
    }
    var artworkBitmap by remember(cleanArtworkUrl) {
        mutableStateOf<ImageBitmap?>(MerchantLogoImageCache.getCached(cleanArtworkUrl))
    }
    LaunchedEffect(cleanArtworkUrl) {
        if (cleanArtworkUrl == null) {
            artworkBitmap = null
        } else if (artworkBitmap == null && !MerchantLogoImageCache.isKnownFailed(cleanArtworkUrl)) {
            artworkBitmap = MerchantLogoImageCache.fetchBitmap(cleanArtworkUrl)
        }
    }
    return artworkBitmap
}

@Composable
fun CardArtworkThumbnail(
    name: String,
    issuer: String?,
    artKey: String?,
    artworkUrl: String?,
    last4: String? = null,
    showLabels: Boolean = true,
    modifier: Modifier = Modifier
) {
    val artworkBitmap = rememberCardArtworkBitmap(artworkUrl, name, issuer)
    val effectiveArtKey = remember(artKey, name, issuer) {
        artKey?.takeIf { it.isNotBlank() } ?: CreditCardTemplates.resolveTemplate(name, issuer)?.imageUrl
    }

    val (startColor, endColor, textColor) = CardArtPalette.resolveColors(
        key = effectiveArtKey,
        fallbackStart = MaterialTheme.colorScheme.primary,
        fallbackEnd = MaterialTheme.colorScheme.tertiary,
        fallbackText = MaterialTheme.colorScheme.onPrimary
    )

    val cornerRadius = if (showLabels) 6.dp else 4.dp
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        shadowElevation = if (showLabels) 2.dp else 0.dp,
        border = BorderStroke(0.75.dp, textColor.copy(alpha = 0.22f))
    ) {
        val loadedBitmap = artworkBitmap
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap,
                contentDescription = if (showLabels) name else null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!showLabels) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(startColor, endColor)))
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(width = 6.dp, height = 4.5.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(textColor.copy(alpha = 0.38f))
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(startColor, endColor)))
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            ) {
                issuer?.takeIf { it.isNotBlank() }?.let { iss ->
                    Text(
                        text = iss.removeSuffix(" Bank").take(8).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 6.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        color = textColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 2.dp)
                        .size(width = 11.dp, height = 8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(textColor.copy(alpha = 0.34f))
                )
                val bottomLabel = last4?.takeIf { it.isNotBlank() }?.let { "•• $it" }
                    ?: name.take(12)
                Text(
                    text = bottomLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 7.5.sp,
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
