package app.overbay.orbit.data

import app.overbay.orbit.data.engine.SelfHostedApiEngine
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.util.OidcCallbackBridge
import kotlinx.datetime.Clock

internal const val AUTH_METHOD_STORAGE_KEY = "orbit_auth_method"

internal fun resolveAuthMethod(
    explicitMethod: String?,
    authProvider: String?,
    cachedMethod: String?
): String? = when {
    !explicitMethod.isNullOrBlank() -> explicitMethod
    !cachedMethod.isNullOrBlank() -> cachedMethod
    authProvider.equals("oidc", ignoreCase = true) -> "oidc"
    authProvider.equals("local", ignoreCase = true) -> "password"
    else -> null
}

internal fun OrbitRepository.persistAuthMethod(method: String?) {
    _authMethod.value = method
    if (!method.isNullOrBlank()) {
        storage.saveString(AUTH_METHOD_STORAGE_KEY, method)
    } else {
        storage.delete(AUTH_METHOD_STORAGE_KEY)
    }
}

internal suspend fun OrbitRepository.refreshSelfHostedSessionImpl(
    client: OrbitApiClient,
    remoteEngine: SelfHostedApiEngine,
    pref: OrbitPreferences,
    hadCachedSession: Boolean
) {
    val meResult = client.getMe()
    val me = meResult.getOrNull()
    if (me != null && me.authenticated) {
        _isOffline.value = false
        storage.delete(OidcCallbackBridge.PENDING_SERVER_URL_KEY)
        _isAuthenticated.value = true
        _currentUser.value = me.displayName ?: me.username ?: me.email
        persistAuthMethod(resolveAuthMethod(me.authMethod, me.authProvider, _authMethod.value))
        val loaded = remoteEngine.loadSnapshot(preFetchedMe = me)
        val existing = _snapshot.value
        val sameData = loaded.copy(
            preferences = loaded.preferences.copy(sharedSessionCachedAtEpochMs = existing.preferences.sharedSessionCachedAtEpochMs)
        ) == existing
        if (!sameData) updateMemoryState(loaded) else _preferences.value = loaded.preferences
        localEngine.saveStartupPreferences(loaded.preferences)
    } else {
        val errMsg = meResult.exceptionOrNull()?.message.orEmpty()
        val is401 = (me != null && !me.authenticated) || errMsg.contains("401") || !hadCachedSession
        if (is401) {
            _isOffline.value = false
            _isAuthenticated.value = false
            _currentUser.value = null
            persistAuthMethod(null)
        } else if (hadCachedSession) {
            _isOffline.value = true
            val cachedEpoch = pref.sharedSessionCachedAtEpochMs
            if (pref.isSharedNonOwnerSession && cachedEpoch != null) {
                val ageMs = Clock.System.now().toEpochMilliseconds() - cachedEpoch
                if (ageMs > 7L * 24 * 3600 * 1000L) {
                    _isAuthenticated.value = false
                    _currentUser.value = null
                    persistAuthMethod(null)
                    activeEngine.clear()
                }
            }
        }
    }
}
