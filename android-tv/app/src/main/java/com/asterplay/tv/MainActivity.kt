package com.asterplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.asterplay.tv.store.PlaylistStore
import com.asterplay.tv.store.XtreamStore
import com.asterplay.tv.ui.screens.BrowseScreen
import com.asterplay.tv.ui.screens.HomeScreen
import com.asterplay.tv.ui.screens.LoadingScreen
import com.asterplay.tv.ui.screens.MovieDetailArgs
import com.asterplay.tv.ui.screens.MovieDetailScreen
import com.asterplay.tv.ui.screens.SeriesDetailArgs
import com.asterplay.tv.ui.screens.SeriesDetailScreen
import com.asterplay.tv.ui.screens.PairingScreen
import com.asterplay.tv.ui.screens.SearchScreen
import com.asterplay.tv.ui.screens.SplashScreen

import com.asterplay.tv.ui.theme.AsterplayTheme
import com.asterplay.tv.ui.theme.BgBase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.asterplay.tv.net.Net.init(applicationContext)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AsterplayApp() }
    }
}

object Routes {
    const val Splash = "splash"
    const val Pairing = "pairing"
    const val Loading = "loading"
    const val Home = "home"
    const val Browse = "browse/{type}"
    const val Search = "search"
    const val MovieDetail = "movie_detail"
    const val SeriesDetail = "series_detail"
    fun browse(type: String) = "browse/$type"
}


@Composable
fun AsterplayApp() {
    AsterplayTheme {
        Box(Modifier.fillMaxSize().background(BgBase)) {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = Routes.Splash) {
                composable(Routes.Splash) {
                    SplashScreen(onDone = {
                        val ctx = it
                        // Sempre passa pelo Loading: ele valida a conta no painel e
                        // pré-carrega o menu. Se falhar, cai na tela de login.
                        if (XtreamStore.get(ctx) == null) PlaylistStore.clear(ctx)
                        nav.navigate(Routes.Loading) {
                            popUpTo(Routes.Splash) { inclusive = true }
                        }
                    })

                }
                composable(Routes.Pairing) {
                    PairingScreen(onActivated = {
                        nav.navigate(Routes.Loading) {
                            popUpTo(Routes.Pairing) { inclusive = true }
                        }
                    })
                }
                composable(Routes.Loading) {
                    LoadingScreen(
                        onReady = {
                            nav.navigate(Routes.Home) {
                                popUpTo(Routes.Loading) { inclusive = true }
                            }
                        },
                        onFail = {
                            nav.navigate(Routes.Pairing) {
                                popUpTo(Routes.Loading) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.Home) {
                    HomeScreen(
                        onOpenBrowse = { type -> nav.navigate(Routes.browse(type)) },
                        onLogout = {
                            nav.navigate(Routes.Pairing) {
                                popUpTo(Routes.Home) { inclusive = true }
                            }
                        },
                        onOpenMovieDetail = { ch, tmdbId ->
                            MovieDetailArgs.set(ch, tmdbId, kind = "movie")
                            nav.navigate(Routes.MovieDetail)
                        },
                        onOpenSeriesDetail = { ch, tmdbId ->
                            SeriesDetailArgs.set(ch, tmdbId)
                            nav.navigate(Routes.SeriesDetail)
                        },
                    )
                }
                composable(Routes.Browse) { backStack ->
                    val type = backStack.arguments?.getString("type") ?: "live"
                    BrowseScreen(
                        type = type,
                        onBack = { nav.popBackStack() },
                        onOpenMovieDetail = { ch ->
                            MovieDetailArgs.set(ch, null, kind = "movie")
                            nav.navigate(Routes.MovieDetail)
                        },
                        onOpenSeriesDetail = { ch ->
                            SeriesDetailArgs.set(ch, null)
                            nav.navigate(Routes.SeriesDetail)
                        },
                    )
                }
                composable(Routes.Search) {
                    SearchScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.MovieDetail) {
                    MovieDetailScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.SeriesDetail) {
                    SeriesDetailScreen(onBack = { nav.popBackStack() })
                }
            }

        }
    }
}
