package com.cdvirtual.app

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cdvirtual.app.navigation.AppNavHost
import com.cdvirtual.app.ui.theme.CDVirtualTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Os botões físicos de volume sempre controlam a mídia enquanto o app está aberto.
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            // T-602: a partir da API 35 o app desenha sob as barras do sistema (edge-to-edge obrigatório); o padding evita conteúdo coberto.
            CDVirtualTheme { Surface { Box(Modifier.fillMaxSize().safeDrawingPadding()) { AppNavHost() } } }
        }
    }
}
