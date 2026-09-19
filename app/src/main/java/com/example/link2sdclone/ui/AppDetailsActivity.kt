package com.example.link2sdclone.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.link2sdclone.R
import com.example.link2sdclone.util.FavoritesManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppDetailsActivity : AppCompatActivity() {

    private lateinit var packageNameArg: String
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_details)

        packageNameArg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run { finish(); return }

        toolbar = findViewById(R.id.toolbar_details)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.navigationIcon?.mutate()?.setTint(Color.WHITE)
        toolbar.inflateMenu(R.menu.menu_app_details)
        toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.ic_more_vert)
            ?.mutate()?.apply { setTint(Color.WHITE) }
        toolbar.menu.findItem(R.id.details_system_info)?.icon =
            ContextCompat.getDrawable(this, R.drawable.ic_info_outline)
        for (i in 0 until toolbar.menu.size()) {
            toolbar.menu.getItem(i).icon?.mutate()?.setTint(Color.WHITE)
        }
        toolbar.setOnMenuItemClickListener { onDetailsMenuItem(it.itemId) }
        updateFavoriteIcon()
        bindHeader()

        val pager = findViewById<ViewPager2>(R.id.details_pager)
        val tabs = findViewById<TabLayout>(R.id.details_tabs)
        pager.adapter = DetailsPagerAdapter(this, packageNameArg)

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = if (position == 0) getString(R.string.tab_storage) else getString(R.string.tab_files)
        }.attach()

        (tabs.getChildAt(0) as? android.widget.LinearLayout)?.apply {
            showDividers = android.widget.LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = ContextCompat.getDrawable(this@AppDetailsActivity, R.drawable.divider_tab)
            dividerPadding = (10 * resources.displayMetrics.density).toInt()
        }
    }

    private fun bindHeader() {
        val pm = packageManager
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
        val nameView = findViewById<TextView>(R.id.details_name)
        findViewById<TextView>(R.id.details_package).text = packageNameArg
        try {
            val info = pm.getApplicationInfo(packageNameArg, flags)
            nameView.text = pm.getApplicationLabel(info)
            findViewById<ImageView>(R.id.details_icon).setImageDrawable(pm.getApplicationIcon(info))
        } catch (e: Exception) {
            nameView.text = packageNameArg
        }
        try {
            val pi = pm.getPackageInfo(packageNameArg, flags)
            findViewById<TextView>(R.id.details_version).text = getString(
                R.string.appinfo_version, pi.versionName ?: "-", PackageInfoCompat.getLongVersionCode(pi)
            )
            val fmt = SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault())
            findViewById<TextView>(R.id.details_date).text =
                getString(R.string.appinfo_date, "\u202A" + fmt.format(Date(pi.lastUpdateTime)) + "\u202C")
        } catch (e: Exception) {
        }
    }

    private fun updateFavoriteIcon() {
        val fav = FavoritesManager.isFavorite(this, packageNameArg)
        toolbar.menu.findItem(R.id.details_favorite)?.icon = ContextCompat.getDrawable(
            this, if (fav) R.drawable.ic_star else R.drawable.ic_star_outline
        )?.mutate()?.apply { setTint(Color.WHITE) }
    }

    private fun onDetailsMenuItem(id: Int): Boolean = when (id) {
        R.id.details_favorite -> {
            val now = !FavoritesManager.isFavorite(this, packageNameArg)
            FavoritesManager.set(this, packageNameArg, now)
            updateFavoriteIcon()
            true
        }
        R.id.details_system_info -> {
            openIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageNameArg, null)))
            true
        }
        R.id.details_run -> {
            val launch = packageManager.getLaunchIntentForPackage(packageNameArg)
            if (launch == null) Toast.makeText(this, R.string.appinfo_cannot_run, Toast.LENGTH_SHORT).show()
            else openIntent(launch)
            true
        }
        R.id.details_play -> {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageNameArg")))
            } catch (e: Exception) {
                openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageNameArg")))
            }
            true
        }
        else -> false
    }

    private fun openIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.appinfo_cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private class DetailsPagerAdapter(activity: FragmentActivity, private val packageName: String) :
        FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment {
            val bundle = Bundle().apply { putString(EXTRA_PACKAGE_NAME, packageName) }
            val fragment = if (position == 0) StorageFragment() else FilesFragment()
            fragment.arguments = bundle
            return fragment
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }
}
