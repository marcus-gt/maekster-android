package com.subtracks.data.audiomuse

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Source
import com.subtracks.data.security.CredentialStore
import com.subtracks.data.security.FakeCredentialStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException

@RunWith(AndroidJUnit4::class)
class AudioMuseConfigurationTest {
    private lateinit var db: SubtracksDatabase
    private val credentials = FakeCredentialStore()
    private lateinit var configs: AudioMuseConfiguration

    @Before fun setUp() =
        runBlocking {
            db = createTestDatabase()
            configs = AudioMuseConfiguration(db.audioMuseConfigDao(), credentials)
            for (id in 1L..2L) db.sourcesDao().upsertSource(Source(id, "server", "https://server", id == 1L, id))
        }

    @After fun tearDown() = db.close()

    @Test fun savesEncryptedConfigurationPerSourceAndPersistsAcrossReaders() =
        runBlocking {
            assertEquals(AudioMuseConfigurationState(), configs.observe(1).first())
            assertNull(configs.connection(1))
            configs.save(1, " muse.example/proxy ", " secret-one ")
            configs.save(2, "http://local:8000/", "secret-two")
            val stored = db.audioMuseConfigDao().get(1)!!
            assertEquals("https://muse.example/proxy", stored.url)
            assertTrue(stored.tokenCiphertext != "secret-one")
            assertEquals("secret-one", credentials.decrypt(stored.tokenCiphertext!!))
            val reopened = AudioMuseConfiguration(db.audioMuseConfigDao(), credentials)
            assertEquals("secret-one", reopened.connection(1)!!.token)
            assertEquals("secret-two", reopened.connection(2)!!.token)
            assertEquals(AudioMuseConfigurationState(stored.url, true, false), reopened.observe(1).first())
            assertFalse(reopened.connection(1).toString().contains("secret-one"))
            configs.disconnect(1)
            assertNull(configs.connection(1))
            assertEquals("secret-two", configs.connection(2)!!.token)
        }

    @Test fun blankTokenExplicitlyReplacesOldCredentialsAndSupportsUnauthenticatedInstances() =
        runBlocking {
            configs.save(1, "https://muse.example", "secret")
            configs.save(1, "https://other.example", "")
            assertNull(db.audioMuseConfigDao().get(1)!!.tokenCiphertext)
            assertNull(configs.connection(1)!!.token)
            assertEquals("https://other.example/", configs.connection(1)!!.url)
            assertTrue(configs.observe(1).first().available)
            assertFalse(configs.observe(1).first().hasToken)
        }

    @Test fun lostKeyDisablesOnlyThatIntegrationUntilTokenIsReplaced() =
        runBlocking {
            configs.save(1, "https://muse.example", "secret")
            configs.save(2, "http://local:8000/")
            credentials.loseKey()
            val state = configs.observe(1).first()
            assertTrue(state.hasToken)
            assertTrue(state.tokenUnreadable)
            assertFalse(state.available)
            assertNull(configs.connection(1))
            assertTrue(configs.observe(2).first().available)
            assertTrue(db.sourcesDao().sourceOnce(1) != null)
            configs.save(1, "https://muse.example", "replacement")
            assertEquals("replacement", configs.connection(1)!!.token)
            assertFalse(configs.observe(1).first().tokenUnreadable)
        }

    @Test fun invalidInputOrFailedEncryptionLeavesPreviousConfigurationIntact() =
        runBlocking {
            configs.save(1, "https://muse.example", "secret")
            for (url in listOf("", "ftp://x", "https://user:pass@x", "https://x/?token=secret", "https://x/#secret")) {
                assertThrows(IllegalArgumentException::class.java) { runBlocking { configs.save(1, url, "new") } }
            }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { configs.save(1, "https://x", "a\nb") } }
            credentials.corrupt = true
            assertThrows(GeneralSecurityException::class.java) { runBlocking { configs.save(1, "https://other", "new") } }
            credentials.corrupt = false
            val broken =
                object : CredentialStore {
                    override fun encrypt(secret: String): String = throw GeneralSecurityException("test failure")

                    override fun decrypt(ciphertext: String): String? = null
                }
            assertThrows(GeneralSecurityException::class.java) {
                runBlocking {
                    AudioMuseConfiguration(db.audioMuseConfigDao(), broken).save(1, "https://other", "new")
                }
            }
            assertEquals("https://muse.example/", configs.connection(1)!!.url)
            assertEquals("secret", configs.connection(1)!!.token)
        }
}
