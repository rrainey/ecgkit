/**
 * 
 */
package com.example.makerecg;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import com.example.makerecg.ADSampleQueue.QUEUE_STATE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLU;
import android.opengl.GLSurfaceView.Renderer;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Divide the display into standard ECG "divisions"; the standard X scale is 40ms
 * per division.  The standard Y scale is 0.1mV per division.
 * ECG trace sweeps from left to right.
 * @author riley
 * @see http://en.wikipedia.org/w/index.php?title=File:ECG_Paper_v2.svg&page=1
 */
public class ECGRendererGL implements Renderer {
	
	/*
	 * Horizontal division count in display
	 */
	static int _nDivisionCount = 60;

	/*
	 * Standard ECG depiction scale for X-Axis
	 */
	static float _fMillisecondsPerDivision = 40.0f;

	/*
	 * Standard ECG depiction scale for Y-Axis
	 */
	static float _fVoltsPerDivision = 0.0001f;

	static short _sHorizDivisionWidth = 1;
	public static final short THICK_LINE_INTERVAL = 5;
	protected float _fHalfWidth;
	protected short _sHalfWidth;
	protected float _fHalfHeight;
	protected short _sHalfHeight;
	protected static int _nPoints = 0;
	protected static int _nThickPoints = 0;
	protected ShortBuffer _thinGridBuffer;
	protected ShortBuffer _thickGridBuffer;
	protected FloatBuffer _ecgTraceBuffer;
	protected boolean _bConnected = false;
	protected boolean _bDataBlockReceived = false;
	
	protected long _lPlotStartTimestamp_ms;				// "time" at left hand side of graph; a capture time
														// elapsed time since capture started

	protected long _lPlotLimitTimestamp_ms;				// a capture timestamp
	
	protected float _fSignalGain;						// Gain of amplifier upstream from Arduino A-D input
	protected float _fADScale_volts;				    // Arduino Mega ADK full scale signal voltage
	protected float _fScaling_voltsPerMinorDivision;	// Desired 'plotted signal' voltage scale for the graph
	protected float _fTotalScaling;						// Computed value (for more efficient calculation of results)
	protected float _fPlotYOrigin_div;						// "zero" signal volts on A-D input
	protected float _fPlotTimeOrigin_div;					// X-plot coordinate corresponding to mnStartTimestamp_ms
	protected long _lLastTimestamp_ms;					// a capture timestamp
	protected long _lCurrentTimestamp_ms;				// a capture timestamp; this is the plotting cursor
	protected long _lTimebaseOrigin_ms;					// System milliseconds at capture time zero
	protected int _lastGridWidth;
	protected int _lastGridHeight;
	protected Bitmap	_beamBitmap;
	
	protected long _lXC[];
	protected long _lYC[];
	protected float _fPoint[];
	
	protected ADSampleQueue	_sampleQueue;
	/*
	 * A simple mechanism to detect when we are restarting our plotting display
	 */
	protected ADSampleQueue.QUEUE_STATE _lastQueueState;
	
	protected Texture _beamSprite;
	
	public ECGRendererGL(Context c) {
		
		_sampleQueue = ADSampleQueue.getSingletonObject();
		
		_lastGridWidth = -1;
		_lastGridHeight = -1;
		_beamBitmap = BitmapFactory.decodeResource( c.getResources(), R.drawable.beam );
		
		_lXC = new long[2048];
		_lYC = new long[2048];
		_fPoint = new float[3*2048];
		
		ByteBuffer vbb = ByteBuffer.allocateDirect(2048*12);
        vbb.order(ByteOrder.nativeOrder());
        FloatBuffer buffer=vbb.asFloatBuffer();
        _ecgTraceBuffer = buffer;
		
		/*
		 * Set input signal scaling parameters
		 * 
		 * Signal amplified 8x in hardware
		 * 5.0v ADC sampling
		 * 1.0mV (0.001volts) per vertical division on the graph
		 */
		setSampleParameters( 8.0f, 5.0f, 0.001f );
	}
	
	public boolean isConnected() {
		return _bConnected;
	}

	public void setConnected(boolean bConnected) {
		this._bConnected = bConnected;
	}

	public boolean isDataBlockReceived() {
		return _bDataBlockReceived;
	}

	public void setDataBlockReceived(boolean bDataBlockReceived) {
		this._bDataBlockReceived = bDataBlockReceived;
	}
	
	public void initializeNewPlot() {
		_lLastTimestamp_ms = 0;
		_lCurrentTimestamp_ms = 0;
		_lTimebaseOrigin_ms = System.currentTimeMillis();
		_lPlotStartTimestamp_ms = _lCurrentTimestamp_ms;
		_lPlotLimitTimestamp_ms = _nDivisionCount * (int) _fMillisecondsPerDivision;
		_lastQueueState = ADSampleQueue.getSingletonObject().getQueueState();
	}
	
	/**
	 * Update plotting parameters to account for the passage of real time
	 * Perform maintenance on the sample queue.
	 */
	public void elapsedTimeUpdate() {
		
		ADSampleQueue q = ADSampleQueue.getSingletonObject();
		
		ADSampleQueue.QUEUE_STATE s = q.getQueueState();
			
		if ( s == ADSampleQueue.QUEUE_STATE.PLOTTING ) {
			
			/*
			 * Detect start of plotting
			 */
			if (s != _lastQueueState ) {
				_lTimebaseOrigin_ms = System.currentTimeMillis();
			}
			_lastQueueState = s;
			
			_lLastTimestamp_ms = _lCurrentTimestamp_ms;
			_lCurrentTimestamp_ms = System.currentTimeMillis() - _lTimebaseOrigin_ms;
			
			//Log.d(ADK.TAG, "current timestamp " + _lCurrentTimestamp_ms + "(ms)");
		
			/*
			 * Wrapping the plot?
			 */
			while (_lCurrentTimestamp_ms >= _lPlotLimitTimestamp_ms) {
				_lPlotStartTimestamp_ms = _lPlotLimitTimestamp_ms;
				_lPlotLimitTimestamp_ms += _nDivisionCount * (int) _fMillisecondsPerDivision;
				
				//Log.d(ADK.TAG, "RR new plotting window; start time " + _lPlotStartTimestamp_ms + "(ms)");
			}
		}
		
		//q.cullHistoryBlocks( _lPlotStartTimestamp_ms );
	}

	/* (non-Javadoc)
	 * @see android.opengl.GLSurfaceView.Renderer#onDrawFrame(javax.microedition.khronos.opengles.GL10)
	 */
	public void onDrawFrame(GL10 gl10) {

		if ( gl10 instanceof GL11 ) {
			
			GL11 gl = (GL11) gl10;
			
			/*
			 * account for the passage of real time
			 */
			elapsedTimeUpdate();

			gl.glPushMatrix();

			//gl.glEnable(GL11.GL_LINE_SMOOTH);
			gl.glEnable(GL11.GL_BLEND);
			gl.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			//gl.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_DONT_CARE);

			gl.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

			gl.glEnableClientState(GL11.GL_VERTEX_ARRAY);

			/*
			 * Major grid lines
			 *
			 * See https://groups.google.com/forum/?fromgroups=#!topic/android-developers/qDPtw7i-ZtE
			 */
			gl.glLineWidth(5.0f);
			gl.glColor4f(0.0f, 0.0f, 1.0f, 0.25f);
			gl.glVertexPointer(3, GL11.GL_SHORT, 0, _thinGridBuffer);
			gl.glDrawArrays(GL11.GL_LINES, 0, _nPoints);

			/*
			 * Minor grid lines
			 */
			gl.glLineWidth(3.0f);
			gl.glColor4f(0.0f, 0.0f, 1.0f, 0.80f);
			gl.glVertexPointer(3, GL11.GL_SHORT, 0, _thickGridBuffer);
			gl.glDrawArrays(GL11.GL_LINES, 0, _nThickPoints);

			plotECGTrace( gl );

			gl.glDisableClientState(GL11.GL_VERTEX_ARRAY);

			gl.glPopMatrix();
		
		}

	}
	
	private void plotECGTrace(GL11 gl) {
		
		int nCount = 0;
		int n = 0;
		float fX_div = 0.0f, fY_div = 0.0f, fZ_div = 0.0f;
		
		/*
		 * We will only plot the trace when we have enough data
		 * samples.
		 */
		QUEUE_STATE s = _sampleQueue.getQueueState();
		
		if ( s == ADSampleQueue.QUEUE_STATE.PLOTTING ||
			 s == ADSampleQueue.QUEUE_STATE.FROZEN ) {
		
			nCount = _sampleQueue.generatePlotPoints( _lPlotStartTimestamp_ms, 
					                                  _lCurrentTimestamp_ms, 
					                                  _lXC, 
					                                  _lYC,
					                                  2048);
			
			for( int i=0; i<nCount; ++i ) {
				fX_div = (_lXC[i] - _lPlotStartTimestamp_ms) / _fMillisecondsPerDivision + _fPlotTimeOrigin_div;
				fY_div = convertSampleToYCoordinate( (short) _lYC[i] );
				fZ_div = convertSampleToZCoordinate((short) i);
				
				_fPoint[n++]  = fX_div;
				_fPoint[n++]  = fY_div;
				_fPoint[n++]  = fZ_div;
				
				//Log.d(ADK.TAG, "plot (" + fX_div + ", " + fY_div + " )");
			}
			
			//Log.d(ADK.TAG, "" + nCount + " total ECG points");

			if ( nCount > 1 ) {

				gl.glLineWidth(5.0f);

				gl.glColor4f( 0.0f, 1.0f, 0.0f, 1.0f );
			    
			    //_ecgTraceBuffer = toFloatBuffer( _fPoint, nCount );

			    _ecgTraceBuffer.position(0);
			    _ecgTraceBuffer.put(_fPoint, 0, nCount*3);
			    _ecgTraceBuffer.position(0);

			    gl.glVertexPointer( 3, GL11.GL_FLOAT, 0, _ecgTraceBuffer );
				gl.glDrawArrays( GL11.GL_LINE_STRIP, 0, nCount );	
				
				//_beamSprite.drawSprite( gl, fX_div, fY_div, fZ_div );
			}
		}
		else {
			//Log.d( ADK.TAG, "No plot;" );
			//_beamSprite.drawSprite( gl, 0.0f, 0.0f, 0.0f );
		}
	}

	public void generateGrid() {
		/*
		 * Draw the grid (from left to right; then bottom up)
		 */
		_nPoints = 0;
		_nThickPoints = 0;

		// Size temporary arrays to collect endpoints of grid lines:
		// Number of Axes * 2 (lines are "pairs" of coordinates) * 
		// number of coordinates (x,Y,Z = 3) * number of divisions
		int nElements = 2*2*3*_nDivisionCount*2;

		int nThickElements = (nElements / THICK_LINE_INTERVAL + 2)*2;

		short[] minor = new short[nElements];
		short[] major = new short[nThickElements];
		short nIndex = 0;
		short nThickIndex = 0;

		_fPlotTimeOrigin_div = (short) -_sHalfWidth;
		
		short sCurX = (short) -_sHalfWidth;
		for ( ; sCurX<=_sHalfWidth; sCurX+=1) {
			if ( sCurX % THICK_LINE_INTERVAL == 0 ) {
				major[nThickIndex++] = sCurX;
				major[nThickIndex++] = (short) -_sHalfHeight;
				major[nThickIndex++] = 0;
				
				major[nThickIndex++] = sCurX;
				major[nThickIndex++] = _sHalfHeight;
				major[nThickIndex++] = 0;
				_nThickPoints += 2;				
			}
			else {
				minor[nIndex++] = sCurX;
				minor[nIndex++] = (short) -_sHalfHeight;
				minor[nIndex++] = 0;
				
				minor[nIndex++] = sCurX;
				minor[nIndex++] = _sHalfHeight;
				minor[nIndex++] = 0;
				_nPoints += 2;
			}
			
		}

		// Olimex Sheild-specfic value
		_fPlotYOrigin_div = /*0.0f*/ (short) _sHalfHeight;
		
		short sCurY = (short) -_sHalfHeight;
		for ( ; sCurY<=_sHalfHeight; sCurY+=1) {
			if ( sCurY % THICK_LINE_INTERVAL == 0 ) {
				major[nThickIndex++] = (short) -_sHalfWidth;
				major[nThickIndex++] = sCurY;
				major[nThickIndex++] = 0;
				
				major[nThickIndex++] = (short) _sHalfWidth;
				major[nThickIndex++] = sCurY;
				major[nThickIndex++] = 0;
				_nThickPoints += 2;
			}
			else {
				minor[nIndex++] = (short) -_sHalfWidth;
				minor[nIndex++] = sCurY;
				minor[nIndex++] = 0;
				
				minor[nIndex++] = (short) _sHalfWidth;
				minor[nIndex++] = sCurY;
				minor[nIndex++] = 0;
				_nPoints += 2;
			}
		}
		_thinGridBuffer = toShortBuffer(minor, _nPoints);
		_thickGridBuffer = toShortBuffer(major, _nThickPoints);
	}

	/* (non-Javadoc)
	 * @see android.opengl.GLSurfaceView.Renderer#onSurfaceChanged(javax.microedition.khronos.opengles.GL10, int, int)
	 */
	public void onSurfaceChanged(GL10 gl10, int width, int height) {
		
		if ( gl10 instanceof GL11 ) {
			
			GL11 gl = (GL11) gl10;
		
			Log.i(ADK.TAG, "onSurfaceChanged");
			
			// Sets the current view port to the new size.
			gl.glViewport( 0, 0, width, height );
			// Select the projection matrix
			gl.glMatrixMode( GL11.GL_PROJECTION );
			// Reset the projection matrix
			gl.glLoadIdentity();
			_fHalfWidth = (float) _nDivisionCount * 0.5f;
			_fHalfHeight = height * _fHalfWidth / (float) width;
			GLU.gluOrtho2D(gl, -(_fHalfWidth + 1.0f), _fHalfWidth + 1.0f, -_fHalfHeight, _fHalfHeight);
			// Subtracting 4.0f here to ensure the vertical lines are shown
			_sHalfWidth = (short) (_fHalfWidth + 0.5f );
			_sHalfHeight = (short) (_fHalfHeight + 0.5f);
			
			// Disable a few things we are not going to use.
		    gl.glDisable(GL11.GL_LIGHTING);
		    gl.glDisable(GL11.GL_CULL_FACE);
		    gl.glDisable(GL11.GL_DEPTH_BUFFER_BIT);
		    gl.glDisable(GL11.GL_DEPTH_TEST);
		    gl.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
		    gl.glShadeModel(GL11.GL_SMOOTH);
	
			// Select the modelview matrix
			gl.glMatrixMode( GL11.GL_MODELVIEW );
			// Reset the modelview matrix
			gl.glLoadIdentity();
			
			// we can avoid regenerating the grid unnecessarily if we 
			// compare the new height/width with the H/W used to generate the original grid
			// and only call generateGrid() when it has changed.
			if (width != _lastGridWidth || height != _lastGridHeight ) {
				generateGrid();
				_lastGridWidth = width;
				_lastGridHeight = height;
			}
		
			_beamSprite = new Texture(gl, _beamBitmap);
		
		}
	}

	/* (non-Javadoc)
	 * @see android.opengl.GLSurfaceView.Renderer#onSurfaceCreated(javax.microedition.khronos.opengles.GL10, javax.microedition.khronos.egl.EGLConfig)
	 */
	public void onSurfaceCreated(GL10 gl, EGLConfig arg1) {
		Log.i(ADK.TAG, "onSurfaceCreated");
		initializeNewPlot();
	}
	
	/**
	 * Save an array of 3D points in a buffer suitable for use in GL drawing calls
	 * @param values short[] array containing values to be saved
	 * @param nCount count of 3D points in the array.
	 * @return
	 */
	private static ShortBuffer toShortBuffer(short[] values, int nCount) {
		ByteBuffer vbb = ByteBuffer.allocateDirect(nCount*6);
        vbb.order(ByteOrder.nativeOrder());
        ShortBuffer buffer=vbb.asShortBuffer();
        buffer.position(0);
        buffer.put(values, 0, nCount*3);
        buffer.position(0);
        return buffer;
	}
	
	/**
	 * Save an array of 3D points in a buffer suitable for use in GL drawing calls
	 * @param values float[] array containing values to be saved
	 * @param nCount count of 3D points in the array.
	 * @return
	 */
	private static FloatBuffer toFloatBuffer(float[] values, int nCount) {
		ByteBuffer vbb = ByteBuffer.allocateDirect(nCount*12);
        vbb.order(ByteOrder.nativeOrder());
        FloatBuffer buffer=vbb.asFloatBuffer();
        buffer.position(0);
        buffer.put(values, 0, nCount*3);
        buffer.position(0);
        return buffer;
	}

	public void setSampleParameters( float fSignalGain, float fARef_volts, float fScaling_voltsPerMinorDivision ) {
		_fSignalGain = fSignalGain;						
		_fADScale_volts = fARef_volts;						
		_fScaling_voltsPerMinorDivision = fScaling_voltsPerMinorDivision;
		_fTotalScaling = _fSignalGain * _fADScale_volts /** _fScaling_voltsPerMinorDivision*/;
	}
	
	public float convertSampleToXCoordinate(ADSampleFrame b, short nCurSample, float fAvgSampleInterval_ms) {
		return (float) ((b.getStartTimestamp() - _lPlotStartTimestamp_ms) + nCurSample * fAvgSampleInterval_ms);
	}

	// Using the Olimex ECG board, I needed to reverse polarity to get the plot to look right
	public float convertSampleToYCoordinate(short nSampleValue) {
		// ADC sample arrives from the Arduino in the range 0..1023; scale to standard ECG plot grid
		return (((float) - nSampleValue / 1023.0f) * _fTotalScaling) + _fPlotYOrigin_div;
	}
	
	public float convertSampleToZCoordinate(short nSample) {
		return 0.0f;
	}

}
