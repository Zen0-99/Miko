package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import eu.kanade.tachiyomi.novelsource.NovelSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter

/**
 * One item in the chapter-audio render queue — the in-memory analog of
 * `model/NovelDownload` (plan 05-02, D-13/D-14). Queue state is deliberately
 * NOT persisted: process death restarts renders from zero and the manager
 * sweeps stale PCM scratch on first use (D-10).
 *
 * Per-item [statusFlow]/[progressFlow] are transient hot streams that the UI
 * merges via `ChapterAudioRenderManager.statusFlow()/progressFlow()` (the
 * `NovelDownloadManager` pattern).
 *
 * @property completedParagraphs same-session resume checkpoint — paragraphs
 *   already synthesized into `cacheDir/chapter_audio/<id>.pcm` (SD-2).
 * @property cancelledForPreempt distinguishes a D-14 "Start render now"
 *   preemption (scratch retained, item requeued) from a D-11 user cancel
 *   (scratch abandoned, item removed). Set by the manager before it cancels
 *   the item's job; read in the render coroutine's cancellation catch.
 */
class ChapterRender(
    val novel: Novel,
    val chapter: NovelChapter,
    val source: NovelSource,
) {

    @Transient
    private val _statusFlow = MutableStateFlow(State.QUEUED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    /** Render progress 0-100 on a paragraphs-done basis (determinate ring). */
    @Transient
    private val _progressFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = _progressFlow.asStateFlow()
    var progress: Int
        get() = _progressFlow.value
        set(progress) {
            _progressFlow.value = progress
        }

    @Volatile
    var completedParagraphs: Int = 0

    @Volatile
    var cancelledForPreempt: Boolean = false

    @Transient
    var job: Job? = null

    enum class State {
        QUEUED,
        RENDERING,
        DONE,
        ERROR,
    }
}
