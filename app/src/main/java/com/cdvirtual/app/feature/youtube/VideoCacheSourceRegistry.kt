package com.cdvirtual.app.feature.youtube

import com.cdvirtual.app.core.database.VideoCacheQuality

/**
 * Contrato de admissão de fontes para o cache temporário.
 *
 * Uma implementação só pode retornar uma mídia quando a fonte e a conta/usuário
 * tiverem autorização para download e armazenamento local. O contrato não autoriza
 * extração de streams, scraping ou contorno de controles da plataforma.
 */
interface VideoCacheSourceProvider {
    val sourceId: String

    suspend fun resolve(
        sourceVideoId: String,
        qualityHeight: Int
    ): VideoCacheResolvedSource?
}

data class VideoCacheResolvedSource(
    val sourceId: String,
    val sourceVideoId: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val qualityHeight: Int,
    val transferRequest: VideoCacheTransferRequest
) {
    init {
        require(sourceId.isNotBlank() && sourceVideoId.isNotBlank())
        require(title.isNotBlank())
        require(durationMs >= 0L)
        require(qualityHeight in VideoCacheQuality.supportedHeights)
    }
}

/**
 * Registro exclusivo da Central de Vídeos. Começa vazio de propósito:
 * nenhum extrator existente é promovido automaticamente a provedor offline.
 */
class VideoCacheSourceRegistry(
    providers: Collection<VideoCacheSourceProvider> = emptyList()
) {
    private val providers = providers.associateBy { it.sourceId }.toMutableMap()

    fun register(provider: VideoCacheSourceProvider) {
        require(provider.sourceId.isNotBlank())
        providers[provider.sourceId] = provider
    }

    fun unregister(sourceId: String) {
        providers.remove(sourceId)
    }

    fun hasProvider(sourceId: String): Boolean = providers.containsKey(sourceId)

    suspend fun resolve(
        sourceId: String,
        sourceVideoId: String,
        qualityHeight: Int
    ): VideoCacheResolvedSource? {
        require(qualityHeight in VideoCacheQuality.supportedHeights)
        return providers[sourceId]?.resolve(sourceVideoId, qualityHeight)
            ?.also { resolved ->
                require(resolved.sourceId == sourceId) {
                    "O provedor retornou um identificador de fonte diferente."
                }
                require(resolved.sourceVideoId == sourceVideoId) {
                    "O provedor retornou um identificador de vídeo diferente."
                }
                require(resolved.qualityHeight == qualityHeight) {
                    "O provedor retornou uma qualidade diferente da solicitada."
                }
            }
    }
}
