package com.baloomba.oauth.core.storage

import com.baloomba.oauth.core.oauth.PendingAuthStore
import com.baloomba.oauth.core.oauth.PendingAuthorization
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-memory [PendingAuthStore]. [take] is an atomic read-and-delete. */
public class InMemoryPendingAuthStore : PendingAuthStore {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, PendingAuthorization>()

    override suspend fun save(session: PendingAuthorization) {
        mutex.withLock { sessions[session.state] = session }
    }

    override suspend fun take(state: String): PendingAuthorization? =
        mutex.withLock { sessions.remove(state) }
}
