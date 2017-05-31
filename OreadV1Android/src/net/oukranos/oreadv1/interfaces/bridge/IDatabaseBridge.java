package net.oukranos.oreadv1.interfaces.bridge;

import java.util.HashMap;

import net.oukranos.oreadv1.types.GenericData;
import net.oukranos.oreadv1.types.Status;

public interface IDatabaseBridge extends IFeatureBridge {
	public boolean isDatabaseOpen();
	public boolean isCursorActive();
	public boolean recordsAvailable();
	
	public Status openDatabase();
	public Status closeDatabase();
	public Status startQuery(String table, String[] columns, 
							 String selection, String[] selectionArgs, 
							 String groupBy, String having, String orderBy);
	public Status finishQuery();
	
	public HashMap<String, GenericData> fetchData();
	
	public Status prevRecord();
	public Status nextRecord();
	public int getCursorColumn(String columnName);
	public Object getData(int column);
	public Status insertData(HashMap<String, GenericData> dataMap); /* TODO */
}
