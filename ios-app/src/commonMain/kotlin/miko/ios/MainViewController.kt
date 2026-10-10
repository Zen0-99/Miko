package miko.ios

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import miko.ui.MikoApp
import platform.UIKit.UIViewController

@Suppress("FunctionNaming", "ktlint:standard:function-naming", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    val graph = remember { AppGraph() }
    MikoApp(graph.uiGraph)
}
