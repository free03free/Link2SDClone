package com.example.link2sdclone.ui

import androidx.drawerlayout.widget.DrawerLayout
import com.example.link2sdclone.R
import com.google.android.material.navigation.NavigationView

interface DrawerActions {
    fun onAllApps()
    fun onOnSdCard()
    fun onOnPhone()
    fun onFrozen()
    fun onFavorites()
    fun onStorageInfo()
    fun onSettings()
    fun onAbout()
    fun onGroups()
}

/**
 * Wires clicks on the NavigationView drawer items to the given callbacks
 * and closes the drawer afterward. Call once in onCreate() after
 * findViewById<NavigationView>(R.id.nav_view).
 */
fun setupDrawer(
    navView: NavigationView,
    drawerLayout: DrawerLayout,
    actions: DrawerActions
) {
    navView.setNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.nav_all_apps -> actions.onAllApps()
            R.id.nav_on_sdcard -> actions.onOnSdCard()
            R.id.nav_on_phone -> actions.onOnPhone()
            R.id.nav_frozen -> actions.onFrozen()
            R.id.nav_favorites -> actions.onFavorites()
            R.id.nav_storage_info -> actions.onStorageInfo()
            R.id.nav_settings -> actions.onSettings()
            R.id.nav_about -> actions.onAbout()
            R.id.nav_groups -> actions.onGroups()
        }
        item.isChecked = true
        drawerLayout.closeDrawers()
        true
    }
}
