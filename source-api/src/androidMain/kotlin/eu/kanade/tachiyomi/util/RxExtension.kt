package eu.kanade.tachiyomi.util

import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle as awaitSingleCore

suspend fun <T> Observable<T>.awaitSingle(): T = awaitSingleCore()
