package com.example.link2sdclone

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.link2sdclone.ui.SettingsFragment

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_container)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        title = getString(R.string.menu_settings)
    }
}
