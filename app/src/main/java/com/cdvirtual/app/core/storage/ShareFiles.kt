package com.cdvirtual.app.core.storage

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Compartilhamento (9.4) de arquivos gerados via FileProvider: só filesDir/exports, somente leitura, sem copiar. */
object ShareFiles {
    fun authority(context: Context) = "${context.packageName}.fileprovider"

    /** Intent de escolha de app para enviar o MP3 (e o CUE, se existir ao lado dele com o mesmo nome base). */
    fun chooser(context: Context, mp3: File): Intent {
        require(mp3.isFile) { "Arquivo não encontrado: ${mp3.name}" }
        val mp3Uri = FileProvider.getUriForFile(context, authority(context), mp3)
        val cue = File(mp3.parentFile, mp3.nameWithoutExtension + ".cue").takeIf { it.isFile }
        val send = if (cue != null) {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(mp3Uri, FileProvider.getUriForFile(context, authority(context), cue)))
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_STREAM, mp3Uri)
            }
        }
        send.putExtra(Intent.EXTRA_SUBJECT, mp3.nameWithoutExtension)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Compartilhar ${mp3.nameWithoutExtension}").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * 10.6: abre o MP3 em outro player instalado (ACTION_VIEW, audio/mpeg) sem copiar o arquivo. Somente leitura, só
     * filesDir/exports. Devolve o seletor de apps; a tag ID3v2.3 (título, álbum, artista, capa, duração) e os capítulos
     * seguem no arquivo, e players sem suporte a CHAP simplesmente os ignoram.
     */
    fun openWith(context: Context, mp3: File): Intent {
        require(mp3.isFile) { "Arquivo não encontrado: ${mp3.name}" }
        val uri = FileProvider.getUriForFile(context, authority(context), mp3)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/mpeg")
            clipData = android.content.ClipData.newRawUri(mp3.name, uri) // garante a permissão ao app escolhido no seletor
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(view, "Abrir ${mp3.nameWithoutExtension} com").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
