package app.overbay.orbit.ui.transactions.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.PlatformRuntime
import app.overbay.orbit.util.PlatformStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Shared thread-safe bitmap, in-flight request deduplicator, persistent native disk cache,
 * and status-report tracker for vendor/merchant logos.
 * Ensures 60/120fps LazyColumn scrolling with lock-free synchronous memory hits and zero
 * cold-start HTTPS download bursts on previously cached merchants.
 */
object MerchantLogoImageCache {
    private val httpClient = HttpClient()
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex = Mutex()
    private val diskStorage by lazy { PlatformStorage() }

    @Volatile
    private var bitmapSnapshot: Map<String, ImageBitmap> = emptyMap()

    @Volatile
    private var failedUrlsSnapshot: Set<String> = emptySet()

    private val inFlightRequests = HashMap<String, Deferred<ImageBitmap?>>()
    private val reportedLogoStatuses = HashSet<String>()

    fun getCached(url: String?): ImageBitmap? =
        if (url.isNullOrBlank()) null else bitmapSnapshot[url]

    fun isKnownFailed(url: String?): Boolean =
        !url.isNullOrBlank() && url in failedUrlsSnapshot

    fun clearFailed(url: String?) {
        if (!url.isNullOrBlank() && url in failedUrlsSnapshot) {
            failedUrlsSnapshot = failedUrlsSnapshot - url
        }
    }

    fun markReported(merchantKey: String, status: String): Boolean {
        if (merchantKey.isBlank()) return false
        val key = "$merchantKey:$status"
        if (reportedLogoStatuses.contains(key)) return false
        reportedLogoStatuses.add(key)
        return true
    }

    @OptIn(ExperimentalResourceApi::class, ExperimentalEncodingApi::class)
    suspend fun fetchBitmap(url: String): ImageBitmap? {
        bitmapSnapshot[url]?.let { return it }
        if (url in failedUrlsSnapshot) return null

        val deferred = stateMutex.withLock {
            bitmapSnapshot[url]?.let { return it }
            if (url in failedUrlsSnapshot) return null
            inFlightRequests.getOrPut(url) {
                fetchScope.async {
                    try {
                        val diskKey = if (!PlatformRuntime.isWeb) buildDiskCacheKey(url) else null
                        if (diskKey != null) {
                            val cachedBase64 = runCatching { diskStorage.loadString(diskKey) }.getOrNull()
                            if (!cachedBase64.isNullOrBlank()) {
                                val decodedDisk = runCatching {
                                    Base64.decode(cachedBase64).decodeToImageBitmap()
                                }.getOrNull()
                                if (decodedDisk != null) {
                                    stateMutex.withLock {
                                        bitmapSnapshot = bitmapSnapshot + (url to decodedDisk)
                                    }
                                    return@async decodedDisk
                                }
                            }
                        }

                        val response = httpClient.get(url)
                        if (!response.status.isSuccess()) {
                            throw IllegalStateException("HTTP ${response.status.value}")
                        }
                        val bytes: ByteArray = response.body()
                        val bitmap = bytes.decodeToImageBitmap()
                        if (diskKey != null && bytes.size <= MAX_DISK_LOGO_BYTES) {
                            runCatching { diskStorage.saveString(diskKey, Base64.encode(bytes)) }
                        }
                        stateMutex.withLock {
                            bitmapSnapshot = bitmapSnapshot + (url to bitmap)
                        }
                        bitmap
                    } catch (_: Throwable) {
                        stateMutex.withLock {
                            failedUrlsSnapshot = failedUrlsSnapshot + url
                        }
                        null
                    } finally {
                        stateMutex.withLock {
                            inFlightRequests.remove(url)
                        }
                    }
                }
            }
        }
        return deferred.await()
    }

    private fun buildDiskCacheKey(url: String): String {
        // 64-bit FNV-1a hash for compact, collision-resistant filename keys
        var hash = -3750763034362895579L // 0xcbf29ce484222325uL
        for (i in url.indices) {
            hash = hash xor url[i].code.toLong()
            hash *= 1099511628211L // 0x100000001b3L
        }
        return "orbit_logo_cache_${hash.toULong().toString(16)}.b64"
    }

    private const val MAX_DISK_LOGO_BYTES = 256 * 1024
}

/**
 * Renders a remote logo image from [url] with in-memory caching and fallback content.
 */
@Composable
fun RemoteLogoImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onLoaded: () -> Unit = {},
    onError: () -> Unit = {},
    fallback: @Composable () -> Unit
) {
    val cleanUrl = url?.trim()?.takeIf { it.isNotEmpty() }
    var bitmap by remember(cleanUrl) { mutableStateOf(MerchantLogoImageCache.getCached(cleanUrl)) }
    var loadFailed by remember(cleanUrl) { mutableStateOf(MerchantLogoImageCache.isKnownFailed(cleanUrl)) }

    LaunchedEffect(cleanUrl) {
        if (cleanUrl == null) {
            bitmap = null
            loadFailed = false
            return@LaunchedEffect
        }
        val cached = MerchantLogoImageCache.getCached(cleanUrl)
        if (cached != null) {
            bitmap = cached
            loadFailed = false
            onLoaded()
            return@LaunchedEffect
        }
        if (MerchantLogoImageCache.isKnownFailed(cleanUrl)) {
            bitmap = null
            loadFailed = true
            onError()
            return@LaunchedEffect
        }
        val fetched = MerchantLogoImageCache.fetchBitmap(cleanUrl)
        if (fetched != null) {
            bitmap = fetched
            loadFailed = false
            onLoaded()
        } else {
            bitmap = null
            loadFailed = true
            onError()
        }
    }

    val loadedBitmap = bitmap
    if (loadedBitmap != null && !loadFailed) {
        Image(
            bitmap = loadedBitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        fallback()
    }
}

/**
 * Interactive merchant logo mark for transactions with automatic status reporting,
 * category icon fallback, and a left-aligned dropdown menu ("Find Logo", "Use Category Icon").
 */
@Composable
fun TransactionMerchantMark(
    transaction: Transaction,
    categoryIcon: String?,
    categoryColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 10.dp,
    onFindLogo: (() -> Unit)? = null,
    onUseCategoryIcon: (() -> Unit)? = null,
    onReportLogoStatus: ((merchantKey: String, status: String) -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    var menuExpanded by remember { mutableStateOf(false) }

    val logo = transaction.merchantLogo
    val logoUrl = logo?.url?.takeIf { it.isNotBlank() && logo.status != "hidden" && logo.status != "failed" }
    var showLogo by remember(logoUrl, logo?.status) { mutableStateOf(logoUrl != null && !MerchantLogoImageCache.isKnownFailed(logoUrl)) }

    val hasMenuActions = onFindLogo != null || onUseCategoryIcon != null
    val shape = RoundedCornerShape(cornerRadius)

    Box(modifier = modifier) {
        val clickModifier = if (hasMenuActions) {
            Modifier.clip(shape).clickable {
                haptics.tick()
                menuExpanded = true
            }
        } else {
            Modifier.clip(shape)
        }

        if (showLogo && logoUrl != null) {
            Surface(
                shape = shape,
                color = Color.White,
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                modifier = Modifier.size(size).then(clickModifier)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    RemoteLogoImage(
                        url = logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        onLoaded = {
                            if (logo.status == "candidate" && MerchantLogoImageCache.markReported(logo.merchantKey, "loaded")) {
                                onReportLogoStatus?.invoke(logo.merchantKey, "loaded")
                            }
                        },
                        onError = {
                            showLogo = false
                            if (MerchantLogoImageCache.markReported(logo.merchantKey, "failed")) {
                                onReportLogoStatus?.invoke(logo.merchantKey, "failed")
                            }
                        },
                        fallback = {
                            FallbackCategoryMarkText(
                                categoryIcon = categoryIcon,
                                merchant = transaction.effectiveMerchant,
                                color = categoryColor
                            )
                        }
                    )
                }
            }
        } else {
            Surface(
                shape = shape,
                color = categoryColor.copy(alpha = 0.15f),
                modifier = Modifier.size(size).then(clickModifier)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    FallbackCategoryMarkText(
                        categoryIcon = categoryIcon,
                        merchant = transaction.effectiveMerchant,
                        color = categoryColor
                    )
                }
            }
        }

        if (hasMenuActions) {
            DropdownMenu(
                expanded = menuExpanded,
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
                if (onUseCategoryIcon != null) {
                    DropdownMenuItem(
                        text = { Text("Use Category Icon") },
                        onClick = {
                            haptics.confirm()
                            menuExpanded = false
                            showLogo = false
                            onUseCategoryIcon()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackCategoryMarkText(
    categoryIcon: String?,
    merchant: String,
    color: Color
) {
    val resolvedEmoji = CategoryEmojiMapper.resolveEmoji(categoryIcon)
    Text(
        text = resolvedEmoji?.takeIf { it.isNotBlank() } ?: merchant.take(1).uppercase().ifBlank { "$" },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color
    )
}
