package com.asterplay.tv.net

/**
 * Item genérico exibido nos grids (canais, filmes, séries).
 * Mantido em pacote net.* pra compatibilidade com CacheDb, BrowseScreen, SearchScreen.
 */
data class Channel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    val tvgId: String? = null,
)
