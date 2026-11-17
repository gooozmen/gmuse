package app.tuxguitar.android.view.tablature;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import androidx.core.content.pm.PackageInfoCompat;

/**
 * Display settings of the score and the app, stored with the other preferences
 * (same file as {@code TGPreferencesFragment}).
 */
public class TGViewSettings {

	public static final String KEY_SMOOTH_CURSOR = "view.smooth.cursor";
	public static final String KEY_UI_SCALE = "view.ui.scale";
	public static final String KEY_SCORE_SCALE = "view.score.scale";
	public static final String KEY_MEASURE_SPLIT = "view.measure.split";

	private static final String MODULE = "tuxguitar";
	private static final String RESOURCE = "settings";

	public static SharedPreferences getPreferences(Context context) {
		StringBuilder name = new StringBuilder("tuxguitar");
		try {
			PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			if( info != null ) {
				name.append("-" + PackageInfoCompat.getLongVersionCode(info));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		name.append("." + MODULE + "-" + RESOURCE);
		return context.getSharedPreferences(name.toString(), Context.MODE_PRIVATE);
	}

	public static boolean isSmoothCursor(Context context) {
		return getPreferences(context).getBoolean(KEY_SMOOTH_CURSOR, true);
	}

	/** Size of the app's interface (buttons, menus, text), 1 = system size. */
	public static float getUiScale(Context context) {
		return parse(getPreferences(context).getString(KEY_UI_SCALE, "1"), 1f);
	}

	/** Size of the notes, 1 = default. */
	public static float getScoreScale(Context context) {
		return parse(getPreferences(context).getString(KEY_SCORE_SCALE, "1"), 1f);
	}

	/** 1 = bars only wrap when wider than the screen; N = every bar is spread out over N pieces. */
	public static int getMeasureSplit(Context context) {
		return Math.max(1, Math.round(parse(getPreferences(context).getString(KEY_MEASURE_SPLIT, "1"), 1f)));
	}

	private static float parse(String value, float fallback) {
		try {
			return Float.parseFloat(value);
		} catch (Exception e) {
			return fallback;
		}
	}
}
