package com.asterplay.tv.net

import android.content.Context
import com.asterplay.tv.store.CacheDb
import com.asterplay.tv.store.XtreamCreds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado global do preload. A tela de categorias observa a versão para
 * atualizar os totais assim que o catálogo termina de baixar em segundo plano.
 */
object PreloadState {
    /** Incrementa toda vez que os totais por categoria mudam. */
    val countsVersion = MutableStateFlow(0)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun launchBackground(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch { runCatching { block() } }
    }
}

/**
 * Preload do menu, dividido em duas etapas:
 *
 * - [fast]: só as categorias dos três tipos (3 requests leves, em paralelo).
 *   É o único bloqueio antes de abrir a Home — leva poucos segundos.
 * - [background]: catálogo completo (totais por categoria + canais ao vivo)
 *   e os destaques do Home. Roda depois, sem travar o app.
 */
object MenuPreload {

    private val KINDS = listOf("live", "vod", "series")

    /** Só categorias — rápido. */
    suspend fun fast(ctx: Context, creds: XtreamCreds, onProgress: (String) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val db = CacheDb.get(ctx)
            val account = CacheDb.accountKey(creds.host, creds.username)
            onProgress("carregando categorias...")
            coroutineScope {
                KINDS.map { kind ->
                    async {
                        if (db.readCategories(account, kind) == null) {
                            val fresh = XtreamApi.categories(creds, kind)
                            if (fresh.isNotEmpty()) {
                                db.writeCategories(account, kind, fresh, CacheDb.TTL_CATEGORIES)
                            }
                        }
                    }
                }.forEach { it.await() }
            }
        }

    /** Catálogo completo em segundo plano: totais + canais ao vivo + destaques. */
    fun startBackground(ctx: Context, creds: XtreamCreds) = PreloadState.launchBackground {
        val db = CacheDb.get(ctx)
        val account = CacheDb.accountKey(creds.host, creds.username)

        coroutineScope {
            KINDS.map { kind ->
                async {
                    val all = runCatching { XtreamApi.allStreams(creds, kind) }.getOrDefault(emptyList())
                    if (all.isEmpty()) return@async
                    val byCat = all.groupBy { it.group.orEmpty() }

                    // Totais reais por categoria (mostrados no menu).
                    db.writeCounts(account, kind, byCat.mapValues { it.value.size }.filterKeys { it.isNotEmpty() })
                    PreloadState.countsVersion.value++

                    // Só canais ao vivo ficam pré-cacheados; filmes/séries seguem lazy.
                    if (kind == "live") {
                        for ((catId, list) in byCat) {
                            if (catId.isBlank() || list.isEmpty()) continue
                            if (db.readStreams(account, "live", catId) == null) {
                                db.writeStreams(account, "live", catId, list, CacheDb.TTL_STREAMS)
                            }
                        }
                    }
                }
            }.forEach { it.await() }
        }

        runCatching { 
            TopHomePreload.run(ctx)
            PreloadState.countsVersion.value++ // Force UI refresh after Top 10 is ready
        }
    }
}

