package app.tuxguitar.android.view.sheet;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

public class TGQuickSheetController {

	private TGQuickSheet view;

	public TGQuickSheetController() {
		super();
	}

	public TGQuickSheet getView() {
		return view;
	}

	public void setView(TGQuickSheet view) {
		this.view = view;
	}

	/** Bar the loop picker is waiting for a second tap on, or 0. Safe from any thread. */
	public int getLoopPickStart() {
		TGQuickSheet view = this.getView();
		return (view != null ? view.getLoopPickStart() : 0);
	}

	/**
	 * Gives a tapped bar to the loop picker. Returns true when the tap was used for
	 * the loop (the caret should not move). May be called from a worker thread.
	 */
	public boolean consumeMeasureTap(final int measureNumber) {
		final TGQuickSheet view = this.getView();
		if( view == null || !view.isLoopPicking() ) {
			return false;
		}
		view.post(new Runnable() {
			public void run() {
				view.onLoopMeasureTapped(measureNumber);
			}
		});
		return true;
	}

	public void updateTempo() {
		final TGQuickSheet view = this.getView();
		if( view != null ) {
			view.post(new Runnable() {
				public void run() {
					view.updateTempo();
				}
			});
		}
	}

	public void updateItems() {
		if( this.getView() != null ) {
			this.getView().updateItems();
		}
	}

	public static TGQuickSheetController getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TGQuickSheetController.class.getName(), new TGSingletonFactory<TGQuickSheetController>() {
			public TGQuickSheetController createInstance(TGContext context) {
				return new TGQuickSheetController();
			}
		});
	}
}
