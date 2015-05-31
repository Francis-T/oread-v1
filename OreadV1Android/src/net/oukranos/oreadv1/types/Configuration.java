package net.oukranos.oreadv1.types;

import java.util.List;
import java.util.ArrayList;

import net.oukranos.oreadv1.util.OLog;

public class Configuration {
    private String _id = "";
    private List<Module> _moduleList = null;
    private List<Procedure> _procedureList = null;
    private List<Data> _dataList = null;

    public Configuration(String id) {
        this._id = id;
        _moduleList = new ArrayList<Module>();
        _procedureList = new ArrayList<Procedure>();
        _dataList = new ArrayList<Data>();
        return;
    }
    
    public String getId() {
        return (this._id);
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

    public Status addModule(String id, String type) {
    	if ((id == null) || (type == null)) {
    		return Status.FAILED;
    	}

    	if ((id.isEmpty() == true) || (type.isEmpty() == true)) {
    		return Status.FAILED;
    	}

        /* Check if our module list already contains such a module */
        if (_moduleList == null) {
            return Status.FAILED;
        }
        for (Module m : _moduleList) {
            if (m.getId().equals(id) == true) {
                return Status.FAILED;
            }
        }

        _moduleList.add(new Module(id, type));

    	return Status.OK;
    }

    public Status addProcedure(String id) {
    	if (id == null) {
    		return Status.FAILED;
    	}

    	if (id.isEmpty() == true) {
    		return Status.FAILED;
    	}

        /* Check if our procedure list already contains such a procedure */
        if (_procedureList == null) {
            return Status.FAILED;
        }
        for (Procedure p : _procedureList) {
            if (p.getId().equals(id) == true) {
                return Status.FAILED;
            }
        }

        _procedureList.add(new Procedure(id));

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
        if (_dataList == null) {
            return Status.FAILED;
        }
        for (Data d : _dataList) {
            if (d.getId().equals(id) == true) {
                return Status.FAILED;
            }
        }

        _dataList.add(new Data(id, type, value));

        OLog.info("Added Data: " + id + ", " + type + ", " + value);
    	return Status.OK;
    }

    public Module getModule(String searchId) {
    	if (_moduleList == null) {
    		return null;
    	}
    	
    	if (_moduleList.isEmpty() == true) {
    		return null;
    	}
    	
        for (Module m : _moduleList) {
        	if (m.getId().equals(searchId) == true) {
        		return m;
        	}
        }
        return null;
    }

    public Procedure getProcedure(String searchId) {
    	if (_procedureList == null) {
    		return null;
    	}
    	
    	if (_procedureList.isEmpty() == true) {
    		return null;
    	}
    	
        for (Procedure p : _procedureList) {
        	if (p.getId().equals(searchId) == true) {
        		return p;
        	}
        }
        return null;
    }

    public Data getData(String searchId) {
    	if (_dataList == null) {
    		return null;
    	}
    	
    	if (_dataList.isEmpty() == true) {
    		return null;
    	}
    	
        for (Data p : _dataList) {
        	if (p.getId().equals(searchId) == true) {
        		return p;
        	}
        }
        return null;
    }
    
    public List<Data> getDataList() {
    	return this._dataList;
    }
    
    public String toString() {
    	String configStr = "configuration {\n";
    	for (Module m : this._moduleList) {
    		configStr += m.toString();
    		configStr += "\n";
    	}
    	
    	for (Procedure p : this._procedureList) {
    		configStr += p.toString();
    		configStr += "\n";
    	}

    	for (Data d : this._dataList) {
    		configStr += d.toString();
    		configStr += "\n";
    	}
    	
    	configStr += "}\n";
    	
    	return (configStr);
    }
}
