package com.subtracks.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.QueueKind
import com.subtracks.data.model.Source
import com.subtracks.data.model.StoredQueueOrder
import com.subtracks.data.model.StoredQueueOrderEntry
import com.subtracks.data.repo.ShufflePrefix
import com.subtracks.data.repo.ShuffleState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueOrderDaoTest {
    private lateinit var db: SubtracksDatabase

    @Before
    fun setUp() {
        db = createTestDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun hugeOrdersStoreReferenceRangesInPositionOrderAndStayServerScoped() =
        runTest {
            db.sourcesDao().upsertSource(Source(1, "one", "https://one", true, 1))
            db.sourcesDao().upsertSource(Source(2, "two", "https://two", false, 2))
            val prefix = ShufflePrefix(ShuffleState(42, 1_000_000_000), 800_000_000)
            val dao = db.queueOrderDao()
            val id = dao.insertOrder(StoredQueueOrder(sourceId = 1, prefix = prefix.encode()))
            val first = StoredQueueOrderEntry(id, 0, 1, QueueKind.Library, "spec", 100, 500_000_099, 500_000_000)
            val second = StoredQueueOrderEntry(id, 1, 1, QueueKind.Album, "album", 0, 499_999_999, 500_000_000)
            dao.insertEntries(listOf(second, first))
            assertEquals(listOf(first, second), dao.entries(id))
            assertEquals(2, dao.entries(id).size)
            assertEquals(
                100L,
                dao
                    .entries(id)
                    .first()
                    .toQueueEntry()
                    .offset,
            )
            val restored = ShufflePrefix.decode(dao.order(1, id)!!.prefix)!!
            assertEquals(prefix.flatFor(799_999_999), restored.flatFor(799_999_999))
            assertNull(dao.order(2, id))
        }

    @Test
    fun deletingAServerCascadesOnlyItsStoredOrders() =
        runTest {
            val dao = db.queueOrderDao()
            val ids =
                (1L..2L).map { source ->
                    db.sourcesDao().upsertSource(Source(source, "server", "https://server", source == 1L, source))
                    val id = dao.insertOrder(StoredQueueOrder(sourceId = source, prefix = "prefix"))
                    dao.insertEntries(listOf(StoredQueueOrderEntry(id, 0, source, QueueKind.Album, "album", null, null, 3)))
                    id
                }
            db.sourcesDao().deleteSource(1)
            assertNull(dao.order(1, ids[0]))
            assertEquals(emptyList<StoredQueueOrderEntry>(), dao.entries(ids[0]))
            assertEquals(1, dao.entries(ids[1]).size)
        }

    @Test
    fun deletingAnOrderCascadesToItsReferencesButKeepsRetainedOrders() =
        runTest {
            db.sourcesDao().upsertSource(Source(1, "one", "https://one", true, 1))
            val dao = db.queueOrderDao()
            val prefix = ShufflePrefix(ShuffleState(1, 3), 2).encode()
            val removed = dao.insertOrder(StoredQueueOrder(sourceId = 1, prefix = prefix))
            val retained = dao.insertOrder(StoredQueueOrder(sourceId = 1, prefix = prefix))
            dao.insertEntries(
                listOf(removed, retained).map { StoredQueueOrderEntry(it, 0, 1, QueueKind.Album, "album", null, null, 3) },
            )
            dao.deleteExcept(listOf(retained))
            assertNull(dao.order(1, removed))
            assertEquals(emptyList<StoredQueueOrderEntry>(), dao.entries(removed))
            assertEquals(1, dao.entries(retained).size)
            dao.deleteExcept(emptyList())
            assertNull(dao.order(1, retained))
            assertEquals(emptyList<StoredQueueOrderEntry>(), dao.entries(retained))
        }
}
