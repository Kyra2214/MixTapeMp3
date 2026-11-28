package com.cdvirtual.app.core.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro persistente de diagnóstico. Não registra URLs, conteúdo digitado ou dados pessoais.
 * Mantém o arquivo atual limitado e conserva uma geração anterior.
 */
object AppDiagnostics {
    private const val TAG = "MixTapeDiagnostics"
    private const val MAX_BYTES = 2 * 1024 * 1024L
    private const val FILE_NAME = "mixtape-lifecycle.log"
    private val lock = Any()
    @Volatile private var appContext: Context? = null
    @Volatile private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private val formatter = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        record("APP", "Processo iniciado", "pid=${android.os.Process.myPid()} sdk=${android.os.Build.VERSION.SDK_INT}")
        synchronized(lock) {
            if (previousHandler == null) {
                previousHandler = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                    record("FATAL", "Exceção não tratada", "thread=${thread.name}\n${error.stackTraceToString()}")
                    previousHandler?.uncaughtException(thread, error)
                }
            }
        }
    }

    fun record(level: String, event: String, details: String? = null) {
        val context = appContext ?: return
        val timestamp = formatter.get().format(Date())
        val thread = Thread.currentThread().name
        val safeEvent = event.replace('\n', ' ').take(300)
        val safeDetails = details?.replace(Regex("https?://\\\\S+"), "<url-redacted>")?.take(12000)
        val line = buildString {
            append(timestamp).append(" [").append(level.uppercase(Locale.ROOT)).append("] ")
            append("[").append(thread).append("] ").append(safeEvent)
            if (!safeDetails.isNullOrBlank()) append("\n").append(safeDetails)
            append("\n")
        }
        Log.println(
            when (level.uppercase(Locale.ROOT)) {
                "ERROR", "FATAL" -> Log.ERROR
                "WARN" -> Log.WARN
                "DEBUG" -> Log.DEBUG
                else -> Log.INFO
            }, TAG, safeEvent
        )
        synchronized(lock) {
            runCatching {
                val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
                val file = File(dir, FILE_NAME)
                if (file.exists() && file.length() + line.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
                    val backup = File(dir, "$FILE_NAME.1")
                    if (backup.exists()) backup.delete()
                    file.renameTo(backup)
                }
                file.appendText(line, Charsets.UTF_8)
            }.onFailure { Log.e(TAG, "Não foi possível persistir o log", it) }
        }
    }

    fun readAll(context: Context): String = synchronized(lock) {
        val dir = File(context.applicationContext.filesDir, "diagnostics")
        val backup = File(dir, "$FILE_NAME.1")
        val current = File(dir, FILE_NAME)
        buildString {
            appendLine("# MixTapeMp3 - Relatório de diagnóstico")
            appendLine()
            appendLine("- Gerado em: ${formatter.get().format(Date())}")
            appendLine("- Versão do Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("- Fabricante/modelo: ${android.os.Build.MANUFACTURER} / ${android.os.Build.MODEL}")
            appendLine()
            appendLine("## Eventos anteriores")
            if (backup.exists()) {
                appendLine("### Arquivo anterior")
                appendLine("```text")
                append(backup.readText(Charsets.UTF_8))
                appendLine("```")
            }
            appendLine("### Sessão atual e eventos recentes")
            appendLine("```text")
            if (current.exists()) append(current.readText(Charsets.UTF_8))
            else appendLine("Nenhum evento registrado.")
            appendLine("```")
        }
    }
}
