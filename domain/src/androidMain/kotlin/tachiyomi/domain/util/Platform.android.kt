package tachiyomi.domain.util

import java.io.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull as okhttpToHttpUrlOrNull

actual interface PlatformSerializable : Serializable

actual typealias Immutable = androidx.compose.runtime.Immutable

internal actual fun String.toHttpUrlOrNull(): String? = okhttpToHttpUrlOrNull()?.toString()
