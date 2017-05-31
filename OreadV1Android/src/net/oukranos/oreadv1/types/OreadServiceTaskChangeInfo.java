package net.oukranos.oreadv1.types;

import android.os.Parcel;
import android.os.Parcelable;

public class OreadServiceTaskChangeInfo extends TaskChangeInfo implements Parcelable {
	public OreadServiceTaskChangeInfo(String task) {
		super(task);
	}
	
	public OreadServiceTaskChangeInfo(TaskChangeInfo taskInfo) {
		super(taskInfo);
	}
	
	public OreadServiceTaskChangeInfo(Parcel p) {
		super(p.readString());
		return;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeString(this.getTask());
		return;
	}

    public static final Parcelable.Creator<OreadServiceTaskChangeInfo> CREATOR
            = new Parcelable.Creator<OreadServiceTaskChangeInfo>() {
        public OreadServiceTaskChangeInfo createFromParcel(Parcel in) {
            return new OreadServiceTaskChangeInfo(in);
        }

        public OreadServiceTaskChangeInfo[] newArray(int size) {
            return new OreadServiceTaskChangeInfo[size];
        }
    };

}
