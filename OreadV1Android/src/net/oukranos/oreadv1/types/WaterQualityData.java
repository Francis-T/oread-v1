package net.oukranos.oreadv1.types;

public class WaterQualityData {
	protected int id = 0;
	protected long timestamp = 0;
	
	public double pH = 0.0;
	public double dissolved_oxygen = 0.0;
	public double conductivity = 0.0;
	public double temperature = 0.0;
	public double tds = 0.0;
	public double salinity = 0.0;
	public double turbidity = 0.0;
	
	public WaterQualityData(int id) {
		this.id = id;
		this.pH = 0.0;
		this.dissolved_oxygen = 0.0;
		this.conductivity = 0.0;
		this.temperature = 0.0;
		this.tds = 0.0;
		this.salinity = 0.0;
		this.timestamp = 0;
		this.turbidity = 0.0;
		
		return;
	}
	
	public WaterQualityData(WaterQualityData data) {
		if (data == null) {
			throw new NullPointerException();
		}

		this.id 				= data.id;
		this.pH 				= data.pH;
		this.dissolved_oxygen 	= data.dissolved_oxygen;
		this.conductivity 		= data.conductivity;
		this.temperature 		= data.temperature;
		this.tds 				= data.tds;
		this.salinity 			= data.salinity;
		this.turbidity			= data.turbidity;
		this.timestamp			= data.getTimestamp();
		
		return;
	}
	
	public int getId() {
		return this.id;
	}
	
	public long getTimestamp() {
		return this.timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
		return;
	}
	
	public void updateTimestamp() {
		this.timestamp = System.currentTimeMillis();
		return;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id = ");
		sb.append(this.id);
		sb.append(", ");
		sb.append("dateRecorded = ");
		sb.append(this.getTimestamp());
		sb.append(", ");
		sb.append("pH = ");
		sb.append(this.pH);
		sb.append(", ");
		sb.append("do2 = ");
		sb.append(this.dissolved_oxygen);
		sb.append(", ");
		sb.append("ec = ");
		sb.append(this.conductivity);
		sb.append(", ");
		sb.append("temp = ");
		sb.append(this.temperature);
		sb.append(", ");
		sb.append("turb = ");
		sb.append(this.turbidity);
		return (sb.toString());
	}
}
