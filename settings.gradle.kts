pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("$rootDir/local-maven") }
            google(); mavenCentral()
        // ASSUMINDO (6.5): LAME via TAndroidLame (JitPack); coordenadas/versão a confirmar no primeiro build com internet.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.naman14")
                includeGroup("com.github.teamnewpipe")
                includeGroup("com.github.TeamNewPipe")
            }
        }
    }
}
rootProject.name = "CDVirtual"
include(":app")
