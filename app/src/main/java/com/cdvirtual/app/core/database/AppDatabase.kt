package com.cdvirtual.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class, CdEntity::class, CdTrackEntity::class], version = 10, exportSchema = true)
/**
 * REGRA (T-103): a v10 é a linha de base da 1.0. Toda mudança de schema exige incrementar a versão e registrar uma
 * `Migration` (addMigrations) — nunca voltar a usar fallbackToDestructiveMigration() sem lista de versões.
 * O arquivo app/schemas/.../10.json é gerado pelo KSP no primeiro build e deve ser versionado.
 */
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun cdDao(): CdDao

    companion object {
        private const val NAME = "cdvirtual.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8, 9) // só builds de desenvolvimento antigos; a v10 é a base da 1.0 e nunca é destruída
                .build().also { instance = it }
        }
    }
}
