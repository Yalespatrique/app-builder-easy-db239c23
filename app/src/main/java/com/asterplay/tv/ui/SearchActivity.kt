package com.asterplay.tv.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.asterplay.tv.R
import com.asterplay.tv.ui.fragments.SearchFragment

class SearchActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.search_fragment, SearchFragment())
                .commit()
        }
    }
}
