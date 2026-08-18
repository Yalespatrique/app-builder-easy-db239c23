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

    /**
     * Sincronização completa e bloqueante: categorias + contagens + destaques.
     * Só retorna quando tudo estiver pronto para a Home exibir.
     */
    suspend fun fullSync(ctx: Context, creds: XtreamCreds, onProgress: (String) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val db = CacheDb.get(ctx)
            val account = CacheDb.accountKey(creds.host, creds.username)

            // Checa se já temos o catálogo completo cacheado e válido
            val hasCategories = KINDS.all { db.readCategories(account, it) != null }
            val hasCounts = KINDS.all { db.readCounts(account, it).isNotEmpty() }
            
            if (hasCategories && hasCounts) {
                onProgress("carregando dados do cache...")
                // Mesmo com cache, atualizamos a versão para a Home ler
                PreloadState.countsVersion.value++
                return@withContext
            }

            // 1. Categorias (rápido)
            onProgress("carregando categorias...")
            coroutineScope {
                KINDS.map { kind ->
                    async {
                        val fresh = XtreamApi.categories(creds, kind)
                        if (fresh.isNotEmpty()) {
                            db.writeCategories(account, kind, fresh, CacheDb.TTL_CATEGORIES)
                        }
                    }
                }.forEach { it.await() }
            }

            // 2. Contagens e Canais (mais pesado)
            onProgress("organizando canais e filmes...")
            coroutineScope {
                KINDS.map { kind ->
                    async {
                        val all = runCatching { XtreamApi.allStreams(creds, kind) }.getOrDefault(emptyList())
                        if (all.isEmpty()) return@async
                        val byCat = all.groupBy { it.group.orEmpty() }

                        // Totais reais por categoria
                        db.writeCounts(account, kind, byCat.mapValues { it.value.size }.filterKeys { it.isNotEmpty() })

                        // Cache de canais ao vivo para navegação instantânea
                        if (kind == "live") {
                            for ((catId, list) in byCat) {
                                if (catId.isBlank() || list.isEmpty()) continue
                                db.writeStreams(account, "live", catId, list, CacheDb.TTL_STREAMS)
                            }
                        }
                    }
                }.forEach { it.await() }
            }

            // 3. Destaques (Top 10 / Recentes)
            onProgress("preparando destaques...")
            runCatching {
                TopHomePreload.run(ctx)
            }

            // Notifica UI que os dados estão prontos
            PreloadState.countsVersion.value++
        }
}

