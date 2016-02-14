package com.example.makerecg;

import android.content.ContentValues;
import android.content.Context;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.apache.http.util.ByteArrayBuffer;

import java.io.IOException;
import java.sql.Blob;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;


/**
 * Created by Riley Rainey on 1/31/16.
 */
public class ADSampleDatabase extends SQLiteOpenHelper {

    private static final String TAG = "ADSampleDatabase";
    private static final String DATABASE_NAME = "samples.db";
    private static final String SAMPLES_TABLE = "samples";
    private static final String COLUMN_SAMPLE_ID = "SAMPLE_ID";
    private static final String COLUMN_SAMPLE_DATE = "SAMPLE_DATE";
    private static final String COLUMN_SAMPLE_COUNT = "SAMPLE_COUNT";
    private static final String COLUMN_SAMPLES = "SAMPLES";
    private static final String COLUMN_START_TIME_MS = "START_TIME_MS";
    private static final String COLUMN_END_TIME_MS = "END_TIME_MS";
    private static final String COLUMN_UPLOADED_TS = "UPLOADED_TS";
    private static final int DATABASE_VERSION = 1;
    private final Context mHelperContext;
    private SQLiteDatabase mDatabase;
    private static ADSampleDatabase mInstance = null;

    static ADSampleDatabase getInstance(Context ctx) {
        /**
         * use the application context as suggested by CommonsWare.
         * this will ensure that you dont accidentally leak an Activitys
         * context (see this article for more information:
         * http://android-developers.blogspot.nl/2009/01/avoiding-memory-leaks.html)
         */
        if (mInstance == null) {
            mInstance = new ADSampleDatabase(ctx.getApplicationContext());
        }
        return mInstance;
    }

    /**
     * Constructor
     *
     * @param ctx The Context within which to work, used to create the DB
     */
    public ADSampleDatabase(Context ctx) {
        super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
        mHelperContext = ctx;

    }

    /* Note that FTS3 does not support column constraints and thus, you cannot
     * declare a primary key. However, "rowid" is automatically used as a unique
     * identifier, so when making requests, we will use "_id" as an alias for "rowid"
     */
    private static final String TABLE_CREATE =
            "CREATE TABLE " + SAMPLES_TABLE +
                " (" +
                COLUMN_SAMPLE_ID + " TEXT PRIMARY KEY" + ", " +
                COLUMN_SAMPLE_DATE + " TEXT" + "," +
                COLUMN_SAMPLE_COUNT + " INTEGER" + "," +
                COLUMN_START_TIME_MS + " INTEGER PRIMARY KEY" + "," +
                COLUMN_END_TIME_MS + " INTEGER" + "," +
                COLUMN_SAMPLES + " BLOB" + "," +
                COLUMN_UPLOADED_TS + " TEXT" + ");";

    @Override
    public void onCreate(SQLiteDatabase db) {
        mDatabase = db;
        mDatabase.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + SAMPLES_TABLE);
        onCreate(db);
    }

    public void startDatabaseWriterThread() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        writeSampleBlocks();
                        Thread.sleep(1000);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    // no action
                }
            }
        }).start();
    }

    /*
     * Save all pending sample blocks to the database
     */
    private void writeSampleBlocks() throws IOException {

        ADSampleQueue queue = ADSampleQueue.getSingletonObject();
        Iterator<ADSampleFrame> it = queue.getWriteableSampleBlocks().iterator();
        ADSampleFrame frame;

        while(it.hasNext()) {
            frame = it.next();
            addFrame(frame);
            frame.setDirty(false);
            it.remove();
        }
    }

    /**
     * Add a frame to the database.
     *
     * @return rowId or -1 if failed
     */
    public long addFrame(ADSampleFrame frame) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues initialValues = new ContentValues();
        //ByteArrayBuffer barb= new ByteArrayBuffer((int)frame.getSampleCount()*2);
        short [] samples = frame.getSamples();
        byte [] s = new byte[(int)(frame.getSampleCount()*2)];

        int j = 0;
        for(int i=0; i<frame.getSampleCount(); ++i) {
            //byte x0, x1;
            s[j] = (byte)((samples[i] >> 8) & 0xff);
            s[j+1] = (byte)(samples[i] & 0xff);
            //barb.append(x0);
            //barb.append(x1);
            j+=2;
        }

        initialValues.put(COLUMN_SAMPLE_ID, frame.getDatasetUuid().toString());
        initialValues.put(COLUMN_SAMPLE_DATE, frame.getDatasetDate());
        initialValues.put(COLUMN_SAMPLE_COUNT,frame.getSampleCount());
        initialValues.put(COLUMN_SAMPLES, s);
        initialValues.put(COLUMN_START_TIME_MS, frame.getStartTimestamp());
        initialValues.put(COLUMN_END_TIME_MS, frame.getEndTimestamp());
        initialValues.put(COLUMN_UPLOADED_TS, (String) null);
        return db.insert(SAMPLES_TABLE, null, initialValues);
    }

    /**
     * Upload any records that might be pending.
     *
     */
    public List<ADSampleFrame> uploadPendingFrames(int maxSyncSize) {

        List<ADSampleFrame> result = new LinkedList<ADSampleFrame>();
        int count = 0;
        SQLiteDatabase db = this.getWritableDatabase();

        Cursor cursor = db.query(SAMPLES_TABLE,
                            new String[] { COLUMN_SAMPLE_ID, COLUMN_SAMPLE_DATE,
                                    COLUMN_SAMPLE_COUNT, COLUMN_SAMPLES,
                                    COLUMN_START_TIME_MS, COLUMN_END_TIME_MS },
                            COLUMN_UPLOADED_TS + " IS NULL",
                            null, null, null,
                            COLUMN_SAMPLE_ID + " ASC," + COLUMN_START_TIME_MS + " ASC");

        if (cursor != null && count < maxSyncSize) {
            cursor.moveToFirst();
            do {
                ADSampleFrame frame = new ADSampleFrame(UUID.fromString(cursor.getString(0)),
                        cursor.getString(1),
                        cursor.getLong(4),
                        cursor.getLong(5),
                        cursor.getShort(2),
                        blobToShortArray(cursor.getBlob(3), cursor.getShort(2)));

                // Send it
                result.add(frame);

                count ++;

            } while (cursor.moveToNext());

        }

        cursor.close();

       return result;
    }

    public void markAsUploaded(List<ADSampleFrame> frames) {

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_UPLOADED_TS, Utilities.getISO8601StringForDate(new Date()));

        for (ADSampleFrame frame: frames) {

            db.update(SAMPLES_TABLE,
                    values,
                    COLUMN_SAMPLE_ID + " = ? and " + COLUMN_START_TIME_MS + " = ?",
                    new String[]{ frame.getDatasetUuid().toString(), "" + frame.getStartTimestamp()});
        }
    }

    private short[] blobToShortArray(byte[] bytes, int length) {
        short [] samples = new short[length];
        int j = 0;
        for(int i=0; i<length; ++i) {
            samples[i] = (short) (((bytes[j] << 8) & 0xff00) | (bytes[j+1] & 0xff));
            j+=2;
        }
        return samples;
    }

}
