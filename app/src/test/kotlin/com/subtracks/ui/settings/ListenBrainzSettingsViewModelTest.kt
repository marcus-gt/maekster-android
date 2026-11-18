package com.subtracks.ui.settings

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.awaitUntil
import com.subtracks.cancelAndJoinBlocking
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.listenbrainz.FakeListenBrainzApi
import com.subtracks.data.listenbrainz.ListenBrainzException
import com.subtracks.data.listenbrainz.ListenBrainzRepository
import com.subtracks.data.listenbrainz.TokenValidation
import com.subtracks.data.prefs.fakeUserPreferences
import com.subtracks.data.security.FakeCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ListenBrainzSettingsViewModelTest {
    private lateinit var db: SubtracksDatabase
    private val api = FakeListenBrainzApi()
    private lateinit var model: ListenBrainzSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = createTestDatabase()
        val repository = ListenBrainzRepository(api, fakeUserPreferences(), FakeCredentialStore(), db.pendingListenDao()) { null }
        model = ListenBrainzSettingsViewModel(repository)
    }

    @After
    fun tearDown() {
        cancelAndJoinBlocking(model.viewModelScope)
        db.close()
        Dispatchers.resetMain()
    }

    private fun connectAndWait(): ListenBrainzForm {
        model.connect("secret", "https://api.listenbrainz.org")
        awaitUntil("connect finished") { !model.form.value.busy }
        return model.form.value
    }

    @Test
    fun aValidTokenConnectsWithoutAnError() {
        assertNull(connectAndWait().error)
        awaitUntil("state connected") { model.state.value.connected }
        assertEquals("listener", model.state.value.username)
    }

    @Test
    fun failuresBecomeMessagesTheUserCanActOn() {
        api.validation = TokenValidation(false, null)
        assertEquals(ListenBrainzConnectError.InvalidToken, connectAndWait().error)

        api.validationFailure = ListenBrainzException.Network(IOException("offline"))
        assertEquals(ListenBrainzConnectError.Network, connectAndWait().error)

        api.validationFailure = ListenBrainzException.Http(503)
        assertEquals(ListenBrainzConnectError.Server, connectAndWait().error)

        api.validationFailure = ListenBrainzException.InvalidAddress()
        assertEquals(ListenBrainzConnectError.Server, connectAndWait().error)
    }

    @Test
    fun aBlankTokenDoesNothing() {
        model.connect("  ", "https://api.listenbrainz.org")

        assertEquals(ListenBrainzForm(), model.form.value)
    }
}
