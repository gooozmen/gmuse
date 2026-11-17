package app.tuxguitar.android.application;

import android.content.Context;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import com.google.android.material.color.DynamicColors;

public class TGApplication extends MultiDexApplication {

	public TGApplication() {
		super();
	}

	@Override
	public void onCreate() {
		super.onCreate();

		// Material You: on Android 12+ take the colour roles from the user's wallpaper palette.
		DynamicColors.applyToActivitiesIfAvailable(this);
	}

	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);

		MultiDex.install(this);
	}
}
