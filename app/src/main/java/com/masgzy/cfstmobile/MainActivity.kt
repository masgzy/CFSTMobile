package com.masgzy.cfstmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masgzy.cfstmobile.ui.AppRoot
import com.masgzy.cfstmobile.ui.theme.CfstTheme
import com.masgzy.cfstmobile.viewmodel.TestViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CfstTheme {
                AppRoot(viewModel = viewModel(factory = TestViewModel.Factory))
            }
        }
    }
}
