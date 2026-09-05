package com.tendril.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tendril.app.applock.LockScreen
import com.tendril.app.applock.showAppUnlockPrompt
import com.tendril.app.notifications.reconcileAlarms
import com.tendril.app.ui.nav.AndroidWorkbenchScaffold
import com.tendril.app.ui.theme.TendrilTheme
import com.tendril.app.ui.theme.defaultTendrilMode
import kotlinx.coroutines.launch

/**
 * `FragmentActivity`, not plain `ComponentActivity` — `BiometricPrompt` (§3.6) needs a
 * `FragmentManager` to host its callback-delivery fragment. Everything else about this
 * Activity is still ordinary Compose (`setContent`), same as Phase 1.
 */
class MainActivity : FragmentActivity() {
    private val container by lazy { AppContainer.from(this) }

    // Cleared (locked) on backgrounding only when lock-on-background is actually on;
    // otherwise this stays true across the whole process lifetime once first unlocked.
    private val isUnlockedForSession = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must precede super.onCreate so the splash theme is swapped for Theme.Tendril
        // before the first frame (§ launch identity).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // targetSdk 36 enforces edge-to-edge unconditionally (§9.2.1) — opt in explicitly
        // rather than relying on the platform default so insets are handled deliberately.
        enableEdgeToEdge()

        isUnlockedForSession.value = !(container.appLockPreferences.enabled.value && container.appLockPreferences.lockOnLaunch.value)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            /**
             * §9.4's "checks for these on resume/launch" — the `.sync-conflict-*` sweep, and
             * the merge that brings in whatever the other device wrote while this one was
             * away. Until now the only thing that ever ran a sync pass was the Settings
             * button, so a conflict file sat undetected until someone went looking for it.
             */
            override fun onStart(owner: LifecycleOwner) {
                container.syncCoordinator.syncInBackground()
            }

            /**
             * §9.4's `onStop`/backgrounding flush — "the normal 'close the page, go do
             * something else' moment is never left waiting on a timer."
             *
             * Skipped on a configuration change, which also calls `onStop`: a rotation is not
             * a backgrounding, and syncing on every one would rewrite the whole folder for
             * nothing. The pass itself runs on the coordinator's own application-scoped,
             * non-cancellable coroutine, so this Activity going away can't stop a write
             * part-written (see [com.tendril.app.sync.SyncCoordinator]).
             */
            override fun onStop(owner: LifecycleOwner) {
                if (container.appLockPreferences.enabled.value && container.appLockPreferences.lockOnBackground.value) {
                    isUnlockedForSession.value = false
                }
                if (!isChangingConfigurations) container.syncCoordinator.syncInBackground()
            }
        })

        // §9.7 self-healing reconciliation sweep — cheap and idempotent, doubles up with
        // the boot receiver rather than needing a separate WorkManager periodic job for v1.
        lifecycleScope.launch { reconcileAlarms(applicationContext) }

        setContent {
            val colorTheme by container.themePreferences.colorTheme.collectAsState()
            val modeIsExplicit by container.themePreferences.modeIsExplicit.collectAsState()
            val explicitMode by container.themePreferences.explicitMode.collectAsState()
            val typeface by container.themePreferences.typeface.collectAsState()
            val systemDefaultMode = defaultTendrilMode()
            val mode = if (modeIsExplicit) explicitMode ?: systemDefaultMode else systemDefaultMode

            var isUnlocked by isUnlockedForSession
            val appLockEnabled by container.appLockPreferences.enabled.collectAsState()

            // The two permission requests below are chained, not concurrent. Firing two
            // ActivityResultLaunchers in the same composition races them: the system shows
            // one dialog and the second request commonly resolves as denied without ever
            // being seen, which silently cost this app its calendar permission on first run.
            // `notificationsSettled` is the baton — the calendar request waits for it.
            var notificationsSettled by remember {
                mutableStateOf(
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                )
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* reminders simply won't show a notification if declined */ notificationsSettled = true }

            LaunchedEffect(Unit) {
                // The SDK check is redundant with `notificationsSettled`'s own initializer —
                // it can only be false on 33+ — but keeping it here means a reader (and lint)
                // sees why touching POST_NOTIFICATIONS is safe without tracing back upward.
                if (!notificationsSettled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // System Calendar Provider registration (§3.2, §9.9 item 3) — requested here,
            // contextually, on first launch (mirroring the POST_NOTIFICATIONS request above)
            // rather than gated behind a Settings toggle, since Provider registration is
            // inherent app behavior, not an opt-in integration like Google Calendar sync.
            val calendarPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { results ->
                if (results.values.all { it }) {
                    lifecycleScope.launch { container.calendarProviderSync.ensureCalendarAndBackfill() }
                }
            }
            // Asked at most once per process. Re-launching on every cold start after a denial
            // just burns the request against Android's auto-deny with no dialog shown.
            var calendarAsked by remember { mutableStateOf(false) }
            LaunchedEffect(notificationsSettled) {
                if (!notificationsSettled) return@LaunchedEffect
                when {
                    container.calendarProviderSync.hasPermission() ->
                        container.calendarProviderSync.ensureCalendarAndBackfill()
                    !calendarAsked -> {
                        calendarAsked = true
                        calendarPermissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                        )
                    }
                }
            }

            fun requestUnlock() {
                showAppUnlockPrompt(this@MainActivity) { unlocked ->
                    if (unlocked) isUnlocked = true
                }
            }

            // Fire the prompt automatically the moment a lock screen appears — a manual
            // retry button (LockScreen) covers the case where the person dismissed it.
            LaunchedEffect(isUnlocked) {
                if (!isUnlocked) requestUnlock()
            }

            TendrilTheme(colorTheme = colorTheme, mode = mode, typeface = typeface) {
                if (appLockEnabled && !isUnlocked) {
                    LockScreen(onUnlockClick = ::requestUnlock)
                } else {
                    AndroidWorkbenchScaffold(container = container)
                }
            }
        }
    }
}
