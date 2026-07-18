package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.IncognitoPolicy

/**
 * Shared logic for resolving incognito state across all media types.
 *
 * Resolution order:
 * 1. Global incognito always wins (overrides everything)
 * 2. If policy is NSFW_AUTO and extension is NSFW → auto-incognito
 * 3. Otherwise, check per-extension manual toggle
 */
internal object IncognitoStateLogic {

    fun resolve(
        globalIncognito: Boolean,
        policy: IncognitoPolicy,
        isNsfw: Boolean,
        inExtensionSet: Boolean,
    ): Boolean {
        if (globalIncognito) return true
        if (policy == IncognitoPolicy.NSFW_AUTO && isNsfw) return true
        return inExtensionSet
    }
}
