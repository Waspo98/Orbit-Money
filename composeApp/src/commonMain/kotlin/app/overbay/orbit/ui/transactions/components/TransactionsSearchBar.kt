package app.overbay.orbit.ui.transactions.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.coroutines.delay

/**
 * Continuous animated search bar that serves both as the resting pill in the Transactions
 * canopy (full-width when expanded, compact beside the title when collapsed) and expands
 * into the full-width active search input at the top of the screen when tapped.
 */
@Composable
fun TransactionsSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    placeholderText: String = if (isCompact) "Search..." else "Search transactions"
) {
    val haptics = LocalOrbitHaptics.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var tfv by remember {
        mutableStateOf(TextFieldValue(text = searchQuery, selection = TextRange(searchQuery.length)))
    }
    var lastExternalQuery by remember { mutableStateOf(searchQuery) }
    val pendingEmissions = remember { ArrayDeque<String>() }

    if (searchQuery != lastExternalQuery) {
        val echoIdx = pendingEmissions.indexOf(searchQuery)
        if (echoIdx >= 0) {
            repeat(echoIdx + 1) { pendingEmissions.removeFirst() }
        } else {
            pendingEmissions.clear()
            if (tfv.text != searchQuery) {
                tfv = TextFieldValue(text = searchQuery, selection = TextRange(searchQuery.length))
            }
        }
        lastExternalQuery = searchQuery
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(110)
            try {
                focusRequester.requestFocus()
            } catch (_: Throwable) {}
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isSearchActive) 0.85f else 0.45f)
        ),
        modifier = modifier.then(
            if (!isSearchActive) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptics.tick()
                    onSearchActiveChange(true)
                }
            } else {
                Modifier
            }
        )
    ) {
        val startPadding = if (isSearchActive) 6.dp else if (isCompact) 12.dp else 16.dp
        val iconSize = if (isSearchActive) 20.dp else if (isCompact) 18.dp else 20.dp

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = startPadding, end = 10.dp)
        ) {
            // Leading Icon: Smooth crossfade between Search and ArrowBack
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    fadeIn(OrbitSprings.effectsDefault()) togetherWith
                        fadeOut(OrbitSprings.effectsDefault())
                },
                contentAlignment = Alignment.Center,
                label = "searchLeadingIconTransition"
            ) { active ->
                if (active) {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            keyboardController?.hide()
                            onSearchActiveChange(false)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

            Spacer(modifier = Modifier.width(if (isSearchActive) 6.dp else if (isCompact) 8.dp else 10.dp))

            // Input / Resting Text Area with smooth fade transition
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = {
                        fadeIn(OrbitSprings.effectsDefault()) togetherWith
                            fadeOut(OrbitSprings.effectsDefault())
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "searchInputContentTransition"
                ) { active ->
                    if (active) {
                        BasicTextField(
                            value = tfv,
                            onValueChange = { next ->
                                val textChanged = next.text != tfv.text
                                tfv = next
                                if (textChanged) {
                                    pendingEmissions.addLast(next.text)
                                    onSearchQueryChange(next.text)
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (tfv.text.isEmpty()) {
                                        Text(
                                            text = placeholderText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    } else {
                        Text(
                            text = if (tfv.text.isNotBlank()) tfv.text else placeholderText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (tfv.text.isNotBlank()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Trailing Action: Clear button
            AnimatedVisibility(
                visible = tfv.text.isNotBlank(),
                enter = fadeIn(OrbitSprings.effectsDefault()),
                exit = fadeOut(OrbitSprings.effectsDefault())
            ) {
                IconButton(
                    onClick = {
                        haptics.tick()
                        pendingEmissions.clear()
                        tfv = TextFieldValue(text = "", selection = TextRange(0))
                        onSearchQueryChange("")
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Centered prompt or empty search state displayed during active search mode,
 * matching Luma Reader's clean search results transition.
 */
@Composable
fun TransactionsSearchPromptOrEmpty(
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        if (searchQuery.isBlank()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Search merchants, descriptions, or notes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No transactions found for \"$searchQuery\"",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Check your spelling or try a different merchant or note keyword",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Shared 2-axis morphing canopy search bar and trailing controls layout used inside [MorphingPageHero].
 */
@Composable
fun BoxWithConstraintsScope.CanopyMorphingSearchContent(
    collapseFraction: Float,
    collapsedTitleRightDp: Dp,
    expandedTextBottomDp: Dp,
    searchActiveProgress: Float,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    expandedPlaceholder: String,
    compactPlaceholder: String = "Search...",
    trailingControls: @Composable (buttonSize: Dp) -> Unit
) {
    val restingHeight = lerp(46.dp, 38.dp, collapseFraction)
    val controlsWidth = restingHeight * 2 + 8.dp
    val gapToControls = 8.dp
    val totalTrailingReserve = controlsWidth + gapToControls

    val collapsedRowLeft = collapsedTitleRightDp + 10.dp
    val restingLeft = lerp(20.dp, collapsedRowLeft, collapseFraction)
    val restingTop = lerp(expandedTextBottomDp, 9.dp, collapseFraction)
    val restingRowRightMargin = lerp(20.dp, 16.dp, collapseFraction)
    val restingTotalRowWidth = (maxWidth - restingRowRightMargin - restingLeft).coerceAtLeast(160.dp)
    val restingSearchWidth = (restingTotalRowWidth - totalTrailingReserve).coerceAtLeast(72.dp)

    val currentLeft = lerp(restingLeft, 12.dp, searchActiveProgress)
    val currentTop = lerp(restingTop, 6.dp, searchActiveProgress)
    val currentSearchWidth = lerp(restingSearchWidth, (maxWidth - 24.dp).coerceAtLeast(100.dp), searchActiveProgress)
    val currentHeight = lerp(restingHeight, 48.dp, searchActiveProgress)

    val controlsLeft = restingLeft + restingSearchWidth + gapToControls
    val controlsAlpha = (1f - searchActiveProgress * 2.2f).coerceIn(0f, 1f)

    val isCompact = !isSearchActive && (collapseFraction > 0.45f)
    val placeholder = if (isSearchActive || collapseFraction <= 0.45f) {
        expandedPlaceholder
    } else {
        compactPlaceholder
    }

    if (controlsAlpha > 0.01f) {
        Box(
            modifier = Modifier
                .offset(x = controlsLeft, y = restingTop)
                .height(restingHeight)
                .graphicsLayer { alpha = controlsAlpha },
            contentAlignment = Alignment.CenterEnd
        ) {
            trailingControls(restingHeight)
        }
    }

    Box(
        modifier = Modifier
            .offset(x = currentLeft, y = currentTop)
            .width(currentSearchWidth)
            .height(currentHeight)
    ) {
        TransactionsSearchBar(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            isSearchActive = isSearchActive,
            onSearchActiveChange = onSearchActiveChange,
            isCompact = isCompact,
            placeholderText = placeholder,
            modifier = Modifier.fillMaxSize()
        )
    }
}
