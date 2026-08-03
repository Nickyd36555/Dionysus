package com.dionysus.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dionysus.tv.ui.components.MainScaffold
import com.dionysus.tv.ui.detail.DetailScreen
import com.dionysus.tv.ui.downloads.DownloadsScreen
import com.dionysus.tv.ui.home.HomeScreen
import com.dionysus.tv.ui.navigation.Routes
import com.dionysus.tv.ui.navigation.TopLevelDestination
import com.dionysus.tv.ui.player.PlayerScreen
import com.dionysus.tv.ui.search.SearchScreen
import com.dionysus.tv.ui.settings.SettingsScreen
import com.dionysus.tv.ui.splash.SplashScreen
import com.dionysus.tv.ui.streams.StreamsScreen

@Composable
fun DionysusApp(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            TopLevel(navController) {
                HomeScreen(onOpenDetail = { navController.navigate(Routes.detail(it)) })
            }
        }

        composable(Routes.SEARCH) {
            TopLevel(navController) {
                SearchScreen(onOpenDetail = { navController.navigate(Routes.detail(it)) })
            }
        }

        composable(Routes.DOWNLOADS) {
            TopLevel(navController) {
                DownloadsScreen(
                    onPlay = { url, title -> navController.navigate(Routes.player(url, title)) },
                )
            }
        }

        composable(Routes.SETTINGS) {
            TopLevel(navController) { SettingsScreen() }
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("mediaId") { type = NavType.StringType }),
        ) {
            DetailScreen(
                onFindSources = { mediaId, season, episode ->
                    navController.navigate(Routes.streams(mediaId, season, episode))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.STREAMS,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("season") { type = NavType.IntType; defaultValue = -1 },
                navArgument("episode") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) {
            StreamsScreen(
                onPlay = { url, title, progressId, poster, backdrop ->
                    navController.navigate(Routes.player(url, title, progressId, poster, backdrop))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("progressId") { type = NavType.StringType; defaultValue = "" },
                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                navArgument("backdrop") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Wraps a top-level screen with the shared navigation rail. */
@Composable
private fun TopLevel(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selected = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
        ?: TopLevelDestination.HOME

    MainScaffold(
        selected = selected,
        onSelect = { dest ->
            if (dest.route != currentRoute) {
                navController.navigate(dest.route) {
                    popUpTo(Routes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        content = content,
    )
}
