package dev.aigw.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aigw.app.AiGatewayApp
import dev.aigw.app.ui.AppRoot
import dev.aigw.app.ui.AppViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 让主题背景延伸到状态栏：内容自绘到系统栏区域，系统栏保持透明
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            // Material 色板跟随 LoopColors 走，必须在主题切换的重建范围内，
            // 所以由 AppRoot 内部包裹（见 AppRoot）。
            AiGatewayRoot()
        }
    }

    override fun onStart() {
        super.onStart()
        // 把 WebView 请求通道挂到引擎上：DeepSeek 原生请求被 403 拦截时自动改走页面 fetch
        val app = application as AiGatewayApp
        app.engine.deepSeekWebTransport = app.deepSeekTransport
    }
}

@Composable
private fun AiGatewayRoot() {
    val vm: AppViewModel = viewModel()
    AppRoot(vm)
}
