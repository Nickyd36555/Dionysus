package com.dionysus.tv.data.debrid.realdebrid

import com.dionysus.tv.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the Real-Debrid OAuth device flow and token refresh. The connect
 * screen calls [startDeviceFlow] then polls [tryComplete]; networking code uses
 * [refresh] transparently via the authenticator when a token expires.
 */
@Singleton
class RealDebridAuth @Inject constructor(
    private val authApi: RealDebridAuthApi,
    private val settings: SettingsRepository,
) {
    /** Step 1: get a user code to display and a device code to poll with. */
    suspend fun startDeviceFlow(): RdDeviceCode = authApi.deviceCode()

    /**
     * Poll until the user authorizes on their phone. Returns true once tokens
     * are stored; false while still waiting (the caller keeps polling).
     */
    suspend fun tryComplete(deviceCode: String): Boolean {
        val creds = runCatching { authApi.credentials(deviceCode = deviceCode) }.getOrNull()
            ?: return false
        val token = authApi.token(creds.clientId, creds.clientSecret, deviceCode)
        settings.setRealDebridOAuth(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            clientId = creds.clientId,
            clientSecret = creds.clientSecret,
        )
        return true
    }

    /** Exchange the stored refresh token for a fresh access token. */
    suspend fun refresh(): String? {
        val refresh = settings.currentRealDebridRefresh() ?: return null
        val clientId = settings.currentRealDebridClientId() ?: return null
        val clientSecret = settings.currentRealDebridClientSecret() ?: return null
        return runCatching {
            val token = authApi.token(clientId, clientSecret, refresh)
            settings.setRealDebridOAuth(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                clientId = clientId,
                clientSecret = clientSecret,
            )
            token.accessToken
        }.getOrNull()
    }

    suspend fun disconnect() = settings.clearRealDebrid()
}
