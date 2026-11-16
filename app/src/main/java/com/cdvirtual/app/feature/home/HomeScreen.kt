package com.cdvirtual.app.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cdvirtual.app.core.storage.OutputFolder
import com.cdvirtual.app.core.storage.OutputFolderState
import com.cdvirtual.app.navigation.Destination

private data class HomeAction(val destination: Destination, val symbol: String, val subtitle: String, val tint: Color)

@Composable
fun HomeScreen(onNavigate: (Destination) -> Unit) {
    val context = LocalContext.current
    val outputFolder = remember { OutputFolder(context) }
    var folder by remember { mutableStateOf(outputFolder.state()) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) folder = outputFolder.choose(uri)
    }
    val actions = listOf(
        HomeAction(Destination.Browser, "◎", "Encontre áudio na web", Color(0xFF58D8D0)),
        HomeAction(Destination.Importer, "↓", "Adicione à sua coleção", Color(0xFFFFA36C)),
        HomeAction(Destination.Library, "♫", "Ouça e organize", Color(0xFFB49AFF)),
        HomeAction(Destination.CdEditor, "▣", "Monte seu CD", Color(0xFF78B7FF)),
        HomeAction(Destination.Equalizer, "≋", "Ajuste o som", Color(0xFFFF8B9A)),
        HomeAction(Destination.Player, "▶", "Seus mixes offline", Color(0xFF72D6A2)),
        HomeAction(Destination.Export, "↗", "Gere e compartilhe", Color(0xFFE2C477))
    )

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF9B7BFF), Color(0xFF52E0D0)))),
                contentAlignment = Alignment.Center
            ) { Text("♫", color = Color(0xFF111323), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            Column(Modifier.padding(start = 12.dp)) {
                Text("MIXTAPE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                Text("Seu som. Seu jeito.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("MP3", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
            }
        }

        Card(
            Modifier.fillMaxWidth().padding(top = 22.dp).animateContentSize(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                Modifier.background(Brush.linearGradient(listOf(Color(0xFF38286F), Color(0xFF172F45), Color(0xFF172A34))))
                    .padding(22.dp)
            ) {
                Column {
                    Text("DO PLAYLIST AO CD", color = Color(0xFF8DE9DE), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Sua coleção,
com identidade.", Modifier.padding(top = 8.dp), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Crie mixes, personalize o áudio e leve sua música com você.",
                        Modifier.padding(top = 8.dp), color = Color(0xFFD7DCEC), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { onNavigate(Destination.CdEditor) }, Modifier.padding(top = 16.dp)) {
                        Text("Criar meu CD  →")
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Seu estúdio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("7 ferramentas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(actions, key = { it.destination.route }) { action ->
                Card(
                    Modifier.fillMaxWidth().height(116.dp).clickable { onNavigate(action.destination) }.animateContentSize(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(action.tint.copy(alpha = 0.17f)), contentAlignment = Alignment.Center) {
                            Text(action.symbol, color = action.tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(action.destination.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = true) {
            Surface(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pasta de saída", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (folder is OutputFolderState.Ready) "Pronta para exportar" else "Defina onde salvar seus CDs",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { pickFolder.launch(null) }) {
                        Text(if (folder is OutputFolderState.Ready) "Alterar" else "Escolher")
                    }
                }
            }
        }
    }
}
