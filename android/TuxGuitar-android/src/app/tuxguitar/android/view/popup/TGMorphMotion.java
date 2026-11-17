package app.tuxguitar.android.view.popup;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

/**
 * "Flows out of the button" motion shared by the floating menus.
 * At progress 0 the card sits exactly on the button (same position and size); it then
 * spreads sideways first and pours down after, with the content fading in while the blur clears.
 * Drive it with a linear 0..1 animator: the easing lives here.
 */
public final class TGMorphMotion {

	public static final long OPEN_DURATION = 560;
	public static final long CLOSE_DURATION = 300;

	private static final float BLUR_RADIUS = 36f;
	private static final PathInterpolator SPREAD = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
	private static final PathInterpolator POUR = new PathInterpolator(0.2f, 0f, 0f, 1f);

	private TGMorphMotion() {
	}

	/**
	 * @param card the menu card, laid out at its final place (cardX, cardY, cardWidth, cardHeight)
	 * @param anchor the button rect {left, top, right, bottom} in the same coordinates as the card
	 */
	public static void apply(View card, int[] anchor, int cardX, int cardY, int cardWidth, int cardHeight, float t) {
		if( cardWidth <= 0 || cardHeight <= 0 ) {
			return;
		}
		float fx = SPREAD.getInterpolation(clamp(t * 1.15f));
		float fy = POUR.getInterpolation(clamp((t - 0.08f) / 0.92f));

		float startScaleX = (anchor[2] - anchor[0]) / (float) cardWidth;
		float startScaleY = (anchor[3] - anchor[1]) / (float) cardHeight;

		card.setPivotX(0f);
		card.setPivotY(0f);
		card.setScaleX(startScaleX + (1f - startScaleX) * fx);
		card.setScaleY(startScaleY + (1f - startScaleY) * fy);
		card.setTranslationX((anchor[0] - cardX) * (1f - fx));
		card.setTranslationY((anchor[1] - cardY) * (1f - fy));
		card.setAlpha(clamp(t * 5f));

		// content appears once the shape has mostly formed
		float contentAlpha = clamp((t - 0.3f) / 0.45f);
		if( card instanceof ViewGroup ) {
			ViewGroup group = (ViewGroup) card;
			for( int i = 0; i < group.getChildCount(); i++ ) {
				group.getChildAt(i).setAlpha(contentAlpha);
			}
		}

		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ) {
			float radius = BLUR_RADIUS * (1f - t) * (1f - t);
			card.setRenderEffect(radius > 0.5f ? RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL) : null);
		}
	}

	/** True when the liquid (shader) version can be used on this device. */
	/**
	 * The view's untransformed rect {l,t,r,b} on screen. getLocationOnScreen() follows the view's
	 * rotation (the settings button keeps turning by 45 degrees), which would move the menu's origin.
	 */
	public static int[] getScreenRect(View view) {
		int[] location = new int[2];
		android.view.ViewParent parent = view.getParent();
		if( parent instanceof View ) {
			((View) parent).getLocationOnScreen(location);
		}
		int left = location[0] + view.getLeft() + Math.round(view.getTranslationX());
		int top = location[1] + view.getTop() + Math.round(view.getTranslationY());
		return new int[] {left, top, left + view.getWidth(), top + view.getHeight()};
	}

	public static boolean isGooAvailable() {
		return (Build.VERSION.SDK_INT >= 33);
	}

	/** Adds the liquid layer behind the card and strips the card's own background. Returns null on older devices. */
	public static View attachGoo(ViewGroup root, final View card, int color) {
		if( !isGooAvailable() ) {
			return null;
		}
		TGGooView goo = new TGGooView(root.getContext(), color);
		root.addView(goo, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		card.setBackground(null);
		card.setElevation(0f);
		final float radius = 28f * root.getResources().getDisplayMetrics().density;
		card.setOutlineProvider(new android.view.ViewOutlineProvider() {
			public void getOutline(View view, android.graphics.Outline outline) {
				outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
			}
		});
		card.setClipToOutline(true);
		return goo;
	}

	/** Liquid motion: the shape is drawn by the goo layer; the card only reveals its content inside it. */
	public static void applyGoo(View gooView, View card, int[] anchor, int cardX, int cardY, int cardWidth, int cardHeight, float t) {
		if( Build.VERSION.SDK_INT < 33 || cardWidth <= 0 || cardHeight <= 0 ) {
			return;
		}
		TGGooView goo = (TGGooView) gooView;
		goo.setGeometry(anchor, cardX, cardY, cardWidth, cardHeight);
		android.graphics.Rect shape = goo.setProgress(t);

		if( t >= 1f ) {
			card.setClipBounds(null);
		} else {
			card.setClipBounds(new android.graphics.Rect(shape.left - cardX, shape.top - cardY, shape.right - cardX, shape.bottom - cardY));
		}
		card.setAlpha(1f);
		float contentAlpha = clamp((t - 0.35f) / 0.45f);
		if( card instanceof ViewGroup ) {
			ViewGroup group = (ViewGroup) card;
			for( int i = 0; i < group.getChildCount(); i++ ) {
				group.getChildAt(i).setAlpha(contentAlpha);
			}
		}
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}
}
