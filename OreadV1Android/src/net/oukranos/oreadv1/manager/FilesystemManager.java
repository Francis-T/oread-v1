package net.oukranos.oreadv1.manager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OLog;
import android.os.Environment;

public class FilesystemManager {
	private static final String _rootDir = Environment.getExternalStorageDirectory().toString();
	private static final String _defaultSavePath = _rootDir + "/OreadPrototype";
	
	public static String getDefaultFilePath() {
		return _defaultSavePath;
	}
	
	public static Status saveFileData(String path, String filename, byte data[]) {
		if ((path == null) || (filename == null) || (data == null)) {
			OLog.err("Invalid params");
			return Status.FAILED;
		}
		
		/* Obtain the file directory object from the given path string  */
		File saveDir = new File(path);
		
		/* Create the directory if it doesn't exist */
		if (!saveDir.exists()) {
			saveDir.mkdirs();
		}
		
		/* Attempt to create the new file */
		File saveFile = new File(saveDir, filename);
		try {
			if (!saveFile.createNewFile())
			{
				OLog.err("Error: Failed to create save file!");
				return Status.FAILED;
			}
		} catch (Exception e) {
			OLog.err("Exception occurred: "  + e.getMessage());
			return Status.FAILED;
		}

		/* Write data into the new file */ 
		try {
			FileOutputStream saveFileStream = new FileOutputStream(saveFile);
			saveFileStream.write(data);
			saveFileStream.close();
		} catch (FileNotFoundException e) {
			OLog.err("Exception occurred: "  + e.getMessage());
			return Status.FAILED;
		} catch (IOException e) {
			OLog.err("Exception occurred: "  + e.getMessage());
			return Status.FAILED;
		}
		
		OLog.info("Saved! (see FilePath:" + saveFile.getParent() + 
							 " FileName:" + saveFile.getName() +" )");
		return Status.OK;
	}

	/* Short-hand identifier class for the FilesystemManager */
	public class FSMan extends FilesystemManager {
		
	}
}
