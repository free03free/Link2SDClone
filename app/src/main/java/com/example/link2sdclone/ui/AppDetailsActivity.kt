package com.example.link2sdclone.ui

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.link2sdclone.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class AppDetailsActivity : AppCompatActivity() {

    private lateinit var packageNameArg: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_details)

        packageNameArg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run { finish(); return }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_details)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageNameArg, 0))
        } catch (e: Exception) { packageNameArg }

        val pager = findViewById<ViewPager2>(R.id.details_pager)
        val tabs = findViewById<TabLayout>(R.id.details_tabs)
        pager.adapter = DetailsPagerAdapter(this, packageNameArg)

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = if (position == 0) getString(R.string.tab_storage) else getString(R.string.tab_files)
        }.attach()
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
