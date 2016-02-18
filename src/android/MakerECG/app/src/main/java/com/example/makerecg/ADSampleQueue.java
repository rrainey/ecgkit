package com.example.makerecg;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import android.util.Log;

/**
 * This is really two queues of data blocks; one contains all logged data, the second
 * "active" queue holds the blocks that contain displayable data, based on the age of
 * the contained data.
 * 
 * This queue is managed as a singleton.
 * 
 * @author riley
 */
public class ADSampleQueue {
	
	/**
	 * plottable samples (samples inside plotting time region)
	 */
	protected Queue<ADSampleFrame> _activeSampleBlocks;
	/**
	 * total sample set
	 */
	protected Queue<ADSampleFrame> _sampleBlocks;
	protected Queue<ADSampleFrame> _writeableSampleBlocks;
	protected long _activeElapsedTime_ms;
	protected long _totalElapsedTime_ms;
	
	protected long _lMaximumElapsedTimeHistory_ms = 240000;
	
	protected final static String TAG = "ADSampleQueue";
	
	/**
	 * This constant represents the time that we'll silently collect samples before beginning
	 * a ECG plot.  The intent is to ensure smooth plotting of the results.
	 * 
	 * The Arduino Mega ADK appears to be able to collect one sample every 1.1 milliseconds.
	 * That's faster than we need for an accurate plot, so we'll have the sketch record one
	 * sample every (roughly) 5ms -- that's 8 samples per horizontal time division (40ms).
	 * If we arbitrarily say that we'll make the latency correspond to two blocks of sample data,
	 * then that corresponds to 20 samples per block.
	 */
	public static final long TARGET_LATENCY_MS = 200;
	
	public enum QUEUE_STATE {
		QUEUEING,
		PLOTTING,
		FROZEN,
	}
	
	protected QUEUE_STATE _queueState;
	
	private static ADSampleQueue _singletonObject;
	
	private ADSampleQueue() {
		_sampleBlocks = new LinkedBlockingQueue<ADSampleFrame>();
		_activeSampleBlocks = new LinkedBlockingQueue<ADSampleFrame>();
		_writeableSampleBlocks = new LinkedBlockingQueue<ADSampleFrame>();
		_activeElapsedTime_ms = 0;
		_totalElapsedTime_ms = 0;
		_queueState = QUEUE_STATE.QUEUEING;
	}
	
	public static synchronized ADSampleQueue getSingletonObject() {
		if (_singletonObject == null) {
			_singletonObject = new ADSampleQueue();
		}
		return _singletonObject;
	}
	
	public Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}
	
	/**
	 * Add an arriving sample frame to the queue.
	 * @param frame the sample frame to be added
	 * @return returns true if the frame was added (based on the current queue state).
	 */
	public synchronized boolean addFrame( ADSampleFrame frame ) {
		
		long frameElapsed_ms = frame.getElapsedTime();
		boolean bSavedFrame = false;
		
		switch (_queueState) {
		case QUEUEING:
		case PLOTTING:

			_sampleBlocks.add (frame);
			_activeSampleBlocks.add (frame);
			_writeableSampleBlocks.add (frame);
	
			_activeElapsedTime_ms += frameElapsed_ms;
			_totalElapsedTime_ms += frameElapsed_ms;

			if (_queueState == QUEUE_STATE.QUEUEING) {
				/* can we begin plotting? */
				if (_totalElapsedTime_ms >= TARGET_LATENCY_MS) {
					_queueState = QUEUE_STATE.PLOTTING;
					Log.d(TAG, "Switching to plotting state");
				}
			}
			bSavedFrame = true;
		}
		
		//Log.d(ADK.TAG, "insertFrame(" + frame +") returns " + bSavedFrame);
		return bSavedFrame;
	}
	
	/*
	 * Cull block(s) that contain no plottable data from the active queue, 
	 * based on the specific current plot time origin.
	 */
	void cullHistoryBlocks(long nPlotTimeOrigin_ms) {
		boolean bRemoved = true;
		while ( bRemoved && _activeSampleBlocks.peek() != null ) {
			if ( _activeSampleBlocks.peek().getEndTimestamp() < nPlotTimeOrigin_ms ) {
				_activeSampleBlocks.remove();
				bRemoved = true;
			}
		}
		
		while ( _totalElapsedTime_ms > _lMaximumElapsedTimeHistory_ms ) {
			ADSampleFrame f = _sampleBlocks.element();
			if ( f != null ) {
				_totalElapsedTime_ms -= f.getElapsedTime();
				_sampleBlocks.remove();
			}
		}
	}

	/**
	 * Clear the queue and prepare to begin sampling
	 */
	public synchronized void restart() {
		Log.d(TAG, "Queue restart called");
		_sampleBlocks.clear();
		_activeSampleBlocks.clear();
		_activeElapsedTime_ms = 0;
		_totalElapsedTime_ms = 0;
		_queueState = QUEUE_STATE.QUEUEING;
	}
	
	public synchronized void freeze() {
		Log.d(TAG, "Queue: freeze called");
		if ( _queueState == QUEUE_STATE.QUEUEING || _queueState == QUEUE_STATE.PLOTTING ) {
			_queueState = QUEUE_STATE.FROZEN;
		}
	}
	
	public synchronized void thaw() {
		Log.d(TAG, "Queue: thaw called");
		if ( _queueState == QUEUE_STATE.FROZEN ) {
			restart();
		}
	}
	
	public synchronized QUEUE_STATE getQueueState() {
		return _queueState;
	}

	public synchronized Queue<ADSampleFrame> getWriteableSampleBlocks() {
		return _writeableSampleBlocks;
	}
	
	
	public synchronized int generatePlotPoints( long lStartTimestamp_ms, 
			                                long lEndTimestamp_ms, 
			                                long[] xpoints, 
			                                long[] ypoints,
			                                int nMaxPoints ) {
		ADSampleFrame last = null;
		ADSampleFrame cur = null;
		Iterator<ADSampleFrame> it = _activeSampleBlocks.iterator();
		int n=0;
		boolean bFirstPlottableFrame = true;
		boolean bDone = false;
		double dCurTimestamp_ms = lStartTimestamp_ms;
		
		/*
		 * Locate the frame with the first plottable data points
		 */
		while(it.hasNext() && bDone == false) {
			
			last = cur;
			cur = it.next();
			
			//Log.d(ADK.TAG, "outer loop: cur = "+cur);
			
			if ( bFirstPlottableFrame ) {
				/*
				 * This special check allows us to gracefully bridge the time gap between two
				 * frames.  Use the final sample in the last frame in this case.
				 */
				if ( last != null ) {
					if ( lStartTimestamp_ms < cur.getStartTimestamp() ) {
						/*
						 * use ending sample in "last"
						 */
						xpoints[n] = lStartTimestamp_ms;
						ypoints[n++] = last.getSamples()[(int) (last.getSampleCount()-1)];
					}
				}
				/*
				 * Time within the bounds of the current frame? add relevant samples in this frame
				 */
				if ( cur.getStartTimestamp() <= lStartTimestamp_ms && lStartTimestamp_ms <= cur.getEndTimestamp() ) {
					/*
					 * Locate the first appropriate sample in "cur" and then add 
					 * time samples starting with that item.
					 */
					long nFrameStartOffset_ms = lStartTimestamp_ms - cur.getStartTimestamp();
					long nFrameLength_ms = cur.getElapsedTime();
					int nIndex = (int)( nFrameStartOffset_ms * cur.getSampleCount() / nFrameLength_ms );
					float fSampleInterval_ms = (float) nFrameLength_ms / cur.getSampleCount();
					short [] nSample = cur.getSamples();
					int nSampleCount = (int) cur.getSampleCount();
					//Log.d(ADK.TAG, "At item "+n+"; sample interval is " + fSampleInterval_ms + "(ms); time is "+dCurTimestamp_ms);
					dCurTimestamp_ms = cur.getStartTimestamp();
					
					while ( nIndex < nSampleCount && bDone == false) {
						if ( (long) dCurTimestamp_ms > lEndTimestamp_ms ) {
							bDone = true;
						}
						else {
							xpoints[n] = (long) dCurTimestamp_ms;
							ypoints[n++] = nSample[ nIndex++ ];
							dCurTimestamp_ms += fSampleInterval_ms;
						}
					}
					
					bFirstPlottableFrame = false;
				}
				
			}
			else {
				/*
				 * After we've processed the first frame
				 */
				int nIndex = 0;
				float fSampleInterval_ms = (float) cur.getElapsedTime() / cur.getSampleCount();
				short [] nSample = cur.getSamples();
				int nSampleCount = (int) cur.getSampleCount();
				//Log.d(ADK.TAG, "At item "+n+"; sample interval is " + fSampleInterval_ms + "(ms); time is "+dCurTimestamp_ms);
				dCurTimestamp_ms = cur.getStartTimestamp();
				
				nIndex = 0;
				
				while ( nIndex < nSampleCount && bDone == false ) {
					if ( (long) dCurTimestamp_ms > lEndTimestamp_ms ) {
						bDone = true;
					}
					else {
						xpoints[n] = (long) dCurTimestamp_ms;
						ypoints[n++] = nSample[ nIndex++ ];
						dCurTimestamp_ms += fSampleInterval_ms;
					}
					
					/*
					 * Bail out if we've reached the maximum number of points
					 */
					if ( n == nMaxPoints ) {
						bDone = true;
					}
				}
			}
		}	
		//Log.d(ADK.TAG, ""+n+" points to be displayed");
		return n;
	}
}
