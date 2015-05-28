package net.oukranos.oreadv1.types;

import java.util.ArrayList;
import java.util.List;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.util.OLog;

public class MainControllerInfo {
	private List<AbstractController> _subcontrollers = null;
	private Configuration _config = null;
	private DataStore _dataStore = null;
	private Object _context = null;

	public MainControllerInfo(Configuration config, DataStore dataStore) {
		this._subcontrollers = new ArrayList<AbstractController>();
		this._config = config;
		this._dataStore = dataStore;
		return;
	}
	
	public void setContext(Object context) {
		this._context = context;
		return;
	}
	
	public Object getContext() {
		return this._context;
	}

	public DataStore getDataStore() {
		return this._dataStore;
	}

	public Configuration getConfig() {
		return this._config;
	}
	
	public Status addSubController(AbstractController controller) {
		if (controller == null) {
			OLog.err("Invalid input parameter");
			return Status.FAILED;
		}
		
		if (_subcontrollers == null) {
			OLog.err("List uninitialized or unavailable");
			return Status.FAILED;
		}
		
		_subcontrollers.add(controller);
		
		return Status.OK;
	}
	
	public Status removeSubController(String name, String type) {
		if ((name == null) || (type == null)) {
			OLog.err("Invalid input parameter/s");
			return Status.FAILED;
		}
		
		if (name.isEmpty() || type.isEmpty()) {
			OLog.err("Invalid input parameter/s");
			return Status.FAILED;
		}
		
		if (_subcontrollers == null) {
			OLog.err("List uninitialized or unavailable");
			return Status.FAILED;
		}
		
		/* Remove the matching subcontroller */
		for (int idx = 0; idx < _subcontrollers.size(); idx++) {
			AbstractController c = _subcontrollers.get(idx);  
			if ( name.equals(c.getName()) && type.equals(c.getType())) {
				_subcontrollers.remove(idx);
				return Status.OK;
			}
		}

		/* If no such subcontroller exists, return a failure status */
		OLog.err("Failed to locate subcontroller: " + type + "." + name );
		return Status.FAILED;
		
	}
	
	public AbstractController getSubController(String name, String type) {
		if ((name == null) || (type == null)) {
			OLog.err("Invalid input parameter/s");
			return null;
		}
		
		if (name.isEmpty() || type.isEmpty()) {
			OLog.err("Invalid input parameter/s");
			return null;
		}
		
		if (_subcontrollers == null) {
			OLog.err("List uninitialized or unavailable");
			return null;
		}
		
		/* Return the matching subcontroller */
		for (AbstractController c : _subcontrollers) {
			if ( name.equals(c.getName()) && type.equals(c.getType())) {
				return c;
			}
		}

		/* If no such subcontroller exists, return a failure status */
		OLog.err("Failed to locate subcontroller: " + type + "." + name );
		return null;
		
	}
}
