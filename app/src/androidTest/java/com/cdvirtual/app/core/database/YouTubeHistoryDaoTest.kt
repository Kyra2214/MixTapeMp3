package com.cdvirtual.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YouTubeHistoryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: YouTubeHistoryDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.youtubeHistoryDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun savesProgressAndMovesResumedVideoToMostRecent() = runBlocking {
        dao.save(
            YouTubeHistoryEntity(
                videoId = "video-one",
                title = "Vídeo um",
                durationSeconds = 300,
                lastPositionMs = 0,
                watchedAt = 100
            )
        )
        dao.save(
            YouTubeHistoryEntity(
                videoId = "video-two",
                title = "Vídeo dois",
                durationSeconds = 600,
                watchedAt = 200
            )
        )

        dao.updateProgress("video-one", 45_000)
        dao.markResumed("video-one", 300)

        val resumed = dao.find("video-one")
        assertNotNull(resumed)
        assertEquals(45_000L, resumed!!.lastPositionMs)
        assertEquals(300L, resumed.watchedAt)
        assertEquals("video-one", dao.findMostRecent()?.videoId)
    }

    @Test
    fun deleteAndClearRemoveOnlyExpectedHistoryRows() = runBlocking {
        dao.save(YouTubeHistoryEntity(videoId = "video-one", title = "Vídeo um"))
        dao.save(YouTubeHistoryEntity(videoId = "video-two", title = "Vídeo dois"))

        dao.delete("video-one")
        assertNull(dao.find("video-one"))
        assertEquals(1, dao.count())

        dao.clear()
        assertEquals(0, dao.count())
        assertNull(dao.findMostRecent())
    }
}
