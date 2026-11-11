package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.accounts.AccountTypeCategory
import app.overbay.orbit.ui.accounts.cards.CardArtworkThumbnail
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.transactions.components.MerchantLogoImageCache
import app.overbay.orbit.ui.transactions.components.RemoteLogoImage
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Leading visual for an Account row:
 * - Credit Cards: 60×38dp landscape (1.586:1) physical card face or gradient swatch.
 * - All Other Accounts: 40×40dp rounded-square institution brandmark with semantic icon fallback.
 */
@Composable
fun AccountLeadingVisual(
    account: Account,
    onReportLogoStatus: ((merchantKey: String, status: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (account.type == AccountType.CREDIT) {
        val profile = account.creditCardProfile
        val cardTitle = profile?.cardName?.takeIf { it.isNotBlank() } ?: account.name
        val issuer = profile?.issuerName?.takeIf { it.isNotBlank() } ?: account.institution
        CardArtworkThumbnail(
            name = cardTitle,
            issuer = issuer,
            artKey = profile?.imageUrl,
            artworkUrl = profile?.artworkUrl,
            last4 = account.accountNumberLast4,
            showLabels = true,
            modifier = modifier.size(width = 60.dp, height = 38.dp)
        )
    } else {
        AccountInstitutionMark(
            account = account,
            size = 40.dp,
            cornerRadius = 10.dp,
            onReportLogoStatus = onReportLogoStatus,
            modifier = modifier
        )
    }
}

/**
 * Rounded-square institution logo mark with disk/memory bitmap caching, automatic load/fail
 * reporting, semantic M3E account-type vector fallback, and optional override menu.
 */
@Composable
fun AccountInstitutionMark(
    account: Account,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 10.dp,
    logoPadding: Dp = 4.dp,
    onFindLogo: (() -> Unit)? = null,
    onUseAccountIcon: (() -> Unit)? = null,
    onReportLogoStatus: ((merchantKey: String, status: String) -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    var menuExpanded by remember { mutableStateOf(false) }

    val logo = account.institutionLogo
    val logoUrl = account.effectiveInstitutionLogoUrl
    var showLogo by remember(logoUrl, logo?.status) {
        mutableStateOf(logoUrl != null && !MerchantLogoImageCache.isKnownFailed(logoUrl))
    }

    val hasMenuActions = onFindLogo != null || onUseAccountIcon != null
    val shape = RoundedCornerShape(cornerRadius)
    val clickModifier = if (hasMenuActions) {
        Modifier.clip(shape).clickable {
            haptics.tick()
            menuExpanded = true
        }
    } else {
        Modifier.clip(shape)
    }

    val (fallbackContainer, fallbackTint, fallbackIcon) = resolveAccountTypeStyle(account.type)
    val iconSize = (size.value * 0.5f).dp

    Box(modifier = modifier) {
        if (showLogo && logoUrl != null) {
            Surface(
                shape = shape,
                color = Color.White,
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                modifier = Modifier.size(size).then(clickModifier)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(logoPadding)) {
                    RemoteLogoImage(
                        url = logoUrl,
                        contentDescription = account.institution ?: account.name,
                        modifier = Modifier.fillMaxSize(),
                        onLoaded = {
                            if (logo != null && logo.status == "candidate" &&
                                MerchantLogoImageCache.markReported(logo.merchantKey, "loaded")
                            ) {
                                onReportLogoStatus?.invoke(logo.merchantKey, "loaded")
                            }
                        },
                        onError = {
                            showLogo = false
                            if (logo != null && MerchantLogoImageCache.markReported(logo.merchantKey, "failed")) {
                                onReportLogoStatus?.invoke(logo.merchantKey, "failed")
                            }
                        },
                        fallback = {
                            Icon(
                                imageVector = fallbackIcon,
                                contentDescription = null,
                                tint = fallbackTint,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    )
                }
            }
        } else {
            Surface(
                shape = shape,
                color = fallbackContainer,
                border = BorderStroke(1.dp, fallbackTint.copy(alpha = 0.16f)),
                modifier = Modifier.size(size).then(clickModifier)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = fallbackTint,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }

        if (hasMenuActions && menuExpanded) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (onFindLogo != null) {
                    DropdownMenuItem(
                        text = { Text("Find Logo") },
                        onClick = {
                            haptics.tick()
                            menuExpanded = false
                            onFindLogo()
                        }
                    )
                }
                if (onUseAccountIcon != null) {
                    DropdownMenuItem(
                        text = { Text("Use Account Icon") },
                        onClick = {
                            haptics.confirm()
                            menuExpanded = false
                            showLogo = false
                            onUseAccountIcon()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Compact overlapping preview stack shown on collapsed AccountGroupSection headers
 * (up to 4 miniature card plates or institution marks + "+N" overflow pill).
 */
@Composable
fun AccountGroupPreviewStack(
    category: AccountTypeCategory,
    accounts: List<Account>,
    onReportLogoStatus: ((merchantKey: String, status: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (accounts.isEmpty()) return

    val previewItems = remember(category, accounts) {
        if (category == AccountTypeCategory.CREDIT_CARDS) {
            accounts.take(4)
        } else {
            val distinctFirst = accounts.distinctBy { it.effectiveInstitutionLogoUrl ?: "id-${it.id}" }
            (distinctFirst + accounts).distinctBy { it.id }.take(4)
        }
    }
    val overflowCount = (accounts.size - previewItems.size).coerceAtLeast(0)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (category == AccountTypeCategory.CREDIT_CARDS) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-8).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                previewItems.forEach { account ->
                    val profile = account.creditCardProfile
                    val cardTitle = profile?.cardName?.takeIf { it.isNotBlank() } ?: account.name
                    val issuer = profile?.issuerName?.takeIf { it.isNotBlank() } ?: account.institution
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        CardArtworkThumbnail(
                            name = cardTitle,
                            issuer = issuer,
                            artKey = profile?.imageUrl,
                            artworkUrl = profile?.artworkUrl,
                            showLabels = false,
                            modifier = Modifier
                                .padding(1.25.dp)
                                .size(width = 28.dp, height = 18.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-6).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                previewItems.forEach { account ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        AccountInstitutionMark(
                            account = account,
                            size = 24.dp,
                            cornerRadius = 7.dp,
                            logoPadding = 2.5.dp,
                            onReportLogoStatus = onReportLogoStatus,
                            modifier = Modifier.padding(1.25.dp)
                        )
                    }
                }
            }
        }

        if (overflowCount > 0) {
            Spacer(modifier = Modifier.width(5.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
            ) {
                Text(
                    text = "+$overflowCount",
                    style = TabularNumStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun resolveAccountTypeStyle(type: AccountType): Triple<Color, Color, ImageVector> {
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        AccountType.CHECKING -> Triple(scheme.primaryContainer.copy(alpha = 0.55f), scheme.onPrimaryContainer, OrbitIcons.Accounts)
        AccountType.SAVINGS -> Triple(scheme.secondaryContainer.copy(alpha = 0.65f), scheme.onSecondaryContainer, OrbitIcons.Goals)
        AccountType.INVESTMENT -> Triple(scheme.tertiaryContainer.copy(alpha = 0.65f), scheme.onTertiaryContainer, OrbitIcons.SpendingTrends)
        AccountType.MORTGAGE -> Triple(scheme.primaryContainer.copy(alpha = 0.45f), scheme.onPrimaryContainer, OrbitIcons.HousingCalculator)
        AccountType.LOAN -> Triple(scheme.errorContainer.copy(alpha = 0.5f), scheme.onErrorContainer, OrbitIcons.Transactions)
        AccountType.CASH -> Triple(scheme.primaryContainer.copy(alpha = 0.55f), scheme.onPrimaryContainer, OrbitIcons.NetWorth)
        AccountType.CREDIT -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, OrbitIcons.CardPerks)
        AccountType.OTHER -> Triple(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, OrbitIcons.Categories)
    }
}
