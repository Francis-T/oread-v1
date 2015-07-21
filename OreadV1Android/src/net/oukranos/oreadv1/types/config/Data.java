package net.oukranos.oreadv1.types.config;

import java.util.ArrayList;
import java.util.List;

import net.oukranos.oreadv1.types.Status;

public class Data {
	private String _id = "";
	private String _type = "";
	private String _value = "";
	private List<Data> _compoundDataList = null;
	
	public Data(String id, String type, String value) {
		this._id = id;
		this._type = type;
		this._value = value;
		
		if (this._type.equals("compound") == true) {
			_compoundDataList = new ArrayList<Data>();
		}
		
		return;
	}
	
	public String getId() {
		return (this._id);
	}
	
	public String getType() {
		return (this._type);
	}
	
	public String getValue() {
		return (this._value);
	}
	
	public Status setId(String id) {
		if (id == null) {
			return Status.FAILED;
		}
		
		if (id.isEmpty() == true) {
			return Status.FAILED;
		}
		
		this._id = id;
		
		return Status.OK;
	}

	public Status setType(String type ) {
		if (type == null) {
			return Status.FAILED;
		}
		
		if (type.isEmpty() == true) {
			return Status.FAILED;
		}
		
		/* If this is being changed into a non-compound data, discard the contents oft
		 * the compound data list first */
		if ((this.getType().equals("compound") == true) &&
				(type.equals("compound") == false)) {
			this._compoundDataList.clear();
			this._compoundDataList = null;
		}

		/* If this is being changed into a compound data, init the compound data list first */
		if ((this.getType().equals("compound") == false) &&
				(type.equals("compound") == true)) {
			this._compoundDataList = new ArrayList<Data>();
		}
		
		this._type = type;
		
		return Status.OK;
	}

	public Status setValue(String value) {
		if (value == null) {
			return Status.FAILED;
		}
		
		this._value = value;
		
		return Status.OK;
	}

    public Status addData(String id, String type, String value) {
    	if ((id == null) || (type == null) || (value == null)) {
    		return Status.FAILED;
    	}

    	if ((id.isEmpty() == true) || (type.isEmpty() == true)) {
    		return Status.FAILED;
    	}

        /* Check if our data list already contains a data w/ the same id */
        if (_compoundDataList == null) {
            return Status.FAILED;
        }
        for (Data d : _compoundDataList) {
            if (d.getId().equals(id) == true) {
                return Status.FAILED;
            }
        }

        _compoundDataList.add(new Data(id, type, value));

    	return Status.OK;
    }
	
	public String toString() {
		String dataStr = "{ data";
		
		dataStr += "id=\"" + this.getId() + "\" ";
		dataStr += "type=\"" + this.getType() + "\" ";
		dataStr += "value=\"" + this.getValue() + "\" ";
		
		if (this.getType().equals("compound") == true) {
			dataStr += "data-list=[ ";
			
			for (Data d : this._compoundDataList) {
				dataStr += "\n";
				dataStr += "    " + d.toString();
			}
			
			dataStr += " ]";
		}
		
		dataStr += " }";
		
		return dataStr;
	}
}
