package com.cdvirtual.app.navigation

enum class Destination(val route: String, val label: String) {
    Welcome("welcome", "Boas-vindas"),
    Home("home", "Início"),
    Library("library", "Biblioteca"),
    Importer("importer", "Importar"),
    Browser("browser", "Navegador"),
    CdEditor("cdeditor", "Playlists"),
    Equalizer("equalizer", "Equalizador"),
    Player("player", "Player"),
    Export("export", "Exportar")
}
