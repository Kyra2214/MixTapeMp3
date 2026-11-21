package com.cdvirtual.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TrackEntity::class, CdEntity::class, CdTrackEntity::class, YouTubeHistoryEntity::class], version = 12, exportSchema = true)
/**
 * REGRA (T-103): a v10 é a linha de base da 1.0 (v11 acrescenta o gênero das músicas). Toda mudança de schema exige incrementar a versão e registrar uma
 * `Migration` (addMigrations) — nunca voltar a usar fallbackToDestructiveMigration() sem lista de versões.
 * O arquivo app/schemas/.../10.json é gerado pelo KSP no primeiro build e deve ser versionado.
 */
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun cdDao(): CdDao
    abstract fun youtubeHistoryDao(): YouTubeHistoryDao

    companion object {
        private const val NAME = "cdvirtual.db"

        @Volatile private var instance: AppDatabase? = null

        /** v10 -> v11: coluna de gênero (NULL = ainda não lido; o app preenche ao abrir). */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN genre TEXT")
            }
        }

        /** v11 -> v12: cria o histórico local e independente da área YouTube. */
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

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8, 9) // só builds de desenvolvimento antigos; a v10 é a base da 1.0 e nunca é destruída
                .build().also { instance = it }
        }
    }
}
