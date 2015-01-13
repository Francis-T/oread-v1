package net.oukranos.oreadv1.types;

import java.io.File;

import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.util.OLog;

import org.apache.http.HttpEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;

public class HttpEncChemicalPresenceData extends ChemicalPresenceData implements HttpEncodableData {

	public HttpEncChemicalPresenceData(int id) {
		super(id);
	}
	
	public HttpEncChemicalPresenceData(ChemicalPresenceData data) {
		super(data);
	}
	
	@Override
	public HttpEntity encodeDataToHttpEntity() {
		MultipartEntity multipartContent = null;
		try {
			multipartContent = new MultipartEntity();
	        multipartContent.addPart("message", new StringBody("test"));
	        FileBody isb = new FileBody(new File(this.getCaptureFilePath() + "/" + this.getCaptureFileName()));                                                        
	        multipartContent.addPart("picture", isb);
		} catch (Exception e) {
			OLog.err("Generate HttpEntity failed");
			return null;
		}
        
		return (HttpEntity)(multipartContent);
	}

}
