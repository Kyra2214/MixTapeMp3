package com.cdvirtual.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TrackEntity::class, CdEntity::class, CdTrackEntity::class, YouTubeHistoryEntity::class, YouTubeChannelEntity::class], version = 13, exportSchema = true)
/**
 * REGRA (T-103): a v10 é a linha de base da 1.0 (v11 acrescenta o gênero das músicas). Toda mudança de schema exige incrementar a versão e registrar uma
 * Migration (addMigrations), nunca voltar a usar fallbackToDestructiveMigration() sem lista de versões.
 */
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun cdDao(): CdDao
    abstract fun youtubeHistoryDao(): YouTubeHistoryDao
    abstract fun youtubeChannelDao(): YouTubeChannelDao

    companion object {
        private const val NAME = "cdvirtual.db"

        @Volatile private var instance: AppDatabase? = null

        /** v10 -> v11: coluna de gênero. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN genre TEXT")
            }
        }

        /** v11 -> v12: cria o histórico local da área YouTube. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS youtube_history (
                        videoId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        uploader TEXT,
                        thumbnailUrl TEXT,
                        durationSeconds INTEGER NOT NULL,
                        lastPositionMs INTEGER NOT NULL,
                        watchedAt INTEGER NOT NULL,
                        watchCount INTEGER NOT NULL
                    )"""
                )
            }
        }

        /** v12 -> v13: cria a coleção local de canais escolhidos pelo usuário. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS youtube_channels (
                        channelUrl TEXT NOT NULL PRIMARY KEY,
                        channelName TEXT NOT NULL,
                        avatarUrl TEXT,
                        addedAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8, 9)
                .build().also { instance = it }
        }
    }
}
