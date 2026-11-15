package com.cdvirtual.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cdvirtual.app.feature.cdeditor.CdEditorScreen
import com.cdvirtual.app.feature.equalizer.EqualizerScreen
import com.cdvirtual.app.feature.export.ExportScreen
import com.cdvirtual.app.feature.home.HomeScreen
import com.cdvirtual.app.feature.importer.ImporterScreen
import com.cdvirtual.app.feature.library.LibraryScreen
import com.cdvirtual.app.feature.player.PlayerScreen
import com.cdvirtual.app.feature.welcome.WelcomeScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Destination.Welcome.route) {
        composable(Destination.Welcome.route) {
            WelcomeScreen(onFinished = {
                nav.navigate(Destination.Home.route) { popUpTo(Destination.Welcome.route) { inclusive = true } }
            })
        }
        composable(Destination.Home.route) { HomeScreen(onNavigate = { nav.navigate(it.route) }) }
        composable(Destination.Library.route) { LibraryScreen() }
        composable(Destination.Importer.route) { ImporterScreen() }
        composable(Destination.CdEditor.route) { CdEditorScreen() }
        composable(Destination.Equalizer.route) { EqualizerScreen() }
        composable(Destination.Player.route) { PlayerScreen() }
        composable(Destination.Export.route) { ExportScreen() }
    }
}
