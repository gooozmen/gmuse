package app.tuxguitar.android.fragment.impl;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.OvershootInterpolator;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import app.tuxguitar.android.view.popup.TGMorphDialog;

import app.tuxguitar.android.R;
import app.tuxguitar.android.fragment.TGCachedFragment;
import app.tuxguitar.android.menu.controller.impl.fragment.TGMainMenu;

public class TGMainFragment extends TGCachedFragment {

	public TGMainFragment() {
		super(R.layout.view_main);
	}

	@Override
	public void onPostCreate(Bundle savedInstanceState) {
		this.attachInstance();
		this.createActionBar(true, false, (String) null);
		// The main screen has no top bar: two floating buttons replace it.
		this.findActionBar().hide();
	}

	@Override
	public void onResume() {
		super.onResume();
		this.findActionBar().hide();
		this.findActivity().setFullBleed(true, new Runnable() {
			public void run() {
				TGMainFragment.this.applyInsets();
			}
		});
		this.applySystemBarIcons(true);
	}

	@Override
	public void onPause() {
		super.onPause();
		this.findActivity().setFullBleed(false, null);
		this.applySystemBarIcons(false);
	}

	/**
	 * The score paper runs up behind the status bar; the two buttons sit right under it,
	 * and the bottom sheet reaches down behind the navigation bar.
	 */
	private void applyInsets() {
		View view = this.getView();
		if( view == null ) {
			return;
		}
		Insets bars = this.findActivity().getSystemBarInsets();
		float density = getResources().getDisplayMetrics().density;
		int gap = (int) (8 * density);
		int side = (int) (12 * density);

		this.setMargins(view.findViewById(R.id.main_menu_button), bars.left + side, bars.top + gap, -1);
		this.setMargins(view.findViewById(R.id.main_settings_button), -1, bars.top + gap, bars.right + side);

		View body = view.findViewById(R.id.main_body);
		// the score runs up to the top edge; it starts below the floating buttons
		// (so they never cover the clef) but scrolls up under them: no dead strip
		int buttonsSpace = gap + (int) (44 * density) + (int) (12 * density);
		body.setPadding(bars.left, 0, bars.right, body.getPaddingBottom());
		View songView = view.findViewById(R.id.tgSongView);
		if( songView instanceof app.tuxguitar.android.view.tablature.TGSongView ) {
			((app.tuxguitar.android.view.tablature.TGSongView) songView).setContentInsetTop(bars.top + buttonsSpace);
		}

		View sheet = view.findViewById(R.id.main_bottom);
		if( sheet != null ) {
			sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(), sheet.getPaddingRight(), gap + bars.bottom);
			sheet.requestLayout();
		}
	}

	private void setMargins(View target, int left, int top, int right) {
		ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) target.getLayoutParams();
		if( left >= 0 ) params.leftMargin = left;
		if( right >= 0 ) params.rightMargin = right;
		params.topMargin = top;
		target.setLayoutParams(params);
	}

	/** Dark status icons over the white score; theme default elsewhere. */
	private void applySystemBarIcons(boolean scoreScreen) {
		Window window = this.findActivity().getWindow();
		boolean themeLight = getResources().getBoolean(R.bool.tg_light_system_bars);
		WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
		controller.setAppearanceLightStatusBars(scoreScreen || themeLight);
		controller.setAppearanceLightNavigationBars(themeLight);
	}

	@Override
	public void onPostInflateView() {
		this.getView().findViewById(R.id.main_menu_button).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGMainFragment.this.findActivity().getDrawerManager().openMenu(view);
			}
		});
		this.getView().findViewById(R.id.main_settings_button).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				// 8 teeth: a 45 degree turn lands on the same silhouette
				view.animate().rotationBy(45f).setDuration(450).setInterpolator(new OvershootInterpolator(2f)).start();
				TGMorphDialog.show(TGMainFragment.this.findActivity(), view, TGMorphDialog.KIND_SETTINGS);
			}
		});
	}

	@Override
	public void onPostCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
		TGMainMenu.getInstance(this.findContext()).inflate(menu, menuInflater);
	}

	public View findChildViewById(int id) {
		if( this.getView() != null ) {
			return this.getView().findViewById(id);
		}
		return null;
	}

	public View getTopView() {
		return this.findChildViewById(R.id.main_top);
	}

	public View getBottomView() {
		return this.findChildViewById(R.id.main_bottom);
	}

	public View getLeftView() {
		return this.findChildViewById(R.id.main_left);
	}

	public View getRightView() {
		return this.findChildViewById(R.id.main_right);
	}

	public View getBodyView() {
		return this.findChildViewById(R.id.main_body);
	}

	public void attachInstance() {
		TGMainFragmentController.getInstance(this.findContext()).attachInstance(this);
	}
}
