package com.cdvirtual.app.media.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.cdvirtual.app.media.normalization.GainPlanner
import com.cdvirtual.app.media.normalization.NormalizationSettingsStore
import android.net.Uri
import com.cdvirtual.app.core.database.TrackEntity
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.cdvirtual.app.media.metadata.Id3Chapter
import com.cdvirtual.app.media.metadata.Id3Reader
import java.io.File
import java.io.FileInputStream

/** Estado observável do player offline. */
data class PlayerState(
    val file: File? = null,
    val playing: Boolean = false,
    val durationMs: Long = 0,
    val error: String? = null,
    /** Faixas do CD (capítulos ID3) do arquivo atual, ordenadas; vazia se o arquivo não as tiver. */
    val chapters: List<Id3Chapter> = emptyList(),
    /** Verdadeiro enquanto o arquivo está sendo preparado (T-504). */
    val loading: Boolean = false,
    val trackId: Long? = null,
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val trackCoverPath: String? = null,
    val volume: Float = 1f
) {
    /** Faixa que contém [positionMs] (a última cujo início já passou), ou null sem capítulos. */
    fun chapterIndexAt(positionMs: Long): Int = chapters.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)

    fun chapterAt(positionMs: Long): Id3Chapter? = chapters.lastOrNull { it.startMs <= positionMs } ?: chapters.firstOrNull()
}

/**
 * Player offline do MP3 gerado (8.1), sobre android.media.MediaPlayer (framework, sem dependências extras).
 * Um único player para o app: iniciar outro arquivo substitui o atual. O segundo plano é o PlaybackService (8.7); notificação
 * com controles e Bluetooth entram nas etapas 8.8–8.9. Somente leitura do arquivo.
 */
object Mp3Player {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var mp: MediaPlayer? = null
    private var outputEqualizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var libraryQueue: List<TrackEntity> = emptyList()
    private var libraryIndex: Int = -1
    private var pendingStartMs = 0L
    private var appContext: Context? = null
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    /** T-505: foco de áudio tratado aqui, para valer em qualquer caminho de retomada (tela, notificação, fones, foco devolvido). */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocusGain = false; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { resumeOnFocusGain = _state.value.playing; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> setVolume(0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                setVolume(1f)
                if (resumeOnFocusGain) { resumeOnFocusGain = false; resumeWithoutFocusRequest() }
            }
        }
    }

    /** Verdadeiro se o foco foi concedido (ou não há contexto para pedir). */
    private fun requestFocus(): Boolean {
        val ctx = appContext ?: return true
        val am = ctx.getSystemService(AudioManager::class.java) ?: return true
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(focusListener)
            .build().also { focusRequest = it }
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val ctx = appContext ?: return
        focusRequest?.let { ctx.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }
        focusRequest = null
        resumeOnFocusGain = false
    }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Começa a tocar [file] do início. T-504: a preparação é assíncrona (prepareAsync) e as tags (capítulos) são lidas em
     * IO, então nenhuma delas bloqueia a thread de UI. Enquanto prepara, [PlayerState.loading] é verdadeiro.
     * Falhas (arquivo ausente/corrompido) vão para [PlayerState.error].
     */
    @Synchronized
    fun play(context: Context, file: File, startMs: Long = 0L) {
        libraryQueue = emptyList(); libraryIndex = -1
        pendingStartMs = startMs.coerceAtLeast(0L)
        stopCurrent() // sem emitir "vazio": o serviço em segundo plano não deve parar ao trocar de arquivo
        if (!file.isFile || file.length() == 0L) {
            _state.value = PlayerState(error = "Arquivo não encontrado: ${file.name}")
            return
        }
        appContext = context.applicationContext
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK) // mantém a CPU ativa com a tela apagada
            FileInputStream(file).use { player.setDataSource(it.fd) } // fd é copiado pelo MediaPlayer
            player.setOnCompletionListener { onCompleted() }
            player.setOnErrorListener { _, what, extra ->
                fail("Erro ao reproduzir (código $what/$extra)")
                true
            }
            val app = context.applicationContext
            player.setOnPreparedListener { onPrepared(app, player, file) }
            mp = player
            _state.value = PlayerState(file = file, playing = false, loading = true)
            player.prepareAsync()
        } catch (e: Throwable) {
            try { player.release() } catch (_: Throwable) { }
            mp = null
            _state.value = PlayerState(error = e.message ?: "Não foi possível reproduzir ${file.name}")
        }
    }

    /** Reproduz uma faixa da biblioteca dentro de uma fila; embaralha a fila quando solicitado. */
    @Synchronized
    fun playLibrary(context: Context, tracks: List<TrackEntity>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val ordered = if (shuffle) tracks.shuffled() else tracks
        libraryQueue = ordered
        libraryIndex = if (shuffle) 0 else startIndex.coerceIn(0, ordered.lastIndex)
        loadLibraryTrack(context, ordered[libraryIndex])
    }

    @Synchronized
    private fun loadLibraryTrack(context: Context, track: TrackEntity) {
        stopCurrent()
        pendingStartMs = 0L
        appContext = context.applicationContext
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            player.setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            player.setDataSource(context.applicationContext, Uri.parse(track.uri))
            player.setOnCompletionListener { onCompleted() }
            player.setOnErrorListener { _, what, extra -> fail("Erro ao reproduzir (código $what/$extra)"); true }
            player.setOnPreparedListener { onPreparedLibrary(context.applicationContext, player, track) }
            mp = player
            _state.value = PlayerState(loading = true, trackId = track.id, trackTitle = track.title,
                trackArtist = track.artist, trackCoverPath = track.coverPath)
            player.prepareAsync()
        } catch (e: Throwable) {
            try { player.release() } catch (_: Throwable) { }
            mp = null
            _state.value = PlayerState(error = e.message ?: "Não foi possível reproduzir ${track.title}")
        }
    }

    @Synchronized
    private fun onPreparedLibrary(app: Context, player: MediaPlayer, track: TrackEntity) {
        if (mp !== player) return
        requestFocus()
        applyTrackEqualizer(player, track)
        applyTrackNormalization(player, track, app)
        try { player.start() } catch (e: Throwable) { fail(e.message ?: "Não foi possível reproduzir ${track.title}"); return }
        PlaybackService.start(app)
        _state.value = PlayerState(playing = true, durationMs = player.duration.toLong().coerceAtLeast(0),
            trackId = track.id, trackTitle = track.title, trackArtist = track.artist, trackCoverPath = track.coverPath)
    }

    /** Aplica o EQ salvo na faixa usando o equalizador de saída do Android, quando suportado pelo aparelho. */
    @Synchronized
    private fun applyTrackEqualizer(player: MediaPlayer, track: TrackEntity) {
        try {
            outputEqualizer?.release()
            val eq = Equalizer(0, player.audioSessionId)
            val range = eq.bandLevelRange
            for (band in 0 until eq.numberOfBands.toInt()) {
                val freq = eq.getCenterFreq(band.toShort()).toInt()
                val db = when {
                    freq < 250_000 -> track.bassDb
                    freq < 4_000_000 -> track.midDb
                    else -> track.trebleDb
                }
                val level = (db * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                eq.setBandLevel(band.toShort(), level.toShort())
            }
            eq.enabled = true
            outputEqualizer = eq
        } catch (_: Throwable) {
            outputEqualizer?.let { try { it.release() } catch (_: Throwable) { } }
            outputEqualizer = null
        }
    }

    /** Aplica ganho automático por loudness e true peak na reprodução da biblioteca, se habilitado. */
    @Synchronized
    private fun applyTrackNormalization(player: MediaPlayer, track: TrackEntity, context: Context) {
        try {
            val settings = NormalizationSettingsStore(context).load()
            if (!settings.enabled) return
            val gainDb = GainPlanner.forTrack(track, settings)?.gainDb ?: return
            val enhancer = LoudnessEnhancer(player.audioSessionId)
            enhancer.setTargetGain((gainDb * 100).toInt())
            enhancer.enabled = true
            loudnessEnhancer = enhancer
        } catch (_: Throwable) {
            loudnessEnhancer?.let { try { it.release() } catch (_: Throwable) { } }
            loudnessEnhancer = null
        }
    }

    /** Próxima faixa da fila da biblioteca; volta ao início ao chegar ao fim. */
    @Synchronized
    fun nextTrack(): Boolean {
        if (libraryQueue.isEmpty()) return false
        libraryIndex = (libraryIndex + 1) % libraryQueue.size
        loadLibraryTrack(appContext ?: return false, libraryQueue[libraryIndex])
        return true
    }

    /** Faixa anterior da fila da biblioteca; no início volta para a última. */
    @Synchronized
    fun previousTrack(): Boolean {
        if (libraryQueue.isEmpty()) return false
        libraryIndex = if (libraryIndex <= 0) libraryQueue.lastIndex else libraryIndex - 1
        loadLibraryTrack(appContext ?: return false, libraryQueue[libraryIndex])
        return true
    }

    /** Chamado na thread principal quando a preparação termina; ignora players já substituídos. */
    @Synchronized
    private fun onPrepared(app: Context, player: MediaPlayer, file: File) {
        if (mp !== player) return
        requestFocus() // ligação em curso etc.: o início segue como antes; perdas posteriores chegam ao focusListener
        try {
            if (pendingStartMs > 0) player.seekTo(pendingStartMs.toInt())
            pendingStartMs = 0L
            player.start()
        } catch (e: Throwable) {
            fail(e.message ?: "Não foi possível reproduzir ${file.name}")
            return
        }
        PlaybackService.start(app)
        _state.value = PlayerState(file = file, playing = true, durationMs = player.duration.toLong().coerceAtLeast(0))
        ioScope.launch {
            val chapters = Id3Reader.readChapters(file)
            _state.update { if (it.file == file) it.copy(chapters = chapters) else it }
        }
    }

    /** Pausa mantendo a posição; sem efeito se não estiver tocando. */
    @Synchronized
    fun pause() {
        val p = mp ?: return
        try { if (p.isPlaying) p.pause() } catch (_: Throwable) { return }
        _state.value = _state.value.copy(playing = false)
    }

    /** Continua de onde parou; se a faixa terminou, recomeça do início. */
    @Synchronized
    fun resume() {
        if (mp == null) return
        if (!requestFocus()) return // T-505: outro app (ligação) está com o áudio; não toca por cima
        resumeWithoutFocusRequest()
    }

    @Synchronized
    private fun resumeWithoutFocusRequest() {
        val p = mp ?: return
        try {
            if (!p.isPlaying) {
                if (p.currentPosition >= p.duration - 50) p.seekTo(0)
                p.start()
            }
        } catch (_: Throwable) { return }
        _state.value = _state.value.copy(playing = true)
    }

    /** Posição atual em ms (0 se nada carregado). */
    @Synchronized
    fun positionMs(): Long = try { mp?.currentPosition?.toLong() ?: 0L } catch (_: Throwable) { 0L }

    /** Vai para [ms] (limitado a 0..duração), mantendo tocando/pausado. */
    @Synchronized
    fun seekTo(ms: Long) {
        val p = mp ?: return
        val target = ms.coerceIn(0, _state.value.durationMs).toInt()
        try { p.seekTo(target) } catch (_: Throwable) { }
    }

    /** Avança (delta > 0) ou retrocede (delta < 0) em ms a partir da posição atual. */
    fun seekBy(deltaMs: Long) = seekTo(positionMs() + deltaMs)

    /** Pula para o início da faixa seguinte; sem capítulos (ou na última faixa), não faz nada e devolve false. */
    @Synchronized
    fun nextChapter(): Boolean {
        val s = _state.value
        if (s.chapters.isEmpty()) return false
        val i = s.chapterIndexAt(positionMs())
        if (i >= s.chapters.lastIndex) return false
        seekTo(s.chapters[i + 1].startMs)
        return true
    }

    /** Se já passaram mais de 3 s da faixa atual, volta ao início dela; senão, vai à faixa anterior. Sem capítulos devolve false. */
    @Synchronized
    fun previousChapter(): Boolean {
        val s = _state.value
        if (s.chapters.isEmpty()) return false
        val pos = positionMs()
        val i = s.chapterIndexAt(pos)
        val cur = s.chapters[i]
        seekTo(if (pos - cur.startMs > 3000 || i == 0) cur.startMs else s.chapters[i - 1].startMs)
        return true
    }

    /** Volume relativo do player (0..1), usado para "ducking" por outros apps. */
    @Synchronized
    fun setVolume(v: Float) {
        val level = v.coerceIn(0f, 1f)
        try { mp?.setVolume(level, level) } catch (_: Throwable) { }
        _state.update { it.copy(volume = level) }
    }

    fun togglePlayPause() { if (_state.value.playing) pause() else resume() }

    /** Para e libera o player (a escolha atual é esquecida). */
    @Synchronized
    fun release() {
        stopCurrent()
        libraryQueue = emptyList(); libraryIndex = -1
        abandonFocus()
        _state.value = PlayerState()
    }

    private fun stopCurrent() {
        loudnessEnhancer?.let { try { it.release() } catch (_: Throwable) { } }
        loudnessEnhancer = null
        outputEqualizer?.let { try { it.release() } catch (_: Throwable) { } }
        outputEqualizer = null
        mp?.let { try { it.stop() } catch (_: Throwable) { }; try { it.release() } catch (_: Throwable) { } }
        mp = null
    }

    @Synchronized
    private fun onCompleted() {
        abandonFocus()
        if (libraryQueue.isNotEmpty() && libraryIndex < libraryQueue.lastIndex) { nextTrack(); return }
        _state.value = _state.value.copy(playing = false)
    }

    @Synchronized
    private fun fail(msg: String) {
        abandonFocus()
        mp?.let { try { it.release() } catch (_: Throwable) { } }
        mp = null
        _state.value = PlayerState(error = msg)
    }
}
