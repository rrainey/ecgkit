package com.example.makerecg;


import java.util.UUID;
import org.json.JSONObject;

import android.util.Log;

public class ADSampleFrame {

    private static final String TAG = "ADSampleFrame";

    /*
     * UUID of this stream of samples
     */
    UUID    _datasetUuid;

    /*
     * ISO-8601 style date of sample (YYYY-MM-DD)
     */
	String	_datasetDate;

    /*
     * Time offset start of this sample frame (milliseconds, "0" is Arduino application start time)
     */
	long	_lTimestamp_ms;

    /*
     * Time offset of last sample in this frame (milliseconds, "0" is Arduino application start time)
     */
	long	_lEndTimestamp_ms;

    /*
     * Count of samples in this frame
     */
	long	_lSampleCount;

	/*
     * Dirty sample blocks have not yet been written to the sqlite database.
     */
	boolean _dirty;

    /*
     * An array of 12-bit integer A/D samples padded to 16-bits (sample values will be in the range 0..1023)
     */
	short[] _samples;
	
	public ADSampleFrame(UUID uuid,
                         String collectionDate,
                         long lStartTimestamp_ms,
                         long lEndTimestamp_ms,
                         int sampleCount,
                         short[] samples) {

        _datasetUuid = uuid;
        _datasetDate = collectionDate;
		_samples = new short[sampleCount];
		System.arraycopy(samples, 0, _samples, 0, sampleCount);
		_lTimestamp_ms = lStartTimestamp_ms;
		_lEndTimestamp_ms = lEndTimestamp_ms;
		_lSampleCount = sampleCount;
        _dirty = true;
	}

	public String getPrimaryKey() {
		return getDatasetUuid().toString() + "." + getStartTimestamp();
	}

    public UUID getDatasetUuid() {
        return _datasetUuid;
    }

    public void setDatasetUuid(UUID _datasetUuid) {
        this._datasetUuid = _datasetUuid;
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
		return _samples;
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

	public String toJson() {
		String r = "{\n" +
				"  id: \"" + getDatasetUuid().toString() + "." + getStartTimestamp() + "\",\n" +
				"  datasetId: \"" + getDatasetUuid().toString() + "\",\n" +
                "  date: \"" + getDatasetDate() + "\",\n" +
				"  timestamp: " + getStartTimestamp() + ",\n" +
				"  endTimestamp: " + getEndTimestamp() + ",\n" +
				"  sampleCount: " + getSampleCount() + ",\n" +
				"  samples: \"" + Utilities.encodeBase64(getSamples()) + "\"\n" +
				"}\n";

		return r;
	}

    /**
     * Convert the ADSampleFrame object into a JSON string.
     * @return a JSON string representation of the object
     */
    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put("id", getDatasetUuid().toString() + "." + getStartTimestamp());

            if (getDatasetUuid() != null) {
                json.put("datasetId", getDatasetUuid().toString());
            }
            if (!isEmpty(getDatasetDate())) {
                json.put("date", getDatasetDate());
            }

            json.put("timestamp", getStartTimestamp());
            json.put("endTimestamp", getEndTimestamp());
            json.put("sampleCount", getSampleCount());
            json.put("samples", Utilities.encodeBase64(getSamples()));


        } catch (final Exception ex) {
            Log.i(TAG, "Error converting ADSampleFrame to JSONObject" + ex.toString());
        }

        return json;
    }

    public static ADSampleFrame valueOf(JSONObject contact) {
        // TODO: not implemented
        return null;
    }

    private boolean isEmpty(String s) {
        return s == null;
    }

}
