package com.example.makerecg;


import java.util.UUID;

public class ADSampleFrame {

    UUID    _datasetUuid;
	String	_datasetDate; // in ISO 8601 format 'YYYY-MM-DD'
	long	_lTimestamp_ms;
	long	_lEndTimestamp_ms;
	long	_lSampleCount;

	/*
         * Dirty sample blocks have not yet been written to the sqlite database.
         */
	boolean _dirty;
	
	short[] mSamples;

    public UUID getDatasetUuid() {
        return _datasetUuid;
    }

    public void setDatasetUuid(UUID _datasetUuid) {
        this._datasetUuid = _datasetUuid;
    }
	
	public ADSampleFrame(UUID uuid, long lStartTimestamp_ms, long lEndTimestamp_ms, int sampleCount, short[] samples) {
        _datasetUuid = uuid;
		mSamples = new short[sampleCount];
		System.arraycopy(samples, 0, mSamples, 0, sampleCount);
		_lTimestamp_ms = lStartTimestamp_ms;
		_lEndTimestamp_ms = lEndTimestamp_ms;
		_lSampleCount = sampleCount;
        _dirty = true;
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

	public boolean isDirty() {
		return _dirty;
	}

	public void setDirty(boolean _dirty) {
		this._dirty = _dirty;
	}

	public String getDatasetDate() {
		return _datasetDate;
	}

	public void setDatasetDate(String _datasetDate) {
		this._datasetDate = _datasetDate;
	}

}
