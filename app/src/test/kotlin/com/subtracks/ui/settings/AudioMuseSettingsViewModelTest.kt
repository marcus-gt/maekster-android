package com.subtracks.ui.settings

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.audiomuse.AudioMuseConfiguration
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Source
import com.subtracks.data.security.FakeCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AudioMuseSettingsViewModelTest {
    private lateinit var db: SubtracksDatabase
    private val credentials = FakeCredentialStore()
    private lateinit var configuration: AudioMuseConfiguration
    private var model: AudioMuseSettingsViewModel? = null

    @Before fun setUp() =
        runBlocking {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            db = createTestDatabase()
            configuration = AudioMuseConfiguration(db.audioMuseConfigDao(), credentials)
            for (id in 1L..2L) db.sourcesDao().upsertSource(Source(id, "server", "https://server", id == 1L, id))
        }

    @After fun tearDown() =
        runBlocking {
            model
                ?.viewModelScope
                ?.coroutineContext
                ?.get(Job)
                ?.cancelAndJoin()
            db.close()
            Dispatchers.resetMain()
        }

    private suspend fun create() =
        AudioMuseSettingsViewModel(2, configuration).also {
            model = it
            it.state.filterNotNull().first()
        }

    @Test fun savesOnlyTheBoundServerAndKeepsTokenOnlyAtTheSameUrl() =
        runBlocking {
            configuration.save(1, "https://one", "one-secret")
            configuration.save(2, "https://two", "two-secret")
            val vm = create()
            vm.save("https://two/", "", true)
            assertTrue(vm.form.first { !it.busy }.saved)
            assertEquals("two-secret", configuration.connection(2)!!.token)
            vm.save("https://different", "", true)
            assertEquals(AudioMuseSettingsError.Invalid, vm.form.first { !it.busy }.error)
            assertEquals("https://two/", configuration.connection(2)!!.url)
            vm.save("https://different", "", false)
            assertTrue(vm.form.first { !it.busy }.saved)
            assertNull(configuration.connection(2)!!.token)
            assertEquals("one-secret", configuration.connection(1)!!.token)
        }

    @Test fun lostTokenRequiresReplacementAndCanRecover() =
        runBlocking {
            configuration.save(2, "https://two", "lost")
            credentials.loseKey()
            val vm = create()
            assertTrue(vm.state.value!!.tokenUnreadable)
            vm.save("https://two", "", true)
            assertEquals(AudioMuseSettingsError.Invalid, vm.form.first { !it.busy }.error)
            vm.save("https://two", "replacement", false)
            assertTrue(vm.form.first { !it.busy }.saved)
            assertEquals("replacement", configuration.connection(2)!!.token)
        }

    @Test fun failureIsReportedWithoutDiscardingSavedConfiguration() =
        runBlocking {
            configuration.save(2, "https://two", "old")
            val vm = create()
            credentials.corrupt = true
            vm.save("https://new", "new", false)
            assertEquals(AudioMuseSettingsError.Save, vm.form.first { !it.busy }.error)
            credentials.corrupt = false
            assertEquals("old", configuration.connection(2)!!.token)
            vm.save("not a url?", "x", false)
            assertEquals(AudioMuseSettingsError.Invalid, vm.form.first { !it.busy }.error)
        }

    @Test fun removalIsScopedToTheBoundServer() =
        runBlocking {
            configuration.save(1, "https://one", "one-secret")
            configuration.save(2, "https://two", "two-secret")
            val vm = create()
            vm.disconnect()
            assertTrue(vm.form.first { !it.busy }.saved)
            assertNull(configuration.connection(2))
            assertEquals("one-secret", configuration.connection(1)!!.token)
        }
}
