package com.masgzy.cfstmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masgzy.cfstmobile.ui.AppRoot
import com.masgzy.cfstmobile.ui.theme.CfstTheme
import com.masgzy.cfstmobile.viewmodel.TestViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 官方 Material 要求的 edge-to-edge：内容延伸到系统栏，
        // Scaffold/LargeTopAppBar 通过 windowInsets 自行避让
        enableEdgeToEdge()
        setContent {
            val viewModel: TestViewModel = viewModel(factory = TestViewModel.Factory)
            // 颜色模式（自动/浅色/深色）+ 动态取色 + AMOLED 纯黑实时生效
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            CfstTheme(
                dynamicColors = settings.dynamicColors,
                themeMode = settings.themeMode,
                amoled = settings.amoled,
            ) {
                AppRoot(viewModel = viewModel)
            }
        }
    }
}
