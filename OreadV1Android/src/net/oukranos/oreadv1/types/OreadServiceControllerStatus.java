package net.oukranos.oreadv1.types;

import android.os.Parcel;
import android.os.Parcelable;

public class OreadServiceControllerStatus extends ControllerStatus implements Parcelable {
	public OreadServiceControllerStatus(ControllerStatus status) {
		super();
		
		this.setName(status.getName());
		this.setType(status.getType());
		this.setState(status.getState());
		this.setLastCmdStatus(status.getLastCmdStatus());
		this.setLastCmdInfo(status.getLastCmdInfo());
		
		return;
	}
	
	public OreadServiceControllerStatus(Parcel p) {
		super();
		
		this.setName(p.readString());
		this.setType(p.readString());
		this.setStateFromString(p.readString());
		this.setLastCmdStatusFromString(p.readString());
		this.setLastCmdInfo(p.readString());
		
		return;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeString(this.getName());
		out.writeString(this.getType());
		out.writeString(this.getStateString());
		out.writeString(this.getLastCmdStatusString());
		out.writeString(this.getLastCmdInfo());
		
		return;
	}
	
	/***************************/
	/** Public Helper Methods **/
	/***************************/
    public String getLastCmdStatusString() {
    	return (this.getLastCmdStatus().toString());
    }

	public void setLastCmdStatusFromString(String s) {
		if (s.equals("OK")) {
			this.setLastCmdStatus(Status.OK);
		} else if (s.equals("ALREADY_STARTED")) {
			this.setLastCmdStatus(Status.ALREADY_STARTED);
		} else if (s.equals("FAILED")) {
			this.setLastCmdStatus(Status.FAILED);
		} else {
			this.setLastCmdStatus(Status.UNKNOWN);
		} 
		
		return;
	}
    
    public String getStateString() {
    	return (this.getState().toString());
    }
	
	public void setStateFromString(String s) {
		if (s.equals("INACTIVE")) {
			this.setState(ControllerState.INACTIVE);
		} else if (s.equals("READY")) {
			this.setState(ControllerState.READY);
		} else if (s.equals("ACTIVE")) {
			this.setState(ControllerState.ACTIVE);
		} else if (s.equals("BUSY")) {
			this.setState(ControllerState.BUSY);
		} else {
			this.setState(ControllerState.UNKNOWN);
		} 
		
		return;
	}

	/*********************/
	/** Creator Methods **/
	/*********************/
    public static final Parcelable.Creator<OreadServiceControllerStatus> CREATOR
            = new Parcelable.Creator<OreadServiceControllerStatus>() {
        public OreadServiceControllerStatus createFromParcel(Parcel in) {
            return new OreadServiceControllerStatus(in);
        }

        public OreadServiceControllerStatus[] newArray(int size) {
            return new OreadServiceControllerStatus[size];
        }
    };

}
