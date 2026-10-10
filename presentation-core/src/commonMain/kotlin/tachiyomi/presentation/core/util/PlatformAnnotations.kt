package tachiyomi.presentation.core.util

/**
 * Range lint marker. On Android this is `androidx.annotation.IntRange`;
 * on iOS it is a plain annotation (lint metadata only).
 */
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.LOCAL_VARIABLE,
)
@Retention(AnnotationRetention.BINARY)
expect annotation class PlatformIntRange(val from: Long, val to: Long)

/**
 * Light/dark preview pair marker. On Android this is
 * `androidx.compose.ui.tooling.preview.PreviewLightDark`; on iOS it is a
 * no-op annotation (CMP previews are declared with plain `@Preview`).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
expect annotation class PreviewLightDark()

/**
 * Compose tooling [Preview]. Actuals map to the androidx preview annotation on Android and a
 * no-op annotation on iOS, where tooling previews do not exist.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
expect annotation class Preview()
