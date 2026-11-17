package app.tuxguitar.android.view.popup;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatDialog;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.DialogFragment;

import app.tuxguitar.android.R;
import app.tuxguitar.android.activity.TGActivity;
import app.tuxguitar.android.fragment.impl.TGPreferencesFragment;

/**
 * A menu that grows out of the button that opened it (blurred blob -> rounded card),
 * covering only part of the screen. Used by the two floating buttons at the top.
 */
public class TGMorphDialog extends DialogFragment {

	public static final int KIND_MENU = 1;
	public static final int KIND_SETTINGS = 2;

	private static final String TAG = "tg-morph-dialog";
	private static final long OPEN_DURATION = 420;
	private static final long CLOSE_DURATION = 200;
	private static final float BLUR_RADIUS = 40f;

	private int kind;
	private int[] anchorRect;
	private Runnable onDismiss;

	private FrameLayout root;
	private View card;
	private boolean closing;

	public static TGMorphDialog show(TGActivity activity, View anchor, int kind) {
		return show(activity, anchor, kind, null);
	}

	public static TGMorphDialog show(TGActivity activity, View anchor, int kind, Runnable onDismiss) {
		if( activity.getSupportFragmentManager().findFragmentByTag(TAG) != null ) {
			return null;
		}
		TGMorphDialog dialog = new TGMorphDialog();
		dialog.kind = kind;
		dialog.anchorRect = TGMorphMotion.getScreenRect(anchor);
		dialog.onDismiss = onDismiss;
		dialog.show(activity.getSupportFragmentManager(), TAG);
		return dialog;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if( this.anchorRect == null ) {
			// recreated by the system (e.g. rotation): the anchor is gone, just close
			this.dismissAllowingStateLoss();
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AppCompatDialog dialog = new AppCompatDialog(requireContext(), R.style.TGMorphDialog);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
			public boolean onKey(android.content.DialogInterface d, int keyCode, KeyEvent event) {
				if( keyCode == KeyEvent.KEYCODE_BACK ) {
					if( event.getAction() == KeyEvent.ACTION_UP ) {
						TGMorphDialog.this.dismissAnimated();
					}
					return true;
				}
				return false;
			}
		});
		// predictive back (Android 13+) goes through the dispatcher, not key events
		dialog.getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
			public void handleOnBackPressed() {
				TGMorphDialog.this.dismissAnimated();
			}
		});
		return dialog;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
		float density = getResources().getDisplayMetrics().density;
		int screenWidth = getResources().getDisplayMetrics().widthPixels;
		int screenHeight = getResources().getDisplayMetrics().heightPixels;

		this.root = new FrameLayout(requireContext());
		this.root.setVisibility(View.INVISIBLE);
		this.root.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View view, MotionEvent event) {
				if( event.getAction() == MotionEvent.ACTION_DOWN ) {
					TGMorphDialog.this.dismissAnimated();
				}
				return true;
			}
		});

		FrameLayout cardFrame = new FrameLayout(requireContext());
		cardFrame.setBackgroundResource(R.drawable.bg_popup);
		cardFrame.setElevation(8 * density);
		cardFrame.setClipToOutline(true);
		cardFrame.setClickable(true);

		int width;
		int height;
		if( this.kind == KIND_SETTINGS ) {
			width = Math.min((int) (360 * density), screenWidth - (int) (24 * density));
			height = (int) (screenHeight * 0.66f);
			FrameLayout container = new FrameLayout(requireContext());
			container.setId(R.id.tg_settings_container);
			cardFrame.addView(container, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		} else {
			width = Math.min((int) (300 * density), screenWidth - (int) (24 * density));
			height = Math.min((int) (screenHeight * 0.6f), (int) (480 * density));
			// the drawer views expect the activity as their context
			View menu = LayoutInflater.from(requireActivity()).inflate(R.layout.view_main_drawer, cardFrame, false);
			cardFrame.addView(menu, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		}
		this.card = cardFrame;
		this.root.addView(cardFrame, new FrameLayout.LayoutParams(width, height));
		// same colour as the floating button, so button and menu read as one body
		this.goo = TGMorphMotion.attachGoo(this.root, cardFrame, com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurfaceBright, Color.WHITE));
		return this.root;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		if( this.kind == KIND_SETTINGS && this.anchorRect != null ) {
			getChildFragmentManager().beginTransaction().replace(R.id.tg_settings_container, new TGPreferencesFragment()).commit();
		}
		this.root.post(new Runnable() {
			public void run() {
				TGMorphDialog.this.placeAndOpen();
			}
		});
	}

	@Override
	public void onStart() {
		super.onStart();
		Window window = (getDialog() != null ? getDialog().getWindow() : null);
		if( window != null ) {
			window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			window.setDimAmount(0f);
			WindowCompat.setDecorFitsSystemWindows(window, false);
		}
	}

	@Override
	public void onDismiss(android.content.DialogInterface dialog) {
		super.onDismiss(dialog);
		if( this.onDismiss != null ) {
			this.onDismiss.run();
		}
	}

	public boolean isOpen() {
		return (this.getDialog() != null && this.getDialog().isShowing() && !this.closing);
	}

	// ------------------------------------------------------------------
	// placement & motion (same feel as the tempo menu)
	// ------------------------------------------------------------------

	private void placeAndOpen() {
		if( this.anchorRect == null || this.card == null ) {
			return;
		}
		int[] rootLocation = new int[2];
		this.root.getLocationOnScreen(rootLocation);
		float density = getResources().getDisplayMetrics().density;
		int margin = (int) (12 * density);
		int gap = (int) (8 * density);

		int anchorLeft = this.anchorRect[0] - rootLocation[0];
		int anchorTop = this.anchorRect[1] - rootLocation[1];
		int anchorRight = this.anchorRect[2] - rootLocation[0];
		int anchorBottom = this.anchorRect[3] - rootLocation[1];
		int anchorCenterX = (anchorLeft + anchorRight) / 2;
		int anchorCenterY = (anchorTop + anchorBottom) / 2;

		int cardWidth = this.card.getLayoutParams().width;
		int cardHeight = this.card.getLayoutParams().height;
		// hang below the button, aligned to the side of the screen it is on
		int x = (anchorCenterX < this.root.getWidth() / 2 ? anchorLeft : anchorRight - cardWidth);
		x = Math.max(margin, Math.min(x, this.root.getWidth() - cardWidth - margin));
		int y = Math.max(margin, Math.min(anchorBottom + gap, this.root.getHeight() - cardHeight - margin));

		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) this.card.getLayoutParams();
		params.leftMargin = x;
		params.topMargin = y;
		this.card.setLayoutParams(params);

		this.motionAnchor = new int[] {anchorLeft, anchorTop, anchorRight, anchorBottom};
		this.motionX = x;
		this.motionY = y;
		this.applyProgress(0f);
		this.root.setVisibility(View.VISIBLE);

		ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
		animator.setDuration(TGMorphMotion.OPEN_DURATION);
		animator.setInterpolator(new android.view.animation.LinearInterpolator());
		animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(ValueAnimator animation) {
				TGMorphDialog.this.applyProgress((Float) animation.getAnimatedValue());
			}
		});
		animator.start();
	}

	private View goo;
	private int[] motionAnchor;
	private int motionX;
	private int motionY;

	private void applyProgress(float progress) {
		if( this.motionAnchor == null ) {
			return;
		}
		int width = (this.card.getLayoutParams().width > 0 ? this.card.getLayoutParams().width : this.card.getWidth());
		int height = (this.card.getLayoutParams().height > 0 ? this.card.getLayoutParams().height : this.card.getHeight());
		if( this.goo != null ) {
			// one liquid shape with the button; the dimming is drawn by the same layer
			TGMorphMotion.applyGoo(this.goo, this.card, this.motionAnchor, this.motionX, this.motionY, width, height, progress);
			return;
		}
		TGMorphMotion.apply(this.card, this.motionAnchor, this.motionX, this.motionY, width, height, progress);
		this.root.setBackgroundColor(Color.argb((int) (0x33 * Math.max(0f, Math.min(1f, progress))), 0, 0, 0));
	}

	public void dismissAnimated() {
		if( this.closing || this.card == null ) {
			return;
		}
		this.closing = true;
		ValueAnimator animator = ValueAnimator.ofFloat(1f, 0f);
		animator.setDuration(TGMorphMotion.CLOSE_DURATION);
		animator.setInterpolator(new android.view.animation.LinearInterpolator());
		animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(ValueAnimator animation) {
				TGMorphDialog.this.applyProgress((Float) animation.getAnimatedValue());
			}
		});
		animator.addListener(new AnimatorListenerAdapter() {
			public void onAnimationEnd(Animator animation) {
				TGMorphDialog.this.dismissAllowingStateLoss();
			}
		});
		animator.start();
	}
}
