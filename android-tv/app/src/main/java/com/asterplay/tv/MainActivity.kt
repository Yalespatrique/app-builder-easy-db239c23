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
import com.asterplay.tv.ui.screens.PairingScreen
import com.asterplay.tv.ui.screens.SearchScreen
import com.asterplay.tv.ui.screens.SplashScreen
import com.asterplay.tv.ui.theme.AsterplayTheme
import com.asterplay.tv.ui.theme.BgBase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        val url = PlaylistStore.get(ctx)
                        val target = if (url != null && PlaylistCache.has(ctx, url)) Routes.Home
                        else {
                            if (url != null) PlaylistStore.clear(ctx)
                            Routes.Pairing
                        }
                        nav.navigate(target) {
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
                        onOpenSearch = { nav.navigate(Routes.Search) },
                        onLogout = {
                            nav.navigate(Routes.Pairing) {
                                popUpTo(Routes.Home) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.Browse) { backStack ->
                    val type = backStack.arguments?.getString("type") ?: "live"
                    BrowseScreen(type = type, onBack = { nav.popBackStack() })
                }
                composable(Routes.Search) {
                    SearchScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
