package miko.ui.reader

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

/** Mirror of app's NovelReaderEnums — same names so getEnum keys converge. */
enum class TextAlignment(val titleRes: StringResource) {
    LEFT(MR.strings.alignment_left),
    CENTER(MR.strings.alignment_center),
    JUSTIFY(MR.strings.alignment_justify),
    RIGHT(MR.strings.alignment_right),
}

/** Fixed reader backgrounds + SMART_THEME (Material background). */
enum class NovelReaderBackgroundColor(val titleRes: StringResource) {
    WHITE(MR.strings.white_background),
    BLACK(MR.strings.black_background),
    SMART_THEME(MR.strings.theme_system),
    GRAY(MR.strings.gray_background),
}
