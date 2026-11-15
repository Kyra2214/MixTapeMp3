package com.cdvirtual.app.feature.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cdvirtual.app.R
import kotlinx.coroutines.delay

/** Tela de boas-vindas: logo, nome e frase; segue sozinha para o Início após ~2,5 s ou ao toque. Só aparece ao abrir o app. */
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val done by rememberUpdatedState(onFinished)
    var left by remember { mutableStateOf(false) }
    fun leave() { if (!left) { left = true; done() } }
    LaunchedEffect(Unit) { delay(2500); leave() }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary).clickable { leave() }.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Logo do MixTape MP3",
            modifier = Modifier.size(160.dp).clip(RoundedCornerShape(36.dp))
        )
        Text("MixTape MP3", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Bem-vindo! Monte seu CD em um único MP3.", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
        )
        Text("Toque para começar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(top = 32.dp))
    }
}
