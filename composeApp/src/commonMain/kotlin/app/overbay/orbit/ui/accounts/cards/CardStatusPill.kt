package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme

enum class CardStatusTone {
    POSITIVE, WARNING, CRITICAL, NEUTRAL
}

@Composable
fun CardStatusPill(
    text: String,
    tone: CardStatusTone,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor, icon) = when (tone) {
        CardStatusTone.POSITIVE -> Triple(OrbitTheme.colors.incomeContainer, OrbitTheme.colors.income, Icons.Filled.CheckCircle)
        CardStatusTone.WARNING -> Triple(OrbitTheme.colors.warningContainer, OrbitTheme.colors.warning, Icons.Filled.Warning)
        CardStatusTone.CRITICAL -> Triple(OrbitTheme.colors.expenseContainer, OrbitTheme.colors.expense, Icons.Filled.Warning)
        CardStatusTone.NEUTRAL -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.Info)
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
