package eu.kanade.domain.source.model

/**
 * Policy for automatic incognito mode.
 *
 * - [MANUAL_ONLY]: Incognito is only enabled by the global toggle or per-extension toggles.
 * - [NSFW_AUTO]: NSFW-flagged extensions are automatically incognito, in addition to manual toggles.
 */
enum class IncognitoPolicy(val displayName: String) {
    MANUAL_ONLY("Manual only"),
    NSFW_AUTO("Automatic (NSFW)"),
}
