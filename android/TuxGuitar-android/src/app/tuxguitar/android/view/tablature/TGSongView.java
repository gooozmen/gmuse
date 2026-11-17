package app.tuxguitar.android.view.tablature;

import app.tuxguitar.android.application.TGApplicationUtil;
import app.tuxguitar.android.graphics.TGPainterImpl;
import app.tuxguitar.android.transport.TGTransport;
import app.tuxguitar.android.transport.TGTransportCache;
import app.tuxguitar.android.view.sheet.TGQuickSheetController;
import app.tuxguitar.graphics.control.TGLayout;
import app.tuxguitar.graphics.control.TGTrackImpl;
import app.tuxguitar.player.base.MidiPlayerMode;
import app.tuxguitar.editor.TGEditorManager;
import app.tuxguitar.ui.resource.UIPainter;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.graphics.control.TGBeatImpl;
import app.tuxguitar.graphics.control.TGMeasureImpl;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGException;
import app.tuxguitar.util.error.TGErrorManager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.google.android.material.color.MaterialColors;

import java.util.Iterator;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class TGSongView extends View {

	private TGContext context;
	private TGSongViewController controller;
	private TGSongViewGestureDetector gestureDetector;

	private Bitmap bufferedBitmap;
	private boolean painting;

	public TGSongView(Context context) {
		super(context);
	}

	public TGSongView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TGSongView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		this.context = TGApplicationUtil.findContext(this);
		this.controller = TGSongViewController.getInstance(this.context);
		this.controller.setSongView(this);
		this.controller.applyViewSettings(getContext());
		this.gestureDetector = new TGSongViewGestureDetector(getContext(), this);
	}

	public float getDefaultScale() {
		// the system density: the app's interface size setting must not resize the notes
		return android.content.res.Resources.getSystem().getDisplayMetrics().density * TGViewSettings.getScoreScale(getContext());
	}

	// ------------------------------------------------------------------
	// room at the top: the score starts below the floating buttons but scrolls under them
	// ------------------------------------------------------------------

	private int contentInsetTop;

	public void setContentInsetTop(int inset) {
		if( this.contentInsetTop != inset ) {
			this.contentInsetTop = inset;
			this.redraw();
		}
	}

	public int getContentInsetTop() {
		return this.contentInsetTop;
	}

	public float getMinimumScale() {
		return (this.getDefaultScale() / 2f);
	}

	public float getMaximumScale() {
		return (this.getDefaultScale() * 2f);
	}

	public void redraw() {
		this.setPainting(true);
		this.postInvalidate();
	}

	public void paintBuffer(Canvas canvas) {
		try {
			UIRectangle area = createClientArea(canvas);

			UIPainter painter = createBufferedPainter(area);

			this.paintArea(painter, area);

			if (this.controller.getScalePreview() != TGSongViewController.EMPTY_SCALE) {
				float currentSale = (1 / (this.controller.getLayout().getScale()) * this.controller.getScalePreview());
				((TGPainterImpl) painter).getCanvas().scale(currentSale, currentSale);
			}

			this.paintTablature(painter, area);

			painter.dispose();
		} catch (Throwable throwable) {
			TGErrorManager.getInstance(this.context).handleError(throwable);
		}
	}

	public void paintArea(UIPainter painter, UIRectangle area) {
		painter.setBackground(this.controller.getResourceFactory().createColor(255, 255, 255));
		painter.initPath(UIPainter.PATH_FILL);
		painter.addRectangle(area.getX(), area.getY(), area.getWidth(), area.getHeight());
		painter.closePath();
	}

	public void paintTablature(UIPainter painter, UIRectangle area) {
		if (this.controller.getSong() != null) {
			this.controller.getLayoutPainter().paint(painter, area, -this.getPaintableScrollX(), this.contentInsetTop - this.getPaintableScrollY());
			this.paintLoopRange(painter);
			this.paintCaret(painter);

			this.controller.updateScroll(area);

			if(!MidiPlayer.getInstance(this.context).isRunning() ) {
				this.followSuspended = false;
			}
			if (MidiPlayer.getInstance(this.context).isRunning()) {
				this.paintTablaturePlayMode(painter, area);
			}
			// Si no estoy reproduciendo y hay cambios
			// muevo el scroll al compas seleccionado
			else if (this.controller.getCaret().hasChanges()) {
				// Mover el scroll puede necesitar redibujar
				// por eso es importante desmarcar los cambios antes de hacer el
				// moveScrollTo
				this.controller.getCaret().setChanges(false);

				this.moveScrollTo(this.controller.getCaret().getMeasure(), area);
			}
		}
	}

	private Paint loopFillPaint;
	private Paint loopEdgePaint;

	/** Tints the looped bars (or the first bar picked so far) over the score. */
	public void paintLoopRange(UIPainter painter) {
		if(!(painter instanceof TGPainterImpl) ) {
			return;
		}
		MidiPlayerMode mode = MidiPlayer.getInstance(this.context).getMode();
		int first;
		int last;
		int pickStart = TGQuickSheetController.getInstance(this.context).getLoopPickStart();
		if( pickStart > 0 ) {
			first = last = pickStart;
		} else if( mode.isLoop() && (mode.getLoopSHeader() > 0 || mode.getLoopEHeader() > 0) ) {
			first = (mode.getLoopSHeader() > 0 ? mode.getLoopSHeader() : 1);
			last = (mode.getLoopEHeader() > 0 ? mode.getLoopEHeader() : Integer.MAX_VALUE);
		} else {
			return;
		}
		if( this.loopFillPaint == null ) {
			int color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0xFF825500);
			this.loopFillPaint = new Paint();
			this.loopFillPaint.setStyle(Paint.Style.FILL);
			this.loopFillPaint.setColor((color & 0x00FFFFFF) | 0x26000000);
			this.loopEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			this.loopEdgePaint.setStyle(Paint.Style.FILL);
			this.loopEdgePaint.setColor(color);
		}
		Canvas canvas = ((TGPainterImpl) painter).getCanvas();
		TGLayout layout = this.controller.getLayout();
		float edge = 3f * getResources().getDisplayMetrics().density;
		Iterator<?> tracks = this.controller.getSong().getTracks();
		while( tracks.hasNext() ) {
			TGTrackImpl track = (TGTrackImpl) tracks.next();
			Iterator<?> measures = track.getMeasures();
			while( measures.hasNext() ) {
				TGMeasureImpl measure = (TGMeasureImpl) measures.next();
				int number = measure.getNumber();
				if( number < first || number > last || measure.isOutOfBounds() || measure.getTs() == null ) {
					continue;
				}
				java.util.List<TGLayoutVerticalSplit.Part> parts = (this.getSplitLayout() != null ? this.getSplitLayout().getParts(measure) : null);
				if( parts != null ) {
					for( TGLayoutVerticalSplit.Part part : parts ) {
						float x = part.x;
						float y = part.y;
						float w = part.getWidth();
						float h = part.ts.getSize();
						canvas.drawRect(x, y, x + w, y + h, this.loopFillPaint);
						if( number == first && part.index == 0 ) {
							canvas.drawRect(x, y, x + edge, y + h, this.loopEdgePaint);
						}
						if( (number == last || pickStart > 0) && part.index == part.count - 1 ) {
							canvas.drawRect(x + w - edge, y, x + w, y + h, this.loopEdgePaint);
						}
					}
					continue;
				}
				float x = measure.getPosX();
				float y = measure.getPosY();
				float w = (measure.getWidth(layout) + measure.getSpacing());
				float h = measure.getTs().getSize();
				canvas.drawRect(x, y, x + w, y + h, this.loopFillPaint);
				if( number == first ) {
					canvas.drawRect(x, y, x + edge, y + h, this.loopEdgePaint);
				}
				if( number == last || pickStart > 0 ) {
					canvas.drawRect(x + w - edge, y, x + w, y + h, this.loopEdgePaint);
				}
			}
		}
	}

	public void paintTablaturePlayMode(UIPainter painter, UIRectangle area) {
		TGTransportCache transportCache = TGTransport.getInstance(this.context).getCache();
		TGMeasureImpl measure = transportCache.getPlayMeasure();
		TGBeatImpl beat = transportCache.getPlayBeat();
		if (measure != null && measure.hasTrack(this.controller.getCaret().getTrack().getNumber())) {
			if( this.getSplitLayout() != null ) {
				this.getSplitLayout().applyPartForBeat(measure, beat);
			}
			if( this.followSuspended && this.isMeasureVisible(measure, area) ) {
				// the user scrolled back to the playing bar: follow it again from here
				this.followSuspended = false;
			}
			if(!this.followSuspended ) {
				this.moveScrollTo(measure, area);
			}

			this.paintPlayModeParts(painter, measure, beat);
		}
	}

	private TGLayoutVerticalSplit getSplitLayout() {
		TGLayout layout = this.controller.getLayout();
		return (layout instanceof TGLayoutVerticalSplit ? (TGLayoutVerticalSplit) layout : null);
	}

	private void paintCaret(UIPainter painter) {
		TGLayoutVerticalSplit split = this.getSplitLayout();
		TGCaret caret = this.controller.getCaret();
		TGLayoutVerticalSplit.Part part = null;
		if( split != null && caret.getMeasure() != null && caret.getSelectedBeat() instanceof TGBeatImpl ) {
			part = split.applyPartForBeat(caret.getMeasure(), (TGBeatImpl) caret.getSelectedBeat());
		}
		if( part != null && painter instanceof TGPainterImpl ) {
			split.beginPart(painter, part);
			caret.paintCaret(this.controller.getLayout(), painter);
			split.endPart(painter);
		} else {
			caret.paintCaret(this.controller.getLayout(), painter);
		}
	}

	/** Red notes of the playing beat (every piece of a cut bar), then the cursor. */
	private void paintPlayModeParts(UIPainter painter, TGMeasureImpl measure, TGBeatImpl beat) {
		TGLayout layout = this.controller.getLayout();
		boolean smooth = this.isSmoothCursor() && (layout.getStyle() & TGLayout.HIGHLIGHT_PLAYED_BEAT) != 0;
		int style = layout.getStyle();
		if( smooth ) {
			// the jumping box is replaced by the gliding one below
			layout.setStyle(style & ~TGLayout.HIGHLIGHT_PLAYED_BEAT);
		}
		try {
			TGLayoutVerticalSplit split = this.getSplitLayout();
			java.util.List<TGLayoutVerticalSplit.Part> parts = (split != null ? split.getParts(measure) : null);
			if( parts != null && painter instanceof TGPainterImpl ) {
				for( TGLayoutVerticalSplit.Part part : parts ) {
					split.applyPart(measure, part);
					split.beginPart(painter, part);
					layout.paintPlayMode(painter, measure, beat);
					split.endPart(painter);
				}
				split.applyPartForBeat(measure, beat);
			} else if(!measure.isOutOfBounds() ) {
				layout.paintPlayMode(painter, measure, beat);
			}
		} finally {
			layout.setStyle(style);
		}
		if( smooth ) {
			this.paintSmoothCursor(painter, measure, beat);
		}
	}

	// ------------------------------------------------------------------
	// smooth cursor: glides between beats with the playback position
	// ------------------------------------------------------------------

	private Boolean smoothCursor;
	private Paint cursorPaint;

	public boolean isSmoothCursor() {
		if( this.smoothCursor == null ) {
			this.smoothCursor = Boolean.valueOf(TGViewSettings.isSmoothCursor(getContext()));
		}
		return this.smoothCursor.booleanValue();
	}

	public void reloadViewSettings() {
		this.smoothCursor = null;
		this.redraw();
	}

	private void paintSmoothCursor(UIPainter painter, TGMeasureImpl measure, TGBeatImpl beat) {
		if( beat == null || measure.getTs() == null || !(painter instanceof TGPainterImpl) ) {
			return;
		}
		TGLayout layout = this.controller.getLayout();
		TGLayoutVerticalSplit split = this.getSplitLayout();
		TGTransportCache cache = TGTransport.getInstance(this.context).getCache();
		MidiPlayer player = MidiPlayer.getInstance(this.context);

		// where the song is right now (between the transport's beat updates)
		long now = cache.getPlayStart() + (player.getTickPosition() - cache.getPlayTick());
		long beatStart = beat.getStart();
		app.tuxguitar.song.models.TGBeat next = layout.getSongManager().getMeasureManager().getNextBeat(measure.getBeats(), beat);
		long nextStart = (next != null ? next.getStart() : measure.getStart() + measure.getLength());
		float fraction = (nextStart > beatStart ? (float) (now - beatStart) / (float) (nextStart - beatStart) : 0f);
		fraction = Math.max(0f, Math.min(1f, fraction));

		TGLayoutVerticalSplit.Part part = (split != null ? split.applyPartForBeat(measure, beat) : null);
		float leftSpacing = measure.getHeaderImpl().getLeftSpacing(layout);
		float x1 = leftSpacing + beat.getPosX() + beat.getSpacing(layout);
		float x2;
		if( next instanceof TGBeatImpl ) {
			x2 = leftSpacing + ((TGBeatImpl) next).getPosX() + ((TGBeatImpl) next).getSpacing(layout);
		} else {
			x2 = measure.getWidth(layout) + measure.getSpacing() - measure.getHeaderImpl().getRightSpacing(layout);
		}
		if( part != null ) {
			// do not glide across a cut: stop at the end of this piece
			x2 = Math.min(x2, part.to);
		}
		float localX = x1 + ((x2 - x1) * fraction);
		float x = (part != null ? part.toScreenX(localX) : measure.getPosX() + localX);

		UIRectangle bounds = measure.getVerticalPosition(layout);
		float scale = layout.getScale();
		float width = Math.max(4f * scale, layout.getStringSpacing() * 1.6f);
		float left = x - (6f * scale);
		if( this.cursorPaint == null ) {
			this.cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		}
		Canvas canvas = ((TGPainterImpl) painter).getCanvas();
		this.cursorPaint.setStyle(Paint.Style.FILL);
		this.cursorPaint.setColor(0x26FF0000);
		canvas.drawRoundRect(left, bounds.getY(), left + width, bounds.getY() + bounds.getHeight(), 3f * scale, 3f * scale, this.cursorPaint);
		this.cursorPaint.setStyle(Paint.Style.STROKE);
		this.cursorPaint.setStrokeWidth(Math.max(1f, layout.getLineWidth(1)));
		this.cursorPaint.setColor(0xFFFF0000);
		canvas.drawRoundRect(left, bounds.getY(), left + width, bounds.getY() + bounds.getHeight(), 3f * scale, 3f * scale, this.cursorPaint);
	}

	// set when the user scrolls during playback: the view stops chasing the playing bar
	private boolean followSuspended;
	private android.animation.ValueAnimator scrollAnimator;
	private float scrollTarget = Float.NaN;

	private void animateScrollY(float target) {
		if( this.scrollAnimator != null && this.scrollAnimator.isRunning() && Math.abs(target - this.scrollTarget) < 1f ) {
			return;
		}
		this.cancelScrollAnimation();
		final TGScrollAxis axis = this.controller.getScroll().getY();
		this.scrollTarget = target;
		this.scrollAnimator = android.animation.ValueAnimator.ofFloat(axis.getValue(), target);
		this.scrollAnimator.setDuration(450);
		this.scrollAnimator.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));
		this.scrollAnimator.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(android.animation.ValueAnimator animation) {
				axis.setValue((Float) animation.getAnimatedValue());
				TGSongView.this.redraw();
			}
		});
		this.scrollAnimator.start();
	}

	private void cancelScrollAnimation() {
		if( this.scrollAnimator != null ) {
			this.scrollAnimator.cancel();
			this.scrollAnimator = null;
		}
		this.scrollTarget = Float.NaN;
	}

	public void suspendFollow() {
		this.cancelScrollAnimation();
		if( MidiPlayer.getInstance(this.context).isRunning() ) {
			this.followSuspended = true;
		}
	}

	/** {x, width} of a bar on screen; for a cut bar, of the piece it currently sits on. */
	private float[] getMeasureBox(TGMeasureImpl measure) {
		TGLayoutVerticalSplit split = this.getSplitLayout();
		TGLayoutVerticalSplit.Part part = (split != null ? split.getAppliedPart(measure) : null);
		if( part != null ) {
			return new float[] {part.x, part.getWidth()};
		}
		return new float[] {measure.getPosX(), measure.getWidth(this.controller.getLayout())};
	}

	private boolean isMeasureVisible(TGMeasureImpl measure, UIRectangle area) {
		if( measure.getTs() == null ) {
			return false;
		}
		float[] box = this.getMeasureBox(measure);
		float mX = box[0];
		float mY = measure.getPosY();
		float mWidth = box[1];
		float mHeight = measure.getTs().getSize();
		return (mX >= 0 && mY >= this.contentInsetTop && mX + mWidth <= area.getWidth() && mY + mHeight <= area.getHeight());
	}

	public boolean moveScrollTo(TGMeasureImpl measure, UIRectangle area) {
		boolean success = false;
		if (measure != null && measure.getTs() != null) {
			int scrollX = getPaintableScrollX();
			int scrollY = getPaintableScrollY();

			float[] box = this.getMeasureBox(measure);
			float mX = box[0];
			float mY = measure.getPosY();
			float mWidth = box[1];
			float mHeight = measure.getTs().getSize();
			float marginWidth = this.controller.getLayout().getFirstMeasureSpacing();
			float marginHeight = this.controller.getLayout().getFirstTrackSpacing() + this.contentInsetTop;

			// Solo se ajusta si es necesario
			// si el largo del compas es mayor al de la pantalla. nunca se puede
			// ajustar a la medida.
			if (mX < 0 || ((mX + mWidth) > area.getWidth() && (area.getWidth() >= mWidth + marginWidth || mX > marginWidth))) {
				this.controller.getScroll().getX().setValue((scrollX + mX) - marginWidth);
				success = true;
			}

			// Solo se ajusta si es necesario
			// si el alto del compas es mayor al de la pantalla. nunca se puede
			// ajustar a la medida.
			if (mY < this.contentInsetTop || ((mY + mHeight) > area.getHeight() && (area.getHeight() >= mHeight + marginHeight || mY > marginHeight))) {
				float target = (scrollY + mY) - marginHeight;
				TGScrollAxis axisY = this.controller.getScroll().getY();
				target = Math.max(axisY.getMinimum(), Math.min(axisY.getMaximum(), target));
				if( MidiPlayer.getInstance(this.context).isRunning() && this.isSmoothCursor() ) {
					this.animateScrollY(target);
				} else {
					this.cancelScrollAnimation();
					axisY.setValue(target);
				}
				success = true;
			}

			if (success) {
				redraw();
			}
		}
		return success;
	}

	public void onDraw(Canvas canvas) {
		try {
			if(!this.controller.isDisposed()) {
				TGEditorManager editor = TGEditorManager.getInstance(this.context);
				if (editor.tryLock()) {
					try {
						this.setPainting(true);

						this.paintBuffer(canvas);

						this.setPainting(false);
					} finally {
						editor.unlock();
					}
				} else {
					// try later
					this.postInvalidate();
				}
			}

			if (this.bufferedBitmap != null) {
				canvas.drawBitmap(this.bufferedBitmap, 0, 0, null);
			}
			if( this.isSmoothCursor() && MidiPlayer.getInstance(this.context).isRunning() ) {
				this.postInvalidateOnAnimation();
			}
		} catch (Throwable throwable) {
			this.handleError(throwable);
		}
	}

	public boolean onTouchEvent(MotionEvent event) {
		this.gestureDetector.processTouchEvent(event);
		this.redraw();

		return true;
	}

	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		this.controller.getCaret().setChanges(true);
		this.controller.resetScroll();
		this.redraw();
	}

	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();

		this.recycleBuffer();
		this.controller.getLayoutPainter().dispose();
	}

	public UIPainter createPainter(Canvas canvas) {
		return new TGPainterImpl(canvas);
	}

	public UIPainter createBufferedPainter(UIRectangle area) {
		if( this.bufferedBitmap == null || this.bufferedBitmap.getWidth() != area.getWidth() || this.bufferedBitmap.getHeight() != area.getHeight() ) {
			this.recycleBuffer();
			this.bufferedBitmap = Bitmap.createBitmap(Math.round(area.getWidth()), Math.round(area.getHeight()), Bitmap.Config.ARGB_8888);
		}
		return createPainter(new Canvas(this.bufferedBitmap));
	}

	public void recycleBuffer() {
		if( this.bufferedBitmap != null && !this.bufferedBitmap.isRecycled() ) {
			this.bufferedBitmap.recycle();
			this.bufferedBitmap = null;
		}
	}

	public UIRectangle createClientArea(Canvas canvas) {
		Rect rect = canvas.getClipBounds();
		return new UIRectangle(rect.left, rect.top, rect.right, rect.bottom);
	}

	public void handleError(Throwable throwable) {
		TGErrorManager.getInstance(this.context).handleError(new TGException(throwable));
	}

	public int getPaintableScrollX() {
		if (this.controller.getScroll().getX().isEnabled()) {
			return Math.round(this.controller.getScroll().getX().getValue());
		}
		return 0;
	}

	public int getPaintableScrollY() {
		if (this.controller.getScroll().getY().isEnabled()) {
			return Math.round(this.controller.getScroll().getY().getValue());
		}
		return 0;
	}

	public TGSongViewController getController() {
		return controller;
	}

	public boolean isPainting() {
		return this.painting;
	}

	public void setPainting(boolean painting) {
		this.painting = painting;
	}
}
