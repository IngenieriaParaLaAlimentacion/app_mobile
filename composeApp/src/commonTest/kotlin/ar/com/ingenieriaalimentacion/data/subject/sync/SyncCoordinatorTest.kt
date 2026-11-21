package ar.com.ingenieriaalimentacion.data.subject.sync

import ar.com.ingenieriaalimentacion.data.auth.SessionExit
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncSummary
import ar.com.ingenieriaalimentacion.domain.sync.SyncEngine
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.inactiveSessionLifecycle
import ar.com.ingenieriaalimentacion.fake.signInForTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {
    @Test
    fun signedOutTimeNeverPrunesAndObsoleteTriggersCannotStartAnotherOwnersSync() = runTest {
        val lifecycle = inactiveSessionLifecycle()
        val resets = mutableListOf<String>()
        val prunes = mutableListOf<String>()
        val syncs = mutableListOf<String>()
        val engine = object : SyncEngine {
            override suspend fun syncPending(): SyncSummary {
                syncs += lifecycle.capture().ownerId
                return SyncSummary()
            }

            override suspend fun resetStuckSyncing() {
                resets += lifecycle.capture().ownerId
            }

            override suspend fun pruneTerminal(olderThan: Duration) {
                prunes += lifecycle.capture().ownerId
            }
        }
        val sync = SyncCoordinator(listOf(engine), FakeConnectivityObserver(), backgroundScope, lifecycle)
        sync.start()
        lifecycle.bootstrap()
        lifecycle.signInForTest("A")
        runCurrent()
        val old = lifecycle.leaseOrNull()!!
        sync.requestSync(old)
        runCurrent()
        assertEquals(listOf("A"), resets)
        assertEquals(listOf("A"), syncs)

        lifecycle.end(old, SessionExit.Blocked)
        advanceTimeBy(2.hours)
        runCurrent()
        assertEquals(emptyList(), prunes)

        lifecycle.signInForTest("B")
        runCurrent()
        sync.requestSync(old)
        runCurrent()
        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(listOf("A", "B"), resets)
        assertEquals(listOf("A"), syncs)
        assertEquals(listOf("B"), prunes)
    }
}
