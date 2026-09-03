package com.tendril.desktopapp

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Compose Multiplatform doesn't provide a default `ViewModelStoreOwner` outside `NavHost`
 * (which this app doesn't use — see `WorkbenchNavState`'s doc comment) — desktop supplies its
 * own, application-lifetime one via `LocalViewModelStoreOwner` (see Main.kt's `App()`). */
class DesktopViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}
