package com.cdvirtual.app

import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cdvirtual.app.navigation.AppNavHost
import com.cdvirtual.app.core.diagnostics.AppDiagnostics
import com.cdvirtual.app.ui.theme.CDVirtualTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDiagnostics.record("LIFECYCLE", "MainActivity.onCreate", "savedState=${savedInstanceState != null}")
        // Mantém as teclas físicas associadas ao volume de mídia enquanto o app está aberto.
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            // T-602: a partir da API 35 o app desenha sob as barras do sistema (edge-to-edge obrigatório); o padding evita conteúdo coberto.
            CDVirtualTheme { Surface { Box(Modifier.fillMaxSize().safeDrawingPadding()) { AppNavHost() } } }
        }
    }

    override fun onStart() {
        super.onStart()
        AppDiagnostics.record("LIFECYCLE", "MainActivity.onStart")
    }

    override fun onResume() {
        super.onResume()
        AppDiagnostics.record("LIFECYCLE", "MainActivity.onResume")
    }

    override fun onPause() {
        AppDiagnostics.record("LIFECYCLE", "MainActivity.onPause")
        super.onPause()
    }

    override fun onStop() {
        AppDiagnostics.record("LIFECYCLE", "MainActivity.onStop")
        super.onStop()
    }

    override fun onDestroy() {
        AppDiagnostics.record("LIFECYCLE", "MainActivity.onDestroy", "changingConfigurations=$isChangingConfigurations")
        super.onDestroy()
    }

    /**
     * Alguns aparelhos/versões do Android não respeitam volumeControlStream de forma consistente
     * em telas Compose. Roteia explicitamente as teclas físicas para STREAM_MUSIC em toda a app.
     * A UI de volume do sistema continua visível e o controle da tela Player acompanha o valor.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            KeyEvent.KEYCODE_VOLUME_MUTE -> AudioManager.ADJUST_MUTE
            else -> null
        }
        if (direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                getSystemService(AudioManager::class.java)?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    direction,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
