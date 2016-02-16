package com.example.makerecg;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;

import android.content.UriMatcher;
import android.util.Log;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import android.text.TextUtils;
import android.content.ContentUris;

/**
 * Created by Riley Rainey on 2/9/16.
 * Define an implementation of ContentProvider that stubs out
 * all methods
 */
public class ECGContentProvider extends ContentProvider {

    public static final String PROVIDER_NAME = "com.example.makerecg.datasync.ECGContent";
    static final String URL = "content://" + PROVIDER_NAME + "/sampleframes";
    static final Uri CONTENT_URI = Uri.parse(URL);

    //static final String _ID = "_id";
    //static final String NAME = "name";
    //static final String GRADE = "grade";

    static final String SAMPLES_TABLE = "samples";
    static final String _ID = "_ID";
    static final String COLUMN_SAMPLE_ID = "SAMPLE_ID";
    static final String COLUMN_SAMPLE_DATE = "SAMPLE_DATE";
    static final String COLUMN_SAMPLE_COUNT = "SAMPLE_COUNT";
    static final String COLUMN_SAMPLES = "SAMPLES";
    static final String COLUMN_START_TIME_MS = "START_TIME_MS";
    static final String COLUMN_END_TIME_MS = "END_TIME_MS";
    static final String COLUMN_UPLOADED_TS = "UPLOADED_TS";

    private static HashMap<String, String> SAMPLE_FRAMES_PROJECTION_MAP;

    static final int SAMPLE_FRAMES = 1;
    static final int SAMPLE_FRAME_ID = 2;

    private SQLiteDatabase db;

    static final UriMatcher uriMatcher;
    static{
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(PROVIDER_NAME, "sampleframes", SAMPLE_FRAMES);
        uriMatcher.addURI(PROVIDER_NAME, "sampleframes/*", SAMPLE_FRAME_ID);
    }

    private class DatabaseHelper extends SQLiteOpenHelper {

        private static final String TAG = "DatabaseHelper";
        private static final String DATABASE_NAME = "samples.db";
        
        private static final int DATABASE_VERSION = 2;
        private final Context mHelperContext;
        private SQLiteDatabase mDatabase;

        /**
         * Constructor
         *
         * @param ctx The Context within which to work, used to create the DB
         */
        public DatabaseHelper(Context ctx) {
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
                        _ID + " TEXT PRIMARY KEY" + ", " +
                        COLUMN_SAMPLE_ID + " TEXT" + ", " +
                        COLUMN_SAMPLE_DATE + " TEXT" + "," +
                        COLUMN_SAMPLE_COUNT + " INTEGER" + "," +
                        COLUMN_START_TIME_MS + " INTEGER" + "," +
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

    }

    /*
     * Always return true, indicating that the
     * provider loaded correctly.
     */
    @Override
    public boolean onCreate() {
        Context context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);

        /**
         * Create a write able database which will trigger its
         * creation if it doesn't already exist.
         */
        db = dbHelper.getWritableDatabase();
        return (db == null)? false:true;
    }
    /*
     * Return no type for MIME type
     */
    @Override
    public String getType(Uri uri) {
        return null;
    }
    /*
     * query() always returns no results
     *
     */
    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(SAMPLES_TABLE);

        switch (uriMatcher.match(uri)) {
            case SAMPLE_FRAMES:
                qb.setProjectionMap(SAMPLE_FRAMES_PROJECTION_MAP);
                break;

            case SAMPLE_FRAME_ID:
                qb.appendWhere( _ID + "=" + uri.getPathSegments().get(1));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (sortOrder == null || sortOrder == ""){
            /**
             * By default sort on sample frame ID
             */
            sortOrder = _ID + " ASC";
        }
        Cursor c = qb.query(db,	projection,	selection, selectionArgs, null, null, sortOrder);

        /**
         * register to watch a content URI for changes
         */
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }
    /*
     * insert() always returns null (no URI)
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        /**
         * Add a new student record
         */
        long rowID = db.insert(	SAMPLES_TABLE, "", values);

        /**
         * If record is added successfully
         */

        if (rowID > 0)
        {
            Uri _uri = ContentUris.withAppendedId(CONTENT_URI, rowID);
            getContext().getContentResolver().notifyChange(_uri, null);

            SyncUtils.TriggerRefresh();

            return _uri;
        }
        throw new SQLException("Failed to add a record into " + uri);
    }
    /*
     * delete() always returns "no rows affected" (0)
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;

        switch (uriMatcher.match(uri)){
            case SAMPLE_FRAMES:
                count = db.delete(SAMPLES_TABLE, selection, selectionArgs);
                break;

            case SAMPLE_FRAME_ID:
                String id = uri.getPathSegments().get(1);
                count = db.delete( SAMPLES_TABLE, _ID +  " = " + id +
                        (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
    /*
     * update() always returns "no rows affected" (0)
     */
    public int update(
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs) {
        
        int count = 0;

        switch (uriMatcher.match(uri)){
            case SAMPLE_FRAMES:
                count = db.update(SAMPLES_TABLE, values, selection, selectionArgs);
                break;

            case SAMPLE_FRAME_ID:
                count = db.update(SAMPLES_TABLE, values, _ID + " = ?",
                        new String[]{ uri.getPathSegments().get(1) });
                        //_ID + " = \"" + uri.getPathSegments().get(1) + "\"",
                        //selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri );
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
