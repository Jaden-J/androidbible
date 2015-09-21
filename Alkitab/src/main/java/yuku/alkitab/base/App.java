package yuku.alkitab.base;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.multidex.MultiDex;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.ViewConfiguration;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;
import com.google.gson.Gson;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.model.VersionImpl;
import yuku.alkitab.base.sync.Gcm;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.debug.R;
import yuku.alkitab.reminder.util.DevotionReminder;
import yuku.alkitabfeedback.FeedbackSender;
import yuku.alkitabintegration.display.Launcher;
import yuku.kirimfidbek.CrashReporter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Locale;

public class App extends yuku.afw.App {
	public static final String TAG = App.class.getSimpleName();

	private static boolean initted = false;
	private static Tracker APP_TRACKER;

	enum OkHttpClientWrapper {
		INSTANCE;

		OkHttpClient httpClient = new OkHttpClient();
	}

	enum GsonWrapper {
		INSTANCE;

		Gson gson = new Gson();
	}

	public static String downloadString(String url) throws IOException {
		return OkHttpClientWrapper.INSTANCE.httpClient.newCall(new Request.Builder().url(url).build()).execute().body().string();
	}

	public static byte[] downloadBytes(String url) throws IOException {
		return OkHttpClientWrapper.INSTANCE.httpClient.newCall(new Request.Builder().url(url).build()).execute().body().bytes();
	}

	public static Call downloadCall(String url) {
		return OkHttpClientWrapper.INSTANCE.httpClient.newCall(new Request.Builder().url(url).build());
	}

	public static OkHttpClient getOkHttpClient() {
		return OkHttpClientWrapper.INSTANCE.httpClient;
	}

	@Override public void onCreate() {
		super.onCreate();

		staticInit();

		{ // Google Analytics V4
			// This can't be in staticInit because we need the Application instance.
			final GoogleAnalytics analytics = GoogleAnalytics.getInstance(context);
			final Tracker t = analytics.newTracker(context.getString(R.string.ga_trackingId));
			t.enableAutoActivityTracking(true);
			t.enableAdvertisingIdCollection(true);
			APP_TRACKER = t;
			analytics.enableAutoActivityReports(this);
		}
	}

	public synchronized static void staticInit() {
		if (initted) return;
		initted = true;

		final CrashReporter cr = new CrashReporter();
		cr.activateDefaultUncaughtExceptionHandler();
		cr.trySend();

		final FeedbackSender fs = FeedbackSender.getInstance(context);
		fs.trySend();

		PreferenceManager.setDefaultValues(context, R.xml.settings_display, false);
		PreferenceManager.setDefaultValues(context, R.xml.settings_usage, false);
		PreferenceManager.setDefaultValues(context, R.xml.secret_settings, false);
		PreferenceManager.setDefaultValues(context, R.xml.sync_settings, false);

		updateConfigurationWithPreferencesLocale();

		// all activities need at least the activeVersion from S, so initialize it here.
		synchronized (S.class) {
			if (S.activeVersion == null) {
				S.activeVersion = VersionImpl.getInternalVersion();
			}
		}

		// also pre-calculate calculated preferences value here
		S.calculateAppliedValuesBasedOnPreferences();

		{ // GCM
			Gcm.renewGcmRegistrationIdIfNeeded(Sync::notifyNewGcmRegistrationId);
		}

		DevotionReminder.scheduleAlarm(context);

		forceOverflowMenu();

		// make sure launcher do not open other variants of the app
		Launcher.setAppPackageName(context.getPackageName());
	}

	private static void forceOverflowMenu() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			return; // no need to do anything, it is already forced on KitKat
		}

		final ViewConfiguration config = ViewConfiguration.get(context);
		try {
			final Field sHasPermanentMenuKey = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
			sHasPermanentMenuKey.setAccessible(true);
			sHasPermanentMenuKey.setBoolean(config, false);
		} catch (Exception e) {
			Log.w(TAG, "ViewConfiguration has no sHasPermanentMenuKey field", e);
		}

		try {
			final Field sHasPermanentMenuKeySet = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKeySet");
			sHasPermanentMenuKeySet.setAccessible(true);
			sHasPermanentMenuKeySet.setBoolean(config, true);
		} catch (Exception e) {
			Log.w(TAG, "ViewConfiguration has no sHasPermanentMenuKeySet field", e);
		}
	}

	private static Locale getLocaleFromPreferences() {
		String lang = Preferences.getString(context.getString(R.string.pref_language_key), context.getString(R.string.pref_language_default));
		if (lang == null || "DEFAULT".equals(lang)) { //$NON-NLS-1$
			lang = Locale.getDefault().getLanguage();
		}

		switch (lang) {
			case "zh-CN":
				return Locale.SIMPLIFIED_CHINESE;
			case "zh-TW":
				return Locale.TRADITIONAL_CHINESE;
			default:
				return new Locale(lang);
		}
	}

	@Override public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		Log.d(TAG, "@@onConfigurationChanged: config changed to: " + newConfig); //$NON-NLS-1$
		updateConfigurationWithPreferencesLocale();
	}

	public static void updateConfigurationWithPreferencesLocale() {
		final Configuration config = context.getResources().getConfiguration();
		final Locale locale = getLocaleFromPreferences();
		if (!U.equals(config.locale.getLanguage(), locale.getLanguage()) || !U.equals(config.locale.getCountry(), locale.getCountry())) {
			Log.d(TAG, "@@updateConfigurationWithPreferencesLocale: locale will be updated to: " + locale); //$NON-NLS-1$

			config.locale = locale;
			context.getResources().updateConfiguration(config, null);
		}
	}

	public static LocalBroadcastManager getLbm() {
		return LocalBroadcastManager.getInstance(context);
	}

	public static Gson getDefaultGson() {
		return GsonWrapper.INSTANCE.gson;
	}

	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		MultiDex.install(this);
	}

	public synchronized static Tracker getTracker() {
		return APP_TRACKER;
	}
}
