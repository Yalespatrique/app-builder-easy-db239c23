package com.asterplay.tv.ui.fragments

import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.lifecycleScope
import com.asterplay.tv.R
import com.asterplay.tv.data.AsterStore
import com.asterplay.tv.data.Channel
import com.asterplay.tv.data.FavoritesStore
import com.asterplay.tv.data.M3UParser
import com.asterplay.tv.data.ResumeStore
import com.asterplay.tv.ui.PlayerActivity
import com.asterplay.tv.ui.SearchActivity
import com.asterplay.tv.ui.presenters.PosterCardPresenter
import kotlinx.coroutines.launch

/**
 * Home estilo Netflix/XCIPTV: linhas horizontais de pôsteres.
 * Ordem fixa: "Continuar assistindo" → "Favoritos" → categorias do M3U.
 */
class MainBrowseFragment : BrowseSupportFragment() {

    private lateinit var favorites: FavoritesStore
    private lateinit var resume: ResumeStore
    private lateinit var store: AsterStore
    private var allChannels: List<Channel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favorites = FavoritesStore(requireContext())
        resume = ResumeStore(requireContext())
        store = AsterStore(requireContext())

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        brandColor = ContextCompat.getColor(requireContext(), R.color.aster_bg)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.aster_primary)

        setOnSearchClickedListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        setOnItemViewClickedListener { _, item, _, _ ->
            (item as? Channel)?.let { openPlayer(it) }
        }

        loadChannels()
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar do player, reconstrói pra atualizar "Continuar" e "Favoritos"
        if (allChannels.isNotEmpty()) rebuildRows()
    }

    private fun openPlayer(ch: Channel) {
        val i = Intent(requireContext(), PlayerActivity::class.java)
            .putExtra("url", ch.url)
            .putExtra("title", ch.name)
            .putExtra("logo", ch.logo)
            .putExtra("group", ch.group)
        startActivity(i)
    }

    private fun loadChannels() {
        val url = store.m3uUrl
        if (url.isBlank()) return
        lifecycleScope.launch {
            allChannels = M3UParser.download(url)
            rebuildRows()
        }
    }

    private fun rebuildRows() {
        val posterPresenter = PosterCardPresenter()
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        var headerId = 0L

        // Linha: Continuar assistindo
        val recentIds = resume.recentIds(20).toSet()
        val continueList = allChannels.filter { it.url in recentIds }
            .sortedBy { recentIds.indexOf(it.url) }
        if (continueList.isNotEmpty()) {
            val row = ArrayObjectAdapter(posterPresenter).apply { setItems(continueList, null) }
            rowsAdapter.add(ListRow(HeaderItem(headerId++, getString(R.string.row_continue)), row))
        }

        // Linha: Favoritos
        val favIds = favorites.all()
        val favList = allChannels.filter { it.url in favIds }
        if (favList.isNotEmpty()) {
            val row = ArrayObjectAdapter(posterPresenter).apply { setItems(favList, null) }
            rowsAdapter.add(ListRow(HeaderItem(headerId++, getString(R.string.row_favorites)), row))
        }

        // Categorias do M3U (group-title)
        val grouped = allChannels.groupBy { it.group.ifBlank { getString(R.string.row_all) } }
        for ((group, items) in grouped) {
            val row = ArrayObjectAdapter(posterPresenter).apply { setItems(items, null) }
            rowsAdapter.add(ListRow(HeaderItem(headerId++, group), row))
        }

        adapter = rowsAdapter
    }
}
