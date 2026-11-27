package com.cdvirtual.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.cdvirtual.app.core.database.AppDatabase
import com.cdvirtual.app.core.storage.VideoCacheFileStore
import com.cdvirtual.app.feature.cdeditor.CdRepository
import com.cdvirtual.app.feature.library.AnalysisRunner
import com.cdvirtual.app.feature.library.CoverStore
import com.cdvirtual.app.feature.library.GenreBackfill
import com.cdvirtual.app.feature.library.TrackImporter
import com.cdvirtual.app.feature.library.TrackRepository
import com.cdvirtual.app.feature.youtube.VideoCacheRepository
import com.cdvirtual.app.feature.youtube.VideoCacheRecovery
import com.cdvirtual.app.feature.youtube.VideoCacheManager
import com.cdvirtual.app.feature.youtube.VideoCacheSettingsStore
import com.cdvirtual.app.feature.youtube.VideoCacheTransfer
import com.cdvirtual.app.feature.youtube.VideoCacheDownloadCoordinator
import com.cdvirtual.app.feature.youtube.VideoCacheSourceRegistry
import com.cdvirtual.app.media.connectors.ImportRecovery
import com.cdvirtual.app.media.service.ExportRecovery

class CDVirtualApp : Application() {
    val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val trackRepository: TrackRepository by lazy { TrackRepository(database.trackDao()) }
    val cdRepository: CdRepository by lazy { CdRepository(database.cdDao(), trackRepository, CoverStore(this)) }
    val analysisRunner: AnalysisRunner by lazy { AnalysisRunner(this, trackRepository) }
    val trackImporter: TrackImporter by lazy { TrackImporter(this, trackRepository) { analysisRunner.kick() } }
    val videoCacheFileStore: VideoCacheFileStore by lazy { VideoCacheFileStore(this) }
    val videoCacheRepository: VideoCacheRepository by lazy { VideoCacheRepository(database.videoCacheDao(), videoCacheFileStore) }
    val videoCacheRecovery: VideoCacheRecovery by lazy { VideoCacheRecovery(videoCacheRepository, videoCacheFileStore) }
    val videoCacheSettings: VideoCacheSettingsStore by lazy { VideoCacheSettingsStore(this) }
    val videoCacheManager: VideoCacheManager by lazy { VideoCacheManager(videoCacheRepository, videoCacheFileStore, videoCacheSettings) }
    val videoCacheTransfer: VideoCacheTransfer by lazy { VideoCacheTransfer(videoCacheFileStore) }
    val videoCacheDownloadCoordinator: VideoCacheDownloadCoordinator by lazy {
        VideoCacheDownloadCoordinator(videoCacheManager, videoCacheTransfer)
    }
    val videoCacheSourceRegistry: VideoCacheSourceRegistry by lazy { VideoCacheSourceRegistry() }

    override fun onCreate() {
        super.onCreate()
        ExportRecovery.recoverOnLaunch(this)
        ImportRecovery.cleanPartials(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ImportRecovery.cleanOrphans(this@CDVirtualApp) { trackRepository.findByUri(it) != null }
            GenreBackfill.run(this@CDVirtualApp, trackRepository) // músicas anteriores à coluna de gênero
            runCatching { videoCacheRecovery.recover() }
                .onSuccess { report ->
                    android.util.Log.i(
                        "VideoCacheRecovery",
                        "Recuperação concluída: interrompidos=${report.interruptedDownloads}, " +
                            "reprodução pausada=${report.pausedPlayback}, arquivos ausentes/corrompidos=${report.missingFiles}, " +
                            "concluídos removidos=${report.completedItemsRemoved}, órfãos preservados=${report.orphanedFiles.size}, " +
                            "parciais preservados=${report.partialFiles.size}"
                    )
                }
                .onFailure { android.util.Log.w("VideoCacheRecovery", "Não foi possível reconciliar o cache de vídeos", it) }
        }
        analysisRunner.kick()
    }
}
