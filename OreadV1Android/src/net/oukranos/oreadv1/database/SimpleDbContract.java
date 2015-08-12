package net.oukranos.oreadv1.database;

import android.provider.BaseColumns;

public final class SimpleDbContract {
    private SimpleDbContract() {
        return;
    }
    
    public static abstract class CachedData implements BaseColumns {
        public static final String TABLE_NAME            = "TB_CACHED_DATA";

        public static final String COL_ID                = "_ID";
        public static final String COL_TIMESTAMP         = "C_TIMESTAMP";
        public static final String COL_STATUS            = "C_STATUS";
        public static final String COL_DATA              = "C_DATA";
        public static final String COL_TYPE              = "C_TYPE";
        public static final String COL_SUBTYPE           = "C_SUBTYPE";

        public static final String COL_ID_CONSTR         = "INTEGER PRIMARY KEY";
        public static final String COL_TIMESTAMP_CONSTR  = "TEXT NOT NULL";
        public static final String COL_STATUS_CONSTR     = "TEXT NOT NULL";
        public static final String COL_DATA_CONSTR       = "TEXT NOT NULL";
        public static final String COL_TYPE_CONSTR       = "TEXT NOT NULL";
        public static final String COL_SUBTYPE_CONSTR    = "TEXT";
    }
    
    public static abstract class PersistentData implements BaseColumns {
        public static final String TABLE_NAME            = "TB_PERSISTENT_DATA";

        public static final String COL_ID                = "_ID";
        public static final String COL_NAME              = "C_NAME";
        public static final String COL_TYPE              = "C_TYPE";
        public static final String COL_VALUE             = "C_VALUE";

        public static final String COL_ID_CONSTR         = "INTEGER PRIMARY KEY";
        public static final String COL_NAME_CONSTR       = "TEXT NOT NULL";
        public static final String COL_TYPE_CONSTR       = "TEXT NOT NULL";
        public static final String COL_VALUE_CONSTR      = "TEXT";
    }
}

