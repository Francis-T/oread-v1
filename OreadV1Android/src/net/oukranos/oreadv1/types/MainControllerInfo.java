package net.oukranos.oreadv1.types;

import java.util.ArrayList;
import java.util.List;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.bridge.IFeatureBridge;
import net.oukranos.oreadv1.types.config.Configuration;
import net.oukranos.oreadv1.types.config.Data;
import net.oukranos.oreadv1.util.OreadLogger;

public class MainControllerInfo {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private List<AbstractController> _subcontrollers = null;
	private List<IFeatureBridge> _features = null;
	private Configuration _config = null;
	private DataStore _dataStore = null;
	private Object _context = null;

	public MainControllerInfo() {
		_subcontrollers = new ArrayList<AbstractController>();
		_features = new ArrayList<IFeatureBridge>();
		
		return;
	}
	
	public MainControllerInfo(Configuration config, DataStore dataStore) {
		this();
		
		_config = config;
		_dataStore = dataStore;
		
		/* Assimilate configurable parameter data from the
		 *  into the DataStore */
		if (assimilateConfigData(_config) != Status.OK) {
			OLog.warn("Failed to assimilate config data" +
							" in MainControllerInfo()");
		}
		
		return;
	}
	
	public void setContext(Object context) {
		this._context = context;
		return;
	}
	
	public Object getContext() {
		return this._context;
	}
	
	public void setDataStore(DataStore dataStore) {
		this._dataStore = dataStore;

		/* Assimilate configurable parameter data from the
		 *  into the DataStore */
		if (assimilateConfigData(_config) != Status.OK) {
			OLog.warn("Failed to assimilate config data" +
					" in MainControllerInfo.setDataStore()");
		}
		
		return;
	}

	public DataStore getDataStore() {
		return this._dataStore;
	}
	
	public void setConfig(Configuration config) {
		_config = config;

		/* Assimilate configurable parameter data from the
		 *  into the DataStore */
		if (assimilateConfigData(_config) != Status.OK) {
			OLog.warn("Failed to assimilate config data" +
					" in MainControllerInfo.setConfig()");
		}
		
		return;
	}

	public Configuration getConfig() {
		return this._config;
	}
	
	public Status addSubController(AbstractController controller) {
		if (controller == null) {
			OLog.err("Invalid input parameter" +
					" in MainControllerInfo.addSubController()");
			return Status.FAILED;
		}
		
		if (_subcontrollers == null) {
			OLog.err("List uninitialized or unavailable" +
					" in MainControllerInfo.addSubController()");
			return Status.FAILED;
		}
		
		for (AbstractController subc : _subcontrollers) {
			String subcName = subc.getName();
			String subcType = subc.getType();
			
			if (subcName.equals(controller.getName()) && 
				subcType.equals(controller.getType()) ) {
				OLog.warn("Duplicate: " + controller.toString());
				return Status.OK;
			}
			
		}
		
		_subcontrollers.add(controller);
		
		OLog.info("Added subcontroller: " + controller.toString());
		return Status.OK;
	}
	
	public Status removeSubController(String name, String type) {
		if ((name == null) || (type == null)) {
			OLog.err("Invalid input parameter/s" +
					" in MainControllerInfo.removeSubController()");
			return Status.FAILED;
		}
		
		if (name.isEmpty() || type.isEmpty()) {
			OLog.err("Blank input parameter/s" +
					" in MainControllerInfo.removeSubController()");
			return Status.FAILED;
		}
		
		if (_subcontrollers == null) {
			OLog.err("List uninitialized or unavailable" +
					" in MainControllerInfo.removeSubController()");
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
		OLog.warn("Failed to locate subcontroller: " + type + "." + name );
		return Status.FAILED;
		
	}
	
	public Status removeAllSubControllers() {
		if (_subcontrollers == null) {
			OLog.err("List uninitialized or unavailable" +
					" in MainControllerInfo.removeAllSubControllers()");
			return Status.FAILED;
		}
		
		_subcontrollers.clear();
		
		return Status.FAILED;
		
	}
	
	public AbstractController getSubController(String name, String type) {
		if ((name == null) || (type == null)) {
			OLog.err("Invalid input parameter/s" +
					" in MainControllerInfo.getSubController()");
			return null;
		}
		
		if (name.isEmpty() || type.isEmpty()) {
			OLog.err("Blank input parameter/s" +
					" in MainControllerInfo.getSubController()");
			return null;
		}
		
		if (_subcontrollers == null) {
			OLog.err("List uninitialized or unavailable" +
					" in MainControllerInfo.getSubController()");
			return null;
		}
		
		/* Return the matching subcontroller */
		for (AbstractController c : _subcontrollers) {
			if ( name.equals(c.getName()) && type.equals(c.getType())) {
				return c;
			}
		}

		/* If no such subcontroller exists, return a failure status */
		OLog.warn("Failed to locate subcontroller: " + type + "." + name );
		return null;
		
	}
	
	public AbstractController getSubController(String commandString) {
		if (commandString == null) {
			return null;
		}

		/* Break apart the commandString */
		String cmdStrArr[] = commandString.split("\\.");
		if (cmdStrArr.length < 2) {
			OLog.info("Invalid length: " + cmdStrArr.length);
			return null;
		}
		
		if ((cmdStrArr[0] == null) || (cmdStrArr[1] == null))  {
			return null;
		}

		String type = cmdStrArr[0];
		String name = cmdStrArr[1];
		
		if ((name == null) || (type == null)) {
			OLog.err("Invalid input parameter/s" +
					" in MainControllerInfo.getSubController()");
			return null;
		}
		
		if (name.isEmpty() || type.isEmpty()) {
			OLog.err("Blank input parameter/s" +
					" in MainControllerInfo.getSubController()");
			return null;
		}
		
		if (_subcontrollers == null) {
			OLog.err("List uninitialized or unavailable" +
					" in MainControllerInfo.getSubController()");
			return null;
		}
		
		/* Return the matching subcontroller */
		for (AbstractController c : _subcontrollers) {
			if ( name.equals(c.getName()) && type.equals(c.getType())) {
				return c;
			}
		}

		/* If no such subcontroller exists, return a failure status */
		OLog.warn("Failed to locate subcontroller: " + type + "." + name );
		return null;
		
	}
	
	public List<AbstractController> getSubcontrollerList() {
		return this._subcontrollers;
	}
	
	public Status addFeature(IFeatureBridge feature) {
		if (feature == null) {
			OLog.err("Invalid input parameter/s" +
					" in MainControllerInfo.addFeature()");
			return Status.FAILED;
		}
		
		/* Check that the feature id is not blank */
		if (feature.getId().equals("")) {
			OLog.err("Invalid feature id");
			return Status.FAILED;
		}

		if (_features.size() == 0) {
			_features.add(feature);
			return Status.OK;
		}
		
		/* Check if a feature with a similar id already exists */
		String newFeatureId = feature.getId();
		for (IFeatureBridge f : _features) {
			String storedFeatureId = f.getId();
			if (storedFeatureId.equals(newFeatureId)) {
				OLog.warn("Feature already exists");
				return Status.OK;
			}
		}
		
		/* Add the feature */
		_features.add(feature);
		return Status.OK;
	}
	
	public Status removeFeature(String featureId) {
		if (featureId == null) {
			OLog.err("Invalid input parameter/s" +
					" in MainControllerInfo.removeFeature()");
			return Status.FAILED;
		}
		
		/* Check that the feature id is not blank */
		if (featureId.equals("")) {
			OLog.err("Invalid feature id");
			return Status.FAILED;
		}

		if (_features.size() == 0) {
			OLog.warn("Feature list is empty");
			return Status.OK;
		}

		/* Check if a feature with a similar id exists and remove it*/
		for (IFeatureBridge f : _features) {
			String storedFeatureId = f.getId();
			if (storedFeatureId.equals(featureId)) {
				_features.remove(f);
				return Status.OK;
			}
		}

		OLog.warn("Feature not found");
		return Status.FAILED;
	}

	
	public IFeatureBridge getFeature(String featureId) {
		if (featureId == null) {
			OLog.err("Invalid input parameter/s" +
					" in MainControllerInfo.getFeature()");
			return null;
		}
		
		/* Check that the feature id is not blank */
		if (featureId.equals("")) {
			OLog.err("Invalid feature id");
			return null;
		}

		if (_features.size() == 0) {
			OLog.err("Feature list is empty");
			return null;
		}

		/* Check if a feature with a similar id exists and return it*/
		for (IFeatureBridge f : _features) {
			String storedFeatureId = f.getId();
			if (storedFeatureId.equals(featureId)) {
				return f;
			}
		}

		OLog.warn("Feature not found");
		return null;
	}

	/*********************/
	/** Private Methods **/
	/*********************/
	private Status assimilateConfigData(Configuration config) {
		if (config == null) {
			OLog.err("Invalid input parameter/s" +
					" in MainControllerInfo.assimilateConfigData()");
			return Status.FAILED;
		}
		
		if (_dataStore == null) {
			OLog.err("DataStore unavailable");
			return Status.FAILED;
		}
		
		/* Store all Config data objects in the DataStore */
		List<Data> dataList = _config.getDataList();
		for (Data d : dataList) {
			_dataStore.add(d.getId(), d.getType(), d.getValue());
		}
		
		return Status.OK;
	}
}
