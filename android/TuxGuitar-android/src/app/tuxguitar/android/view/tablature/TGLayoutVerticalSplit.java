package app.tuxguitar.android.view.tablature;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import app.tuxguitar.android.graphics.TGPainterImpl;
import app.tuxguitar.graphics.control.TGBeatImpl;
import app.tuxguitar.graphics.control.TGController;
import app.tuxguitar.graphics.control.TGLayout;
import app.tuxguitar.graphics.control.TGLyricImpl;
import app.tuxguitar.graphics.control.TGMeasureImpl;
import app.tuxguitar.graphics.control.TGTrackImpl;
import app.tuxguitar.graphics.control.TGTrackSpacing;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.ui.resource.UIPainter;
import app.tuxguitar.ui.resource.UIRectangle;

/**
 * Page layout (one line after another) that can cut a bar into several pieces, Songsterr style.
 * A cut bar always takes a whole number of lines: each piece is stretched over a full line
 * (the notes are spread out, no empty tail). With a split count of N every bar is cut into N
 * pieces; otherwise only bars wider than the screen are cut. The pieces are joined by dashed
 * lines so they still read as one bar.
 *
 * A piece is painted by moving the bar so that the piece lands on the line, clipping everything
 * else away and evening out the small rest of the stretch with a horizontal scale. A bar has a
 * single position, so callers that need it (caret, played beat, touch) pick the piece first with
 * {@link #applyPart} / {@link #applyPartForBeat} and paint inside {@link #beginPart} / {@link #endPart}.
 */
public class TGLayoutVerticalSplit extends TGLayout {

	public static class Part {
		public int index;
		public int count;
		public float from;
		public float to;
		public float x;
		public float y;
		public float scale = 1f;
		public float screenWidth;
		public TGTrackSpacing ts;

		/** Width on screen (a full line). */
		public float getWidth() {
			return this.screenWidth;
		}

		/** Bar-local x (as painted unscaled at the bar's position) to screen x. */
		public float toScreenX(float localX) {
			return this.x + ((localX - this.from) * this.scale);
		}

		/** Screen x to the x the bar would have unscaled at its applied position. */
		public float toUnscaledX(float screenX) {
			return this.x + ((screenX - this.x) / this.scale);
		}
	}

	private static class Segment {
		int measureIndex;
		int index;
		int count;
		float from;
		float to;
		float spacing;

		float width() {
			return (this.to - this.from);
		}
	}

	private static class Line {
		final List<Segment> segments = new ArrayList<Segment>();
		float width;
		boolean full;
	}

	private final Map<TGMeasureImpl, List<Part>> parts = new IdentityHashMap<TGMeasureImpl, List<Part>>();
	private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private int splitCount = 1;
	private float maximumWidth;
	private float marginLeft;
	private float marginRight;

	public TGLayoutVerticalSplit(TGController controller, int style) {
		super(controller, style);
		this.dashPaint.setStyle(Paint.Style.STROKE);
	}

	public int getMode() {
		return MODE_VERTICAL;
	}

	public int getSplitCount() {
		return this.splitCount;
	}

	public void setSplitCount(int splitCount) {
		this.splitCount = Math.max(1, splitCount);
	}

	// ------------------------------------------------------------------
	// pieces
	// ------------------------------------------------------------------

	/** The pieces of a bar that was cut on the last paint, or null if it was painted whole. */
	public List<Part> getParts(TGMeasureImpl measure) {
		return this.parts.get(measure);
	}

	public void applyPart(TGMeasureImpl measure, Part part) {
		measure.setPosX(part.x - part.from);
		measure.setPosY(part.y);
		measure.setTs(part.ts);
	}

	/** The piece the bar currently sits on (see applyPart), or null for a whole bar. */
	public Part getAppliedPart(TGMeasureImpl measure) {
		List<Part> list = this.parts.get(measure);
		if( list != null ) {
			for( Part part : list ) {
				if( Math.abs((part.x - part.from) - measure.getPosX()) < 0.5f && Math.abs(part.y - measure.getPosY()) < 0.5f ) {
					return part;
				}
			}
		}
		return null;
	}

	public float getBeatLocalX(TGMeasureImpl measure, TGBeatImpl beat) {
		return (measure.getHeaderImpl().getLeftSpacing(this) + beat.getPosX() + beat.getSpacing(this));
	}

	public Part findPart(TGMeasureImpl measure, float localX) {
		List<Part> list = this.parts.get(measure);
		if( list == null || list.isEmpty() ) {
			return null;
		}
		for( Part part : list ) {
			if( localX < part.to ) {
				return part;
			}
		}
		return list.get(list.size() - 1);
	}

	/** Moves the bar onto the piece that shows the given beat. Returns that piece (null if the bar is whole). */
	public Part applyPartForBeat(TGMeasureImpl measure, TGBeatImpl beat) {
		if( measure == null || beat == null ) {
			return null;
		}
		Part part = this.findPart(measure, this.getBeatLocalX(measure, beat));
		if( part != null ) {
			this.applyPart(measure, part);
		}
		return part;
	}

	public void clipToPart(UIPainter painter, Part part) {
		Canvas canvas = ((TGPainterImpl) painter).getCanvas();
		canvas.clipRect(part.x, -100000f, part.x + part.getWidth(), 100000f);
	}

	/** Clip to the piece and stretch it over its line; pair with {@link #endPart}. */
	public void beginPart(UIPainter painter, Part part) {
		Canvas canvas = ((TGPainterImpl) painter).getCanvas();
		canvas.save();
		this.clipToPart(painter, part);
		canvas.scale(part.scale, 1f, part.x, 0f);
	}

	public void endPart(UIPainter painter) {
		((TGPainterImpl) painter).getCanvas().restore();
	}

	// ------------------------------------------------------------------
	// painting
	// ------------------------------------------------------------------

	public void paintSong(UIPainter painter, UIRectangle clientArea, float fromX, float fromY) {
		this.marginLeft = getFirstMeasureSpacing();
		this.marginRight = 10;
		this.maximumWidth = (clientArea.getWidth() - (this.marginLeft + this.marginRight));
		this.setHeight(0);
		this.setWidth(0);
		this.clearTrackPositions();
		this.measureLines = new ArrayList<Integer>();
		this.parts.clear();

		int number = getComponent().getTrackSelection();
		TGTrackImpl reference = null;
		Iterator<TGTrack> it = getSong().getTracks();
		while( it.hasNext() && reference == null ) {
			TGTrackImpl track = (TGTrackImpl) it.next();
			if( number < 0 || track.getNumber() == number ) {
				reference = track;
			}
		}
		if( reference == null ) {
			return;
		}

		List<Segment> segments = this.buildSegments(reference);
		float posY = Math.round(fromY + getFirstTrackSpacing());
		float height = getFirstTrackSpacing();
		int lineNb = 0;
		int next = 0;
		while( next < segments.size() ) {
			Line line = this.takeLine(segments, next, reference);
			next += line.segments.size();
			boolean lastLine = (next >= segments.size());

			Iterator<TGTrack> tracks = getSong().getTracks();
			while( tracks.hasNext() ) {
				TGTrackImpl track = (TGTrackImpl) tracks.next();
				if( number >= 0 && track.getNumber() != number ) {
					continue;
				}
				int style = getStyle(track);
				TGTrackSpacing ts = new TGTrackSpacing(this, track);
				ts.setSize(TGTrackSpacing.POSITION_SCORE_MIDDLE_LINES, ((style & DISPLAY_SCORE) != 0 ? (getScoreLineSpacing() * 5) : 0));

				Segment first = line.segments.get(0);
				if( first.measureIndex == 0 && first.index == 0 ) {
					((TGLyricImpl) track.getLyrics()).start();
				}

				float minY = 0;
				float maxY = 0;
				if( (style & DISPLAY_TABLATURE) == 0 && (style & DISPLAY_SCORE) != 0 ) {
					maxY = ((getScoreLineSpacing() * 4) + (getScoreLineSpacing() * 4));
					minY = -(getScoreLineSpacing() * 3);
				}
				int registered = -1;
				for( Segment segment : line.segments ) {
					if( segment.measureIndex == registered ) {
						continue;
					}
					registered = segment.measureIndex;
					TGMeasureImpl measure = (TGMeasureImpl) track.getMeasure(segment.measureIndex);
					maxY = Math.max(maxY, measure.getMaxY());
					minY = Math.min(minY, measure.getMinY());
					measure.registerSpacing(this, ts);
				}
				if( (style & DISPLAY_SCORE) != 0 ) {
					ts.setSize(TGTrackSpacing.POSITION_SCORE_UP_LINES, Math.abs(minY));
					if( maxY > track.getScoreHeight() ) {
						ts.setSize(TGTrackSpacing.POSITION_SCORE_DOWN_LINES, (maxY - track.getScoreHeight()));
					}
				}
				if( (style & DISPLAY_TABLATURE) != 0 ) {
					ts.setSize(TGTrackSpacing.POSITION_TABLATURE_TOP_SEPARATOR, ((style & DISPLAY_SCORE) != 0 ? getMinScoreTabSpacing() : Math.max(Math.abs(minY), getStringSpacing())));
					ts.setSize(TGTrackSpacing.POSITION_TABLATURE, ((style & DISPLAY_SCORE) != 0 ? track.getTabHeight() + getStringSpacing() + 1 : Math.max(maxY, track.getTabHeight() + getStringSpacing() + 1)));
				}
				ts.setSize(TGTrackSpacing.POSITION_LYRIC, 10);
				checkDefaultSpacing(track, ts);

				float usedWidth = this.paintLine(track, line, painter, fromX, posY, ts, clientArea);

				float lineHeight = ts.getSize();
				addTrackPosition(track.getNumber(), posY, lineHeight);

				// staff lines over what is left of the line (end of song, or a line of bar pieces)
				float emptyX = (this.marginLeft + fromX + usedWidth);
				float emptyWidth = ((this.marginLeft + fromX + this.maximumWidth) - emptyX);
				boolean fill = (lastLine ? (emptyWidth - 20) > 0 : emptyWidth > 1);
				if( fill && emptyX < (clientArea.getX() + clientArea.getWidth()) && posY + lineHeight > clientArea.getY() && posY < clientArea.getY() + clientArea.getHeight() ) {
					paintLines(track, ts, painter, emptyX, posY, emptyWidth);
				}

				float lineHeightWithSpacing = Math.round(lineHeight + getTrackSpacing() + 0.5f);
				posY += lineHeightWithSpacing;
				height += lineHeightWithSpacing;

				for( Segment segment : line.segments ) {
					while( this.measureLines.size() <= segment.measureIndex ) {
						this.measureLines.add(Integer.valueOf(lineNb));
					}
				}
				lineNb++;
			}
		}

		this.setHeight(height);
		this.setWidth(getWidth() + this.marginRight);
		this.updateTimeToScroll(reference);
	}

	/** Returns the width used by the line. */
	private float paintLine(TGTrackImpl track, Line line, UIPainter painter, float fromX, float fromY, TGTrackSpacing ts, UIRectangle clientArea) {
		float posX = Math.round(this.marginLeft + fromX);
		float posY = Math.round(fromY);
		float startX = posX;

		boolean isAtY = (posY + ts.getSize() > clientArea.getY() && posY < clientArea.getY() + clientArea.getHeight() + (getScale() * 80f));

		// leftover room goes to the whole bars of the line (pieces keep their cut width)
		int wholeCount = 0;
		for( Segment segment : line.segments ) {
			if( segment.count == 1 ) {
				wholeCount ++;
			}
		}
		float defaultSpacing = 0;
		if( line.full && wholeCount > 0 ) {
			float diff = (this.maximumWidth - line.width);
			if( diff > 0 ) {
				defaultSpacing = (diff / wholeCount);
			}
		}
		float spacing = defaultSpacing;

		for( int i = 0; i < line.segments.size(); i ++ ) {
			Segment segment = line.segments.get(i);
			TGMeasureImpl measure = (TGMeasureImpl) track.getMeasure(segment.measureIndex);
			((TGLyricImpl) track.getLyrics()).setCurrentMeasure(measure);
			measure.setFirstOfLine(i == 0);
			measure.setLastOfLine(i == line.segments.size() - 1);
			measure.setPosY(posY);
			measure.setTs(ts);

			if( segment.count == 1 ) {
				measure.setPosX(posX);
				float measureWidth = measure.getWidth(this);
				float withSpacing = Math.round(measureWidth + spacing);
				float spacingAfterRound = (withSpacing - measureWidth);
				boolean isAtX = (posX + withSpacing > clientArea.getX() && posX < clientArea.getX() + clientArea.getWidth());
				if( isAtX && isAtY ) {
					paintMeasure(measure, painter, spacingAfterRound);
					((TGLyricImpl) track.getLyrics()).paintCurrentNoteBeats(painter, this, measure, posX, posY);
				} else {
					measure.setOutOfBounds(true);
				}
				posX += withSpacing;
				spacing = (defaultSpacing + (spacing - spacingAfterRound));
			} else {
				Part part = new Part();
				part.index = segment.index;
				part.count = segment.count;
				part.from = segment.from;
				part.to = segment.to;
				part.x = posX;
				part.y = posY;
				part.screenWidth = this.maximumWidth;
				part.scale = (segment.width() > 0 ? this.maximumWidth / segment.width() : 1f);
				part.ts = ts;
				List<Part> list = this.parts.get(measure);
				if( list == null || segment.index == 0 ) {
					list = new ArrayList<Part>();
					this.parts.put(measure, list);
				}
				list.add(part);

				this.applyPart(measure, part);
				measure.setSpacing(segment.spacing);
				float width = part.getWidth();
				boolean isAtX = (posX + width > clientArea.getX() && posX < clientArea.getX() + clientArea.getWidth());
				if( isAtX && isAtY && painter instanceof TGPainterImpl ) {
					this.beginPart(painter, part);
					paintMeasure(measure, painter, segment.spacing);
					((TGLyricImpl) track.getLyrics()).paintCurrentNoteBeats(painter, this, measure, measure.getPosX(), posY);
					this.endPart(painter);
					this.paintJoins(painter, measure, part);
				} else if( segment.index == 0 ) {
					measure.setOutOfBounds(true);
				}
				posX += Math.round(width);
			}
		}
		this.setWidth(Math.max(getWidth(), (posX - startX) + this.marginLeft));
		return (posX - startX);
	}

	/** Dashed lines where a bar was cut: the pieces still read as one bar. */
	private void paintJoins(UIPainter painter, TGMeasureImpl measure, Part part) {
		Canvas canvas = ((TGPainterImpl) painter).getCanvas();
		UIRectangle bounds = measure.getVerticalPosition(this);
		float scale = getScale();
		this.dashPaint.setColor(0xFF9A9A9A);
		this.dashPaint.setStrokeWidth(Math.max(1f, 1.2f * scale));
		this.dashPaint.setPathEffect(new DashPathEffect(new float[] {4f * scale, 3f * scale}, 0f));
		float y1 = bounds.getY();
		float y2 = bounds.getY() + bounds.getHeight();
		if( part.index > 0 ) {
			float x = part.x + (0.5f * scale);
			canvas.drawLine(x, y1, x, y2, this.dashPaint);
		}
		if( part.index < part.count - 1 ) {
			float x = part.x + part.getWidth() - (0.5f * scale);
			canvas.drawLine(x, y1, x, y2, this.dashPaint);
		}
	}

	// ------------------------------------------------------------------
	// cutting & line building
	// ------------------------------------------------------------------

	private List<Segment> buildSegments(TGTrackImpl track) {
		List<Segment> segments = new ArrayList<Segment>();
		int count = track.countMeasures();
		for( int index = 0; index < count; index ++ ) {
			TGMeasureImpl measure = (TGMeasureImpl) track.getMeasure(index);
			float width = measure.getWidth(this);

			// how many lines the bar takes: the requested count, or enough to fit
			int pieces = this.splitCount;
			if( width > this.maximumWidth ) {
				pieces = Math.max(pieces, (int) Math.ceil(width / this.maximumWidth));
			}
			List<Float> cuts = new ArrayList<Float>();
			float spacing = 0f;
			for( int attempt = 0; attempt < 4 && pieces > 1; attempt ++ ) {
				// spread the notes so the bar is exactly <pieces> lines long, then cut between beats
				spacing = Math.max(0f, (pieces * this.maximumWidth) - width);
				measure.setSpacing(spacing);
				cuts = this.findCuts(measure, width + spacing, pieces);
				if( cuts.size() == pieces - 1 ) {
					break;
				}
				// not enough beats to cut that often: fewer, longer pieces
				pieces = cuts.size() + 1;
			}
			if( pieces <= 1 || cuts.isEmpty() ) {
				measure.setSpacing(0f);
				Segment segment = new Segment();
				segment.measureIndex = index;
				segment.count = 1;
				segment.to = width;
				segments.add(segment);
				continue;
			}
			float from = 0;
			for( int p = 0; p < pieces; p ++ ) {
				Segment segment = new Segment();
				segment.measureIndex = index;
				segment.index = p;
				segment.count = pieces;
				segment.spacing = spacing;
				segment.from = from;
				segment.to = (p < cuts.size() ? cuts.get(p).floatValue() : width + spacing);
				segments.add(segment);
				from = segment.to;
			}
		}
		return segments;
	}

	/** Cut points (bar-local x, notes spread) just before beats, as close as possible to even pieces. */
	private List<Float> findCuts(TGMeasureImpl measure, float width, int pieces) {
		List<Float> cuts = new ArrayList<Float>();
		float pad = (6f * getScale());
		float minPiece = (24f * getScale());
		List<Float> candidates = new ArrayList<Float>();
		float leftSpacing = measure.getHeaderImpl().getLeftSpacing(this);
		boolean first = true;
		float lastStart = -1;
		for( TGBeat beat : measure.getBeats() ) {
			TGBeatImpl beatImpl = (TGBeatImpl) beat;
			float start = leftSpacing + beatImpl.getPosX() + beatImpl.getSpacing(this);
			if( first ) {
				first = false;
				continue;
			}
			if( Math.abs(start - lastStart) < 0.5f ) {
				continue;
			}
			lastStart = start;
			float cut = start - pad;
			if( cut > minPiece && cut < width - minPiece ) {
				candidates.add(Float.valueOf(cut));
			}
		}
		float previous = 0;
		for( int k = 1; k < pieces; k ++ ) {
			float target = (width * k) / pieces;
			Float best = null;
			for( Float candidate : candidates ) {
				if( candidate.floatValue() <= previous + minPiece ) {
					continue;
				}
				if( best == null || Math.abs(candidate.floatValue() - target) < Math.abs(best.floatValue() - target) ) {
					best = candidate;
				}
			}
			if( best == null ) {
				break;
			}
			cuts.add(best);
			previous = best.floatValue();
		}
		return cuts;
	}

	private Line takeLine(List<Segment> segments, int fromIndex, TGTrackImpl track) {
		Line line = new Line();
		for( int i = fromIndex; i < segments.size(); i ++ ) {
			Segment segment = segments.get(i);
			if( segment.count > 1 ) {
				// a piece of a cut bar has the whole line to itself
				if( line.segments.isEmpty() ) {
					line.segments.add(segment);
					line.width = this.maximumWidth;
					line.full = true;
				}
				return line;
			}
			if( !line.segments.isEmpty() && segment.index == 0 && ((TGMeasureImpl) track.getMeasure(segment.measureIndex)).isLineBreak() ) {
				return line;
			}
			if( !line.segments.isEmpty() && line.width + segment.width() > this.maximumWidth ) {
				line.full = true;
				return line;
			}
			line.segments.add(segment);
			line.width += segment.width();
		}
		return line;
	}

	private void updateTimeToScroll(TGTrackImpl track) {
		this.timeToNextScrollMs.clear();
		int measureCount = getSong().countMeasureHeaders();
		if( measureCount == 0 ) {
			return;
		}
		float lastLineY = ((TGMeasureImpl) track.getMeasure(measureCount - 1)).getPosY();
		for( int index = measureCount - 1; index >= 0; index -- ) {
			this.timeToNextScrollMs.add(0, 0);
			TGMeasureImpl measure = (TGMeasureImpl) track.getMeasure(index);
			if( measure.getPosY() != lastLineY && index + 1 < measureCount ) {
				if( measure.getPosY() != ((TGMeasureImpl) track.getMeasure(index + 1)).getPosY() ) {
					this.timeToNextScrollMs.set(0, measure.getHeader().getDurationInMs());
				} else {
					this.timeToNextScrollMs.set(0, this.timeToNextScrollMs.get(1) + measure.getHeader().getDurationInMs());
				}
			}
		}
	}
}
