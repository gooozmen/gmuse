package app.tuxguitar.android.view.sheet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import app.tuxguitar.android.R;
import app.tuxguitar.android.activity.TGActivity;
import app.tuxguitar.android.view.popup.TGMorphMotion;
import app.tuxguitar.android.view.tablature.TGCaret;
import app.tuxguitar.android.view.tablature.TGSongViewController;
import app.tuxguitar.document.TGDocumentContextAttributes;
import app.tuxguitar.document.TGDocumentManager;
import app.tuxguitar.editor.action.TGActionProcessor;
import app.tuxguitar.editor.action.composition.TGChangeTempoRangeAction;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerMode;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGTempo;
import app.tuxguitar.song.models.TGTempoBase;
import app.tuxguitar.util.TGContext;

/**
 * Tempo &amp; speed menu. It grows out of the button that opened it: starts as a small
 * blurred blob at the button and settles into a rounded card, covering only part of the screen.
 */
public class TGTempoPopup {

	private static final int[] SPEED_PERCENTS = {50, 60, 70, 80, 90, 100};
	private static final long OPEN_DURATION = 420;
	private static final long CLOSE_DURATION = 200;
	private static final float BLUR_RADIUS = 40f;

	private final TGActivity activity;
	private final TGContext context;
	private final View anchor;
	private final Runnable onChanged;

	private PopupWindow window;
	private FrameLayout root;
	private View card;
	private boolean closing;

	private TGMeasureHeader header;
	private TGTempo originalTempo;
	private int tempoValue;
	private int tempoBase;
	private boolean tempoDotted;
	private int applyTo = TGChangeTempoRangeAction.APPLY_TO_NEXT;
	private boolean committed;
	private boolean switching;
	private boolean openedAbove;
	private final java.util.List<View> speedChips = new java.util.ArrayList<View>();
	private final java.util.List<View> baseChips = new java.util.ArrayList<View>();

	public TGTempoPopup(TGActivity activity, TGContext context, View anchor, Runnable onChanged) {
		this.activity = activity;
		this.context = context;
		this.anchor = anchor;
		this.onChanged = onChanged;
	}

	public void show() {
		TGCaret caret = TGSongViewController.getInstance(this.context).getCaret();
		if( caret == null || caret.getMeasure() == null ) {
			return;
		}
		this.header = caret.getMeasure().getHeader();
		this.originalTempo = caret.getMeasure().getTempo();
		this.tempoValue = this.originalTempo.getRawValue();

		this.root = new FrameLayout(this.activity);
		this.root.setBackgroundColor(Color.TRANSPARENT);
		this.root.setFocusableInTouchMode(true);
		this.card = LayoutInflater.from(this.activity).inflate(R.layout.view_tempo_popup, this.root, false);
		this.card.setClickable(true);
		this.root.addView(this.card);
		// same colour as the speed chip the menu grows from
		this.goo = TGMorphMotion.attachGoo(this.root, this.card, com.google.android.material.color.MaterialColors.getColor(this.activity, com.google.android.material.R.attr.colorSurfaceContainer, Color.WHITE));
		this.root.setVisibility(View.INVISIBLE);

		this.bind();

		this.root.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View view, MotionEvent event) {
				if( event.getAction() == MotionEvent.ACTION_DOWN ) {
					TGTempoPopup.this.dismiss();
				}
				return true;
			}
		});
		this.root.setOnKeyListener(new View.OnKeyListener() {
			public boolean onKey(View view, int keyCode, KeyEvent event) {
				if( keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP ) {
					// from the second page, back returns to the first one
					if( TGTempoPopup.this.card.findViewById(R.id.tempo_popup_page_more).getVisibility() == View.VISIBLE ) {
						TGTempoPopup.this.switchPage(false);
						return true;
					}
					TGTempoPopup.this.dismiss();
					return true;
				}
				return (keyCode == KeyEvent.KEYCODE_BACK);
			}
		});

		this.window = new PopupWindow(this.root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
		this.window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		this.window.setOutsideTouchable(false);
		this.window.setClippingEnabled(false);
		this.window.setAnimationStyle(0);
		this.window.showAtLocation(this.anchor.getRootView(), android.view.Gravity.NO_GRAVITY, 0, 0);
		this.resetAnchorState();

		this.root.post(new Runnable() {
			public void run() {
				TGTempoPopup.this.placeAndOpen();
			}
		});
	}

	// ------------------------------------------------------------------
	// content
	// ------------------------------------------------------------------

	private void bind() {
		boolean running = MidiPlayer.getInstance(this.context).isRunning();
		this.tempoBase = this.originalTempo.getBase();
		this.tempoDotted = this.originalTempo.isDotted();

		View minus = this.card.findViewById(R.id.tempo_popup_minus);
		View plus = this.card.findViewById(R.id.tempo_popup_plus);
		View more = this.card.findViewById(R.id.tempo_popup_more);
		// the song's tempo cannot be edited during playback (speed still can)
		minus.setEnabled(!running);
		plus.setEnabled(!running);
		more.setEnabled(!running);
		minus.setAlpha(running ? 0.38f : 1f);
		plus.setAlpha(running ? 0.38f : 1f);
		more.setAlpha(running ? 0.38f : 1f);

		minus.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGTempoPopup.this.changeTempo(-1);
			}
		});
		plus.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGTempoPopup.this.changeTempo(1);
			}
		});
		minus.setOnLongClickListener(new View.OnLongClickListener() {
			public boolean onLongClick(View view) {
				TGTempoPopup.this.changeTempo(-10);
				return true;
			}
		});
		plus.setOnLongClickListener(new View.OnLongClickListener() {
			public boolean onLongClick(View view) {
				TGTempoPopup.this.changeTempo(10);
				return true;
			}
		});
		more.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGTempoPopup.this.switchPage(true);
			}
		});
		this.card.findViewById(R.id.tempo_popup_back).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGTempoPopup.this.switchPage(false);
			}
		});

		LayoutInflater inflater = LayoutInflater.from(this.activity);

		// playback speed: two even rows
		LinearLayout[] speedRows = {(LinearLayout) this.card.findViewById(R.id.tempo_popup_speed_items), (LinearLayout) this.card.findViewById(R.id.tempo_popup_speed_items_2)};
		for( int i = 0; i < SPEED_PERCENTS.length; i++ ) {
			final int percent = SPEED_PERCENTS[i];
			LinearLayout row = speedRows[i * speedRows.length / SPEED_PERCENTS.length];
			TextView chip = (TextView) inflater.inflate(R.layout.view_tempo_chip, row, false);
			chip.setText(percent + "%");
			chip.setTag(Integer.valueOf(percent));
			chip.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					TGTempoPopup.this.setSpeed(percent);
				}
			});
			row.addView(chip);
			this.speedChips.add(chip);
		}

		// beat unit
		TGTempoBase[] bases = TGTempoBase.getTempoBases();
		LinearLayout[] baseRows = {(LinearLayout) this.card.findViewById(R.id.tempo_popup_base_items), (LinearLayout) this.card.findViewById(R.id.tempo_popup_base_items_2)};
		for( int i = 0; i < bases.length; i++ ) {
			final TGTempoBase base = bases[i];
			LinearLayout row = baseRows[i * baseRows.length / bases.length];
			View chip = inflater.inflate(R.layout.view_tempo_base_chip, row, false);
			int iconId = this.findDurationIcon(base.getBase(), base.isDotted());
			if( iconId != 0 ) {
				((ImageView) chip.findViewById(R.id.tempo_base_chip_icon)).setImageResource(iconId);
			}
			chip.setContentDescription("1/" + base.getBase() + (base.isDotted() ? "." : ""));
			chip.setTag(base);
			chip.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					TGTempoPopup.this.tempoBase = base.getBase();
					TGTempoPopup.this.tempoDotted = base.isDotted();
					TGTempoPopup.this.updateValues();
				}
			});
			row.addView(chip);
			this.baseChips.add(chip);
		}

		// range
		LinearLayout applyItems = (LinearLayout) this.card.findViewById(R.id.tempo_popup_apply_items);
		int[] applyValues = {TGChangeTempoRangeAction.APPLY_TO_NEXT, TGChangeTempoRangeAction.APPLY_TO_END, TGChangeTempoRangeAction.APPLY_TO_ALL};
		int[] applyLabels = {R.string.tempo_popup_apply_next, R.string.tempo_popup_apply_end, R.string.tempo_popup_apply_song};
		for( int i = 0; i < applyValues.length; i++ ) {
			final int value = applyValues[i];
			TextView rowView = (TextView) inflater.inflate(R.layout.view_tempo_apply_row, applyItems, false);
			rowView.setText(applyLabels[i]);
			rowView.setTag(Integer.valueOf(value));
			rowView.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					TGTempoPopup.this.applyTo = value;
					TGTempoPopup.this.updateValues();
				}
			});
			applyItems.addView(rowView);
		}
		this.updateValues();
	}

	private int findDurationIcon(int base, boolean dotted) {
		String iconName = "duration_" + base + (dotted ? "dotted" : "");
		return this.activity.getResources().getIdentifier(iconName, "drawable", this.activity.getPackageName());
	}

	private void updateValues() {
		((TextView) this.card.findViewById(R.id.tempo_popup_value)).setText(String.valueOf(this.tempoValue));
		int iconId = this.findDurationIcon(this.tempoBase, this.tempoDotted);
		if( iconId != 0 ) {
			((ImageView) this.card.findViewById(R.id.tempo_popup_note)).setImageResource(iconId);
		}
		MidiPlayerMode mode = MidiPlayer.getInstance(this.context).getMode();
		int current = (mode.getType() == MidiPlayerMode.TYPE_SIMPLE ? mode.getSimplePercent() : -1);
		for( View chip : this.speedChips ) {
			chip.setSelected(Integer.valueOf(current).equals(chip.getTag()));
		}
		for( View chip : this.baseChips ) {
			TGTempoBase base = (TGTempoBase) chip.getTag();
			chip.setSelected(base.getBase() == this.tempoBase && base.isDotted() == this.tempoDotted);
		}
		LinearLayout applyItems = (LinearLayout) this.card.findViewById(R.id.tempo_popup_apply_items);
		for( int i = 0; i < applyItems.getChildCount(); i++ ) {
			View rowView = applyItems.getChildAt(i);
			rowView.setSelected(Integer.valueOf(this.applyTo).equals(rowView.getTag()));
		}
	}

	/** Swaps the card's content in place; the liquid shape stretches to the new height. */
	private void switchPage(boolean more) {
		if( this.closing || this.switching || this.motionAnchor == null ) {
			return;
		}
		final View from = this.card.findViewById(more ? R.id.tempo_popup_page_main : R.id.tempo_popup_page_more);
		final View to = this.card.findViewById(more ? R.id.tempo_popup_page_more : R.id.tempo_popup_page_main);
		final int width = this.card.getWidth();
		final int fromHeight = this.card.getHeight();
		final int fromY = this.motionY;

		from.setVisibility(View.GONE);
		to.setVisibility(View.VISIBLE);
		this.card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		final int toHeight = this.card.getMeasuredHeight();
		// keep the edge next to the button in place
		final int toY = Math.max(this.minY(), (this.openedAbove ? fromY + fromHeight - toHeight : fromY));
		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) this.card.getLayoutParams();
		params.topMargin = toY;
		this.card.setLayoutParams(params);
		this.motionY = toY;

		if( this.goo == null ) {
			return;
		}
		this.switching = true;
		to.setAlpha(0f);
		ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
		animator.setDuration(380);
		animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
		animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(ValueAnimator animation) {
				float f = (Float) animation.getAnimatedValue();
				int y = Math.round(fromY + (toY - fromY) * f);
				int height = Math.round(fromHeight + (toHeight - fromHeight) * f);
				TGMorphMotion.applyGoo(TGTempoPopup.this.goo, TGTempoPopup.this.card, TGTempoPopup.this.motionAnchor, TGTempoPopup.this.motionX, y, width, height, 1f);
				TGTempoPopup.this.card.setClipBounds(new android.graphics.Rect(0, y - toY, width, y - toY + height));
				to.setAlpha(Math.max(0f, Math.min(1f, (f - 0.3f) / 0.5f)));
			}
		});
		animator.addListener(new AnimatorListenerAdapter() {
			public void onAnimationEnd(Animator animation) {
				TGTempoPopup.this.card.setClipBounds(null);
				to.setAlpha(1f);
				TGTempoPopup.this.switching = false;
			}
		});
		animator.start();
	}

	private int minY() {
		return (int) (12 * this.activity.getResources().getDisplayMetrics().density);
	}

	private void changeTempo(int delta) {
		this.tempoValue = Math.max(TGChangeTempoRangeAction.MIN_TEMPO, Math.min(TGChangeTempoRangeAction.MAX_TEMPO, this.tempoValue + delta));
		this.updateValues();
	}

	private void setSpeed(int percent) {
		MidiPlayer midiPlayer = MidiPlayer.getInstance(this.context);
		MidiPlayerMode mode = midiPlayer.getMode();
		mode.setType(MidiPlayerMode.TYPE_SIMPLE);
		mode.setSimplePercent(percent);
		mode.setCurrentPercent(percent);
		if( midiPlayer.isRunning() ) {
			midiPlayer.updateSequence();
		}
		this.updateValues();
		this.notifyChanged();
	}

	/** The new tempo is written once, when the menu closes (one undo step). */
	private void commitTempo() {
		boolean changed = (this.tempoValue != this.originalTempo.getRawValue() || this.tempoBase != this.originalTempo.getBase()
			|| this.tempoDotted != this.originalTempo.isDotted() || this.applyTo != TGChangeTempoRangeAction.APPLY_TO_NEXT);
		if( !changed || this.committed || MidiPlayer.getInstance(this.context).isRunning() ) {
			return;
		}
		// written only once, even if dismiss() runs twice
		this.committed = true;
		TGActionProcessor processor = new TGActionProcessor(this.context, TGChangeTempoRangeAction.NAME);
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, TGDocumentManager.getInstance(this.context).getSong());
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER, this.header);
		processor.setAttribute(TGChangeTempoRangeAction.ATTRIBUTE_TEMPO, Integer.valueOf(this.tempoValue));
		processor.setAttribute(TGChangeTempoRangeAction.ATTRIBUTE_TEMPO_BASE, Integer.valueOf(this.tempoBase));
		processor.setAttribute(TGChangeTempoRangeAction.ATTRIBUTE_TEMPO_BASE_DOTTED, Boolean.valueOf(this.tempoDotted));
		processor.setAttribute(TGChangeTempoRangeAction.ATTRIBUTE_APPLY_TO, Integer.valueOf(this.applyTo));
		processor.processOnNewThread();
	}

	/** The popup window steals focus mid-ripple, which leaves the chip stuck in its pressed colour. */
	private void resetAnchorState() {
		this.anchor.setPressed(false);
		this.anchor.jumpDrawablesToCurrentState();
		this.anchor.refreshDrawableState();
	}

	private void notifyChanged() {
		if( this.onChanged != null ) {
			this.onChanged.run();
		}
	}

	// ------------------------------------------------------------------
	// placement & motion
	// ------------------------------------------------------------------

	private void placeAndOpen() {
		int[] rootLocation = new int[2];
		int[] anchorLocation = new int[2];
		this.root.getLocationOnScreen(rootLocation);
		int[] anchorRect = app.tuxguitar.android.view.popup.TGMorphMotion.getScreenRect(this.anchor);
		anchorLocation[0] = anchorRect[0];
		anchorLocation[1] = anchorRect[1];

		float density = this.activity.getResources().getDisplayMetrics().density;
		int margin = (int) (12 * density);
		int gap = (int) (8 * density);
		int cardWidth = this.card.getWidth();
		int cardHeight = this.card.getHeight();
		int anchorX = anchorLocation[0] - rootLocation[0];
		int anchorY = anchorLocation[1] - rootLocation[1];
		int anchorCenterX = anchorX + this.anchor.getWidth() / 2;

		int x = Math.max(margin, Math.min(anchorCenterX - cardWidth / 2, this.root.getWidth() - cardWidth - margin));
		// open above the button when there is room, otherwise below it
		int y = anchorY - cardHeight - gap;
		this.openedAbove = (y >= margin);
		if( y < margin ) {
			y = Math.min(anchorY + this.anchor.getHeight() + gap, this.root.getHeight() - cardHeight - margin);
		}
		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) this.card.getLayoutParams();
		params.leftMargin = x;
		params.topMargin = y;
		this.card.setLayoutParams(params);

		// grow from the button that was tapped
		this.motionAnchor = new int[] {anchorX, anchorY, anchorX + this.anchor.getWidth(), anchorY + this.anchor.getHeight()};
		this.motionX = x;
		this.motionY = y;
		this.applyProgress(0f);
		this.root.setVisibility(View.VISIBLE);
		this.root.requestFocus();

		ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
		animator.setDuration(TGMorphMotion.OPEN_DURATION);
		// emphasized decelerate
		animator.setInterpolator(new android.view.animation.LinearInterpolator());
		animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(ValueAnimator animation) {
				TGTempoPopup.this.applyProgress((Float) animation.getAnimatedValue());
			}
		});
		animator.addListener(new AnimatorListenerAdapter() {
			public void onAnimationEnd(Animator animation) {
				TGTempoPopup.this.clearBlur();
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

	private void clearBlur() {
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ) {
			this.card.setRenderEffect(null);
		}
	}

	public void dismiss() {
		if( this.closing || this.window == null ) {
			return;
		}
		this.closing = true;
		this.commitTempo();

		ValueAnimator animator = ValueAnimator.ofFloat(1f, 0f);
		animator.setDuration(TGMorphMotion.CLOSE_DURATION);
		// emphasized accelerate
		animator.setInterpolator(new android.view.animation.LinearInterpolator());
		animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(ValueAnimator animation) {
				TGTempoPopup.this.applyProgress((Float) animation.getAnimatedValue());
			}
		});
		animator.addListener(new AnimatorListenerAdapter() {
			public void onAnimationEnd(Animator animation) {
				if( TGTempoPopup.this.window != null && TGTempoPopup.this.window.isShowing() ) {
					TGTempoPopup.this.window.dismiss();
				}
				TGTempoPopup.this.window = null;
				TGTempoPopup.this.resetAnchorState();
				TGTempoPopup.this.notifyChanged();
			}
		});
		animator.start();
	}
}
