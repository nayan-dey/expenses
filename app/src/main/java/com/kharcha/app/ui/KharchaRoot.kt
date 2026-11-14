package com.kharcha.app.ui

import android.view.WindowManager
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.kharcha.app.platform.Launch
import com.kharcha.app.ui.habit.HabitScreen
import com.kharcha.app.ui.insights.InsightsScreen
import com.kharcha.app.ui.nav.HabitRoute
import com.kharcha.app.ui.nav.InsightsRoute
import com.kharcha.app.ui.nav.PeopleRoute
import com.kharcha.app.ui.nav.PersonRoute
import com.kharcha.app.ui.nav.RemindersRoute
import com.kharcha.app.ui.people.PeopleScreen
import com.kharcha.app.ui.people.PersonScreen
import com.kharcha.app.ui.settings.RemindersScreen
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kharcha.app.AppContainer
import com.kharcha.app.core.model.ThemeChoice
import com.kharcha.app.platform.findActivity
import com.kharcha.app.ui.categories.CategoriesScreen
import com.kharcha.app.ui.categories.CategoryEditScreen
import com.kharcha.app.ui.home.HomeScreen
import com.kharcha.app.ui.item.ItemEditScreen
import com.kharcha.app.ui.nav.AboutRoute
import com.kharcha.app.ui.nav.AppIconRoute
import com.kharcha.app.ui.nav.AppearanceRoute
import com.kharcha.app.ui.nav.BackupRoute
import com.kharcha.app.ui.nav.CategoriesRoute
import com.kharcha.app.ui.nav.CategoryRoute
import com.kharcha.app.ui.nav.ExportRoute
import com.kharcha.app.ui.nav.HomeRoute
import com.kharcha.app.ui.nav.ImportRoute
import com.kharcha.app.ui.nav.ItemRoute
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.nav.Messenger
import com.kharcha.app.ui.nav.NavViewModel
import com.kharcha.app.ui.nav.Navigator
import com.kharcha.app.ui.nav.PrintRoute
import com.kharcha.app.ui.nav.PrivacyRoute
import com.kharcha.app.ui.nav.RatesRoute
import com.kharcha.app.ui.nav.Route
import com.kharcha.app.ui.nav.SettingsRoute
import com.kharcha.app.ui.nav.SheetEditRoute
import com.kharcha.app.ui.nav.SheetRoute
import com.kharcha.app.ui.nav.StatsRoute
import com.kharcha.app.ui.nav.TrashRoute
import com.kharcha.app.ui.nav.TrendsRoute
import com.kharcha.app.ui.onboarding.LockScreen
import com.kharcha.app.ui.onboarding.OnboardingScreen
import com.kharcha.app.ui.rates.RatesScreen
import com.kharcha.app.ui.settings.AboutScreen
import com.kharcha.app.ui.settings.AppIconScreen
import com.kharcha.app.ui.settings.AppearanceScreen
import com.kharcha.app.ui.settings.BackupScreen
import com.kharcha.app.ui.settings.ExportScreen
import com.kharcha.app.ui.settings.ImportScreen
import com.kharcha.app.ui.settings.PrintScreen
import com.kharcha.app.ui.settings.PrivacyScreen
import com.kharcha.app.ui.settings.SettingsScreen
import com.kharcha.app.ui.settings.TrashScreen
import com.kharcha.app.ui.sheet.SheetEditScreen
import com.kharcha.app.ui.sheet.SheetScreen
import com.kharcha.app.ui.stats.StatsScreen
import com.kharcha.app.ui.stats.TrendsScreen
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.theme.Look

/**
 * The top of the UI tree. It turns Settings into a theme, shows the welcome screen on first
 * launch and the lock when "App lock" is on, and otherwise shows the current screen.
 *
 * @param onThemeResolved tells the Activity whether the app is dark, so the phone's status
 *   and navigation bar icons get the right colour.
 */
@Composable
fun KharchaRoot(container: AppContainer, onThemeResolved: (dark: Boolean) -> Unit) {
    val loaded by container.loaded.collectAsStateWithLifecycle()
    // Until the files are read from disk the splash screen stays up; draw nothing.
    if (!loaded) return
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val locked by container.lock.locked.collectAsStateWithLifecycle()

    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
        ThemeChoice.SYSTEM -> systemDark
    }
    DisposableEffect(dark) {
        onThemeResolved(dark)
        onDispose { }
    }

    // "Block screenshots": Android then also blanks the app in the recent-apps view.
    val window = LocalContext.current.findActivity()?.window
    DisposableEffect(settings.blockScreenshots, window) {
        if (settings.blockScreenshots) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }

    val navViewModel: NavViewModel = viewModel()
    val nav = navViewModel.navigator
    val messenger = navViewModel.messenger

    val look = Look(
        dark = dark,
        dynamicColor = settings.dynamicColor,
        accent = settings.accent,
        pureBlack = settings.pureBlack,
        font = settings.font,
        textSize = settings.textSize,
        corners = settings.corners,
    )
    KharchaTheme(look = look, sensitive = settings.sensitive) {
        CompositionLocalProvider(
            LocalContainer provides container,
            LocalNav provides nav,
            LocalMessenger provides messenger,
        ) {
            // A Material Surface paints the background and tells everything inside which
            // colour text and icons should be (light in the dark theme, dark in the light one).
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                val showing = settings.onboarded && !(settings.appLock && locked)
                // A tapped widget or notification asked for a particular screen: go there once the app is open.
                val action by container.pendingAction.collectAsStateWithLifecycle()
                LaunchedEffect(action, showing) {
                    val wanted = action
                    if (wanted != null && showing) {
                        when (wanted) {
                            Launch.NEW_ITEM -> {
                                val sheet = container.ledger.current.liveSheets.maxByOrNull { it.editedAt }
                                if (sheet != null) nav.go(ItemRoute(sheet.id))
                            }
                            Launch.INSIGHTS -> nav.go(InsightsRoute)
                            Launch.HABIT -> nav.go(HabitRoute)
                        }
                        container.pendingAction.value = null
                    }
                }
                when {
                    !settings.onboarded -> OnboardingScreen()
                    settings.appLock && locked -> LockScreen()
                    else -> MainScreens(nav, messenger)
                }
            }
        }
    }
}

@Composable
private fun MainScreens(nav: Navigator, messenger: Messenger) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = nav.backStack,
                onBack = { nav.back() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = { forward() },
                popTransitionSpec = { backward() },
                predictivePopTransitionSpec = { backward() },
                entryProvider = entryProvider<Route> {
                    entry<HomeRoute> { HomeScreen() }
                    entry<SheetRoute> { key -> SheetScreen(key) }
                    entry<SheetEditRoute> { key -> SheetEditScreen(key) }
                    entry<ItemRoute> { key -> ItemEditScreen(key) }
                    entry<StatsRoute> { key -> StatsScreen(key) }
                    entry<TrendsRoute> { key -> TrendsScreen(key) }
                    entry<RatesRoute> { key -> RatesScreen(key) }
                    entry<SettingsRoute> { SettingsScreen() }
                    entry<CategoriesRoute> { CategoriesScreen() }
                    entry<CategoryRoute> { key -> CategoryEditScreen(key) }
                    entry<BackupRoute> { BackupScreen() }
                    entry<ExportRoute> { key -> ExportScreen(key) }
                    entry<ImportRoute> { key -> ImportScreen(key) }
                    entry<TrashRoute> { TrashScreen() }
                    entry<AppearanceRoute> { AppearanceScreen() }
                    entry<AppIconRoute> { AppIconScreen() }
                    entry<PrivacyRoute> { PrivacyScreen() }
                    entry<PrintRoute> { PrintScreen() }
                    entry<AboutRoute> { AboutScreen() }
                    entry<PeopleRoute> { PeopleScreen() }
                    entry<PersonRoute> { key -> PersonScreen(key) }
                    entry<InsightsRoute> { InsightsScreen() }
                    entry<HabitRoute> { HabitScreen() }
                    entry<RemindersRoute> { RemindersScreen() }
                },
            )
            // Leave room for the keyboard, or for the phone's own gesture bar.
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
        }
        SnackbarHost(
            hostState = messenger.host,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = 88.dp),
        )
    }
}

/** Going deeper slides in from the right; going back slides in from the left. */
private fun forward(): ContentTransform =
    (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 9 }) togetherWith fadeOut(tween(120))

private fun backward(): ContentTransform =
    (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { -it / 9 }) togetherWith fadeOut(tween(120))
