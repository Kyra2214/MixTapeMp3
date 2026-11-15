package com.cdvirtual.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Projeto de CD virtual. Nome/capa (5.2), faixas (5.3–5.5) e salvamento (5.8) expandem esta entidade. */
@Entity(tableName = "cds")
data class CdEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverPath: String? = null,
    val artist: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
