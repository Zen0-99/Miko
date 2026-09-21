package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.entity.Scm
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.util.htmlReadyLicenseContent
import com.mikepenz.aboutlibraries.util.withContext
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

class OpenSourceLicensesScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.licenses),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            LibrariesContainer(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = contentPadding,
                librariesBlock = { context ->
                    val libs = Libs.Builder().withContext(context).build()
                    libs.copy(
                        libraries = (libs.libraries + pocketVoicesLibrary + supertonicVoicesLibrary).toImmutableList(),
                        licenses = (libs.licenses + pocketVoiceLicense + supertonicLicense).toImmutableSet(),
                    )
                },
                onLibraryClick = {
                    navigator.push(
                        OpenSourceLibraryLicenseScreen(
                            name = it.name,
                            website = it.website,
                            license = it.licenses.firstOrNull()?.htmlReadyLicenseContent.orEmpty(),
                        ),
                    )
                },
            )
        }
    }
}

/**
 * CC-BY-4.0 attribution notice for the Pocket TTS voice data (REQ-006f).
 * Mirrors `LICENSES/pocket-tts.md` / `CREDITS.md` from the upstream
 * marmalade-tts-android port. Rendered as HTML on the license detail screen.
 */
private const val POCKET_VOICE_ATTRIBUTION = """
<b>Pocket TTS engine</b><br/>
Model and inference code by <b>Kyutai</b> — MIT License
(https://github.com/kyutai-labs/pocket-tts). The engine bundle is downloaded
on demand and is not shipped inside the app.<br/><br/>
<b>Voice data — attribution required (CC-BY-4.0)</b><br/>
• <b>alba</b> — "Alba Mackenna", released by Kyutai under CC-BY-4.0.<br/>
• <b>azelma</b>, <b>eponine</b>, <b>fantine</b> — derived from the CSTR VCTK
Corpus (speakers p303, p262, p244), Centre for Speech Technology Research,
University of Edinburgh — CC-BY-4.0. https://doi.org/10.7488/ds/2645<br/>
• <b>javert</b>, <b>marius</b> — Unmute Voice Donation Project — CC0
(no attribution required; acknowledged with thanks).<br/>
• <b>vctk_*</b> extra voices (Miko-expanded bundle) — CSTR VCTK Corpus,
University of Edinburgh — CC-BY-4.0.<br/><br/>
The upstream cosette (Expresso) and jean (EARS) voices are CC-BY-NC-4.0
(non-commercial) and are deliberately excluded. ONNX export layout derives
from KevinAHM/pocket-tts-onnx (Apache-2.0); inference runs on Microsoft's
onnxruntime-android (MIT).
"""

private val pocketVoiceLicense = License(
    name = "CC-BY-4.0 / CC0",
    url = "https://creativecommons.org/licenses/by/4.0/",
    licenseContent = POCKET_VOICE_ATTRIBUTION.trimIndent(),
    hash = "pocket-tts-voices-attribution",
)

private val pocketVoicesLibrary = Library(
    uniqueId = "kyutai:pocket-tts-voices",
    artifactVersion = null,
    name = "Pocket TTS voices",
    description = "Voice reference recordings for the Pocket TTS engine " +
        "(downloaded engine bundle). alba, azelma, eponine, fantine: CC-BY-4.0 " +
        "(Alba Mackenna / CSTR VCTK Corpus, U. Edinburgh); javert, marius: CC0 " +
        "(Unmute Voice Donation Project); vctk_* extras: CC-BY-4.0. " +
        "Engine code: MIT (Kyutai).",
    website = "https://github.com/kyutai-labs/pocket-tts",
    developers = persistentListOf(),
    organization = null,
    scm = Scm(
        connection = null,
        developerConnection = null,
        url = "https://github.com/kyutai-labs/pocket-tts",
    ),
    licenses = persistentSetOf(pocketVoiceLicense),
)

/**
 * OpenRAIL-M attribution notice for the Supertonic-3 model + bundled
 * voice styles (D-10). Mirrors `LICENSES/supertonic-3.md`. Rendered as
 * HTML on the license detail screen.
 */
private const val SUPERTONIC_ATTRIBUTION = """
<b>Supertonic-3 TTS engine</b><br/>
Model by <b>Supertone</b> — OpenRAIL-M license
(https://huggingface.co/Supertone/supertonic-3). The engine bundle is
downloaded on demand and is not shipped inside the app.<br/><br/>
<b>Voice styles</b><br/>
The ten bundled voices (F1–F5, M1–M5) ship inside the model's
voice.bin tensor stack and are covered by the same OpenRAIL-M license.
Use restrictions apply — see the model card for the full text.<br/><br/>
The int8 weights are republished via the sherpa-onnx tts-models release
(k2-fsa/sherpa-onnx); inference runs on Microsoft's onnxruntime-android
(MIT — see the Pocket TTS voices entry).
"""

private val supertonicLicense = License(
    name = "OpenRAIL-M",
    url = "https://huggingface.co/Supertone/supertonic-3",
    licenseContent = SUPERTONIC_ATTRIBUTION.trimIndent(),
    hash = "supertonic-3-attribution",
)

private val supertonicVoicesLibrary = Library(
    uniqueId = "supertone:supertonic-3",
    artifactVersion = null,
    name = "Supertonic-3 voices",
    description = "Supertonic-3 TTS model and bundled voice styles " +
        "(downloaded engine bundle). Model + voices: OpenRAIL-M (Supertone). " +
        "int8 weights ship via the sherpa-onnx tts-models release; " +
        "inference runs on onnxruntime-android (MIT).",
    website = "https://huggingface.co/Supertone/supertonic-3",
    developers = persistentListOf(),
    organization = null,
    scm = Scm(
        connection = null,
        developerConnection = null,
        url = "https://huggingface.co/Supertone/supertonic-3",
    ),
    licenses = persistentSetOf(supertonicLicense),
)
