package com.zugaldia.speedofsound.core.desktop.portals

import com.zugaldia.stargate.sdk.globalshortcuts.BoundShortcut
import com.zugaldia.stargate.sdk.globalshortcuts.ShortcutActivation
import com.zugaldia.stargate.sdk.remotedesktop.StartResponse
import com.zugaldia.stargate.sdk.session.CreateSessionResponse
import com.zugaldia.stargate.sdk.session.SessionClosedEvent
import kotlinx.coroutines.flow.Flow

interface PortalsSessionClient {
    val isPortalAvailable: Boolean
    val sessionClosedEvents: Flow<SessionClosedEvent>

    suspend fun registerApplication(): Result<Unit>

    suspend fun startRemoteDesktopSession(restoreToken: String?): Result<StartResponse>

    suspend fun createGlobalShortcutsSession(): Result<CreateSessionResponse>

    suspend fun bindGlobalShortcuts(): Result<List<BoundShortcut>>

    fun observeShortcutActivated(): Flow<ShortcutActivation>
}
