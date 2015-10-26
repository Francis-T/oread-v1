package net.oukranos.oreadv1.database;

import net.oukranos.oreadv1.database.SimpleDbContract.CachedData;
import net.oukranos.oreadv1.database.SimpleDbContract.PersistentData;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SimpleDbHelper extends SQLiteOpenHelper {
	private static final String DB_NAME = "OreadData.db";
	private static final int    DB_VER  = 1;
	private static final String DB_SPACE   = " ";
	private static final String DB_SEP  = ", ";
	
	private static final String DB_CREATE_CACHED_DATA_TABLES =
			"CREATE TABLE " + CachedData.TABLE_NAME + " ("
							+ CachedData.COL_ID + DB_SPACE 
								+ CachedData.COL_ID_CONSTR + DB_SEP
							+ CachedData.COL_TIMESTAMP + DB_SPACE 
								+ CachedData.COL_TIMESTAMP_CONSTR + DB_SEP
							+ CachedData.COL_STATUS + DB_SPACE 
								+ CachedData.COL_STATUS_CONSTR + DB_SEP
							+ CachedData.COL_DATA + DB_SPACE 
								+ CachedData.COL_DATA_CONSTR + DB_SEP
							+ CachedData.COL_TYPE + DB_SPACE 
								+ CachedData.COL_TYPE_CONSTR + DB_SEP
							+ CachedData.COL_SUBTYPE + DB_SPACE 
								+ CachedData.COL_SUBTYPE_CONSTR
							+ ")";

	private static final String DB_CREATE_PERSISTENT_DATA_TABLES =
			"CREATE TABLE " + PersistentData.TABLE_NAME + " ("
							+ PersistentData.COL_ID + DB_SPACE 
								+ PersistentData.COL_ID_CONSTR + DB_SEP
							+ PersistentData.COL_NAME + DB_SPACE 
								+ PersistentData.COL_NAME_CONSTR + DB_SEP
							+ PersistentData.COL_TYPE + DB_SPACE 
								+ PersistentData.COL_TYPE_CONSTR + DB_SEP
							+ PersistentData.COL_VALUE + DB_SPACE 
								+ PersistentData.COL_VALUE_CONSTR
							+ ")";
	
	private static final String DB_DELETE_CACHED_DATA_TABLES = 
			"DROP TABLE IF EXISTS " + CachedData.TABLE_NAME;

	private static final String DB_DELETE_PERSISTENT_DATA_TABLES =
		    "DROP TABLE IF EXISTS " + PersistentData.TABLE_NAME; 

	public SimpleDbHelper(Object initObject) {
		super((Context) initObject, DB_NAME, null, DB_VER);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(DB_CREATE_CACHED_DATA_TABLES);
		db.execSQL(DB_CREATE_PERSISTENT_DATA_TABLES);
		return;
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL(DB_DELETE_CACHED_DATA_TABLES);
		db.execSQL(DB_DELETE_PERSISTENT_DATA_TABLES);
		
		onCreate(db);
		
		return;
	}
	
	
}
