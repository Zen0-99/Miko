package eu.kanade.tachiyomi.crash

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.crash.CrashScreen
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CrashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val exception = GlobalExceptionHandler.getThrowableFromIntent(intent)
        // Mode-aware theme — the per-mode light/dark/amoled prefs the
        // main UI already resolves; the crash screen inherits whichever
        // mode's theme was active when the app died. setComposeContent
        // is not used here because it hardcodes the manga-mode theme.
        val uiPreferences = Injekt.get<UiPreferences>()
        setContent {
            TachiyomiTheme(contentMode = uiPreferences.contentMode().get()) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodySmall,
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                ) {
                    CrashScreen(
                        exception = exception,
                        onRestartClick = {
                            finishAffinity()
                            startActivity(Intent(this@CrashActivity, MainActivity::class.java))
                        },
                    )
                }
            }
        }
    }
}
