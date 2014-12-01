package net.oukranos.oreadv1.types;

public class WaterQualityData {
	private int id = 0;
	private long timestamp = 0;
	
	public double pH = 0.0;
	public double dissolved_oxygen = 0.0;
	public double conductivity = 0.0;
	public double temperature = 0.0;
	public double tds = 0.0;
	public double salinity = 0.0;
	
	public WaterQualityData(int id) {
		this.id = id;
		this.pH = 0.0;
		this.dissolved_oxygen = 0.0;
		this.conductivity = 0.0;
		this.temperature = 0.0;
		this.tds = 0.0;
		this.salinity = 0.0;
		this.timestamp = 0;
		
		return;
	}
	
	public int getId() {
		return this.id;
	}
	
	public long getTimestamp() {
		return this.timestamp;
	}
	
	public void updateTimestamp() {
		this.timestamp = System.currentTimeMillis();
		return;
	}
}
