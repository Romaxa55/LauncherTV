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

package org.cosinus.launchertv.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.cosinus.launchertv.AppInfo;
import org.cosinus.launchertv.R;
import org.cosinus.launchertv.Setup;
import org.cosinus.launchertv.Utils;
import org.cosinus.launchertv.activities.ApplicationList;
import org.cosinus.launchertv.activities.Preferences;
import org.cosinus.launchertv.views.ApplicationView;

import java.text.DateFormat;
import java.util.Date;

@SuppressWarnings("PointlessBooleanExpression")
public class ApplicationFragment extends Fragment implements View.OnClickListener, View.OnLongClickListener {
	public static final String TAG = "ApplicationFragment";
	private static final String PREFERENCES_NAME = "applications";
	private static final int REQUEST_CODE_WALLPAPER = 0x1F;

	private TextView mClock;
	private TextView mDate;
	private DateFormat mTimeFormat;
	private DateFormat mDateFormat;

	private final Handler mHandler = new Handler();
	private final Runnable mTimerTick = new Runnable() {
		@Override
		public void run() {
			setClock();
		}
	};

	private int mGridX = 3;
	private int mGridY = 2;
	private LinearLayout mContainer;
	private ApplicationView[][] mApplications = null;
	private View mSettings;
	private View mGridView;
	private Setup mSetup;

	private ActivityResultLauncher<Intent> preferencesLauncher;
	private ActivityResultLauncher<Intent> applicationListLauncher;
	private ActivityResultLauncher<Intent> applicationStartLauncher;

	public ApplicationFragment() {
		// Required empty public constructor
	}

	public static ApplicationFragment newInstance() {
		return new ApplicationFragment();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Инициализируем launchers для новых API
		preferencesLauncher = registerForActivityResult(
				new ActivityResultContracts.StartActivityForResult(),
				result -> {
					if (result.getResultCode() == Activity.RESULT_OK) {
						restartActivity();
					}
				}
		);

		applicationListLauncher = registerForActivityResult(
				new ActivityResultContracts.StartActivityForResult(),
				result -> {
					if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
						String packageName = result.getData().getStringExtra(ApplicationList.PACKAGE_NAME);
						openApplication(packageName);
					}
				}
		);

		applicationStartLauncher = registerForActivityResult(
				new ActivityResultContracts.StartActivityForResult(),
				result -> {
					if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
						Bundle extra = result.getData().getExtras();
						int appNum = result.getData().getExtras().getInt(ApplicationList.APPLICATION_NUMBER);

						if (extra.containsKey(ApplicationList.DELETE) && extra.getBoolean(ApplicationList.DELETE)) {
							writePreferences(appNum, null);
						} else {
							writePreferences(appNum, result.getData().getStringExtra(ApplicationList.PACKAGE_NAME));
						}
						updateApplications();
					}
				}
		);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_application, container, false);

		mSetup = new Setup(getContext());
		mContainer = (LinearLayout) view.findViewById(R.id.container);
		mSettings = view.findViewById(R.id.settings);
		mGridView = view.findViewById(R.id.application_grid);
		mClock = (TextView) view.findViewById(R.id.clock);
		mDate = (TextView) view.findViewById(R.id.date);

		mTimeFormat = android.text.format.DateFormat.getTimeFormat(getActivity());
		mDateFormat = android.text.format.DateFormat.getLongDateFormat(getActivity());

		if (mSetup.keepScreenOn())
			mContainer.setKeepScreenOn(true);

		if (mSetup.showDate() == false)
			mDate.setVisibility(View.GONE);


		mSettings.setOnClickListener(this);
		mGridView.setOnClickListener(this);

		createApplications();

		return view;
	}

	private void createApplications() {
		mContainer.removeAllViews();

		mGridX = mSetup.getGridX();
		mGridY = mSetup.getGridY();

		if (mGridX < 2)
			mGridX = 2;
		if (mGridY < 1)
			mGridY = 1;

		int marginX = Utils.getPixelFromDp(getContext(), mSetup.getMarginX());
		int marginY = Utils.getPixelFromDp(getContext(), mSetup.getMarginY());

		boolean showNames = mSetup.showNames();

		mApplications = new ApplicationView[mGridY][mGridX];

		int position = 0;
		for (int y = 0; y < mGridY; y++) {
			LinearLayout ll = new LinearLayout(getContext());
			ll.setOrientation(LinearLayout.HORIZONTAL);
			ll.setGravity(Gravity.CENTER_VERTICAL);
			ll.setFocusable(false);
			ll.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
			));

			for (int x = 0; x < mGridX; x++) {
				ApplicationView av = new ApplicationView(getContext());
				av.setOnClickListener(this);
				av.setOnLongClickListener(this);
				av.setOnMenuOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						onLongClick(v);
					}
				});
				av.setPosition(position++);
				av.showName(showNames);
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
					av.setId(0x00FFFFFF + position);
				} else {
					av.setId(View.generateViewId());
				}
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
				lp.setMargins(marginX, marginY, marginX, marginY);
				av.setLayoutParams(lp);
				ll.addView(av);
				mApplications[y][x] = av;
			}
			mContainer.addView(ll);
		}

		updateApplications();
		setApplicationOrder();
	}

	private void setApplicationOrder() {
		for (int y = 0; y < mGridY; y++) {
			for (int x = 0; x < mGridX; x++) {
				int upId = R.id.application_grid;
				int downId = R.id.settings;
				int leftId = R.id.application_grid;
				int rightId = R.id.settings;

				if (y > 0)
					upId = mApplications[y - 1][x].getId();

				if (y + 1 < mGridY)
					downId = mApplications[y + 1][x].getId();

				if (x > 0)
					leftId = mApplications[y][x - 1].getId();
				else if (y > 0)
					leftId = mApplications[y - 1][mGridX - 1].getId();

				if (x + 1 < mGridX)
					rightId = mApplications[y][x + 1].getId();
				else if (y + 1 < mGridY)
					rightId = mApplications[y + 1][0].getId();

				mApplications[y][x].setNextFocusLeftId(leftId);
				mApplications[y][x].setNextFocusRightId(rightId);
				mApplications[y][x].setNextFocusUpId(upId);
				mApplications[y][x].setNextFocusDownId(downId);
			}
		}

		mGridView.setNextFocusLeftId(R.id.settings);
		mGridView.setNextFocusRightId(mApplications[0][0].getId());
		mGridView.setNextFocusUpId(R.id.settings);
		mGridView.setNextFocusDownId(mApplications[0][0].getId());

		mSettings.setNextFocusLeftId(mApplications[mGridY - 1][mGridX - 1].getId());
		mSettings.setNextFocusRightId(R.id.application_grid);
		mSettings.setNextFocusUpId(mApplications[mGridY - 1][mGridX - 1].getId());
		mSettings.setNextFocusDownId(R.id.application_grid);
	}


	private void updateApplications() {
		PackageManager pm = getActivity().getPackageManager();
		SharedPreferences prefs = getActivity().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

		for (int y = 0; y < mGridY; y++) {
			for (int x = 0; x < mGridX; x++) {
				ApplicationView app = mApplications[y][x];
				setApplication(pm, app, prefs.getString(app.getPreferenceKey(), null));
			}
		}
	}


	private void restartActivity() {

		Intent intent = getActivity().getIntent();
		getActivity().finish();
		startActivity(intent);
	}


	private void writePreferences(int appNum, String packageName) {
		SharedPreferences prefs = getActivity().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		String key = ApplicationView.getPreferenceKey(appNum);

		if (TextUtils.isEmpty(packageName))
			editor.remove(key);
		else
			editor.putString(key, packageName);

		editor.apply();
	}

	private void setApplication(PackageManager pm, ApplicationView app, String packageName) {
		try {

			if (TextUtils.isEmpty(packageName) == false) {
				PackageInfo pi = pm.getPackageInfo(packageName, 0);
				if (pi != null) {
					AppInfo appInfo = new AppInfo(pm, pi.applicationInfo);
					app.setImageDrawable(appInfo.getIcon())
							.setText(appInfo.getName())
							.setPackageName(appInfo.getPackageName());
				}
			} else {
				app.setImageResource(R.drawable.ic_add)
						.setText("")
						.setPackageName(null);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		setClock();

		mHandler.postDelayed(mTimerTick, 1000);
	}

	@Override
	public void onPause() {
		super.onPause();
		mHandler.removeCallbacks(mTimerTick);

	}

	private void setClock() {
		// Получаем текущую дату и время
		Date date = new Date(System.currentTimeMillis());

		// Проверяем, используется ли 24-часовой формат
		boolean is24HourFormat = android.text.format.DateFormat.is24HourFormat(getContext());

		// Устанавливаем формат времени в зависимости от настроек устройства
		java.text.DateFormat timeFormat = is24HourFormat ?
				new java.text.SimpleDateFormat("HH:mm:ss") :
				new java.text.SimpleDateFormat("hh:mm:ss a");

		mClock.setText(timeFormat.format(date));
		mDate.setText(mDateFormat.format(date));

		// Обновляем время каждую секунду
		mHandler.postDelayed(mTimerTick, 1000);
	}

	@Override
	public boolean onLongClick(View v) {
		if (v instanceof ApplicationView) {
			ApplicationView appView = (ApplicationView) v;
			if (appView.hasPackage() && mSetup.iconsLocked()) {
				Toast.makeText(getActivity(), R.string.home_locked, Toast.LENGTH_SHORT).show();
			} else {
				openApplicationList(ApplicationList.VIEW_LIST, appView.getPosition(), appView.hasPackage());
			}
			return (true);
		}
		return (false);
	}

	@Override
	public void onClick(View v) {
		if (v instanceof ApplicationView) {
			openApplication((ApplicationView) v);
			return;
		}

		int id = v.getId();
		if (id == R.id.application_grid) {
			openApplicationList(ApplicationList.VIEW_GRID, 0, false);
		} else if (id == R.id.settings) {
			Intent intent = new Intent(getContext(), Preferences.class);
			preferencesLauncher.launch(intent);
		}
	}

	private void openApplication(ApplicationView v) {
		if (v.hasPackage() == false) {
			openApplicationList(ApplicationList.VIEW_LIST, v.getPosition(), false);
			return;
		}

		try {
			Toast.makeText(getActivity(), v.getName(), Toast.LENGTH_SHORT).show();
			startActivity(getLaunchIntentForPackage(v.getPackageName()));
		} catch (Exception e) {
			Toast.makeText(getActivity(), v.getName() + " : " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void openApplication(String packageName) {
		try {
			Intent startApp = getLaunchIntentForPackage(packageName);
			Toast.makeText(getActivity(), packageName, Toast.LENGTH_SHORT).show();
			startActivity(startApp);
		} catch (Exception e) {
			Toast.makeText(getActivity(), packageName + " : " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void openApplicationList(int viewType, int appNum, boolean showDelete) {
		Intent intent = new Intent(getActivity(), ApplicationList.class);
		intent.putExtra(ApplicationList.APPLICATION_NUMBER, appNum);
		intent.putExtra(ApplicationList.VIEW_TYPE, viewType);
		intent.putExtra(ApplicationList.SHOW_DELETE, showDelete);
		applicationListLauncher.launch(intent);
	}

	private Intent getLaunchIntentForPackage(String packageName) {
		PackageManager pm = getActivity().getPackageManager();
		Intent launchIntent = pm.getLaunchIntentForPackage(packageName);

		if (launchIntent == null) {
			launchIntent = pm.getLeanbackLaunchIntentForPackage(packageName);
		}

		return launchIntent;
	}
}

