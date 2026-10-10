package miko.jssource

/**
 * URL helpers — ported from NovelUrlUtils. On JVM this is the identical
 * java.net.URI implementation (semantic parity with Android guaranteed);
 * iOS uses NSURL.
 */
expect fun platformResolveUrl(input: String, base: String?): String

expect fun platformGetPathname(url: String): String

expect fun platformBase64Encode(bytes: ByteArray): String

expect fun platformBase64Decode(b64: String): ByteArray
