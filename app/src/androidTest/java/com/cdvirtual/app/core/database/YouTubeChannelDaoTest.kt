package com.cdvirtual.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class YouTubeChannelDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: YouTubeChannelDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.youtubeChannelDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun savesListsAndRemovesFollowedChannels() = runBlocking {
        val channel = YouTubeChannelEntity(
            channelUrl = "https://www.youtube.com/channel/example",
            channelName = "Canal exemplo",
            avatarUrl = "https://example.com/avatar.jpg",
            addedAt = 100L
        )

        assertEquals(-1L, dao.add(channel))
        assertTrue(dao.contains(channel.channelUrl))
        assertEquals("Canal exemplo", dao.observeAll().first().single().channelName)

        dao.remove(channel.channelUrl)

        assertFalse(dao.contains(channel.channelUrl))
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun duplicateChannelDoesNotCreateAnotherEntry() = runBlocking {
        val channel = YouTubeChannelEntity(
            channelUrl = "https://www.youtube.com/@example",
            channelName = "Exemplo"
        )

        dao.add(channel)
        dao.add(channel.copy(channelName = "Nome atualizado"))

        assertEquals(1, dao.count())
        assertEquals("Exemplo", dao.observeAll().first().single().channelName)
    }
}
