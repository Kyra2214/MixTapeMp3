package com.cdvirtual.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Faixa dentro de um CD. [position] começa em 0 e define a ordem (reordenação na 5.4). */
@Entity(
    tableName = "cd_tracks",
    primaryKeys = ["cdId", "trackId"],
    foreignKeys = [
        ForeignKey(CdEntity::class, parentColumns = ["id"], childColumns = ["cdId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("trackId")]
)
data class CdTrackEntity(val cdId: Long, val trackId: Long, val position: Int)
