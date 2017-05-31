package net.oukranos.oreadv1.types;

import android.os.Parcel;
import android.os.Parcelable;

public class OreadServiceProcStateChangeInfo extends ProcStateChangeInfo implements Parcelable {
	public OreadServiceProcStateChangeInfo(String procState) {
		super(procState);
	}

	public OreadServiceProcStateChangeInfo(ProcStateChangeInfo procStateInfo) {
		super(procStateInfo);
	}
	
	public OreadServiceProcStateChangeInfo(Parcel p) {
		super(p.readString());
		return;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeString(this.getStateInfo());
		return;
	}

    public static final Parcelable.Creator<OreadServiceProcStateChangeInfo> CREATOR
            = new Parcelable.Creator<OreadServiceProcStateChangeInfo>() {
        public OreadServiceProcStateChangeInfo createFromParcel(Parcel in) {
            return new OreadServiceProcStateChangeInfo(in);
        }

        public OreadServiceProcStateChangeInfo[] newArray(int size) {
            return new OreadServiceProcStateChangeInfo[size];
        }
    };
}
