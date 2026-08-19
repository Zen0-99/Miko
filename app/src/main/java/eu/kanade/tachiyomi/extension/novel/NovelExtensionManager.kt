package eu.kanade.tachiyomi.extension.novel

import android.content.Context
import android.graphics.drawable.Drawable
import eu.kanade.domain.extension.novel.interactor.TrustNovelExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.novel.api.NovelExtensionApi
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelLoadResult
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionInstaller
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionLoader
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.extensionrepo.novel.interactor.CreateNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.DeleteNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

class NovelExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences = Injekt.get(),
    private val trustExtension: TrustNovelExtension = Injekt.get(),
) {

    val scope = CoroutineScope(SupervisorJob())

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val api = NovelExtensionApi()

    private val installer by lazy { NovelExtensionInstaller(context) }

    companion object {
        private const val DEFAULT_REPO_URL = "https://raw.githubusercontent.com/Zen0-99/MikoNovelSources/main"
    }

    private val iconMap = mutableMapOf<String, Drawable>()

    private val installedExtensionsMapFlow = MutableStateFlow(emptyMap<String, NovelExtension.Installed>())
    val installedExtensionsFlow = installedExtensionsMapFlow.mapExtensions(scope)

    private val availableExtensionsMapFlow = MutableStateFlow(emptyMap<String, NovelExtension.Available>())
    val availableExtensionsFlow = availableExtensionsMapFlow.mapExtensions(scope)

    private val untrustedExtensionsMapFlow = MutableStateFlow(emptyMap<String, NovelExtension.Untrusted>())
    val untrustedExtensionsFlow = untrustedExtensionsMapFlow.mapExtensions(scope)

    init {
        initExtensions()
        NovelExtensionInstallReceiver(NovelInstallationListener()).register(context)
        seedDefaultRepoIfNeeded()
    }

    /**
     * Seeds the default MikoNovelSources extension repo on first launch so that
     * available extensions and updates are detected without manual setup.
     *
     * Also migrates the repo URL if it was previously seeded with an old URL
     * (e.g. keypop3750/MikoNovelSources → Zen0-99/MikoNovelSources).
     */
    private fun seedDefaultRepoIfNeeded() {
        val seeded = preferences.novelDefaultRepoSeeded()
        scope.launch {
            try {
                val getRepo = Injekt.get<GetNovelExtensionRepo>()
                val createRepo = Injekt.get<CreateNovelExtensionRepo>()
                val deleteRepo = Injekt.get<DeleteNovelExtensionRepo>()
                val repos = getRepo.getAll()
                val oldUrl = "https://raw.githubusercontent.com/keypop3750/MikoNovelSources/main"
                val hasOldRepo = repos.any { it.baseUrl == oldUrl }
                val hasNewRepo = repos.any { it.baseUrl == DEFAULT_REPO_URL }

                android.util.Log.i("NovelExtMgr", "seedDefaultRepo: seeded=${seeded.get()}, repos=${repos.size}, hasOld=$hasOldRepo, hasNew=$hasNewRepo")
                repos.forEach { repo ->
                    android.util.Log.i("NovelExtMgr", "  existing repo: ${repo.baseUrl}")
                }

                if (!seeded.get()) {
                    // First launch — seed the default repo
                    createRepo.await(DEFAULT_REPO_URL)
                    seeded.set(true)
                    android.util.Log.i("NovelExtMgr", "Seeded default novel extension repo: $DEFAULT_REPO_URL")
                } else if (hasOldRepo && !hasNewRepo) {
                    // Migrate: delete old repo, create new one
                    android.util.Log.i("NovelExtMgr", "Migrating novel extension repo: $oldUrl -> $DEFAULT_REPO_URL")
                    deleteRepo.await(oldUrl)
                    createRepo.await(DEFAULT_REPO_URL)
                } else if (!hasNewRepo) {
                    // Seeded flag was set but repo is missing — re-seed
                    android.util.Log.i("NovelExtMgr", "Re-seeding default novel extension repo (was missing)")
                    createRepo.await(DEFAULT_REPO_URL)
                } else {
                    android.util.Log.i("NovelExtMgr", "Repo already correct, no migration needed")
                }
                // Refresh available extensions
                findAvailableExtensions()
            } catch (e: Exception) {
                android.util.Log.e("NovelExtMgr", "Failed to seed/migrate default novel extension repo", e)
            }
        }
    }

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages().isSet()

    fun getExtensionPackage(sourceId: Long): String? {
        return installedExtensionsFlow.value.find { extension ->
            extension.sources.any { it.id == sourceId }
        }
            ?.pkgName
    }

    fun getExtensionPackageAsFlow(sourceId: Long): Flow<String?> {
        return installedExtensionsFlow.map { extensions ->
            extensions.find { extension ->
                extension.sources.any { it.id == sourceId }
            }
                ?.pkgName
        }
    }

    fun isNsfwForSource(sourceId: Long): Boolean {
        return installedExtensionsFlow.value.find { extension ->
            extension.sources.any { it.id == sourceId }
        }?.isNsfw ?: false
    }

    fun isNsfwForSourceAsFlow(sourceId: Long): Flow<Boolean> {
        return installedExtensionsFlow.map { extensions ->
            extensions.find { extension ->
                extension.sources.any { it.id == sourceId }
            }?.isNsfw ?: false
        }
    }

    fun getAppIconForSource(sourceId: Long): Drawable? {
        val pkgName = installedExtensionsMapFlow.value.values
            .find { ext ->
                ext.sources.any { it.id == sourceId }
            }
            ?.pkgName
            ?: return null

        return iconMap[pkgName] ?: iconMap.getOrPut(pkgName) {
            NovelExtensionLoader.getNovelExtensionPackageInfoFromPkgName(context, pkgName)!!.applicationInfo!!
                .loadIcon(context.packageManager)
        }
    }

    private var availableExtensionsSourcesData: Map<Long, StubNovelSource> = emptyMap()

    private fun setupAvailableExtensionsSourcesDataMap(extensions: List<NovelExtension.Available>) {
        if (extensions.isEmpty()) return
        availableExtensionsSourcesData = extensions
            .flatMap { ext -> ext.sources.map { StubNovelSource(id = it.id, lang = ext.lang, name = ext.name) } }
            .associateBy { it.id }
    }

    fun getSourceData(id: Long) = availableExtensionsSourcesData[id]

    private fun initExtensions() {
        val extensions = NovelExtensionLoader.loadExtensions(context)

        installedExtensionsMapFlow.value = extensions
            .filterIsInstance<NovelLoadResult.Success>()
            .associate { it.extension.pkgName to it.extension }

        untrustedExtensionsMapFlow.value = extensions
            .filterIsInstance<NovelLoadResult.Untrusted>()
            .associate { it.extension.pkgName to it.extension }

        _isInitialized.value = true
    }

    suspend fun findAvailableExtensions() {
        val extensions: List<NovelExtension.Available> = try {
            api.findExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            withUIContext { context.toast(MR.strings.extension_api_error) }
            emptyList()
        }

        enableAdditionalSubLanguages(extensions)

        availableExtensionsMapFlow.value = extensions.associateBy { it.pkgName }
        updatedInstalledExtensionsStatuses(extensions)
        setupAvailableExtensionsSourcesDataMap(extensions)
    }

    private fun enableAdditionalSubLanguages(extensions: List<NovelExtension.Available>) {
        if (subLanguagesEnabledOnFirstRun || extensions.isEmpty()) {
            return
        }

        val availableLanguages = extensions
            .flatMap(NovelExtension.Available::sources)
            .distinctBy { it.lang }
            .map { it.lang }

        val deviceLanguage = Locale.getDefault().language
        val defaultLanguages = preferences.enabledLanguages().defaultValue()
        val languagesToEnable = availableLanguages.filter {
            it != deviceLanguage && it.startsWith(deviceLanguage)
        }

        preferences.enabledLanguages().set(defaultLanguages + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    private fun updatedInstalledExtensionsStatuses(
        availableExtensions: List<NovelExtension.Available>,
    ) {
        if (availableExtensions.isEmpty()) {
            preferences.novelExtensionUpdatesCount().set(0)
            return
        }

        val installedExtensionsMap = installedExtensionsMapFlow.value.toMutableMap()
        var changed = false

        for ((pkgName, extension) in installedExtensionsMap) {
            val availableExt = availableExtensions.find { it.pkgName == pkgName }

            if (availableExt == null && !extension.isObsolete) {
                installedExtensionsMap[pkgName] = extension.copy(isObsolete = true)
                changed = true
            } else if (availableExt != null) {
                val hasUpdate = extension.updateExists(availableExt)
                if (extension.hasUpdate != hasUpdate) {
                    installedExtensionsMap[pkgName] = extension.copy(
                        hasUpdate = hasUpdate,
                        repoUrl = availableExt.repoUrl,
                    )
                } else {
                    installedExtensionsMap[pkgName] = extension.copy(
                        repoUrl = availableExt.repoUrl,
                    )
                }
                changed = true
            }
        }
        if (changed) {
            installedExtensionsMapFlow.value = installedExtensionsMap
        }
        updatePendingUpdatesCount()
    }

    fun installExtension(extension: NovelExtension.Available): Flow<InstallStep> {
        return installer.downloadAndInstall(api.getApkUrl(extension), extension)
    }

    fun updateExtension(extension: NovelExtension.Installed): Flow<InstallStep> {
        val availableExt = availableExtensionsMapFlow.value[extension.pkgName] ?: return emptyFlow()
        return installExtension(availableExt)
    }

    fun cancelInstallUpdateExtension(extension: NovelExtension) {
        installer.cancelInstall(extension.pkgName)
    }

    fun setInstalling(downloadId: Long) {
        installer.updateInstallStep(downloadId, InstallStep.Installing)
    }

    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        installer.updateInstallStep(downloadId, step)
    }

    fun uninstallExtension(extension: NovelExtension) {
        installer.uninstallApk(extension.pkgName)
    }

    suspend fun trust(extension: NovelExtension.Untrusted) {
        untrustedExtensionsMapFlow.value[extension.pkgName] ?: return

        trustExtension.trust(extension.pkgName, extension.versionCode, extension.signatureHash)

        untrustedExtensionsMapFlow.value -= extension.pkgName

        NovelExtensionLoader.loadExtensionFromPkgName(context, extension.pkgName)
            .let { it as? NovelLoadResult.Success }
            ?.let { registerNewExtension(it.extension) }
    }

    private fun registerNewExtension(extension: NovelExtension.Installed) {
        installedExtensionsMapFlow.value += extension
    }

    private fun registerUpdatedExtension(extension: NovelExtension.Installed) {
        installedExtensionsMapFlow.value += extension
    }

    private fun unregisterExtension(pkgName: String) {
        installedExtensionsMapFlow.value -= pkgName
        untrustedExtensionsMapFlow.value -= pkgName
    }

    private inner class NovelInstallationListener : NovelExtensionInstallReceiver.Listener {

        override fun onExtensionInstalled(extension: NovelExtension.Installed) {
            registerNewExtension(extension.withUpdateCheck())
            updatePendingUpdatesCount()
        }

        override fun onExtensionUpdated(extension: NovelExtension.Installed) {
            registerUpdatedExtension(extension.withUpdateCheck())
            updatePendingUpdatesCount()
        }

        override fun onExtensionUntrusted(extension: NovelExtension.Untrusted) {
            installedExtensionsMapFlow.value -= extension.pkgName
            untrustedExtensionsMapFlow.value += extension
            updatePendingUpdatesCount()
        }

        override fun onPackageUninstalled(pkgName: String) {
            unregisterExtension(pkgName)
            updatePendingUpdatesCount()
        }
    }

    private fun NovelExtension.Installed.withUpdateCheck(): NovelExtension.Installed {
        return if (updateExists()) {
            copy(hasUpdate = true)
        } else {
            this
        }
    }

    private fun NovelExtension.Installed.updateExists(
        availableExtension: NovelExtension.Available? = null,
    ): Boolean {
        val availableExt = availableExtension
            ?: availableExtensionsMapFlow.value[pkgName]
            ?: return false

        return (availableExt.versionCode > versionCode || availableExt.libVersion > libVersion)
    }

    private fun updatePendingUpdatesCount() {
        val pendingUpdateCount = installedExtensionsMapFlow.value.values.count { it.hasUpdate }
        preferences.novelExtensionUpdatesCount().set(pendingUpdateCount)
        if (pendingUpdateCount == 0) {
            ExtensionUpdateNotifier(context).dismiss()
        }
    }

    private operator fun <T : NovelExtension> Map<String, T>.plus(extension: T) = plus(extension.pkgName to extension)

    private fun <T : NovelExtension> StateFlow<Map<String, T>>.mapExtensions(
        scope: CoroutineScope,
    ): StateFlow<List<T>> {
        return map { it.values.toList() }.stateIn(scope, SharingStarted.Lazily, value.values.toList())
    }
}
