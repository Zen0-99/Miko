package tachiyomi.domain.util

/**
 * Marker for domain models that must survive platform serialization.
 * On Android this is `java.io.Serializable` (Bundle/Intent passing);
 * on iOS it is an empty marker.
 */
expect interface PlatformSerializable

/**
 * Compose stability marker. On Android this is
 * `androidx.compose.runtime.Immutable` so the Compose compiler treats
 * annotated models as stable; on iOS it is a no-op annotation.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPE_PARAMETER,
)
@Retention(AnnotationRetention.BINARY)
expect annotation class Immutable()

/**
 * Parses this string as an `http(s)` URL and returns the normalized form,
 * or `null` when malformed. Android delegates to OkHttp; iOS delegates
 * to `NSURL`.
 */
internal expect fun String.toHttpUrlOrNull(): String?
