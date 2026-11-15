package com.cdvirtual.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.cdvirtual.app.core.database.AppDatabase
import com.cdvirtual.app.feature.cdeditor.CdRepository
import com.cdvirtual.app.feature.library.AnalysisRunner
import com.cdvirtual.app.feature.library.CoverStore
import com.cdvirtual.app.feature.library.TrackImporter
import com.cdvirtual.app.feature.library.TrackRepository
import com.cdvirtual.app.media.connectors.ImportRecovery
import com.cdvirtual.app.media.service.ExportRecovery

class CDVirtualApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val trackRepository: TrackRepository by lazy { TrackRepository(database.trackDao()) }
    val cdRepository: CdRepository by lazy { CdRepository(database.cdDao(), trackRepository, CoverStore(this)) }
    val analysisRunner: AnalysisRunner by lazy { AnalysisRunner(this, trackRepository) }
    val trackImporter: TrackImporter by lazy { TrackImporter(this, trackRepository) { analysisRunner.kick() } }

    override fun onCreate() {
        super.onCreate()
        ExportRecovery.recoverOnLaunch(this)
        ImportRecovery.cleanPartials(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { ImportRecovery.cleanOrphans(this@CDVirtualApp) { trackRepository.findByUri(it) != null } }
        analysisRunner.kick()
    }
}
