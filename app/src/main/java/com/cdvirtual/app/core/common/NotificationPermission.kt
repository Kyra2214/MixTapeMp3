package com.cdvirtual.app.core.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * T-503: pede POST_NOTIFICATIONS (Android 13+) antes de uma ação que usa serviço em primeiro plano (gerar MP3, tocar) e
 * executa a ação de qualquer jeito, mesmo se a permissão for negada (a notificação é um extra, não um requisito).
 * Uso: `val withNotifications = rememberNotificationGate()` e `withNotifications { ... }`.
 */
@Composable
fun rememberNotificationGate(): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val action = pending
        pending = null
        action?.invoke()
    }
    return { action ->
        val missing = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (missing) { pending = action; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } else action()
    }
}
