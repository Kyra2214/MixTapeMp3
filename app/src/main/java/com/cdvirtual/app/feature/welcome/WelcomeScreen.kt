package com.cdvirtual.app.feature.welcome

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cdvirtual.app.R
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val done by rememberUpdatedState(onFinished)
    var left by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (left) 0f else 1f, tween(550, easing = FastOutSlowInEasing), label = "welcomeFade")
    fun leave() { if (!left) { left = true; done() } }
    LaunchedEffect(Unit) { delay(2200); leave() }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF17142D), Color(0xFF0B1726), Color(0xFF090D18))))
            .clickable { leave() }.alpha(alpha).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Logo do MixTape MP3",
            modifier = Modifier.size(184.dp).clip(RoundedCornerShape(44.dp))
        )
        Text(
            "MIXTAPE MP3", style = MaterialTheme.typography.headlineMedium,
            color = Color.White, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            "Seu som. Seu jeito.", style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF72E3D5), modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Crie. Personalize. Leve com você.",
            style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD1D8E8),
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "TOQUE PARA COMEÇAR", style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF9B7BFF), modifier = Modifier.padding(top = 48.dp)
        )
    }
}
