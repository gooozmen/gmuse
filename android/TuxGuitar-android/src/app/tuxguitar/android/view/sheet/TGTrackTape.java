package app.tuxguitar.android.view.sheet;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.tuxguitar.android.R;
import app.tuxguitar.android.action.TGActionProcessorListener;
import app.tuxguitar.android.action.impl.track.TGGoToTrackAction;
import app.tuxguitar.android.application.TGApplicationUtil;
import app.tuxguitar.android.view.tablature.TGCaret;
import app.tuxguitar.android.view.tablature.TGSongViewController;
import app.tuxguitar.document.TGDocumentContextAttributes;
import app.tuxguitar.document.TGDocumentManager;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

public class TGTrackTape extends HorizontalScrollView {

	public TGTrackTape(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void refresh() {
		LinearLayout items = (LinearLayout) findViewById(R.id.track_tape_items);
		if( items == null ) {
			return;
		}
		TGContext context = TGApplicationUtil.findContext(this);
		if( context == null ) {
			return;
		}
		TGDocumentManager documentManager = TGDocumentManager.getInstance(context);
		TGSong song = documentManager.getSong();
		TGCaret caret = TGSongViewController.getInstance(context).getCaret();
		TGTrack selected = (caret != null ? caret.getTrack() : null);
		int trackCount = (song != null ? song.countTracks() : 0);
		LayoutInflater inflater = LayoutInflater.from(getContext());

		while( items.getChildCount() > trackCount ) {
			items.removeViewAt(items.getChildCount() - 1);
		}
		for( int i = 0; i < trackCount; i++ ) {
			TGTrack track = song.getTrack(i);
			TextView chip = (i < items.getChildCount() ? (TextView) items.getChildAt(i) : (TextView) inflater.inflate(R.layout.view_track_tape_item, items, false));
			if( i >= items.getChildCount() ) {
				items.addView(chip);
			}
			chip.setText(trackLabel(track, i));
			boolean current = (selected != null && selected.getNumber() == track.getNumber());
			chip.setSelected(current);
			TGActionProcessorListener listener = new TGActionProcessorListener(context, TGGoToTrackAction.NAME);
			listener.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, track);
			chip.setOnClickListener(listener);
		}
	}

	private String trackLabel(TGTrack track, int index) {
		String name = (track != null ? track.getName() : null);
		if( name != null && name.length() > 0 ) {
			return name;
		}
		return String.valueOf(index + 1);
	}
}
