package com.asterplay.tv.net

import android.content.Context
import com.asterplay.tv.store.CacheDb
import com.asterplay.tv.store.XtreamCreds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pré-carrega o menu logo após a validação no painel:
 * - categorias de CANAIS, FILMES e SÉRIES;
 * - TODOS os canais ao vivo (já separados por categoria no cache).
 *
 * Filmes e séries continuam lazy: só baixam os conteúdos quando a
 * categoria for aberta na BrowseScreen.
 */
object MenuPreload {

    suspend fun run(ctx: Context, creds: XtreamCreds, onProgress: (String) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val db = CacheDb.get(ctx)
            val account = CacheDb.accountKey(creds.host, creds.username)

            // 1) Categorias dos três tipos (leve).
            for ((kind, label) in listOf(
                "live" to "canais",
                "vod" to "filmes",
                "series" to "séries",
            )) {
                onProgress("carregando categorias de $label...")
                if (db.readCategories(account, kind) == null) {
                    val fresh = XtreamApi.categories(creds, kind)
                    if (fresh.isNotEmpty()) {
                        db.writeCategories(account, kind, fresh, CacheDb.TTL_CATEGORIES)
                    }
                }
            }

            // 2) Todos os canais ao vivo, agrupados por categoria.
            val liveCats = db.readCategories(account, "live").orEmpty()
            if (liveCats.isNotEmpty()) {
                val missing = liveCats.filter { db.readStreams(account, "live", it.id) == null }
                if (missing.isNotEmpty()) {
                    onProgress("carregando canais...")
                    val all = XtreamApi.allStreams(creds, "live")
                    if (all.isNotEmpty()) {
                        val byCat = all.groupBy { it.group.orEmpty() }
                        for (cat in liveCats) {
                            val list = byCat[cat.id].orEmpty()
                            if (list.isNotEmpty()) {
                                db.writeStreams(account, "live", cat.id, list, CacheDb.TTL_STREAMS)
                            }
                        }
                    }
                }
            }
        }
}
