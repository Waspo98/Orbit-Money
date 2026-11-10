package app.overbay.orbit.ui.accounts.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.CardArtPalette

@Composable
fun CardFace(
    name: String,
    issuer: String?,
    network: String?,
    artKey: String?,
    holderLabel: String?,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (targetStart, targetEnd, targetText) = CardArtPalette.resolveColors(
        key = artKey,
        fallbackStart = MaterialTheme.colorScheme.primary,
        fallbackEnd = MaterialTheme.colorScheme.tertiary,
        fallbackText = MaterialTheme.colorScheme.onPrimary
    )

    val startColor by animateColorAsState(targetStart, spring(stiffness = Spring.StiffnessLow), label = "cardGradStart")
    val endColor by animateColorAsState(targetEnd, spring(stiffness = Spring.StiffnessLow), label = "cardGradEnd")
    val textColor by animateColorAsState(targetText, spring(stiffness = Spring.StiffnessLow), label = "cardTextCol")

    val displayName = name.ifBlank { "Credit Card" }
    val displayIssuer = issuer?.takeIf { it.isNotBlank() } ?: "Orbit Card"
    val cardHeight = if (compact) 92.dp else 138.dp
    val shapeRadius = if (compact) 14.dp else 18.dp
    val elevation = if (compact) 2.dp else 4.dp
    val padding = if (compact) 10.dp else 14.dp

    Surface(
        modifier = modifier.fillMaxWidth().height(cardHeight),
        shape = RoundedCornerShape(shapeRadius),
        shadowElevation = elevation,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.22f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(startColor, endColor)))
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row: Issuer and Network
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayIssuer.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = if (compact) 9.sp else 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = textColor.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!network.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = textColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = network.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                fontSize = if (compact) 8.5.sp else 10.sp,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Middle: Chip visual & Card Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = if (compact) 24.dp else 30.dp, height = if (compact) 18.dp else 22.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(textColor.copy(alpha = 0.28f))
                    )
                    Text(
                        text = displayName,
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Bottom row: Cardholder Label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!holderLabel.isNullOrBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = textColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = holderLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = if (compact) 8.5.sp else 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
