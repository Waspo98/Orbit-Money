package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CreditCardStrategy
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils

@Composable
fun CreditCardLivePreviewCard(
    accountName: String,
    cardName: String,
    issuerName: String,
    network: String,
    imageUrl: String,
    annualFeeCents: Cents,
    strategy: CreditCardStrategy,
    modifier: Modifier = Modifier
) {
    val (targetStart, targetEnd, targetText) = when (imageUrl) {
        "preset://sapphire-blue" -> Triple(Color(0xFF0F2B5B), Color(0xFF1E56A0), Color.White)
        "preset://amex-gold" -> Triple(Color(0xFF8C621E), Color(0xFFD4AF37), Color(0xFF1B1406))
        "preset://venture-silver" -> Triple(Color(0xFF232931), Color(0xFF4E5B6E), Color.White)
        "preset://apple-card" -> Triple(Color(0xFFD8DCE3), Color(0xFFF5F7FA), Color(0xFF1D1F23))
        "preset://discover-plum" -> Triple(Color(0xFF4A1C40), Color(0xFF8A307F), Color.White)
        else -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onPrimary
        )
    }

    val startColor by animateColorAsState(targetStart, spring(stiffness = Spring.StiffnessLow), label = "cardGradStart")
    val endColor by animateColorAsState(targetEnd, spring(stiffness = Spring.StiffnessLow), label = "cardGradEnd")
    val textColor by animateColorAsState(targetText, spring(stiffness = Spring.StiffnessLow), label = "cardTextCol")

    val displayTitle = cardName.ifBlank { accountName.ifBlank { "Your Credit Card" } }
    val displayIssuer = issuerName.ifBlank { "Select a template or customize below" }

    Surface(
        modifier = modifier.fillMaxWidth().height(138.dp),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.22f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(startColor, endColor)))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayIssuer.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = textColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(shape = RoundedCornerShape(6.dp), color = textColor.copy(alpha = 0.16f)) {
                        Text(
                            text = network.ifBlank { "VISA" }.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 30.dp, height = 22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(textColor.copy(alpha = 0.28f))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (accountName.isNotBlank() && !accountName.equals(displayTitle, ignoreCase = true)) {
                            Text(
                                text = accountName,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        strategy.cardholder?.takeIf { it.isNotBlank() }?.let { holder ->
                            CardPreviewChip(text = holder, textColor = textColor)
                        }
                        CardPreviewChip(
                            text = if (strategy.isBusinessCard) "Business • 5/24 Exempt" else "Personal • 5/24",
                            textColor = textColor
                        )
                        strategy.openedDate?.let { opened ->
                            CardPreviewChip(text = "Since ${opened.take(7)}", textColor = textColor)
                        }
                    }
                    Text(
                        text = if (annualFeeCents > 0L) "${MoneyUtils.formatCents(annualFeeCents, showCents = false)}/yr" else "$0 Fee",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CardPreviewChip(text: String, textColor: Color) {
    Surface(shape = CircleShape, color = textColor.copy(alpha = 0.16f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun WizardSectionContainer(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (!subtitle.isNullOrBlank()) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
fun ExpandableSettingsCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun LumaWizardBottomBar(
    currentPage: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentPage > 0) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Step", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(pageCount) { index ->
                val isSelected = currentPage == index
                val dotWidth by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else 8.dp,
                    animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                    label = "wizardDotWidth"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "wizardDotColor"
                )
                Box(modifier = Modifier.height(8.dp).width(dotWidth).clip(CircleShape).background(dotColor))
            }
        }

        if (currentPage < pageCount - 1) {
            FilledTonalIconButton(
                onClick = onNext,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = CircleShape
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Step")
            }
        } else {
            Button(onClick = onSave, shape = RoundedCornerShape(12.dp)) {
                Text("Save Card", fontWeight = FontWeight.Bold)
            }
        }
    }
}
