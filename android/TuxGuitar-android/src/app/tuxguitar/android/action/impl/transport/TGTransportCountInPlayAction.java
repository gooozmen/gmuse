package app.tuxguitar.android.action.impl.transport;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.android.action.TGActionBase;
import app.tuxguitar.android.transport.TGTransport;
import app.tuxguitar.util.TGContext;

public class TGTransportCountInPlayAction extends TGActionBase {

	public static final String NAME = "action.transport.count-in-play";

	public TGTransportCountInPlayAction(TGContext context) {
		super(context, NAME);
	}

	protected void processAction(TGActionContext context){
		TGTransport.getInstance(getContext()).play(true);
	}
}
