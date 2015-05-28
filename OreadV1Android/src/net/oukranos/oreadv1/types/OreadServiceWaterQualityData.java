package net.oukranos.oreadv1.types;

import android.os.Parcel;
import android.os.Parcelable;

public class OreadServiceWaterQualityData extends WaterQualityData implements Parcelable {
//	protected int id = 0;
//	protected long timestamp = 0;
//	
//	public double pH = 0.0;
//	public double dissolved_oxygen = 0.0;
//	public double conductivity = 0.0;
//	public double temperature = 0.0;
//	public double tds = 0.0;
//	public double salinity = 0.0;
	
	public OreadServiceWaterQualityData(WaterQualityData data) {
		super(data);
	}
	
	public OreadServiceWaterQualityData(Parcel p) {
		super(p.readInt());
		this.timestamp = p.readLong();
		this.pH = p.readDouble();
		this.dissolved_oxygen = p.readDouble();
		this.conductivity = p.readDouble();
		this.temperature = p.readDouble();
		this.tds = p.readDouble();
		this.salinity = p.readDouble();
		this.turbidity = p.readDouble();
		return;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(id);
		out.writeLong(timestamp);
		out.writeDouble(pH);
		out.writeDouble(dissolved_oxygen);
		out.writeDouble(conductivity);
		out.writeDouble(temperature);
		out.writeDouble(tds);
		out.writeDouble(salinity);
	}
	

    public static final Parcelable.Creator<OreadServiceWaterQualityData> CREATOR
            = new Parcelable.Creator<OreadServiceWaterQualityData>() {
        public OreadServiceWaterQualityData createFromParcel(Parcel in) {
            return new OreadServiceWaterQualityData(in);
        }

        public OreadServiceWaterQualityData[] newArray(int size) {
            return new OreadServiceWaterQualityData[size];
        }
    };

}
