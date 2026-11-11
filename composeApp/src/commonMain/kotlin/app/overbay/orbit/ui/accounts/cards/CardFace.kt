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
    modifier: Modifier = Modifier
) {
    val artworkBitmap = rememberCardArtworkBitmap(artworkUrl, name, issuer)

    val (startColor, endColor, textColor) = CardArtPalette.resolveColors(
        key = artKey,
        fallbackStart = MaterialTheme.colorScheme.primary,
        fallbackEnd = MaterialTheme.colorScheme.tertiary,
        fallbackText = MaterialTheme.colorScheme.onPrimary
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(0.75.dp, textColor.copy(alpha = 0.22f))
    ) {
        val loadedBitmap = artworkBitmap
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(startColor, endColor)))
                    .padding(5.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(width = 11.dp, height = 8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(textColor.copy(alpha = 0.32f))
                )
                Text(
                    text = name.take(12),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
