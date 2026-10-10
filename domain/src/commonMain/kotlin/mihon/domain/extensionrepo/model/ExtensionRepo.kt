package mihon.domain.extensionrepo.model

data class ExtensionRepo(
    val baseUrl: String,
    val name: String,
    val shortName: String?,
    val website: String,
    val signingKeyFingerprint: String,
) {
    companion object {
        private val mediumTokenSuffix =
            Regex("""\s+(anime|manga|manhwa|manhua|comic|novel|novels|books|shows|series)$""", RegexOption.IGNORE_CASE)

        /**
         * Relabel a repo name with the carrier it serves in this tab —
         * a combined repo (e.g. MikoDB) declares one index name
         * ("MikoDB Anime") but its row shows up under Shows, Manga, and
         * Books alike, so a stale medium token gets swapped for the
         * tab's label and names without one get the label appended.
         */
        fun mediumLabeledRepoName(name: String, label: String): String {
            val base = name.trim().replace(mediumTokenSuffix, "").trim().ifBlank { name.trim() }
            return "$base $label"
        }
    }
}
