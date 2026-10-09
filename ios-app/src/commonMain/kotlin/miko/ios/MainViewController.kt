package miko.ios

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

@Suppress("FunctionNaming", "ktlint:standard:function-naming", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { ProbeScreen() }
