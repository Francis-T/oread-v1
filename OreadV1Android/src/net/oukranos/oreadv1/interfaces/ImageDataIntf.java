package net.oukranos.oreadv1.interfaces;

public abstract class ImageDataIntf {
	private String captureFilePath = "";
	private String captureFileName = "";

	public String getCaptureFileName() {
		return this.captureFileName;
	}

	public String getCaptureFilePath() {
		return this.captureFilePath;
	}
	
	public void setCaptureFile(String fileName, String path) {
		if ( fileName != null ) {
			this.captureFileName = fileName;
		}
		
		if ( path != null ) {
			this.captureFilePath = path;	
		}
		
		return;
	}
}
