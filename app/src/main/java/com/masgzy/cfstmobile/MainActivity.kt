package com.masgzy.cfstmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masgzy.cfstmobile.ui.AppRoot
import com.masgzy.cfstmobile.ui.theme.CfstTheme
import com.masgzy.cfstmobile.viewmodel.TestViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: TestViewModel = viewModel(factory = TestViewModel.Factory)
            // 动态取色开关在设置里，实时生效（Material You / Cloudflare 品牌色）
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            CfstTheme(dynamicColors = settings.dynamicColors) {
                AppRoot(viewModel = viewModel)
            }
        }
    }
}
