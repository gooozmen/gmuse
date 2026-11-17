package app.tuxguitar.android.view.sheet;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import app.tuxguitar.android.R;
import app.tuxguitar.android.action.TGActionProcessorListener;
import app.tuxguitar.android.action.impl.gui.TGOpenDialogAction;
import app.tuxguitar.android.action.impl.gui.TGOpenMenuAction;
import app.tuxguitar.android.action.impl.layout.TGToggleHighlightPlayedBeatAction;
import app.tuxguitar.android.action.impl.tools.TGTransposeAction;
import app.tuxguitar.android.action.impl.track.TGGoToTrackAction;
import app.tuxguitar.android.action.impl.transport.TGTransportCountInPlayAction;
import app.tuxguitar.android.action.impl.transport.TGTransportSetLoopEHeaderAction;
import app.tuxguitar.android.action.impl.transport.TGTransportSetLoopSHeaderAction;
import app.tuxguitar.android.action.impl.transport.TGTransportStopAction;
import app.tuxguitar.android.activity.TGActivity;
import app.tuxguitar.android.application.TGApplicationUtil;
import app.tuxguitar.android.action.impl.transport.TGTransportPlayAction;
import app.tuxguitar.android.transport.TGTransport;
import app.tuxguitar.android.menu.controller.TGMenuController;
import app.tuxguitar.android.menu.controller.impl.contextual.TGBeatMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGCompositionMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGDurationMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGEditMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGEffectMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGMeasureMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGTrackMenu;
import app.tuxguitar.android.menu.controller.impl.contextual.TGVelocityMenu;
import app.tuxguitar.android.view.dialog.transport.TGTransportModeDialogController;
import app.tuxguitar.android.view.keyboard.TGTabKeyboardController;
import app.tuxguitar.android.view.tablature.TGCaret;
import app.tuxguitar.android.view.tablature.TGSongViewController;
import app.tuxguitar.document.TGDocumentContextAttributes;
import app.tuxguitar.document.TGDocumentManager;
import app.tuxguitar.editor.action.TGActionProcessor;
import app.tuxguitar.editor.action.channel.TGUpdateChannelAction;
import app.tuxguitar.editor.action.track.TGChangeTrackMuteAction;
import app.tuxguitar.editor.action.track.TGChangeTrackSoloAction;
import app.tuxguitar.editor.action.transport.TGTransportMetronomeAction;
import app.tuxguitar.graphics.control.TGLayout;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerMode;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGChannel;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

public class TGQuickSheet extends LinearLayout {

	private static final int MIXER_MAX_HEIGHT_DP = 128;
	private static final int MORE_MAX_HEIGHT_DP = 240;
	// Score area that must stay visible above a tall panel.
	private static final int SCORE_MIN_VISIBLE_DP = 120;
	private static final int[] SPEED_PERCENTS = {50, 60, 70, 80, 90, 100};

	private static final int LOOP_PICK_NONE = 0;
	private static final int LOOP_PICK_START = 1;
	private static final int LOOP_PICK_END = 2;
	private static final int PANEL_NONE = 0;
	private static final int PANEL_KEYBOARD = 1;
	private static final int PANEL_MIXER = 2;
	private static final int PANEL_LOOP = 3;
	private static final int PANEL_MORE = 4;
	private static final int PANEL_TRACKS = 5;
	private static final int PANEL_TRANSPOSE = 6;
	private static final int PANEL_SPEED = 7;

	private BottomSheetBehavior<View> behavior;
	private int lastPeekHeight;
	private boolean mixerDragging;
	private int openPanel;
	private int transposeValue;
	private boolean metronomeVolumeShown;
	private volatile int loopPickStep;
	private volatile int loopPickStart;
	private boolean loopSwitchUpdating;

	public TGQuickSheet(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void onFinishInflate() {
		this.attachView();
		this.addListeners();
		super.onFinishInflate();
	}

	public void attachView() {
		TGQuickSheetController.getInstance(this.findContext()).setView(this);
	}

	public void addListeners() {
		TGContext context = this.findContext();
		TGActivity activity = this.findActivity();

		findViewById(R.id.quick_sheet_tracks_toggle).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.togglePanel(PANEL_TRACKS);
			}
		});
		findViewById(R.id.quick_sheet_mixer_toggle).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.togglePanel(PANEL_MIXER);
			}
		});
		findViewById(R.id.quick_sheet_metronome).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.toggleMetronome();
			}
		});
		findViewById(R.id.quick_sheet_metronome).setOnLongClickListener(new View.OnLongClickListener() {
			public boolean onLongClick(View view) {
				return TGQuickSheet.this.showMetronomeVolume();
			}
		});
		SeekBar metronomeVolume = (SeekBar) findViewById(R.id.quick_sheet_metronome_volume);
		if( metronomeVolume != null ) {
			metronomeVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				public void onStopTrackingTouch(SeekBar seekBar) {
					TGQuickSheet.this.commitMetronomeVolume();
				}

				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if( fromUser ) {
						TGQuickSheet.this.updateMetronomeVolume(progress);
					}
				}
			});
		}
		findViewById(R.id.quick_sheet_keyboard).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.togglePanel(PANEL_KEYBOARD);
			}
		});
		findViewById(R.id.quick_sheet_loop_toggle).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.toggleLoopPanel();
			}
		});
		findViewById(R.id.quick_sheet_takeover_close).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				if( TGQuickSheet.this.isTakeoverPanel() ) {
					TGQuickSheet.this.togglePanel(TGQuickSheet.this.openPanel);
				}
			}
		});
		findViewById(R.id.quick_sheet_play).setOnClickListener(new TGActionProcessorListener(context, TGTransportPlayAction.NAME));
		findViewById(R.id.quick_sheet_speed_toggle).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.openTempoPopup(view);
			}
		});
		findViewById(R.id.quick_sheet_tempo_edit).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.openTempoDialog();
			}
		});
		findViewById(R.id.quick_sheet_count_in).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.toggleCountIn();
			}
		});
		findViewById(R.id.quick_sheet_transpose_toggle).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.togglePanel(PANEL_TRANSPOSE);
			}
		});
		findViewById(R.id.quick_sheet_more_toggle).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.togglePanel(PANEL_MORE);
			}
		});
		findViewById(R.id.quick_sheet_transpose_minus).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.changeTranspose(-1);
			}
		});
		findViewById(R.id.quick_sheet_transpose_plus).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.changeTranspose(1);
			}
		});
		findViewById(R.id.quick_sheet_transpose_apply).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.applyTranspose();
			}
		});

		findViewById(R.id.quick_sheet_stop).setOnClickListener(new TGActionProcessorListener(context, TGTransportStopAction.NAME));
		findViewById(R.id.quick_sheet_play_mode).setOnClickListener(createDialogActionListener(new TGTransportModeDialogController()));
		((CompoundButton) findViewById(R.id.quick_sheet_loop_enable)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton button, boolean checked) {
				if(!TGQuickSheet.this.loopSwitchUpdating ) {
					TGQuickSheet.this.setLoopEnabled(checked);
				}
			}
		});
		findViewById(R.id.quick_sheet_loop_pick).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.toggleLoopPicking();
			}
		});
		findViewById(R.id.quick_sheet_loop_clear).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.clearLoop();
			}
		});
		findViewById(R.id.quick_sheet_loop_start_minus).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.nudgeLoop(-1, 0);
			}
		});
		findViewById(R.id.quick_sheet_loop_start_plus).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.nudgeLoop(1, 0);
			}
		});
		findViewById(R.id.quick_sheet_loop_end_minus).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.nudgeLoop(0, -1);
			}
		});
		findViewById(R.id.quick_sheet_loop_end_plus).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.nudgeLoop(0, 1);
			}
		});
		this.createSpeedChips();
		View handle = findViewById(R.id.quick_sheet_handle);
		if( handle != null ) {
			handle.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					TGQuickSheet.this.toggleSheetSize();
				}
			});
		}
		findViewById(R.id.quick_sheet_highlight).setOnClickListener(new TGActionProcessorListener(context, TGToggleHighlightPlayedBeatAction.NAME));

		findViewById(R.id.quick_sheet_edit).setOnClickListener(createContextMenuActionListener(new TGEditMenu(activity)));
		findViewById(R.id.quick_sheet_duration).setOnClickListener(createContextMenuActionListener(new TGDurationMenu(activity)));
		findViewById(R.id.quick_sheet_dynamic).setOnClickListener(createContextMenuActionListener(new TGVelocityMenu(activity)));
		findViewById(R.id.quick_sheet_effects).setOnClickListener(createContextMenuActionListener(new TGEffectMenu(activity)));
		findViewById(R.id.quick_sheet_beat).setOnClickListener(createContextMenuActionListener(new TGBeatMenu(activity)));
		findViewById(R.id.quick_sheet_measure).setOnClickListener(createContextMenuActionListener(new TGMeasureMenu(activity)));
		findViewById(R.id.quick_sheet_track).setOnClickListener(createContextMenuActionListener(new TGTrackMenu(activity)));
		findViewById(R.id.quick_sheet_composition).setOnClickListener(createContextMenuActionListener(new TGCompositionMenu(activity)));

		this.post(new Runnable() {
			public void run() {
				TGQuickSheet.this.updateItems();
				TGQuickSheet.this.updatePeekHeight();
			}
		});
	}

	public TGActionProcessorListener createContextMenuActionListener(TGMenuController controller) {
		TGActionProcessorListener tgActionProcessor = new TGActionProcessorListener(this.findContext(), TGOpenMenuAction.NAME);
		tgActionProcessor.setAttribute(TGOpenMenuAction.ATTRIBUTE_MENU_CONTROLLER, controller);
		tgActionProcessor.setAttribute(TGOpenMenuAction.ATTRIBUTE_MENU_ACTIVITY, this.findActivity());
		return tgActionProcessor;
	}

	public TGActionProcessorListener createDialogActionListener(app.tuxguitar.android.view.dialog.TGDialogController controller) {
		TGActionProcessorListener tgActionProcessor = new TGActionProcessorListener(this.findContext(), TGOpenDialogAction.NAME);
		tgActionProcessor.setAttribute(TGOpenDialogAction.ATTRIBUTE_DIALOG_CONTROLLER, controller);
		tgActionProcessor.setAttribute(TGOpenDialogAction.ATTRIBUTE_DIALOG_ACTIVITY, this.findActivity());
		return tgActionProcessor;
	}

	public void updateItems() {
		this.post(new Runnable() {
			public void run() {
				TGQuickSheet.this.applyItems();
			}
		});
	}

	private void applyItems() {
		TGContext context = this.findContext();
		if( context == null || findViewById(R.id.quick_sheet_metronome) == null ) {
			return;
		}

		MidiPlayer midiPlayer = MidiPlayer.getInstance(context);
		TGCaret caret = TGSongViewController.getInstance(context).getCaret();
		this.syncKeyboardPanel();
		if( caret == null || caret.getMeasure() == null ) {
			this.applyExclusivePanels();
			this.updatePeekHeight();
			return;
		}
		MidiPlayerMode playMode = midiPlayer.getMode();
		boolean running = midiPlayer.isRunning();
		int measureNumber = caret.getMeasure().getNumber();
		TGLayout layout = TGSongViewController.getInstance(context).getLayout();
		int style = layout.getStyle();

		setPeekSelected(R.id.quick_sheet_metronome, midiPlayer.isMetronomeEnabled());
		this.applyExclusivePanels();

		findViewById(R.id.quick_sheet_stop).setEnabled(running);
		this.applyPlayState(running);
		TGTrack currentTrack = caret.getTrack();
		String trackName = (currentTrack != null ? currentTrack.getName() : null);
		((TextView) findViewById(R.id.quick_sheet_track_name)).setText(trackName != null ? trackName : "");

		CheckBox highlight = (CheckBox) findViewById(R.id.quick_sheet_highlight);
		highlight.setChecked((style & TGLayout.HIGHLIGHT_PLAYED_BEAT) != 0);

		this.updatePeekHeight();
	}

	private void togglePanel(int panel) {
		this.openPanel = (this.openPanel == panel ? PANEL_NONE : panel);
		if( this.openPanel != PANEL_NONE ) {
			// another menu takes the place of the metronome volume slider
			this.metronomeVolumeShown = false;
		}
		if( this.openPanel != PANEL_LOOP ) {
			this.setLoopPickStep(LOOP_PICK_NONE);
		}
		this.applyExclusivePanels();
		this.updatePeekHeight();
	}

	private void syncKeyboardPanel() {
		if( this.isKeyboardVisible() ) {
			if( this.openPanel != PANEL_KEYBOARD ) {
				this.metronomeVolumeShown = false;
				this.setLoopPickStep(LOOP_PICK_NONE);
			}
			this.openPanel = PANEL_KEYBOARD;
		} else if( this.openPanel == PANEL_KEYBOARD ) {
			this.openPanel = PANEL_NONE;
		}
	}

	private void applyExclusivePanels() {
		TGTabKeyboardController keyboardController = TGTabKeyboardController.getInstance(this.findContext());
		if( this.openPanel == PANEL_KEYBOARD ) {
			if( !this.isKeyboardVisible() ) {
				keyboardController.showNow();
			}
		} else if( this.isKeyboardVisible() ) {
			keyboardController.hideNow();
		}

		this.applyTakeover();
		this.applyTracks();
		this.applyMixer();
		this.applyMetronomeVolume();
		this.applyLoopPanel();
		this.applyTransposePanel();
		this.applyMorePanel();
		this.applySpeedPanel();
		this.setPeekSelected(R.id.quick_sheet_count_in, TGTransport.getInstance(this.findContext()).isCountInEnabled());
		this.setPeekSelected(R.id.quick_sheet_tracks_toggle, this.openPanel == PANEL_TRACKS);
		this.setPeekSelected(R.id.quick_sheet_keyboard, this.openPanel == PANEL_KEYBOARD);
		this.setPeekSelected(R.id.quick_sheet_mixer_toggle, this.openPanel == PANEL_MIXER);
		this.setPeekSelected(R.id.quick_sheet_loop_toggle, this.openPanel == PANEL_LOOP || MidiPlayer.getInstance(this.findContext()).getMode().isLoop());
		this.setPeekSelected(R.id.quick_sheet_transpose_toggle, this.openPanel == PANEL_TRANSPOSE);
		this.setPeekSelected(R.id.quick_sheet_more_toggle, this.openPanel == PANEL_MORE);

		// something opened while the sheet is pulled down: bring it back up
		BottomSheetBehavior<View> sheetBehavior = this.getBehavior();
		if( sheetBehavior != null && sheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED && (this.openPanel != PANEL_NONE || this.metronomeVolumeShown) ) {
			sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
		}
	}

	/** The handle flips between the full sheet and the pulled-down one. */
	private void toggleSheetSize() {
		BottomSheetBehavior<View> sheetBehavior = this.getBehavior();
		if( sheetBehavior == null ) {
			return;
		}
		sheetBehavior.setState(sheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED);
	}

	private void applyLoopPanel() {
		View loop = findViewById(R.id.quick_sheet_loop);
		if( loop == null ) {
			return;
		}
		loop.setVisibility(this.openPanel == PANEL_LOOP ? VISIBLE : GONE);
		if( this.openPanel != PANEL_LOOP ) {
			return;
		}
		MidiPlayerMode mode = MidiPlayer.getInstance(this.findContext()).getMode();
		int count = this.countMeasures();
		boolean hasRange = (mode.getLoopSHeader() > 0 || mode.getLoopEHeader() > 0);
		int start = this.loopStart(mode);
		int end = this.loopEnd(mode, count);

		CompoundButton enable = (CompoundButton) findViewById(R.id.quick_sheet_loop_enable);
		if( enable.isChecked() != mode.isLoop() ) {
			this.loopSwitchUpdating = true;
			enable.setChecked(mode.isLoop());
			this.loopSwitchUpdating = false;
		}

		TextView hint = (TextView) findViewById(R.id.quick_sheet_loop_hint);
		if( this.loopPickStep == LOOP_PICK_START ) {
			hint.setText(R.string.quick_sheet_loop_pick_start);
		} else if( this.loopPickStep == LOOP_PICK_END ) {
			hint.setText(R.string.quick_sheet_loop_pick_end);
		} else if( hasRange ) {
			hint.setText(start == end
				? getResources().getString(R.string.quick_sheet_loop_single, start)
				: getResources().getString(R.string.quick_sheet_loop_range, start, end));
		} else if( mode.isLoop() ) {
			hint.setText(R.string.quick_sheet_loop_whole);
		} else {
			hint.setText(R.string.quick_sheet_loop_empty);
		}
		hint.setTextColor(com.google.android.material.color.MaterialColors.getColor(hint,
			this.loopPickStep != LOOP_PICK_NONE ? androidx.appcompat.R.attr.colorPrimary : com.google.android.material.R.attr.colorOnSurface));

		View range = findViewById(R.id.quick_sheet_loop_range);
		range.setVisibility(hasRange && this.loopPickStep == LOOP_PICK_NONE ? VISIBLE : GONE);
		((TextView) findViewById(R.id.quick_sheet_loop_start_value)).setText(String.valueOf(start));
		((TextView) findViewById(R.id.quick_sheet_loop_end_value)).setText(String.valueOf(end));
		findViewById(R.id.quick_sheet_loop_start_minus).setEnabled(start > 1);
		findViewById(R.id.quick_sheet_loop_start_plus).setEnabled(start < end);
		findViewById(R.id.quick_sheet_loop_end_minus).setEnabled(end > start);
		findViewById(R.id.quick_sheet_loop_end_plus).setEnabled(end < count);

		((TextView) findViewById(R.id.quick_sheet_loop_pick)).setText(this.loopPickStep != LOOP_PICK_NONE ? R.string.quick_sheet_loop_cancel : R.string.quick_sheet_loop_select);
		findViewById(R.id.quick_sheet_loop_clear).setEnabled(hasRange || mode.isLoop());
	}

	// ------------------------------------------------------------------
	// Loop (Songsterr-style: open the panel, tap the first and the last bar)
	// ------------------------------------------------------------------

	private void toggleLoopPanel() {
		boolean opening = (this.openPanel != PANEL_LOOP);
		this.togglePanel(PANEL_LOOP);
		if( opening ) {
			MidiPlayerMode mode = MidiPlayer.getInstance(this.findContext()).getMode();
			if( mode.getLoopSHeader() <= 0 && mode.getLoopEHeader() <= 0 ) {
				this.setLoopPickStep(LOOP_PICK_START);
				this.applyLoopPanel();
				this.updatePeekHeight();
			}
		}
	}

	private void toggleLoopPicking() {
		this.setLoopPickStep(this.loopPickStep == LOOP_PICK_NONE ? LOOP_PICK_START : LOOP_PICK_NONE);
		this.applyLoopPanel();
		this.updatePeekHeight();
	}

	private void setLoopPickStep(int step) {
		boolean changed = (this.loopPickStep != step);
		this.loopPickStep = step;
		if( step != LOOP_PICK_END ) {
			this.loopPickStart = 0;
		}
		if( changed ) {
			this.redrawScore();
		}
	}

	public boolean isLoopPicking() {
		return (this.loopPickStep != LOOP_PICK_NONE);
	}

	public int getLoopPickStart() {
		return (this.loopPickStep == LOOP_PICK_END ? this.loopPickStart : 0);
	}

	/** Called (on the UI thread) with the bar the user tapped while picking a loop. */
	public void onLoopMeasureTapped(int number) {
		if( this.loopPickStep == LOOP_PICK_START ) {
			this.loopPickStart = number;
			this.loopPickStep = LOOP_PICK_END;
			this.redrawScore();
		} else if( this.loopPickStep == LOOP_PICK_END ) {
			int first = Math.min(this.loopPickStart, number);
			int last = Math.max(this.loopPickStart, number);
			this.loopPickStep = LOOP_PICK_NONE;
			this.loopPickStart = 0;
			this.applyLoop(first, last, true);
		}
		this.applyLoopPanel();
		this.updatePeekHeight();
	}

	private void nudgeLoop(int startDelta, int endDelta) {
		MidiPlayerMode mode = MidiPlayer.getInstance(this.findContext()).getMode();
		int count = this.countMeasures();
		int end = this.loopEnd(mode, count);
		int start = Math.max(1, Math.min(this.loopStart(mode) + startDelta, end));
		end = Math.max(start, Math.min(end + endDelta, count));
		this.applyLoop(start, end, true);
	}

	private void setLoopEnabled(boolean enabled) {
		MidiPlayerMode mode = MidiPlayer.getInstance(this.findContext()).getMode();
		this.applyLoop(mode.getLoopSHeader(), mode.getLoopEHeader(), enabled);
	}

	private void clearLoop() {
		this.setLoopPickStep(LOOP_PICK_NONE);
		this.applyLoop(-1, -1, false);
	}

	private void applyLoop(int start, int end, boolean enabled) {
		MidiPlayer midiPlayer = MidiPlayer.getInstance(this.findContext());
		MidiPlayerMode mode = midiPlayer.getMode();
		mode.setLoopSHeader(start);
		mode.setLoopEHeader(end);
		mode.setLoop(enabled);
		if( midiPlayer.isRunning() ) {
			midiPlayer.updateSequence();
		} else {
			midiPlayer.updateLoop(true);
		}
		this.redrawScore();
		this.applyExclusivePanels();
		this.updatePeekHeight();
	}

	private int loopStart(MidiPlayerMode mode) {
		return (mode.getLoopSHeader() > 0 ? mode.getLoopSHeader() : 1);
	}

	private int loopEnd(MidiPlayerMode mode, int count) {
		return (mode.getLoopEHeader() > 0 ? Math.min(mode.getLoopEHeader(), count) : count);
	}

	private int countMeasures() {
		TGSong song = TGDocumentManager.getInstance(this.findContext()).getSong();
		return (song != null ? Math.max(1, song.countMeasureHeaders()) : 1);
	}

	private void redrawScore() {
		TGActivity activity = this.findActivity();
		if( activity != null ) {
			activity.updateCache(false);
		}
	}

	// ------------------------------------------------------------------
	// Speed
	// ------------------------------------------------------------------

	private void createSpeedChips() {
		LinearLayout items = (LinearLayout) findViewById(R.id.quick_sheet_speed_items);
		if( items == null ) {
			return;
		}
		LayoutInflater inflater = LayoutInflater.from(getContext());
		for( final int percent : SPEED_PERCENTS ) {
			TextView chip = (TextView) inflater.inflate(R.layout.view_track_tape_item, items, false);
			chip.setText(percent + "%");
			chip.setTag(Integer.valueOf(percent));
			chip.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					TGQuickSheet.this.setSpeed(percent);
				}
			});
			items.addView(chip);
		}
	}

	private void toggleCountIn() {
		TGTransport transport = TGTransport.getInstance(this.findContext());
		transport.setCountInEnabled(!transport.isCountInEnabled());
		this.setPeekSelected(R.id.quick_sheet_count_in, transport.isCountInEnabled());
	}

	/** Editing panels (keyboard, menus) take the whole sheet: the tiles give way to a title and a close button. */
	private boolean isTakeoverPanel() {
		return (this.openPanel == PANEL_KEYBOARD || this.openPanel == PANEL_MORE);
	}

	private void applyTakeover() {
		boolean takeover = this.isTakeoverPanel();
		findViewById(R.id.quick_sheet_takeover_header).setVisibility(takeover ? VISIBLE : GONE);
		findViewById(R.id.quick_sheet_hero).setVisibility(takeover ? GONE : VISIBLE);
		findViewById(R.id.quick_sheet_peek).setVisibility(takeover ? GONE : VISIBLE);
		findViewById(R.id.quick_sheet_row2).setVisibility(takeover ? GONE : VISIBLE);
		if( takeover ) {
			((TextView) findViewById(R.id.quick_sheet_takeover_title)).setText(this.openPanel == PANEL_KEYBOARD ? R.string.quick_sheet_edit : R.string.quick_sheet_more);
		}
	}

	/** The row the panels hang under: the tiles, or the takeover header. */
	private View anchorRow() {
		return findViewById(this.isTakeoverPanel() ? R.id.quick_sheet_takeover_header : R.id.quick_sheet_peek);
	}

	private void applySpeedPanel() {
		View panel = findViewById(R.id.quick_sheet_speed);
		if( panel != null ) {
			panel.setVisibility(this.openPanel == PANEL_SPEED ? VISIBLE : GONE);
		}
		this.applySpeedChips(MidiPlayer.getInstance(this.findContext()).getMode());
		this.updateTempo();
	}

	/** Shows the current tempo on the hero card and in the speed panel. */
	public void updateTempo() {
		TGContext context = this.findContext();
		MidiPlayer midiPlayer = MidiPlayer.getInstance(context);
		app.tuxguitar.song.models.TGTempo tempo = null;
		int percent = 100;
		if( midiPlayer.isRunning() && midiPlayer.getCurrentTempo() != null ) {
			tempo = midiPlayer.getCurrentTempo();
			percent = midiPlayer.getMode().getCurrentPercent();
		} else {
			TGCaret caret = TGSongViewController.getInstance(context).getCaret();
			if( caret != null && caret.getMeasure() != null ) {
				tempo = caret.getMeasure().getTempo();
			}
		}
		if( tempo == null ) {
			return;
		}
		int value = (tempo.getRawValue() * percent / 100);
		TextView hero = (TextView) findViewById(R.id.quick_sheet_tempo_value);
		if( hero != null ) {
			hero.setText(String.valueOf(value));
		}
		TextView label = (TextView) findViewById(R.id.quick_sheet_tempo_label);
		if( label != null ) {
			String iconName = "duration_" + tempo.getBase() + (tempo.isDotted() ? "dotted" : "");
			int iconId = getResources().getIdentifier(iconName, "drawable", getContext().getPackageName());
			label.setCompoundDrawablesWithIntrinsicBounds(iconId != 0 ? getContext().getDrawable(iconId) : null, null, null, null);
			label.setText("= " + value);
		}
		View edit = findViewById(R.id.quick_sheet_tempo_edit);
		if( edit != null ) {
			edit.setEnabled(!midiPlayer.isRunning());
		}
	}

	private Boolean lastRunning;

	/** Play/pause icon; the scalloped shape turns by 60 degrees on every change (12 lobes, so a lobe ends up on top again). */
	private void applyPlayState(boolean running) {
		((android.widget.ImageView) findViewById(R.id.quick_sheet_play_icon)).setImageResource(running ? R.drawable.ic_tile_pause : R.drawable.ic_tile_play);
		if( this.lastRunning != null && this.lastRunning.booleanValue() != running ) {
			View shape = findViewById(R.id.quick_sheet_play_shape);
			float target = (Math.round(shape.getRotation() / 30f) * 30f) + 60f;
			shape.animate().cancel();
			shape.animate()
				.rotation(target)
				.setDuration(560)
				.setInterpolator(new android.view.animation.OvershootInterpolator(1.6f))
				.withEndAction(new Runnable() {
					public void run() {
						View s = findViewById(R.id.quick_sheet_play_shape);
						// keep the number small; 360 is a multiple of 30, so a lobe stays on top
						s.setRotation(s.getRotation() % 360f);
					}
				})
				.start();
			View icon = findViewById(R.id.quick_sheet_play_icon);
			icon.setScaleX(0.6f);
			icon.setScaleY(0.6f);
			icon.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start();
		}
		this.lastRunning = Boolean.valueOf(running);
	}

	private void openTempoPopup(View anchor) {
		new TGTempoPopup(this.findActivity(), this.findContext(), anchor, new Runnable() {
			public void run() {
				TGQuickSheet.this.applySpeedChips(MidiPlayer.getInstance(TGQuickSheet.this.findContext()).getMode());
				TGQuickSheet.this.updateTempo();
			}
		}).show();
	}

	private void openTempoDialog() {
		if( MidiPlayer.getInstance(this.findContext()).isRunning() ) {
			return;
		}
		this.createDialogActionListener(new app.tuxguitar.android.view.dialog.tempo.TGTempoDialogController()).onClick(this);
	}

	private void applySpeedChips(MidiPlayerMode mode) {
		int percent = mode.getCurrentPercent();
		TextView value = (TextView) findViewById(R.id.quick_sheet_speed_value);
		if( value != null ) {
			value.setText(percent + "%");
		}
		// stays tonal: the tempo menu grows out of this chip and must match its colour
		this.setPeekSelected(R.id.quick_sheet_speed_toggle, false);

		LinearLayout items = (LinearLayout) findViewById(R.id.quick_sheet_speed_items);
		if( items == null ) {
			return;
		}
		int current = (mode.getType() == MidiPlayerMode.TYPE_SIMPLE ? mode.getSimplePercent() : -1);
		for( int i = 0; i < items.getChildCount(); i++ ) {
			View chip = items.getChildAt(i);
			chip.setSelected(Integer.valueOf(current).equals(chip.getTag()));
		}
	}

	private void setSpeed(int percent) {
		MidiPlayer midiPlayer = MidiPlayer.getInstance(this.findContext());
		MidiPlayerMode mode = midiPlayer.getMode();
		mode.setType(MidiPlayerMode.TYPE_SIMPLE);
		mode.setSimplePercent(percent);
		mode.setCurrentPercent(percent);
		if( midiPlayer.isRunning() ) {
			midiPlayer.updateSequence();
		}
		this.applySpeedChips(mode);
		this.redrawScore();
	}

	private void applyTracks() {
		LinearLayout items = (LinearLayout) findViewById(R.id.quick_sheet_tracks_items);
		View panel = findViewById(R.id.quick_sheet_tracks);
		if( items == null || panel == null ) {
			return;
		}
		TGDocumentManager documentManager = TGDocumentManager.getInstance(this.findContext());
		TGSong song = documentManager.getSong();
		TGCaret caret = TGSongViewController.getInstance(this.findContext()).getCaret();
		TGTrack selected = (caret != null ? caret.getTrack() : null);
		int trackCount = (song != null ? song.countTracks() : 0);
		panel.setVisibility(this.openPanel == PANEL_TRACKS && trackCount > 0 ? VISIBLE : GONE);
		if( trackCount <= 0 ) {
			items.removeAllViews();
			return;
		}
		LayoutInflater inflater = LayoutInflater.from(getContext());
		while( items.getChildCount() > trackCount ) {
			items.removeViewAt(items.getChildCount() - 1);
		}
		for( int i = 0; i < trackCount; i++ ) {
			TGTrack track = song.getTrack(i);
			TextView row = (i < items.getChildCount() ? (TextView) items.getChildAt(i) : (TextView) inflater.inflate(R.layout.view_quick_track_item, items, false));
			if( i >= items.getChildCount() ) {
				items.addView(row);
			}
			row.setText(this.mixerTrackLabel(track, null, i));
			boolean current = (selected != null && selected.getNumber() == track.getNumber());
			row.setSelected(current);
			TGActionProcessorListener listener = new TGActionProcessorListener(this.findContext(), TGGoToTrackAction.NAME);
			listener.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, track);
			row.setOnClickListener(listener);
		}
		this.limitScrollHeightPx(R.id.quick_sheet_tracks_scroll, R.id.quick_sheet_tracks_items, this.availablePanelHeight());
	}

	private void applyTransposePanel() {
		View panel = findViewById(R.id.quick_sheet_transpose);
		if( panel == null ) {
			return;
		}
		panel.setVisibility(this.openPanel == PANEL_TRANSPOSE ? VISIBLE : GONE);
		TextView valueView = (TextView) findViewById(R.id.quick_sheet_transpose_value);
		if( valueView != null ) {
			valueView.setText((this.transposeValue > 0 ? "+" : "") + this.transposeValue);
		}
		View apply = findViewById(R.id.quick_sheet_transpose_apply);
		if( apply != null ) {
			apply.setEnabled(this.transposeValue != 0);
		}
	}

	private void changeTranspose(int delta) {
		int next = this.transposeValue + delta;
		if( next < -12 ) {
			next = -12;
		} else if( next > 12 ) {
			next = 12;
		}
		this.transposeValue = next;
		this.applyTransposePanel();
	}

	private void applyTranspose() {
		if( this.transposeValue == 0 ) {
			return;
		}
		CheckBox allTracks = (CheckBox) findViewById(R.id.quick_sheet_transpose_all_tracks);
		TGActionProcessor processor = new TGActionProcessor(this.findContext(), TGTransposeAction.NAME);
		processor.setAttribute(TGTransposeAction.ATTRIBUTE_TRANSPOSITION, Integer.valueOf(this.transposeValue));
		processor.setAttribute(TGTransposeAction.ATTRIBUTE_TRY_KEEP_STRING, Boolean.TRUE);
		processor.setAttribute(TGTransposeAction.ATTRIBUTE_APPLY_TO_CHORDS, Boolean.TRUE);
		processor.setAttribute(TGTransposeAction.ATTRIBUTE_APPLY_TO_ALL_MEASURES, Boolean.TRUE);
		processor.setAttribute(TGTransposeAction.ATTRIBUTE_APPLY_TO_ALL_TRACKS, Boolean.valueOf(allTracks == null || allTracks.isChecked()));
		processor.process();
		this.transposeValue = 0;
		this.applyTransposePanel();
	}

	private void applyMetronomeVolume() {
		View row = findViewById(R.id.quick_sheet_metronome_volume_row);
		SeekBar volumeBar = (SeekBar) findViewById(R.id.quick_sheet_metronome_volume);
		MidiPlayer midiPlayer = MidiPlayer.getInstance(this.findContext());
		// The metronome action runs asynchronously; the row appears once it reports "enabled".
		boolean enabled = midiPlayer.isMetronomeEnabled();
		if( row != null ) {
			row.setVisibility(enabled && this.metronomeVolumeShown ? VISIBLE : GONE);
		}
		if( volumeBar != null && volumeBar.getProgress() != midiPlayer.getMetronomeVolume() ) {
			volumeBar.setProgress(midiPlayer.getMetronomeVolume());
		}
	}

	private void updateMetronomeVolume(int volume) {
		// Only remember the value while dragging; the clicks are re-rendered on release.
		MidiPlayer.getInstance(this.findContext()).setMetronomeVolume(volume);
	}

	private void commitMetronomeVolume() {
		MidiPlayer midiPlayer = MidiPlayer.getInstance(this.findContext());
		if( midiPlayer.isRunning() ) {
			// rebuild in place: playback keeps going (it used to stop here)
			midiPlayer.updateSequence();
		}
	}

	private void toggleMetronome() {
		MidiPlayer midiPlayer = MidiPlayer.getInstance(this.findContext());
		boolean enable = !midiPlayer.isMetronomeEnabled();
		new TGActionProcessor(this.findContext(), TGTransportMetronomeAction.NAME).process();
		this.metronomeVolumeShown = enable;
		if( enable && this.openPanel != PANEL_NONE ) {
			this.openPanel = PANEL_NONE;
			this.setLoopPickStep(LOOP_PICK_NONE);
		}
		this.applyExclusivePanels();
		this.updatePeekHeight();
	}

	private boolean showMetronomeVolume() {
		if(!MidiPlayer.getInstance(this.findContext()).isMetronomeEnabled() ) {
			return false;
		}
		this.metronomeVolumeShown = !this.metronomeVolumeShown;
		if( this.metronomeVolumeShown ) {
			this.openPanel = PANEL_NONE;
			this.setLoopPickStep(LOOP_PICK_NONE);
		}
		this.applyExclusivePanels();
		this.updatePeekHeight();
		return true;
	}

	private void applyMorePanel() {
		View more = findViewById(R.id.quick_sheet_more);
		if( more == null ) {
			return;
		}
		boolean open = (this.openPanel == PANEL_MORE);
		more.setVisibility(open ? VISIBLE : GONE);
		if( open ) {
			this.limitScrollHeightPx(R.id.quick_sheet_more, R.id.quick_sheet_more_items, this.availablePanelHeight());
		}
	}

	/** Height a panel may take so the whole menu fits, leaving a strip of the score visible. */
	private int availablePanelHeight() {
		float density = getResources().getDisplayMetrics().density;
		int fallback = (int) (MORE_MAX_HEIGHT_DP * density + 0.5f);
		ViewParent parent = getParent();
		int parentHeight = (parent instanceof View ? ((View) parent).getHeight() : 0);
		View peek = this.anchorRow();
		if( parentHeight <= 0 || peek == null || peek.getBottom() <= 0 ) {
			return fallback;
		}
		int used = peek.getBottom() + getPaddingBottom() + (int) (SCORE_MIN_VISIBLE_DP * density + 0.5f);
		// the tile rows below the panel are on screen too once the sheet is pulled up
		View row2 = findViewById(R.id.quick_sheet_row2);
		used += (row2 != null && row2.getVisibility() == VISIBLE ? row2.getHeight() : 0);
		return Math.max((int) (MIXER_MAX_HEIGHT_DP * density + 0.5f), parentHeight - used);
	}

	private void onSheetStateChanged(int newState) {
		if( newState == BottomSheetBehavior.STATE_DRAGGING || newState == BottomSheetBehavior.STATE_SETTLING ) {
			return;
		}
		this.updatePeekHeight();
	}

	private void applyMixer() {
		LinearLayout items = (LinearLayout) findViewById(R.id.quick_sheet_mixer_items);
		View mixer = findViewById(R.id.quick_sheet_mixer);
		if( items == null || mixer == null ) {
			return;
		}

		TGDocumentManager documentManager = TGDocumentManager.getInstance(this.findContext());
		TGSong song = documentManager.getSong();
		TGSongManager songManager = documentManager.getSongManager();
		int trackCount = (song != null ? song.countTracks() : 0);
		mixer.setVisibility(this.openPanel == PANEL_MIXER && trackCount > 0 ? VISIBLE : GONE);
		if( this.mixerDragging || trackCount <= 0 ) {
			if( trackCount <= 0 ) {
				items.removeAllViews();
			}
			return;
		}

		LayoutInflater inflater = LayoutInflater.from(getContext());
		while( items.getChildCount() > trackCount ) {
			items.removeViewAt(items.getChildCount() - 1);
		}
		for( int i = 0; i < trackCount; i++ ) {
			TGTrack track = song.getTrack(i);
			TGChannel channel = songManager.getChannel(song, track.getChannelId());
			View row = (i < items.getChildCount() ? items.getChildAt(i) : inflater.inflate(R.layout.view_quick_mixer_item, items, false));
			if( i >= items.getChildCount() ) {
				this.bindMixerRow(row);
				items.addView(row);
			}

			TextView nameView = (TextView) row.findViewById(R.id.quick_sheet_mixer_name);
			nameView.setText(this.mixerTrackLabel(track, channel, i));

			SeekBar volumeBar = (SeekBar) row.findViewById(R.id.quick_sheet_mixer_volume);
			volumeBar.setTag(channel);
			volumeBar.setEnabled(channel != null);
			int volume = (channel != null ? channel.getVolume() : 0);
			if( volumeBar.getProgress() != volume ) {
				volumeBar.setProgress(volume);
			}

			TextView muteView = (TextView) row.findViewById(R.id.quick_sheet_mixer_mute);
			if( muteView != null ) {
				muteView.setTag(track);
				muteView.setSelected(track.isMute());
			}

			TextView soloView = (TextView) row.findViewById(R.id.quick_sheet_mixer_solo);
			if( soloView != null ) {
				soloView.setTag(track);
				soloView.setSelected(track.isSolo());
			}
		}
		this.limitScrollHeightPx(R.id.quick_sheet_mixer_scroll, R.id.quick_sheet_mixer_items, this.availablePanelHeight());
	}

	private void bindMixerRow(View row) {
		SeekBar volumeBar = (SeekBar) row.findViewById(R.id.quick_sheet_mixer_volume);
		volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			private short originalVolume;

			public void onStartTrackingTouch(SeekBar seekBar) {
				TGQuickSheet.this.mixerDragging = true;
				TGChannel channel = (TGChannel) seekBar.getTag();
				this.originalVolume = (channel != null ? channel.getVolume() : 0);
			}

			public void onStopTrackingTouch(SeekBar seekBar) {
				TGQuickSheet.this.mixerDragging = false;
				TGChannel channel = (TGChannel) seekBar.getTag();
				if( channel != null ) {
					// commit once, as a single undoable change
					short volume = channel.getVolume();
					channel.setVolume(this.originalVolume);
					TGQuickSheet.this.updateChannelVolume(channel, volume);
				}
				TGQuickSheet.this.applyMixer();
			}

			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				TGChannel channel = (TGChannel) seekBar.getTag();
				if( fromUser && channel != null && progress >= 0 && progress <= 127 ) {
					// live preview: only the volume controller, no program change, playback untouched
					channel.setVolume((short) progress);
					MidiPlayer.getInstance(TGQuickSheet.this.findContext()).updateChannelControllers(channel);
				}
			}
		});
		row.findViewById(R.id.quick_sheet_mixer_mute).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.toggleTrackFlag((TGTrack) view.getTag(), TGChangeTrackMuteAction.NAME);
			}
		});
		row.findViewById(R.id.quick_sheet_mixer_solo).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				TGQuickSheet.this.toggleTrackFlag((TGTrack) view.getTag(), TGChangeTrackSoloAction.NAME);
			}
		});
	}

	private void toggleTrackFlag(TGTrack track, String actionName) {
		if( track == null ) {
			return;
		}
		TGActionProcessor processor = new TGActionProcessor(this.findContext(), actionName);
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, track);
		processor.process();
	}

	private String mixerTrackLabel(TGTrack track, TGChannel channel, int index) {
		String name = (track != null ? track.getName() : null);
		if( name != null && name.length() > 0 ) {
			return name;
		}
		if( channel != null && channel.getName() != null && channel.getName().length() > 0 ) {
			return channel.getName();
		}
		return String.valueOf(index + 1);
	}

	private void updateChannelVolume(TGChannel channel, short volume) {
		if( channel == null || volume == channel.getVolume() || volume < 0 || volume > 127 ) {
			return;
		}
		TGActionProcessor processor = new TGActionProcessor(this.findContext(), TGUpdateChannelAction.NAME);
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_CHANNEL, channel);
		processor.setAttribute(TGUpdateChannelAction.ATTRIBUTE_VOLUME, Short.valueOf(volume));
		processor.process();
	}

	private void limitScrollHeightPx(int scrollId, int contentId, int maxHeight) {
		View scroll = findViewById(scrollId);
		View items = findViewById(contentId);
		if( scroll == null || items == null ) {
			return;
		}
		int width = scroll.getWidth();
		if( width <= 0 ) {
			width = getWidth();
		}
		if( width <= 0 ) {
			return;
		}
		items.measure(
			MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
			MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
		);
		int contentHeight = items.getMeasuredHeight();
		ViewGroup.LayoutParams params = scroll.getLayoutParams();
		int target = (contentHeight > maxHeight ? maxHeight : ViewGroup.LayoutParams.WRAP_CONTENT);
		if( params.height != target ) {
			params.height = target;
			scroll.setLayoutParams(params);
		}
	}

	private void setPeekSelected(int id, boolean selected) {
		View button = findViewById(id);
		if( button == null ) {
			return;
		}
		button.setSelected(selected);
	}

	private boolean isKeyboardVisible() {
		View keyboard = findViewById(R.id.tgTabKeyboard);
		return keyboard != null && keyboard.getVisibility() == VISIBLE;
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		// Re-measure after this pass: changing the peek height requests a new layout,
		// which would be dropped if done in the middle of this one.
		this.post(new Runnable() {
			public void run() {
				TGQuickSheet.this.updatePeekHeight();
				if( TGQuickSheet.this.openPanel == PANEL_MORE ) {
					TGQuickSheet.this.limitScrollHeightPx(R.id.quick_sheet_more, R.id.quick_sheet_more_items, TGQuickSheet.this.availablePanelHeight());
				}
			}
		});
	}

	private void updatePeekHeight() {
		// Keep the peek height in sync even while the sheet is pulled up: a stale (taller)
		// value would leave the score padded by a panel that is already closed.
		BottomSheetBehavior<View> current = this.getBehavior();
		if( current != null ) {
			int state = current.getState();
			if( state == BottomSheetBehavior.STATE_DRAGGING || state == BottomSheetBehavior.STATE_SETTLING ) {
				return;
			}
		}
		View peek = this.anchorRow();
		if( peek == null || peek.getBottom() <= 0 || peek.isLayoutRequested() ) {
			// not laid out yet (e.g. just switched between tiles and takeover header); onLayout retries
			return;
		}

		int fullHeight = peek.getBottom();
		int[] panels = {
			R.id.quick_sheet_metronome_volume_row, R.id.tgTabKeyboard, R.id.quick_sheet_tracks, R.id.quick_sheet_mixer,
			R.id.quick_sheet_loop, R.id.quick_sheet_speed, R.id.quick_sheet_transpose, R.id.quick_sheet_more
		};
		for( int id : panels ) {
			View panel = findViewById(id);
			if( panel != null && panel.getVisibility() == VISIBLE && panel.getBottom() > fullHeight ) {
				fullHeight = panel.getBottom();
			}
		}
		fullHeight += getPaddingBottom();

		// pulled down, only the first card stays (tempo + play, or the editing header)
		View mini = findViewById(this.isTakeoverPanel() ? R.id.quick_sheet_takeover_header : R.id.quick_sheet_hero);
		int miniHeight = (mini != null && mini.getVisibility() == VISIBLE && mini.getBottom() > 0 ? mini.getBottom() : peek.getBottom()) + getPaddingBottom();
		miniHeight = Math.min(miniHeight, fullHeight);

		BottomSheetBehavior<View> sheetBehavior = this.getBehavior();
		if( sheetBehavior != null ) {
			if( miniHeight != this.lastPeekHeight ) {
				this.lastPeekHeight = miniHeight;
				sheetBehavior.setPeekHeight(miniHeight);
			}
			boolean collapsed = (sheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED);
			this.updateBodyPadding(collapsed ? miniHeight : fullHeight);
		}
	}

	private void updateBodyPadding(int peekHeight) {
		ViewParent parent = getParent();
		if( parent instanceof View ) {
			View body = ((View) parent).findViewById(R.id.main_body);
			if( body != null && body.getPaddingBottom() != peekHeight ) {
				body.setPadding(body.getPaddingLeft(), body.getPaddingTop(), body.getPaddingRight(), peekHeight);
			}
		}
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		this.ensureBehavior();
		this.updateItems();
		if( getParent() instanceof ViewGroup ) {
			// the filler under a sliding sheet is drawn below its own bounds
			((ViewGroup) getParent()).setClipChildren(false);
		}
		getViewTreeObserver().addOnPreDrawListener(this.smoothListener);
	}

	@Override
	protected void onDetachedFromWindow() {
		getViewTreeObserver().removeOnPreDrawListener(this.smoothListener);
		if( this.smoothAnimator != null ) {
			this.smoothAnimator.cancel();
		}
		super.onDetachedFromWindow();
	}

	// ------------------------------------------------------------------
	// smooth height changes: whenever a panel opens/closes, the sheet's top
	// would jump; instead it glides from where it was to where it belongs,
	// and newly shown panels fade and rise into place.
	// ------------------------------------------------------------------

	private static final long SLIDE_DURATION = 420;
	private static final int[] ANIMATED_PANELS = {
		R.id.quick_sheet_metronome_volume_row, R.id.quick_sheet_tracks, R.id.quick_sheet_mixer, R.id.quick_sheet_loop,
		R.id.quick_sheet_speed, R.id.quick_sheet_transpose, R.id.quick_sheet_more, R.id.tgTabKeyboard,
		R.id.quick_sheet_takeover_header, R.id.quick_sheet_hero, R.id.quick_sheet_peek, R.id.quick_sheet_row2
	};

	private final android.util.SparseBooleanArray panelShown = new android.util.SparseBooleanArray();
	private final android.graphics.Paint fillPaint = new android.graphics.Paint();
	private android.animation.ValueAnimator smoothAnimator;
	private int smoothLastTop = -1;

	private final android.view.ViewTreeObserver.OnPreDrawListener smoothListener = new android.view.ViewTreeObserver.OnPreDrawListener() {
		public boolean onPreDraw() {
			TGQuickSheet.this.onSmoothPreDraw();
			return true;
		}
	};

	private boolean isSheetMovedByUser() {
		BottomSheetBehavior<View> current = this.getBehavior();
		if( current == null ) {
			return false;
		}
		int state = current.getState();
		return (state == BottomSheetBehavior.STATE_DRAGGING || state == BottomSheetBehavior.STATE_SETTLING);
	}

	private void onSmoothPreDraw() {
		int top = getTop();
		if( this.isSheetMovedByUser() ) {
			// the finger drives the sheet: no smoothing
			if( this.smoothAnimator != null ) {
				this.smoothAnimator.cancel();
			}
			setTranslationY(0f);
		} else if( this.smoothLastTop >= 0 && top != this.smoothLastTop && isShown() ) {
			float visualTop = this.smoothLastTop + getTranslationY();
			this.startSlide(visualTop - top);
		}
		this.smoothLastTop = top;

		for( int id : ANIMATED_PANELS ) {
			View panel = findViewById(id);
			if( panel == null ) {
				continue;
			}
			boolean shown = (panel.getVisibility() == VISIBLE);
			boolean known = (this.panelShown.indexOfKey(id) >= 0);
			if( known && shown && !this.panelShown.get(id) ) {
				this.revealPanel(panel);
			}
			this.panelShown.put(id, shown);
		}
	}

	private void startSlide(float from) {
		if( this.smoothAnimator != null ) {
			this.smoothAnimator.cancel();
		}
		if( Math.abs(from) < 1f ) {
			setTranslationY(0f);
			return;
		}
		setTranslationY(from);
		this.smoothAnimator = android.animation.ValueAnimator.ofFloat(from, 0f);
		this.smoothAnimator.setDuration(SLIDE_DURATION);
		this.smoothAnimator.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));
		this.smoothAnimator.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(android.animation.ValueAnimator animation) {
				setTranslationY((Float) animation.getAnimatedValue());
				invalidate();
			}
		});
		this.smoothAnimator.start();
	}

	private void revealPanel(View panel) {
		float density = getResources().getDisplayMetrics().density;
		panel.animate().cancel();
		panel.setAlpha(0f);
		panel.setTranslationY(16f * density);
		panel.animate()
			.alpha(1f)
			.translationY(0f)
			.setStartDelay(70)
			.setDuration(340)
			.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
			.start();
	}

	@Override
	public void draw(android.graphics.Canvas canvas) {
		super.draw(canvas);
		// while a shorter sheet glides down, keep the screen bottom covered
		float lifted = -getTranslationY();
		if( lifted > 0f ) {
			this.fillPaint.setColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer));
			canvas.drawRect(0f, getHeight() - 1f, getWidth(), getHeight() + lifted, this.fillPaint);
		}
	}

	@SuppressWarnings("unchecked")
	private BottomSheetBehavior<View> getBehavior() {
		this.ensureBehavior();
		return this.behavior;
	}

	@SuppressWarnings("unchecked")
	private void ensureBehavior() {
		if( this.behavior != null ) {
			return;
		}
		ViewGroup.LayoutParams params = getLayoutParams();
		if( params instanceof CoordinatorLayout.LayoutParams ) {
			CoordinatorLayout.LayoutParams coordinatorParams = (CoordinatorLayout.LayoutParams) params;
			if( coordinatorParams.getBehavior() instanceof BottomSheetBehavior ) {
				this.behavior = (BottomSheetBehavior<View>) coordinatorParams.getBehavior();
			} else {
				this.behavior = new BottomSheetBehavior<View>();
				coordinatorParams.setBehavior(this.behavior);
			}
			this.behavior.setHideable(false);
			this.post(new Runnable() {
				public void run() {
					TGQuickSheet.this.applyMaxHeight();
				}
			});
			this.behavior.setFitToContents(true);
			this.behavior.setSkipCollapsed(false);
			// opened in full; dragging it down leaves only the tempo / play card
			this.behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
			this.behavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
				public void onStateChanged(View bottomSheet, int newState) {
					TGQuickSheet.this.onSheetStateChanged(newState);
				}

				public void onSlide(View bottomSheet, float slideOffset) {
				}
			});
		}
	}

	/** The sheet never reaches the top of the screen, so its rounded edge is always visible. */
	private void applyMaxHeight() {
		ViewParent parent = getParent();
		if( this.behavior != null && parent instanceof View && ((View) parent).getHeight() > 0 ) {
			int margin = (int) (24 * getResources().getDisplayMetrics().density + 0.5f);
			this.behavior.setMaxHeight(((View) parent).getHeight() - margin);
		}
	}

	private TGActivity findActivity() {
		return (TGActivity) getContext();
	}

	private TGContext findContext() {
		return TGApplicationUtil.findContext(this);
	}
}
