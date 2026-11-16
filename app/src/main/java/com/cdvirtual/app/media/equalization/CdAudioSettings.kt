package com.cdvirtual.app.media.equalization

/** Equalização aplicada somente ao áudio renderizado do CD; os arquivos originais permanecem intactos. */
data class CdAudioSettings(
    val presetId: String = "original",
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val normalize: Boolean = true
) {
    companion object {
        fun preset(id: String, normalize: Boolean = true): CdAudioSettings {
            val values = when (id) {
                "acoustic" -> Triple(0f, 2f, 1.5f)
                "bass" -> Triple(6f, 0f, 0f)
                "treble" -> Triple(0f, 0f, 5f)
                "funk" -> Triple(6f, 1f, 2f)
                "pagode" -> Triple(1f, 3f, 2f)
                "blues" -> Triple(2f, 3f, 1f)
                "rock" -> Triple(4f, 2f, 3f)
                else -> Triple(0f, 0f, 0f)
            }
            return CdAudioSettings(id, values.first, values.second, values.third, normalize)
        }
    }
}
