package com.asterplay.tv.ui.fragments

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.lifecycle.lifecycleScope
import com.asterplay.tv.R
import com.asterplay.tv.data.AsterStore
import com.asterplay.tv.data.Channel
import com.asterplay.tv.data.M3UParser
import com.asterplay.tv.ui.PlayerActivity
import com.asterplay.tv.ui.presenters.PosterCardPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private var channels: List<Channel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        setOnItemViewClickedListener { _, item, _, _ ->
            (item as? Channel)?.let { ch ->
                startActivity(
                    Intent(requireContext(), PlayerActivity::class.java)
                        .putExtra("url", ch.url)
                        .putExtra("title", ch.name)
                        .putExtra("logo", ch.logo)
                        .putExtra("group", ch.group)
                )
            }
        }

        val store = AsterStore(requireContext())
        lifecycleScope.launch {
            channels = withContext(Dispatchers.IO) { M3UParser.download(store.m3uUrl) }
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String?): Boolean {
        applyQuery(newQuery.orEmpty()); return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        applyQuery(query.orEmpty()); return true
    }

    private fun applyQuery(raw: String) {
        rowsAdapter.clear()
        val q = normalize(raw)
        if (q.length < 2) return
        val matches = channels.filter { normalize(it.name).contains(q) }.take(80)
        if (matches.isEmpty()) return
        val row = ArrayObjectAdapter(PosterCardPresenter()).apply { setItems(matches, null) }
        rowsAdapter.add(ListRow(HeaderItem(0, getString(R.string.search_results, matches.size)), row))
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase()
}
