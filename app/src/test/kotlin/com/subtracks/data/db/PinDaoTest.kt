package com.subtracks.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinDaoTest {
    private lateinit var db: SubtracksDatabase

    @Before fun setUp() {
        db = createTestDatabase()
    }

    @After fun tearDown() {
        db.close()
    }

    private suspend fun source() =
        db.sourcesDao().insertSource(
            Source(name = "Server", address = "http://localhost", createdAt = 0),
        )

    @Test
    fun pinsAreServerScopedTypedOrderedAndBounded() =
        runTest {
            val first = source()
            val second = source()
            val dao = db.pinDao()
            dao.pin(first, PinKind.Album, "same")
            dao.pin(first, PinKind.Playlist, "same")
            dao.pin(first, PinKind.Artist, "same")
            dao.pin(second, PinKind.Album, "same")
            assertEquals(listOf(PinKind.Album, PinKind.Playlist), dao.pins(first, 2).first().map { it.kind })
            assertEquals(listOf(0L, 1L, 2L), dao.pins(first, 10).first().map { it.position })
            assertEquals(1, dao.pins(second, 10).first().size)
            assertEquals(
                0L,
                dao
                    .pins(second, 10)
                    .first()
                    .single()
                    .position,
            )
            assertTrue(dao.isPinned(first, PinKind.Artist, "same").first())
            assertFalse(dao.isPinned(second, PinKind.Artist, "same").first())
        }

    @Test
    fun duplicatePinIsNoOpAndRepinAppendsAfterRemainingPins() =
        runTest {
            val source = source()
            val dao = db.pinDao()
            dao.pin(source, PinKind.Album, "a")
            dao.pin(source, PinKind.Album, "b")
            dao.pin(source, PinKind.Album, "a")
            assertEquals(listOf("a", "b"), dao.pins(source, 10).first().map { it.refId })
            assertEquals(listOf(0L, 1L), dao.pins(source, 10).first().map { it.position })
            dao.unpin(source, PinKind.Album, "a")
            dao.unpin(source, PinKind.Album, "missing")
            assertFalse(dao.isPinned(source, PinKind.Album, "a").first())
            dao.pin(source, PinKind.Album, "a")
            assertEquals(listOf("b", "a"), dao.pins(source, 10).first().map { it.refId })
            assertEquals(listOf(1L, 2L), dao.pins(source, 10).first().map { it.position })
        }

    @Test
    fun deletingServerCascadesOnlyItsPins() =
        runTest {
            val first = source()
            val second = source()
            val dao = db.pinDao()
            PinKind.entries.forEach { dao.pin(first, it, "same") }
            dao.pin(second, PinKind.Album, "same")
            db.sourcesDao().deleteSource(first)
            assertTrue(dao.pins(first, 10).first().isEmpty())
            assertEquals(1, dao.pins(second, 10).first().size)
        }
}
