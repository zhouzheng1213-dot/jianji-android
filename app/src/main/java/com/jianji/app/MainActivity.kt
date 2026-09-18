package com.jianji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.jianji.app.ui.JianJiRoot
import com.jianji.app.ui.theme.JianJiTheme
import com.jianji.app.ui.theme.Palette
import com.jianji.app.vm.LedgerViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val warmWhite = Palette.WarmWhite.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(warmWhite, warmWhite),
            navigationBarStyle = SystemBarStyle.light(warmWhite, warmWhite)
        )
        super.onCreate(savedInstanceState)

        val app = application as JianJiApp
        val factory = LedgerViewModel.Factory(app.repository, app.settings)

        setContent {
            JianJiTheme {
                JianJiRoot(factory = factory)
            }
        }
    }
}
