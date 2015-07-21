package net.oukranos.oreadv1.types;

import java.io.File;

import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.util.OreadLogger;

import org.apache.http.HttpEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;

public class HttpEncChemicalPresenceData extends ChemicalPresenceData implements HttpEncodableData {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();

	public HttpEncChemicalPresenceData(int id) {
		super(id);
	}
	
	public HttpEncChemicalPresenceData(ChemicalPresenceData data) {
		super(data);
	}
	
	@Override
	public HttpEntity encodeDataToHttpEntity() {
		String jsonStr = "";
		MultipartEntity multipartContent = null;
		
		jsonStr = "{\"origin\":1, \"message\":\"test\", \"recordStatus\":\"ok\", \"dateRecorded\":" + System.currentTimeMillis() + "}\r\n";
		try {
			multipartContent = new MultipartEntity();
	        multipartContent.addPart("message", new StringBody(new String(jsonStr)));
	        FileBody isb = new FileBody(new File(this.getCaptureFilePath() + "/" + this.getCaptureFileName()));                                                        
	        multipartContent.addPart("picture", isb);
		} catch (Exception e) {
			OLog.err("Generate HttpEntity failed");
			return null;
		}
        
		return (HttpEntity)(multipartContent);
	}

}
