package net.oukranos.oreadv1.types;

import net.oukranos.oreadv1.interfaces.HttpEncodableData;

public class SendableData {
	private String url = "";
	private String method = "";
	private HttpEncodableData data = null;

	public SendableData(String url, String method, HttpEncodableData data) {
		this.url = url;
		this.data = data;
		this.method = method;
	}
	
	public String getMethod() {
		return this.method;
	}

	public String getUrl() {
		return this.url;
	}

	public HttpEncodableData getData() {
		return this.data;
	}
}