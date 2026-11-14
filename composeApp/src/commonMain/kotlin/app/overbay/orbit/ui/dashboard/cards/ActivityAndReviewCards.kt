package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitSectionCard

/**
 * Quick category check launch card opening the swipeable review modal,
 * reflecting the live review queue count.
 */
@Composable
fun CategorizeRecentCard(
    onOpenReview: () -> Unit,
    reviewCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val hasItems = reviewCount > 0

    OrbitSectionCard(
        title = "Categorize Recent Transactions",
        headerBadge = {
            OrbitBadge(
                text = if (hasItems) "$reviewCount waiting" else "Caught up",
                containerColor = (if (hasItems) colors.warning else colors.income).copy(alpha = 0.14f),
                contentColor = if (hasItems) colors.warning else colors.income
            )
        },
        actionText = if (hasItems) "Review" else "Open",
        onActionClick = onOpenReview,
        onCardClick = onOpenReview,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ReviewLaunchGraphic()

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasItems) "Quick category check" else "All caught up",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (hasItems) {
                        val noun = if (reviewCount == 1) "transaction needs" else "transactions need"
                        "$reviewCount $noun a quick category review."
                    } else {
                        "Everything is categorized—tap to review recent activity anyway."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun ReviewLaunchGraphic(
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val primary = MaterialTheme.colorScheme.primary
    val cardBorderColor = primary.copy(alpha = 0.36f)
    val cardShape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier.size(width = 84.dp, height = 78.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = cardShape,
            color = colors.danger.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, cardBorderColor),
            modifier = Modifier
                .size(width = 46.dp, height = 62.dp)
                .offset(x = (-17).dp)
                .graphicsLayer {
                    rotationZ = -22f
                    alpha = 0.75f
                }
        ) {}

        Surface(
            shape = cardShape,
            color = primary.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, cardBorderColor),
            modifier = Modifier
                .size(width = 46.dp, height = 62.dp)
                .offset(x = 18.dp)
                .graphicsLayer {
                    rotationZ = 20f
                    alpha = 0.75f
                }
        ) {}

        Surface(
            shape = cardShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, cardBorderColor),
            modifier = Modifier
                .size(width = 46.dp, height = 62.dp)
                .graphicsLayer {
                    rotationZ = -8f
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(9.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.45f))
                )
            }
        }
    }
}

