package com.example.makerecg;


public class ADSampleFrame {

	long	_lTimestamp_ms;
	long	_lEndTimestamp_ms;
	long	_lSampleCount;
	
	short[] mSamples;
	
	public ADSampleFrame(long lStartTimestamp_ms, long lEndTimestamp_ms, int sampleCount, short[] samples) {
		mSamples = new short[sampleCount];
		System.arraycopy(samples, 0, mSamples, 0, sampleCount);
		_lTimestamp_ms = lStartTimestamp_ms;
		_lEndTimestamp_ms = lEndTimestamp_ms;
		_lSampleCount = sampleCount;
	}

	public long getStartTimestamp() {
		return _lTimestamp_ms;
	}
	
	public long getEndTimestamp() {
		return _lEndTimestamp_ms;
	}
	
	public long getElapsedTime() {
		return _lEndTimestamp_ms - _lTimestamp_ms;
	}

	public long getSampleCount() {
		return _lSampleCount;
	}

	public short[] getSamples() {
		return mSamples;
	}
	
	public String toString() {
		return "ADSampleFrame; start="+getStartTimestamp() + "(ms); end="+getEndTimestamp()+"(ms); "+getSampleCount()+" samples";
	}

}
