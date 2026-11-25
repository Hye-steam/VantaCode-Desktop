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
        requestMediaAccessIfNeeded()
        setContent {
            // Material 色板跟随 LoopColors 走，必须在主题切换的重建范围内，
            // 所以由 AppRoot 内部包裹（见 AppRoot）。
            AiGatewayRoot()
        }
    }

    /**
     * 申请「所有文件访问」。
     *
     * 生成出来的图片/视频要落在公共目录，客户端才读得到；没有这个权限只能写进
     * /Android/data/<包名>，那边对其他应用是隔离的。用户拒绝也不阻断使用，
     * 只是媒体会退到私有目录并打告警。
     */
    private fun requestMediaAccessIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (android.os.Environment.isExternalStorageManager()) return
        val prefs = getSharedPreferences("aigw_ui", MODE_PRIVATE)
        // 只在首次进入时引导一次，之后由设置页手动开启，避免每次启动都弹系统页
        if (prefs.getBoolean("media_access_asked", false)) return
        prefs.edit().putBoolean("media_access_asked", true).apply()
        runCatching {
            startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:$packageName"),
                ),
            )
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
