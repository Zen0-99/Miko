package eu.kanade.tachiyomi.extension.novel.util

import eu.kanade.tachiyomi.novelsource.model.NovelChapterPage
import eu.kanade.tachiyomi.novelsource.model.NovelComment
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat
import java.lang.reflect.Field
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

/**
 * Adapter that wraps an extension built against the `yokai.extension.novel.lib` library and
 * exposes it through the fork's own [NovelHttpSource] contract.
 *
 * The wrapped [source] classes live inside the extension APK and are therefore only available
 * through reflection (they are not on the app's compile classpath). This bridge reads the
 * source's properties, invokes its suspend functions with a hand-rolled [Continuation], and
 * converts the returned library model objects into the fork's [SNovel] / [SNovelChapter] /
 * [NovelsPage] models.
 */
class NovelSourceAdapter(private val source: Any) : NovelHttpSource() {

    private val tag = "[NOVEL_ADAPTER]"

    /**
     * The COROUTINE_SUSPENDED marker from the extension's own classloader.
     *
     * When [ChildFirstPathClassLoader] is used and the extension bundles its own Kotlin stdlib,
     * the extension's COROUTINE_SUSPENDED is a different object instance than the app's.
     * Comparing `result === COROUTINE_SUSPENDED` (app's) would fail when the extension's
     * suspend function suspends, causing the adapter to treat the marker as a direct result
     * (empty list) instead of waiting for the continuation callback.
     */
    private val extensionCoroutineSuspended: Any? by lazy {
        val cl = source.javaClass.classLoader
        for (className in listOf(
            "kotlin.coroutines.intrinsics.IntrinsicsKt",
            "kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt",
        )) {
            try {
                val clazz = Class.forName(className, false, cl)
                try {
                    val field = clazz.getDeclaredField("COROUTINE_SUSPENDED")
                    field.isAccessible = true
                    return@lazy field.get(null)
                } catch (_: NoSuchFieldException) {}
                for (methodName in listOf("getCoroutineSuspended", "getCOROUTINE_SUSPENDED")) {
                    try {
                        val method = clazz.getDeclaredMethod(methodName)
                        method.isAccessible = true
                        return@lazy method.invoke(null)
                    } catch (_: NoSuchMethodException) {}
                }
            } catch (_: ClassNotFoundException) {}
        }
        COROUTINE_SUSPENDED
    }

    override val id: Long
        get() {
            val v = getProp(source, "id")
            return when (v) {
                is Long -> v
                is Int -> v.toLong()
                is Number -> v.toLong()
                else -> 0L
            }
        }

    override val name: String
        get() = getProp(source, "name") as? String ?: "Unknown"

    override val lang: String
        get() = getProp(source, "lang") as? String ?: "en"

    override val baseUrl: String
        get() = getProp(source, "baseUrl") as? String ?: ""

    private val hasMainPage: Boolean
        get() = getProp(source, "hasMainPage") as? Boolean ?: false

    override val supportsLatest: Boolean
        get() = hasMainPage

    /**
     * Check if the wrapped extension source supports chapter comments.
     * Reads `supportsComments` from the extension's capabilities, or falls back
     * to checking if `getChapterComments` is overridden (not the default emptyList).
     */
    override val supportsComments: Boolean
        get() {
            // Try reading the capabilities object's supportsComments field
            val caps = getProp(source, "capabilities") ?: return false
            return getProp(caps, "supportsComments") as? Boolean ?: false
        }

    /**
     * Minimum delay between requests in milliseconds, read from the extension's
     * `rateLimitMs` property. Returns 0 if the extension doesn't define one.
     */
    val rateLimitMs: Long
        get() = (getProp(source, "rateLimitMs") as? Number)?.toLong() ?: 0L

    override val isRateLimited: Boolean
        get() = rateLimitMs > 0L

    init {
        logcat(LogPriority.DEBUG) { "$tag Created adapter for ${source.javaClass.name}" }
        logcat(LogPriority.DEBUG) { "$tag Source name=$name lang=$lang baseUrl=$baseUrl hasMainPage=$hasMainPage" }
    }

    /**
     * Inject the app's [OkHttpClient] into the wrapped source's `client` field (declared as a
     * `lateinit var` on the library's base `NovelSource`). Walks the class hierarchy to find it.
     */
    fun injectHttpClient(client: OkHttpClient) {
        try {
            var clientField: Field? = null
            var currentClass: Class<*>? = source.javaClass
            while (currentClass != null && clientField == null) {
                clientField = try {
                    currentClass.getDeclaredField("client")
                } catch (e: NoSuchFieldException) {
                    currentClass = currentClass.superclass
                    null
                }
            }
            if (clientField != null) {
                clientField.isAccessible = true
                clientField.set(source, client)
                logcat(LogPriority.DEBUG) { "$tag Injected OkHttpClient into ${source.javaClass.name}" }
            } else {
                logcat(LogPriority.WARN) { "$tag No 'client' field found on ${source.javaClass.name}" }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "$tag Failed to inject OkHttpClient into ${source.javaClass.name}" }
        }
    }

    /**
     * Inject a WebView resolver proxy into the wrapped source's `webViewResolver` field
     * (declared as a `var` on the library's base `NovelSource`). Creates a dynamic proxy
     * that implements the extension library's `WebViewResolver` interface via reflection,
     * since that interface is not on the app's compile classpath.
     */
    fun injectWebViewResolver(resolver: NovelWebViewResolver) {
        try {
            var resolverField: Field? = null
            var currentClass: Class<*>? = source.javaClass
            while (currentClass != null && resolverField == null) {
                resolverField = try {
                    currentClass.getDeclaredField("webViewResolver")
                } catch (e: NoSuchFieldException) {
                    currentClass = currentClass.superclass
                    null
                }
            }
            if (resolverField != null) {
                resolverField.isAccessible = true
                val proxy = NovelWebViewResolver.createProxy(resolver, source.javaClass.classLoader)
                resolverField.set(source, proxy)
                logcat(LogPriority.DEBUG) { "$tag Injected WebViewResolver into ${source.javaClass.name}" }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "$tag Failed to inject WebViewResolver into ${source.javaClass.name}" }
        }
    }

    override suspend fun getNovelDetails(novel: SNovel): SNovel = withContext(Dispatchers.IO) {
        logcat(LogPriority.DEBUG) { "$tag getNovelDetails url=${novel.url}" }
        try {
            val result = invokeSuspendMethodWithString("getNovelDetails", novel.url)
            if (result == null) {
                logcat(LogPriority.WARN) { "$tag getNovelDetails returned null for ${novel.url}" }
                novel.apply { initialized = false }
            } else {
                logcat(LogPriority.DEBUG) { "$tag getNovelDetails got result type=${result.javaClass.name}" }
                toSNovelDetails(result, novel.url)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "$tag getNovelDetails failed for ${novel.url}" }
            novel.apply { initialized = false }
        }
    }

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> = withContext(Dispatchers.IO) {
        logcat(LogPriority.DEBUG) { "$tag getChapterList url=${novel.url}" }
        try {
            val result = invokeSuspendListMethodWithString("getChapterList", novel.url)
            logcat(LogPriority.DEBUG) { "$tag getChapterList got ${result.size} chapters" }
            result.filterNotNull().map { toSNovelChapter(it) }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "$tag getChapterList failed for ${novel.url}" }
            emptyList()
        }
    }

    override suspend fun getChapterListPage(novel: SNovel, page: Int): NovelChapterPage = withContext(Dispatchers.IO) {
        logcat(LogPriority.DEBUG) { "$tag getChapterListPage url=${novel.url} page=$page" }
        try {
            // Check if the extension has a getChapterListPage method
            val hasMethod = source.javaClass.methods.any { m ->
                m.name == "getChapterListPage" &&
                    m.parameterCount == 3 &&
                    m.parameterTypes[0] == String::class.java &&
                    (m.parameterTypes[1] == Int::class.javaPrimitiveType || m.parameterTypes[1] == Int::class.java)
            }
            if (hasMethod) {
                val result = invokeSuspendMethodWithStringInt("getChapterListPage", novel.url, page)
                if (result != null) {
                    val chapters = (getProp(result, "chapters") as? List<*>)?.filterNotNull()?.map { toSNovelChapter(it) } ?: emptyList()
                    val totalPages = (getProp(result, "totalPages") as? Number)?.toInt() ?: 1
                    val currentPage = (getProp(result, "currentPage") as? Number)?.toInt() ?: page
                    logcat(LogPriority.DEBUG) { "$tag getChapterListPage got ${chapters.size} chapters, totalPages=$totalPages" }
                    NovelChapterPage(chapters, totalPages, currentPage)
                } else {
                    logcat(LogPriority.DEBUG) { "$tag getChapterListPage returned null, falling back" }
                    super.getChapterListPage(novel, page)
                }
            } else {
                logcat(LogPriority.DEBUG) { "$tag getChapterListPage not supported, falling back to getChapterList" }
                super.getChapterListPage(novel, page)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "$tag getChapterListPage failed, falling back to getChapterList" }
            super.getChapterListPage(novel, page)
        }
    }

    override suspend fun getLatestChapters(novel: SNovel, existingCount: Int): List<SNovelChapter> = withContext(Dispatchers.IO) {
        logcat(LogPriority.DEBUG) { "$tag getLatestChapters url=${novel.url} existingCount=$existingCount" }
        try {
            // Log all methods with this name for debugging
            source.javaClass.methods.filter { it.name == "getLatestChapters" }.forEach { m ->
                logcat(LogPriority.DEBUG) {
                    "$tag   Found: ${m.name}(${m.parameterTypes.joinToString(", ") { it.simpleName }}) paramCount=${m.parameterCount}"
                }
            }

            // Check if the extension has a getLatestChapters method
            val hasMethod = source.javaClass.methods.any { m ->
                m.name == "getLatestChapters" &&
                    (m.parameterCount == 3 || m.parameterCount == 2)
            }
            if (hasMethod) {
                val result = invokeSuspendListMethodWithStringInt("getLatestChapters", novel.url, existingCount)
                logcat(LogPriority.DEBUG) { "$tag getLatestChapters got ${result.size} chapters" }
                result.filterNotNull().map { toSNovelChapter(it) }
            } else {
                // Fall back to full chapter list
                logcat(LogPriority.DEBUG) { "$tag getLatestChapters not supported, falling back to getChapterList" }
                getChapterList(novel)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "$tag getLatestChapters failed for ${novel.url}, falling back to getChapterList" }
            getChapterList(novel)
        }
    }

    override suspend fun getChapterText(chapter: SNovelChapter): String = withContext(Dispatchers.IO) {
        logcat(LogPriority.DEBUG) { "$tag getChapterText url=${chapter.url}" }
        try {
            when (val result = invokeSuspendMethodWithString("getChapterContent", chapter.url)) {
                is String -> result
                null -> ""
                else -> getProp(result, "content") as? String ?: result.toString()
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "$tag getChapterText failed for ${chapter.url}" }
            ""
        }
    }

    override suspend fun getChapterComments(chapter: SNovelChapter): List<NovelComment> = withContext(Dispatchers.IO) {
        logcat(LogPriority.DEBUG) { "$tag getChapterComments url=${chapter.url}" }
        try {
            val result = invokeSuspendListMethodWithString("getChapterComments", chapter.url)
            result.filterNotNull().map { toNovelComment(it) }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "$tag getChapterComments failed for ${chapter.url}" }
            emptyList()
        }
    }

    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage =
        withContext(Dispatchers.IO) {
            logcat(LogPriority.DEBUG) { "$tag getSearchNovels query='$query' page=$page" }
            try {
                val result = invokeSuspendListMethodWithStringInt("search", query, page)
                logcat(LogPriority.DEBUG) { "$tag getSearchNovels got ${result.size} results" }
                NovelsPage(result.filterNotNull().map { toSNovelSearch(it) }, hasNextPage = result.isNotEmpty())
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "$tag getSearchNovels failed for query='$query'" }
                NovelsPage(emptyList(), hasNextPage = false)
            }
        }

    override suspend fun getPopularNovels(page: Int): NovelsPage = withContext(Dispatchers.IO) {
        logcat(LogPriority.DEBUG) { "$tag getPopularNovels page=$page hasMainPage=$hasMainPage" }
        try {
            val result = invokeSuspendListMethodWithInt("getPopularNovels", page)
            logcat(LogPriority.DEBUG) { "$tag getPopularNovels got ${result.size} results" }
            NovelsPage(result.filterNotNull().map { toSNovelSearch(it) }, hasNextPage = result.isNotEmpty())
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "$tag getPopularNovels failed for $name page=$page" }
            NovelsPage(emptyList(), hasNextPage = false)
        }
    }

    override suspend fun getLatestUpdates(page: Int): NovelsPage = withContext(Dispatchers.IO) {
        logcat(LogPriority.DEBUG) { "$tag getLatestUpdates page=$page hasMainPage=$hasMainPage" }
        try {
            val result = invokeSuspendListMethodWithInt("getLatestUpdates", page)
            logcat(LogPriority.DEBUG) { "$tag getLatestUpdates got ${result.size} results" }
            NovelsPage(result.filterNotNull().map { toSNovelSearch(it) }, hasNextPage = result.isNotEmpty())
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "$tag getLatestUpdates failed for $name page=$page" }
            NovelsPage(emptyList(), hasNextPage = false)
        }
    }

    override fun getFilterList(): NovelFilterList = NovelFilterList()

    override fun toString() = "$name (${lang.uppercase()})"

    // ===== NovelHttpSource abstract stubs =====
    // These are unused because the public entry points above are overridden to delegate
    // reflectively to the extension source. They exist only to satisfy the abstract class contract.

    override fun popularNovelsRequest(page: Int): Request =
        throw UnsupportedOperationException("Delegating adapter does not build requests")

    override fun popularNovelsParse(response: Response): NovelsPage =
        throw UnsupportedOperationException("Delegating adapter does not parse responses")

    override fun searchNovelsRequest(page: Int, query: String, filters: NovelFilterList): Request =
        throw UnsupportedOperationException("Delegating adapter does not build requests")

    override fun searchNovelsParse(response: Response): NovelsPage =
        throw UnsupportedOperationException("Delegating adapter does not parse responses")

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Delegating adapter does not build requests")

    override fun latestUpdatesParse(response: Response): NovelsPage =
        throw UnsupportedOperationException("Delegating adapter does not parse responses")

    override fun novelDetailsParse(response: Response): SNovel =
        throw UnsupportedOperationException("Delegating adapter does not parse responses")

    override fun chapterListParse(response: Response): List<SNovelChapter> =
        throw UnsupportedOperationException("Delegating adapter does not parse responses")

    override fun chapterTextParse(response: Response): String =
        throw UnsupportedOperationException("Delegating adapter does not parse responses")

    // ===== Model converters =====

    private fun toSNovelSearch(obj: Any): SNovel = SNovel.create().apply {
        url = getProp(obj, "url") as? String ?: ""
        title = getProp(obj, "title") as? String ?: ""
        author = getProp(obj, "author") as? String
        thumbnail_url = getProp(obj, "coverUrl") as? String
        status = mapStatus(getProp(obj, "status"))
    }

    private fun toSNovelDetails(obj: Any, fallbackUrl: String): SNovel = SNovel.create().apply {
        url = (getProp(obj, "url") as? String).takeUnless { it.isNullOrEmpty() } ?: fallbackUrl
        title = getProp(obj, "title") as? String ?: ""
        author = getProp(obj, "author") as? String
        artist = getProp(obj, "artist") as? String
        description = getProp(obj, "description") as? String
        thumbnail_url = getProp(obj, "coverUrl") as? String
        val genres = (getProp(obj, "genres") as? List<*>)?.filterNotNull()?.map { it.toString() }
        if (!genres.isNullOrEmpty()) genre = genres.joinToString(", ")
        status = mapStatus(getProp(obj, "status"))
        initialized = true
    }

    private fun toSNovelChapter(obj: Any): SNovelChapter = SNovelChapter.create().apply {
        url = getProp(obj, "url") as? String ?: ""
        name = getProp(obj, "name") as? String ?: ""
        date_upload = readDateUpload(obj)
        chapter_number = getProp(obj, "chapterNumber") as? Float ?: -1f
        scanlator = getProp(obj, "scanlator") as? String
    }

    /**
     * Convert an extension NovelComment object to the fork's [NovelComment] model.
     * Handles nested replies recursively.
     */
    private fun toNovelComment(obj: Any): NovelComment {
        val repliesList = (getProp(obj, "replies") as? List<*>)?.filterNotNull()?.map { toNovelComment(it) } ?: emptyList()
        val dateLong = (getProp(obj, "date") as? Number)?.toLong() ?: 0L
        return NovelComment(
            id = getProp(obj, "id")?.toString() ?: "",
            userName = getProp(obj, "userName") as? String ?: "Unknown",
            avatarUrl = getProp(obj, "avatarUrl") as? String,
            content = getProp(obj, "content") as? String ?: "",
            likes = (getProp(obj, "likes") as? Number)?.toInt() ?: 0,
            replyCount = (getProp(obj, "replyCount") as? Number)?.toInt() ?: repliesList.size,
            date = dateLong,
            replies = repliesList,
        )
    }

    private fun readDateUpload(obj: Any): Long {
        val asLong = getProp(obj, "dateUpload") as? Long
            ?: getProp(obj, "date_upload") as? Long
        if (asLong != null) return asLong

        val asNumber = getProp(obj, "dateUpload") as? Number
            ?: getProp(obj, "date_upload") as? Number
        if (asNumber != null) return asNumber.toLong()

        val asString = getProp(obj, "dateUpload") as? String
            ?: getProp(obj, "date_upload") as? String
        if (!asString.isNullOrBlank()) {
            return parseDateString(asString)
        }

        val asDate = getProp(obj, "dateUpload") as? java.util.Date
            ?: getProp(obj, "date_upload") as? java.util.Date
        return asDate?.time ?: 0L
    }

    private fun parseDateString(dateStr: String): Long {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "MMM dd, yyyy",
            "dd MMM yyyy",
        )
        for (format in formats) {
            runCatching {
                java.text.SimpleDateFormat(format, java.util.Locale.US).parse(dateStr)?.time
            }.getOrNull()?.let { return it }
        }
        return 0L
    }

    private fun mapStatus(statusObj: Any?): Int {
        val statusName = (statusObj as? Enum<*>)?.name ?: (getProp(statusObj, "name") as? String)
        return when (statusName) {
            "ONGOING" -> SNovel.ONGOING
            "COMPLETED" -> SNovel.COMPLETED
            "LICENSED" -> SNovel.LICENSED
            "PUBLISHING_FINISHED" -> SNovel.PUBLISHING_FINISHED
            "CANCELLED" -> SNovel.CANCELLED
            "ON_HIATUS" -> SNovel.ON_HIATUS
            else -> SNovel.UNKNOWN
        }
    }

    // ===== Reflection helpers =====

    /**
     * Reads a Kotlin property from [obj] by trying a raw field first (walking the class hierarchy),
     * then falling back to the generated getter. This matches the Miko reference implementation
     * and is more reliable for extension-loaded classes where getters may not be found via [getMethod].
     */
    private fun getProp(obj: Any?, name: String): Any? {
        if (obj == null) return null

        // Try field first, walking up the class hierarchy
        var currentClass: Class<*>? = obj.javaClass
        while (currentClass != null) {
            try {
                val field = currentClass.getDeclaredField(name)
                field.isAccessible = true
                return field.get(obj)
            } catch (e: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }

        // Fall back to getter method
        return try {
            val getter = "get" + name.replaceFirstChar { it.uppercase() }
            obj.javaClass.getMethod(getter).invoke(obj)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Invokes a suspend function with a single Int param that returns a List.
     * Used for getPopularNovels(page: Int) and getLatestUpdates(page: Int).
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun invokeSuspendListMethodWithInt(methodName: String, page: Int): List<Any> {
        return suspendCancellableCoroutine { cont ->
            try {
                source.javaClass.methods.filter { it.name == methodName }.forEach { m ->
                    logcat(LogPriority.DEBUG) {
                        "$tag   Found: ${m.name}(${m.parameterTypes.joinToString(", ") { it.simpleName }}) paramCount=${m.parameterCount}"
                    }
                }

                val method = source.javaClass.methods.find { m ->
                    m.name == methodName &&
                        m.parameterCount == 2 &&
                        (m.parameterTypes[0] == Int::class.javaPrimitiveType || m.parameterTypes[0] == Int::class.java) &&
                        m.parameterTypes[1].name.contains("Continuation")
                }

                if (method == null) {
                    logcat(LogPriority.WARN) { "$tag Could not find suspend method $methodName(int, Continuation) for $name" }
                    val anyMethod = source.javaClass.methods.find { it.name == methodName }
                    if (anyMethod != null) {
                        logcat(LogPriority.DEBUG) {
                            "$tag Found method with name $methodName: ${anyMethod.parameterTypes.joinToString(", ") { it.name }}"
                        }
                    }
                    cont.resume(emptyList())
                    return@suspendCancellableCoroutine
                }

                logcat(LogPriority.DEBUG) { "$tag Invoking $methodName($page, Continuation)" }

                // Check if HTTP client was injected
                try {
                    var clientField: Field? = null
                    var currentClass: Class<*>? = source.javaClass
                    while (currentClass != null && clientField == null) {
                        try {
                            clientField = currentClass.getDeclaredField("client")
                        } catch (e: NoSuchFieldException) {
                            currentClass = currentClass.superclass
                        }
                    }
                    if (clientField != null) {
                        clientField.isAccessible = true
                        val client = clientField.get(source)
                        logcat(LogPriority.DEBUG) {
                            "$tag HTTP client check: ${if (client != null) "INITIALIZED" else "NULL - NOT INJECTED!"}"
                        }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN) { "$tag Error checking HTTP client: ${e.message}" }
                }

                val continuation = object : Continuation<Any?> {
                    override val context: CoroutineContext = cont.context
                    override fun resumeWith(result: Result<Any?>) {
                        logcat(LogPriority.DEBUG) { "$tag $methodName continuation.resumeWith called" }
                        result.fold(
                            onSuccess = { value ->
                                logcat(LogPriority.DEBUG) {
                                    "$tag $methodName success, value type: ${value?.javaClass?.name}"
                                }
                                val list = value as? List<Any> ?: emptyList()
                                logcat(LogPriority.DEBUG) { "$tag $methodName completed with ${list.size} items" }
                                if (!cont.isCompleted) cont.resume(list)
                            },
                            onFailure = { e ->
                                logcat(LogPriority.ERROR, e) { "$tag $methodName failed in continuation" }
                                if (!cont.isCompleted) cont.resume(emptyList())
                            },
                        )
                    }
                }

                val result = method.invoke(source, page, continuation)
                logcat(LogPriority.DEBUG) {
                    "$tag $methodName invoke returned: ${result?.javaClass?.name}, isSuspended=${result === extensionCoroutineSuspended}"
                }

                if (result === extensionCoroutineSuspended) {
                    logcat(LogPriority.DEBUG) { "$tag $methodName suspended, waiting for continuation callback" }
                } else {
                    val list = result as? List<Any> ?: emptyList()
                    logcat(LogPriority.DEBUG) { "$tag $methodName returned immediately with ${list.size} items" }
                    if (!cont.isCompleted) cont.resume(list)
                }
            } catch (e: java.lang.reflect.InvocationTargetException) {
                val actual = e.targetException ?: e.cause ?: e
                logcat(LogPriority.ERROR, actual) {
                    "$tag InvocationTargetException in $methodName: ${actual.message}"
                }
                logcat(LogPriority.ERROR) { "$tag Full stack trace: ${actual.stackTraceToString()}" }
                if (!cont.isCompleted) cont.resume(emptyList())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "$tag Error invoking $methodName: ${e.message}" }
                logcat(LogPriority.ERROR) { "$tag Full stack trace: ${e.stackTraceToString()}" }
                if (!cont.isCompleted) cont.resume(emptyList())
            }
        }
    }

    /**
     * Invokes a suspend function with (String, Int) params that returns a List.
     * Used for search(query: String, page: Int).
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun invokeSuspendListMethodWithStringInt(methodName: String, arg1: String, arg2: Int): List<Any> {
        return suspendCancellableCoroutine { cont ->
            try {
                val method = source.javaClass.methods.find { m ->
                    m.name == methodName &&
                        m.parameterCount == 3 &&
                        m.parameterTypes[0] == String::class.java &&
                        (m.parameterTypes[1] == Int::class.javaPrimitiveType || m.parameterTypes[1] == Int::class.java) &&
                        m.parameterTypes[2].name.contains("Continuation")
                }

                if (method == null) {
                    // Try regular method without Continuation
                    val regularMethod = try {
                        source.javaClass.getMethod(methodName, String::class.java, Int::class.javaPrimitiveType)
                    } catch (e: NoSuchMethodException) {
                        try {
                            source.javaClass.getMethod(methodName, String::class.java, Int::class.java)
                        } catch (e2: NoSuchMethodException) {
                            logcat(LogPriority.WARN) { "$tag Could not find method $methodName(String, Int)" }
                            cont.resume(emptyList())
                            return@suspendCancellableCoroutine
                        }
                    }
                    logcat(LogPriority.DEBUG) { "$tag Invoking regular method $methodName(String, Int)" }
                    val result = regularMethod.invoke(source, arg1, arg2) as? List<Any> ?: emptyList()
                    cont.resume(result)
                    return@suspendCancellableCoroutine
                }

                logcat(LogPriority.DEBUG) { "$tag Invoking suspend method $methodName(String, Int, Continuation)" }

                val continuation = object : Continuation<Any?> {
                    override val context: CoroutineContext = cont.context
                    override fun resumeWith(result: Result<Any?>) {
                        result.fold(
                            onSuccess = { value ->
                                val list = value as? List<Any> ?: emptyList()
                                logcat(LogPriority.DEBUG) { "$tag $methodName completed with ${list.size} items" }
                                if (!cont.isCompleted) cont.resume(list)
                            },
                            onFailure = { e ->
                                logcat(LogPriority.ERROR, e) { "$tag $methodName failed in continuation" }
                                if (!cont.isCompleted) cont.resume(emptyList())
                            },
                        )
                    }
                }

                val result = method.invoke(source, arg1, arg2, continuation)
                if (result === extensionCoroutineSuspended) {
                    logcat(LogPriority.DEBUG) { "$tag $methodName suspended, waiting for continuation" }
                } else {
                    val list = result as? List<Any> ?: emptyList()
                    logcat(LogPriority.DEBUG) { "$tag $methodName returned immediately with ${list.size} items" }
                    if (!cont.isCompleted) cont.resume(list)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "$tag Error invoking $methodName: ${e.message}" }
                if (!cont.isCompleted) cont.resume(emptyList())
            }
        }
    }

    /**
     * Invokes a suspend function with a String and Int param that returns Any? (not a List).
     * Used for getChapterListPage(url: String, page: Int).
     */
    private suspend fun invokeSuspendMethodWithStringInt(methodName: String, arg1: String, arg2: Int): Any? {
        return suspendCancellableCoroutine { cont ->
            try {
                val method = source.javaClass.methods.find { m ->
                    m.name == methodName &&
                        m.parameterCount == 3 &&
                        m.parameterTypes[0] == String::class.java &&
                        (m.parameterTypes[1] == Int::class.javaPrimitiveType || m.parameterTypes[1] == Int::class.java) &&
                        m.parameterTypes[2].name.contains("Continuation")
                }

                if (method == null) {
                    val regularMethod = try {
                        source.javaClass.getMethod(methodName, String::class.java, Int::class.javaPrimitiveType)
                    } catch (e: NoSuchMethodException) {
                        try {
                            source.javaClass.getMethod(methodName, String::class.java, Int::class.java)
                        } catch (e2: NoSuchMethodException) {
                            logcat(LogPriority.WARN) { "$tag Could not find method $methodName(String, Int)" }
                            cont.resume(null)
                            return@suspendCancellableCoroutine
                        }
                    }
                    logcat(LogPriority.DEBUG) { "$tag Invoking regular method $methodName(String, Int)" }
                    val result = regularMethod.invoke(source, arg1, arg2)
                    cont.resume(result)
                    return@suspendCancellableCoroutine
                }

                logcat(LogPriority.DEBUG) { "$tag Invoking suspend method $methodName(String, Int, Continuation)" }

                val continuation = object : Continuation<Any?> {
                    override val context: CoroutineContext = cont.context
                    override fun resumeWith(result: Result<Any?>) {
                        result.fold(
                            onSuccess = { value ->
                                logcat(LogPriority.DEBUG) {
                                    "$tag $methodName success, value type: ${value?.javaClass?.name}"
                                }
                                if (!cont.isCompleted) cont.resume(value)
                            },
                            onFailure = { e ->
                                logcat(LogPriority.ERROR, e) { "$tag $methodName failed in continuation" }
                                if (!cont.isCompleted) cont.resume(null)
                            },
                        )
                    }
                }

                val result = method.invoke(source, arg1, arg2, continuation)
                if (result === extensionCoroutineSuspended) {
                    logcat(LogPriority.DEBUG) { "$tag $methodName suspended, waiting for continuation" }
                } else {
                    logcat(LogPriority.DEBUG) {
                        "$tag $methodName returned immediately, type=${result?.javaClass?.name}"
                    }
                    if (!cont.isCompleted) cont.resume(result)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "$tag Error invoking $methodName: ${e.message}" }
                if (!cont.isCompleted) cont.resume(null)
            }
        }
    }

    /**
     * Invokes a suspend function with a single String param that returns Any?.
     * Used for getNovelDetails(url: String) and getChapterContent(url: String).
     */
    private suspend fun invokeSuspendMethodWithString(methodName: String, arg: String): Any? {
        return suspendCancellableCoroutine { cont ->
            try {
                val method = source.javaClass.methods.find { m ->
                    m.name == methodName &&
                        m.parameterCount == 2 &&
                        m.parameterTypes[0] == String::class.java &&
                        m.parameterTypes[1].name.contains("Continuation")
                }

                if (method == null) {
                    // Try regular method without Continuation
                    val regularMethod = try {
                        source.javaClass.getMethod(methodName, String::class.java)
                    } catch (e: NoSuchMethodException) {
                        logcat(LogPriority.WARN) { "$tag Could not find method $methodName(String)" }
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    logcat(LogPriority.DEBUG) { "$tag Invoking regular method $methodName(String)" }
                    val result = regularMethod.invoke(source, arg)
                    cont.resume(result)
                    return@suspendCancellableCoroutine
                }

                logcat(LogPriority.DEBUG) { "$tag Invoking suspend method $methodName(String, Continuation)" }

                val continuation = object : Continuation<Any?> {
                    override val context: CoroutineContext = cont.context
                    override fun resumeWith(result: Result<Any?>) {
                        result.fold(
                            onSuccess = { value ->
                                logcat(LogPriority.DEBUG) {
                                    "$tag $methodName success, value type: ${value?.javaClass?.name}"
                                }
                                if (!cont.isCompleted) cont.resume(value)
                            },
                            onFailure = { e ->
                                logcat(LogPriority.ERROR, e) { "$tag $methodName failed in continuation" }
                                if (!cont.isCompleted) cont.resume(null)
                            },
                        )
                    }
                }

                val result = method.invoke(source, arg, continuation)
                if (result === extensionCoroutineSuspended) {
                    logcat(LogPriority.DEBUG) { "$tag $methodName suspended, waiting for continuation" }
                } else {
                    logcat(LogPriority.DEBUG) {
                        "$tag $methodName returned immediately, type=${result?.javaClass?.name}"
                    }
                    if (!cont.isCompleted) cont.resume(result)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "$tag Error invoking $methodName: ${e.message}" }
                if (!cont.isCompleted) cont.resume(null)
            }
        }
    }

    /**
     * Invokes a suspend function with a single String param that returns a List.
     * Used for getChapterList(novelUrl: String).
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun invokeSuspendListMethodWithString(methodName: String, arg: String): List<Any> {
        return suspendCancellableCoroutine { cont ->
            try {
                val method = source.javaClass.methods.find { m ->
                    m.name == methodName &&
                        m.parameterCount == 2 &&
                        m.parameterTypes[0] == String::class.java &&
                        m.parameterTypes[1].name.contains("Continuation")
                }

                if (method == null) {
                    // Try regular method without Continuation
                    val regularMethod = try {
                        source.javaClass.getMethod(methodName, String::class.java)
                    } catch (e: NoSuchMethodException) {
                        logcat(LogPriority.WARN) { "$tag Could not find method $methodName(String)" }
                        cont.resume(emptyList())
                        return@suspendCancellableCoroutine
                    }
                    logcat(LogPriority.DEBUG) { "$tag Invoking regular method $methodName(String)" }
                    val result = regularMethod.invoke(source, arg) as? List<Any> ?: emptyList()
                    cont.resume(result)
                    return@suspendCancellableCoroutine
                }

                logcat(LogPriority.DEBUG) { "$tag Invoking suspend method $methodName(String, Continuation)" }

                val continuation = object : Continuation<Any?> {
                    override val context: CoroutineContext = cont.context
                    override fun resumeWith(result: Result<Any?>) {
                        result.fold(
                            onSuccess = { value ->
                                val list = value as? List<Any> ?: emptyList()
                                logcat(LogPriority.DEBUG) { "$tag $methodName completed with ${list.size} items" }
                                if (!cont.isCompleted) cont.resume(list)
                            },
                            onFailure = { e ->
                                logcat(LogPriority.ERROR, e) { "$tag $methodName failed in continuation" }
                                if (!cont.isCompleted) cont.resume(emptyList())
                            },
                        )
                    }
                }

                val result = method.invoke(source, arg, continuation)
                if (result === extensionCoroutineSuspended) {
                    logcat(LogPriority.DEBUG) { "$tag $methodName suspended, waiting for continuation" }
                } else {
                    val list = result as? List<Any> ?: emptyList()
                    logcat(LogPriority.DEBUG) { "$tag $methodName returned immediately with ${list.size} items" }
                    if (!cont.isCompleted) cont.resume(list)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "$tag Error invoking $methodName: ${e.message}" }
                if (!cont.isCompleted) cont.resume(emptyList())
            }
        }
    }
}
