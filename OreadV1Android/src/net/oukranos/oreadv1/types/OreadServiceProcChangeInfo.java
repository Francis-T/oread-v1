package net.oukranos.oreadv1.types;

import android.os.Parcel;
import android.os.Parcelable;

public class OreadServiceProcChangeInfo extends ProcChangeInfo implements Parcelable {
	public OreadServiceProcChangeInfo(String proc) {
		super(proc);
	}

	public OreadServiceProcChangeInfo(ProcChangeInfo procStateInfo) {
		super(procStateInfo);
	}
	
	public OreadServiceProcChangeInfo(Parcel p) {
		super(p.readString());
		return;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeString(this.getProcedure());
		return;
	}

    public static final Parcelable.Creator<OreadServiceProcChangeInfo> CREATOR
            = new Parcelable.Creator<OreadServiceProcChangeInfo>() {
        public OreadServiceProcChangeInfo createFromParcel(Parcel in) {
            return new OreadServiceProcChangeInfo(in);
        }

        public OreadServiceProcChangeInfo[] newArray(int size) {
            return new OreadServiceProcChangeInfo[size];
        }
    };
}
