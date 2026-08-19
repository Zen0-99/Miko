package eu.kanade.tachiyomi.extension.novel.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dalvik.system.PathClassLoader
import eu.kanade.domain.extension.novel.interactor.TrustNovelExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelLoadResult
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.NovelSourceFactory
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.storage.copyAndSetReadOnlyTo
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File

@SuppressLint("PackageManagerGetSignatures")
internal object NovelExtensionLoader {

    private val preferences: SourcePreferences by injectLazy()
    private val trustExtension: TrustNovelExtension by injectLazy()
    private val networkHelper: NetworkHelper by injectLazy()
    private val loadNsfwSource by lazy {
        preferences.showNsfwSource().get()
    }

    private const val EXTENSION_FEATURE = "yokai.novel.extension"
    private const val METADATA_SOURCE_CLASS = "yokai.novel.extension.class"
    private const val METADATA_SOURCE_FACTORY = "yokai.novel.extension.factory"
    private const val METADATA_NSFW = "yokai.novel.extension.nsfw"
    const val LIB_VERSION_MIN = 1.0
    const val LIB_VERSION_MAX = 2.0

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val PRIVATE_EXTENSION_EXTENSION = "ext"

    private fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "novel_exts")

    fun installPrivateExtensionFile(context: Context, file: File): Boolean {
        val extension = context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PACKAGE_FLAGS,
        )
            ?.takeIf { isPackageAnExtension(it) } ?: return false
        val currentExtension = getNovelExtensionPackageInfoFromPkgName(
            context,
            extension.packageName,
        )

        if (currentExtension != null) {
            if (PackageInfoCompat.getLongVersionCode(extension) <
                PackageInfoCompat.getLongVersionCode(currentExtension)
            ) {
                logcat(LogPriority.ERROR) { "Installed extension version is higher. Downgrading is not allowed." }
                return false
            }

            val extensionSignatures = getSignatures(extension)
            if (extensionSignatures.isNullOrEmpty()) {
                logcat(LogPriority.ERROR) { "Extension to be installed is not signed." }
                return false
            }

            if (!extensionSignatures.containsAll(getSignatures(currentExtension)!!)) {
                logcat(LogPriority.ERROR) { "Installed extension signature is not matched." }
                return false
            }
        }

        val target = File(
            getPrivateExtensionDir(context),
            "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION",
        )
        return try {
            target.delete()
            file.copyAndSetReadOnlyTo(target, overwrite = true)
            if (currentExtension != null) {
                NovelExtensionInstallReceiver.notifyReplaced(context, extension.packageName)
            } else {
                NovelExtensionInstallReceiver.notifyAdded(context, extension.packageName)
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to copy extension file." }
            target.delete()
            false
        }
    }

    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
    }

    fun loadExtensions(context: Context): List<NovelLoadResult> {
        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
            )
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { NovelExtensionInfo(packageInfo = it, isShared = true) }

        val privateExtPkgs = getPrivateExtensionDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull {
                if (it.canWrite()) {
                    it.setReadOnly()
                }
                val path = it.absolutePath
                pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
                    ?.apply { applicationInfo!!.fixBasePaths(path) }
            }
            ?.filter { isPackageAnExtension(it) }
            ?.map { NovelExtensionInfo(packageInfo = it, isShared = false) }
            ?: emptySequence()

        val extPkgs = (sharedExtPkgs + privateExtPkgs)
            .distinctBy { it.packageInfo.packageName }
            .mapNotNull { sharedPkg ->
                val privatePkg = privateExtPkgs
                    .singleOrNull { it.packageInfo.packageName == sharedPkg.packageInfo.packageName }
                selectExtensionPackage(sharedPkg, privatePkg)
            }
            .toList()

        if (extPkgs.isEmpty()) return emptyList()

        return runBlocking {
            val deferred = extPkgs.map {
                async { loadExtension(context, it) }
            }
            deferred.awaitAll()
        }
    }

    suspend fun loadExtensionFromPkgName(context: Context, pkgName: String): NovelLoadResult {
        val extensionPackage = getNovelExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            logcat(LogPriority.ERROR) { "Extension package is not found ($pkgName)" }
            return NovelLoadResult.Error
        }
        return loadExtension(context, extensionPackage)
    }

    fun getNovelExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getNovelExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }

    private fun getNovelExtensionInfoFromPkgName(context: Context, pkgName: String): NovelExtensionInfo? {
        val privateExtensionFile = File(
            getPrivateExtensionDir(context),
            "$pkgName.$PRIVATE_EXTENSION_EXTENSION",
        )
        val privatePkg = if (privateExtensionFile.isFile) {
            context.packageManager.getPackageArchiveInfo(
                privateExtensionFile.absolutePath,
                PACKAGE_FLAGS,
            )
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    it.applicationInfo!!.fixBasePaths(privateExtensionFile.absolutePath)
                    NovelExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val sharedPkg = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                .takeIf { isPackageAnExtension(it) }
                ?.let {
                    NovelExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (error: PackageManager.NameNotFoundException) {
            null
        }

        return selectExtensionPackage(sharedPkg, privatePkg)
    }

    private suspend fun loadExtension(context: Context, extensionInfo: NovelExtensionInfo): NovelLoadResult {
        val pkgManager = context.packageManager
        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo!!
        val pkgName = pkgInfo.packageName

        val extName = pkgManager.getApplicationLabel(appInfo).toString().substringAfter(
            "Tachiyomi: ",
        )
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Missing versionName for extension $extName" }
            return NovelLoadResult.Error
        }

        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            logcat(LogPriority.WARN) {
                "Lib version is $libVersion, while only versions " +
                    "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed"
            }
            return NovelLoadResult.Error
        }

        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Package $pkgName isn't signed" }
            return NovelLoadResult.Error
        } else if (!trustExtension.isTrusted(pkgInfo, signatures)) {
            val extension = NovelExtension.Untrusted(
                extName,
                pkgName,
                versionName,
                versionCode,
                libVersion,
                signatures.last(),
            )
            logcat(LogPriority.WARN) { "Extension $pkgName isn't trusted" }
            return NovelLoadResult.Untrusted(extension)
        }

        val isNsfw = try {
            appInfo.metaData.getBoolean(METADATA_NSFW)
        } catch (e: Exception) {
            appInfo.metaData.getInt(METADATA_NSFW) == 1
        }
        if (!loadNsfwSource && isNsfw) {
            logcat(LogPriority.WARN) { "NSFW extension $pkgName not allowed" }
            return NovelLoadResult.Error
        }

        // Use PathClassLoader (parent-first) so the extension shares the app's Kotlin stdlib
        // and coroutines. This is critical because NovelSourceAdapter passes the app's
        // Continuation to the extension's suspend functions and compares COROUTINE_SUSPENDED
        // — both require the same class instances. ChildFirstPathClassLoader would load the
        // extension's bundled Kotlin stdlib, creating incompatible class instances.
        val classLoader = try {
            PathClassLoader(appInfo.sourceDir, null, context.classLoader)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($pkgName)" }
            return NovelLoadResult.Error
        }

        val sources = appInfo.metaData.getString(METADATA_SOURCE_CLASS)!!
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap { className ->
                try {
                    val obj = Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance()
                    resolveSources(obj)
                } catch (e: LinkageError) {
                    try {
                        val fallBackClassLoader = ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)
                        val obj = Class.forName(className, false, fallBackClassLoader)
                            .getDeclaredConstructor().newInstance()
                        resolveSources(obj)
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($className)" }
                        return NovelLoadResult.Error
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($className)" }
                    return NovelLoadResult.Error
                }
            }

        // Inject the app's OkHttp client into any adapter-wrapped extension-lib sources.
        val okHttpClient = try {
            networkHelper.client
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Could not obtain OkHttpClient for novel sources" }
            null
        }
        if (okHttpClient != null) {
            sources.filterIsInstance<NovelSourceAdapter>().forEach { it.injectHttpClient(okHttpClient) }
        }

        // Inject the WebViewResolver for sources that need WebView interaction (countdowns, non-CF CAPTCHAs).
        val webViewResolver = try {
            NovelWebViewResolver(context)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Could not create WebViewResolver for novel sources" }
            null
        }
        if (webViewResolver != null) {
            sources.filterIsInstance<NovelSourceAdapter>().forEach { it.injectWebViewResolver(webViewResolver) }
        }

        val langs = sources.filterIsInstance<NovelCatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val extension = NovelExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            sources = sources,
            pkgFactory = appInfo.metaData.getString(METADATA_SOURCE_FACTORY),
            icon = appInfo.loadIcon(pkgManager),
            isShared = extensionInfo.isShared,
        )
        return NovelLoadResult.Success(extension)
    }

    private fun selectExtensionPackage(shared: NovelExtensionInfo?, private: NovelExtensionInfo?): NovelExtensionInfo? {
        when {
            private == null && shared != null -> return shared
            shared == null && private != null -> return private
            shared == null && private == null -> return null
        }

        return if (PackageInfoCompat.getLongVersionCode(shared!!.packageInfo) >=
            PackageInfoCompat.getLongVersionCode(private!!.packageInfo)
        ) {
            shared
        } else {
            private
        }
    }

    /**
     * Resolves an instantiated source/factory object into a list of [NovelSource].
     *
     * Handles both native fork sources (which implement [NovelSource]/[NovelSourceFactory]) and
     * extensions built against the `yokai.extension.novel.lib` library. The latter are detected by
     * class/interface name (their types are only present inside the extension APK) and wrapped in a
     * [NovelSourceAdapter].
     */
    private fun resolveSources(obj: Any): List<NovelSource> {
        return when {
            obj is NovelSource -> listOf(obj)
            obj is NovelSourceFactory -> obj.createSources()
            // Extension-lib factory: reflectively create sources and wrap each one.
            classHierarchyContains(obj.javaClass, "NovelSourceFactory") -> {
                val method = obj.javaClass.getMethod("createSources")
                @Suppress("UNCHECKED_CAST")
                val extSources = method.invoke(obj) as List<Any>
                extSources.map { NovelSourceAdapter(it) }
            }
            // Extension-lib single source.
            classHierarchyContains(obj.javaClass, "NovelSource") -> listOf(NovelSourceAdapter(obj))
            else -> throw Exception("Unknown source class type: ${obj.javaClass}")
        }
    }

    /**
     * Returns true if [clazz], any of its superclasses, or any implemented interface has a name
     * containing [namePart].
     */
    private fun classHierarchyContains(clazz: Class<*>, namePart: String): Boolean {
        var current: Class<*>? = clazz
        while (current != null) {
            if (current.name.contains(namePart)) return true
            if (current.interfaces.any { it.name.contains(namePart) }) return true
            current = current.superclass
        }
        return false
    }

    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo!!
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
            ?.map { Hash.sha256(it.toByteArray()) }
            ?.toList()
    }

    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) {
            sourceDir = apkPath
        }
        if (publicSourceDir == null) {
            publicSourceDir = apkPath
        }
    }

    private data class NovelExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}
