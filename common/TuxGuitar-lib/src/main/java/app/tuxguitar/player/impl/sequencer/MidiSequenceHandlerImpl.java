package app.tuxguitar.player.impl.sequencer;

import app.tuxguitar.player.base.MidiSequenceHandler;
import app.tuxguitar.song.models.TGTimeSignature;

public class MidiSequenceHandlerImpl extends MidiSequenceHandler{

	private MidiSequencerImpl seq;
	private java.util.List<MidiEvent> pending;

	public MidiSequenceHandlerImpl(MidiSequencerImpl seq,int tracks) {
		this(seq, tracks, false);
	}

	public MidiSequenceHandlerImpl(MidiSequencerImpl seq,int tracks, boolean deferred) {
		super(tracks);
		this.seq = seq;
		if( deferred ) {
			this.pending = new java.util.ArrayList<MidiEvent>();
		} else {
			this.seq.getMidiTrackController().init(getTracks());
		}
	}

	private void add(MidiEvent event) {
		if( this.pending != null ) {
			this.pending.add(event);
		} else {
			this.seq.addEvent(event);
		}
	}

	public void addControlChange(long tick,int track,int channel, int controller, int value) {
		this.add(MidiEvent.controlChange(tick, track, channel, controller, value));
	}

	public void addNoteOff(long tick,int track,int channel, int note, int velocity, int voice, boolean bendMode) {
		this.add(MidiEvent.noteOff(tick, track, channel, note, velocity, voice, bendMode));
	}

	public void addNoteOn(long tick,int track,int channel, int note, int velocity, int voice, boolean bendMode) {
		this.add(MidiEvent.noteOn(tick, track, channel, note, velocity, voice, bendMode));
	}

	public void addPitchBend(long tick,int track,int channel, int value, int voice, boolean bendMode) {
		this.add(MidiEvent.pitchBend(tick, track, channel, value, voice, bendMode));
	}

	public void addProgramChange(long tick,int track,int channel, int instrument) {
		this.add(MidiEvent.programChange(tick, track, channel, instrument));
	}

	public void addTrackName(long tick, int track, String name) {
		this.add(MidiEvent.trackName(tick, track, name));
	}

	public void addTempoInUSQ(long tick,int track,int usq) {
		this.add(MidiEvent.tempoInUSQ(tick, usq));
	}

	public void addTimeSignature(long tick,int track,TGTimeSignature ts) {
		//not implemented
	}

	public void notifyFinish(){
		if( this.pending != null ) {
			this.seq.replaceSequence(getTracks(), this.pending);
			this.pending = null;
		}
	}
}
