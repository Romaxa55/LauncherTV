/*
 * Simple TV Launcher
 * Copyright 2017 Alexandre Del Bigio
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

package org.cosinus.launchertv.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.Toast;

import org.cosinus.launchertv.R;
import org.cosinus.launchertv.Setup;

import java.util.Locale;

@SuppressWarnings("deprecation")
public class Preferences extends PreferenceActivity {
	public static final String PREFERENCE_DEFAULT_TRANSPARENCY = "preference_default_transparency";
	public static final String PREFERENCE_TRANSPARENCY = "preference_transparency";
	public static final String PREFERENCE_SCREEN_ON = "preference_screen_always_on";
	public static final String PREFERENCE_SHOW_NAME = "preference_show_name";
	public static final String PREFERENCE_LOCKED = "preference_locked";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Setup setup = new Setup(this);
		addPreferencesFromResource(R.xml.preferences);

		// Найдите предпочтение для изменения фона
		Preference backgroundPreference = findPreference("preference_background");
		if (backgroundPreference != null) {
			backgroundPreference.setOnPreferenceClickListener(preference -> {
				// Создайте Intent для установки обоев
				Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
				if (intent.resolveActivity(getPackageManager()) != null) {
					// Если активность найдена, запустите ее
					startActivity(intent);
				} else {
					// Если активности нет, покажите сообщение
					Toast.makeText(this, "Need install WALLPAPER MANAGER", Toast.LENGTH_SHORT).show();
				}
				return true;
			});
		}
	}


	@Override
	public void onDestroy() {
		if (getParent() == null) {
			setResult(Activity.RESULT_OK, null);
		} else {
			getParent().setResult(Activity.RESULT_OK, null);
		}
		super.onDestroy();
	}
}
