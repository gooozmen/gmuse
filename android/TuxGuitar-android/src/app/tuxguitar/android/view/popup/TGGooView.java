package app.tuxguitar.android.view.popup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RuntimeShader;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.RequiresApi;

/**
 * Draws a button and the menu growing out of it as ONE liquid shape.
 * A shader evaluates the distance to both rounded rectangles and blends them with a
 * smooth minimum, so the two stay joined by a soft neck that stretches while the menu moves.
 * The inside of the button itself is left untouched (the real button shows through).
 */
@RequiresApi(33)
public class TGGooView extends View {

	private static final String SHADER =
		"uniform float2 aC; uniform float2 aH; uniform float aR;" +
		"uniform float2 sC; uniform float2 sH;" +
		"uniform float2 cC; uniform float2 cH; uniform float cR;" +
		"uniform float k; uniform float scrim;" +
		"uniform float2 bMin; uniform float2 bMax;" +
		"layout(color) uniform float4 color;" +
		"float sdRR(float2 p, float2 c, float2 h, float r) {" +
		"  float2 q = abs(p - c) - h + r;" +
		"  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;" +
		"}" +
		"half4 main(float2 p) {" +
		"  float ds = sdRR(p, sC, sH, aR);" +
		"  float dc = sdRR(p, cC, cH, cR);" +
		"  float h = clamp(0.5 + 0.5 * (dc - ds) / k, 0.0, 1.0);" +
		"  float d = mix(dc, ds, h) - k * h * (1.0 - h);" +
		// the smooth union swells where two edges coincide: never leave the shapes' common bounds
		"  d = max(d, max(max(bMin.x - p.x, p.x - bMax.x), max(bMin.y - p.y, p.y - bMax.y)));" +
		"  float inside = clamp(0.5 - d, 0.0, 1.0);" +
		"  float hole = clamp(-0.5 - sdRR(p, aC, aH, aR), 0.0, 1.0);" +
		"  float fill = inside * (1.0 - hole);" +
		"  float4 res = float4(color.rgb * fill, fill) + float4(0.0, 0.0, 0.0, scrim * (1.0 - inside));" +
		"  return half4(res);" +
		"}";

	private static final float SCRIM_ALPHA = 0.2f;
	private static final PathInterpolator SPREAD = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
	private static final OvershootInterpolator POUR = new OvershootInterpolator(1.4f);

	private final RuntimeShader shader = new RuntimeShader(SHADER);
	private final Paint paint = new Paint();
	private final Rect current = new Rect();
	private final float density;

	private int[] anchor;
	private int cardX, cardY, cardWidth, cardHeight;

	public TGGooView(Context context, int color) {
		super(context);
		this.density = context.getResources().getDisplayMetrics().density;
		this.shader.setColorUniform("color", color);
		this.paint.setShader(this.shader);
	}

	/** Button rect {l,t,r,b} and the menu's final rect, both in this view's coordinates. */
	public void setGeometry(int[] anchor, int cardX, int cardY, int cardWidth, int cardHeight) {
		this.anchor = anchor;
		this.cardX = cardX;
		this.cardY = cardY;
		this.cardWidth = cardWidth;
		this.cardHeight = cardHeight;
	}

	/** t: 0 = menu folded into the button, 1 = menu fully open. Returns the menu's current rect. */
	public Rect setProgress(float t) {
		if( this.anchor == null ) {
			return this.current;
		}
		float c = Math.max(0f, Math.min(1f, t));
		// spreads sideways first, then pours along the other axis with a springy overshoot
		float fx = SPREAD.getInterpolation(Math.min(1f, c * 1.15f));
		float fy = POUR.getInterpolation(Math.max(0f, (c - 0.06f) / 0.94f));

		float left = lerp(this.anchor[0], this.cardX, fx);
		float right = lerp(this.anchor[2], this.cardX + this.cardWidth, fx);
		float top = lerp(this.anchor[1], this.cardY, fy);
		float bottom = lerp(this.anchor[3], this.cardY + this.cardHeight, fy);
		this.current.set(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));

		float anchorHalfW = (this.anchor[2] - this.anchor[0]) / 2f;
		float anchorHalfH = (this.anchor[3] - this.anchor[1]) / 2f;
		float anchorRadius = Math.min(anchorHalfW, anchorHalfH);
		float halfW = Math.max(1f, (right - left) / 2f);
		float halfH = Math.max(1f, (bottom - top) / 2f);
		float radius = Math.min(lerp(anchorRadius, 28f * this.density, c), Math.min(halfW, halfH));
		// thicker, stretchier neck while moving; a calm joint at rest
		float k = (16f + 10f * (float) Math.sin(Math.PI * c)) * this.density;

		this.shader.setFloatUniform("aC", (this.anchor[0] + this.anchor[2]) / 2f, (this.anchor[1] + this.anchor[3]) / 2f);
		this.shader.setFloatUniform("aH", anchorHalfW, anchorHalfH);
		this.shader.setFloatUniform("aR", anchorRadius);
		this.shader.setFloatUniform("cC", (left + right) / 2f, (top + bottom) / 2f);
		this.shader.setFloatUniform("cH", halfW, halfH);
		this.shader.setFloatUniform("cR", radius);
		// the button itself is stretched (same width, same round ends) until it reaches into the menu,
		// so the neck is exactly as wide as the button and has no corners
		float anchorCX = (this.anchor[0] + this.anchor[2]) / 2f;
		float anchorCY = (this.anchor[1] + this.anchor[3]) / 2f;
		// reach past the menu's corner by a whole button radius, so the stem's round end sits
		// inside the menu's straight edge (otherwise its curve and the corner curve leave a notch)
		float reach = (anchorCY <= (top + bottom) / 2f ? Math.min(top + radius + anchorRadius, bottom) : Math.max(bottom - radius - anchorRadius, top));
		float stemTop = Math.min(this.anchor[1], reach);
		float stemBottom = Math.max(this.anchor[3], reach);
		float stemCX = Math.max(left + anchorHalfW, Math.min(anchorCX, right - anchorHalfW));
		this.shader.setFloatUniform("sC", stemCX, (stemTop + stemBottom) / 2f);
		this.shader.setFloatUniform("sH", Math.min(anchorHalfW, Math.max(1f, halfW)), (stemBottom - stemTop) / 2f);
		float stemHalfW = Math.min(anchorHalfW, Math.max(1f, halfW));
		this.shader.setFloatUniform("bMin", Math.min(left, stemCX - stemHalfW), Math.min(top, stemTop));
		this.shader.setFloatUniform("bMax", Math.max(right, stemCX + stemHalfW), Math.max(bottom, stemBottom));
		this.shader.setFloatUniform("k", k);
		this.shader.setFloatUniform("scrim", SCRIM_ALPHA * c);
		this.invalidate();
		return this.current;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if( this.anchor != null ) {
			canvas.drawPaint(this.paint);
		}
	}

	private static float lerp(float from, float to, float f) {
		return from + (to - from) * f;
	}
}
