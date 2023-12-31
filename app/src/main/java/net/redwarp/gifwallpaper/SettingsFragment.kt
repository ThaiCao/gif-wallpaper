/* Copyright 2020 Benoit Vermont
 * Copyright 2020 GifWallpaper Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.redwarp.gifwallpaper

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import kotlinx.coroutines.runBlocking

@Keep
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val dataStore = AppSettingsPreferencesDataStore(requireContext())
        preferenceManager?.preferenceDataStore = dataStore

        setPreferencesFromResource(R.xml.preferences, rootKey)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            preferenceManager.findPreference<SwitchPreferenceCompat>("thermal_throttle")?.isVisible =
                false
        }
    }
}

class AppSettingsPreferencesDataStore(context: Context) : PreferenceDataStore() {
    private val appSettings = AppSettings.get(context)

    override fun getBoolean(key: String, defValue: Boolean): Boolean = runBlocking {
        appSettings.getBoolean(key, defValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        runBlocking {
            appSettings.putBoolean(key, value)
        }
    }
}
