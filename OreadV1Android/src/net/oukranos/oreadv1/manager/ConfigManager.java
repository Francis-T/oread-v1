package net.oukranos.oreadv1.manager;

import org.apache.http.HttpEntity;

import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.interfaces.IPersistentDataBridge;
import net.oukranos.oreadv1.interfaces.bridge.IConnectivityBridge;
import net.oukranos.oreadv1.interfaces.bridge.IDeviceInfoBridge;
import net.oukranos.oreadv1.interfaces.bridge.IInternetBridge;
import net.oukranos.oreadv1.manager.FilesystemManager.FSMan;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.SendableData;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.config.Configuration;
import net.oukranos.oreadv1.types.config.Data;
import net.oukranos.oreadv1.util.ConfigXmlParser;
import net.oukranos.oreadv1.util.OreadLogger;

public class ConfigManager {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private static final String DEFAULT_CFG_FILE_PATH = FSMan.getDefaultFilePath();
	private static final String DEFAULT_CFG_FILE_NAME = "oread_config.xml";
	private static final String DEFAULT_CFG_FILE_TEMP_NAME = "oread_config_temp.xml";
	private static final String DEFAULT_CFG_PREV_FILE_PATH	= DEFAULT_CFG_FILE_PATH + "/" + DEFAULT_CFG_FILE_TEMP_NAME;
	private static final String DEFAULT_CFG_FULL_FILE_PATH 	= DEFAULT_CFG_FILE_PATH + "/" + DEFAULT_CFG_FILE_NAME;
	private static final String DEFAULT_DEVICE_CONFIG_URL_BASE = "http://miningsensors.info/deviceconf";
	private static final String DEFAULT_DEVICE_CONFIG_URL_ID = "TEST_DEVICE";
	private static final long 	DEFAULT_CONFIG_FILE_AGE_LIMIT = (8 * 60 * 60 * 1000); // ~8 hours old
	
	private static ConfigManager _configMgr = null;
	private Configuration _config = null;
	private MainControllerInfo _mainInfo = null;
	
	private IConnectivityBridge _connBridge = null;
	private IInternetBridge _internetBridge = null;
	private IPersistentDataBridge _storedDataBridge = null;
	
	private ConfigManager() {
		return;
	}
	
	public static ConfigManager getInstance() {
		if (_configMgr == null) {
			_configMgr = new ConfigManager();
		}
		return _configMgr; 
	}

	public Status initialize(Object initObject) {
		if (initObject == null) {
			OLog.err("Invalid initializer object in ConfigManager.initialize()");
			return Status.FAILED;
		}

		try {
			_mainInfo = (MainControllerInfo) initObject;
		} catch(Exception e) {
			OLog.err("Initializer object is not a valid " +
					 "MainControllerInfo object");
			return Status.FAILED;
		}
		
		if (getConnectivityBridge() == null) {
			OLog.err("Failed to get connectivity bridge");
			return Status.FAILED;
		}
		
		if (getInternetBridge() == null) {
			OLog.err("Failed to get internet bridge");
			return Status.FAILED;
		}
		
		if (getPersistentDataBridge() == null) {
			OLog.err("Failed to get persistent data bridge");
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	private IConnectivityBridge getConnectivityBridge() {
		_connBridge = (IConnectivityBridge) _mainInfo
				.getFeature("connectivity");
		if (_connBridge == null) {
			return null;
		}
		
		if ( _connBridge.isReady() == false ) {
			if (_connBridge.initialize(_mainInfo) != Status.OK) {
				_connBridge = null;
			}
		}
		
		return _connBridge;
	}

	private IInternetBridge getInternetBridge() {
		_internetBridge = (IInternetBridge) _mainInfo
				.getFeature("internet");
		if (_internetBridge == null) {
			OLog.err("Retrieve failure");
			return null;
		}
		
		if ( _internetBridge.isReady() == false ) {
			if (_internetBridge.initialize(_mainInfo) != Status.OK) {
				OLog.err("Init failure");
				_internetBridge = null;
			}
		}
		
		return _internetBridge;
	}
	
	private IPersistentDataBridge getPersistentDataBridge() {
		_storedDataBridge = (IPersistentDataBridge) _mainInfo
				.getFeature("persistentDataStore");
		if (_storedDataBridge == null) {
			return null;
		}
		
		if ( _storedDataBridge.isReady() == false ) {
			if (_storedDataBridge.initialize(_mainInfo) != Status.OK) {
				_storedDataBridge = null;
			}
		}
		
		return _storedDataBridge;
	}
	
	public Configuration getConfig(String file) {
		if (file == null) {
			OLog.err("Invalid parameter: file is NULL");
			return null;
		}
		
		Configuration config = new Configuration("default");
		config = new Configuration("default");
		
		try {
			ConfigXmlParser cfgParse = new ConfigXmlParser();
			if ( cfgParse.parseXml(file, config) != Status.OK )
			{
				OLog.err("Config Xml Parsing Failed");
				config = null;
			}
		} catch (Exception e) {
			OLog.err("Exception occurred: " + e.getMessage());
			config = null;
		}
		
		return config;
	}
	
	public Configuration getLoadedConfig() {
		return this._config;
	}
	
	public Status loadConfig(Configuration config) {
		_config = config;
		
		return Status.OK;
	}
	
	public Status loadConfigFile(String file) {
		if (file == null) {
			OLog.err("Invalid parameter: file is NULL");
			_config = null;
			return Status.FAILED;
		}
		
		_config = this.getConfig(file);
		if (_config == null) {
			OLog.err("Failed to get config file: " + file);
			_config = null;
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	private Configuration retrieveOldConfig() {
		String cfgFilePaths[] = {
			this.getPrevConfigFilePath(),
			this.getFullConfigFilePath() 
		};
		
		Configuration oldConfig = null;
		for (String filePath : cfgFilePaths) {
			OLog.info("Attempting to load old config file: " + filePath);
			oldConfig = getConfig(filePath);
			if (oldConfig != null) {
				OLog.info("Old config file loaded successfully");
				return oldConfig;
			}
		}
		
		return null;
	}
	
	public Status runConfigFileUpdate() {
		_config = retrieveOldConfig();
		if (_config == null) {
			OLog.err("Failed to load old config file");
			return Status.FAILED;
		}

		/* Get the old config file age limit and time since last update */
		long ageLimit = this.getConfigFileAgeLimit();
		long lastUpdateAge = this.getTimeSinceLastConfigUpdate();
		
		/* If the elapsed time since the last config file update has hit the
		 *   age limit, then a new config file should be downloaded */
		if (lastUpdateAge < ageLimit) {
			OLog.info("Config file still up-to-date: " 
					+ Long.toString(lastUpdateAge));
			return Status.OK;
		}
		
		/* Attempt to download the new config file */
		OLog.info("Downloading config file...");
		if (this.downloadConfigFile(DEFAULT_CFG_FILE_PATH, 
				DEFAULT_CFG_FILE_TEMP_NAME) == Status.OK) {
			String tempCfgFilePath = 
					DEFAULT_CFG_FILE_PATH + "/" + DEFAULT_CFG_FILE_TEMP_NAME;
			
			/* Attempt to reload the config file */
			OLog.info("Loading config file...");
			if ( this.loadConfigFile(tempCfgFilePath) != Status.OK ) {
				OLog.err("Failed to load config file");
				return Status.FAILED;
			}
		} else {
			OLog.err("Failed to download config file");
			return Status.FAILED;
		}
		
		/* Record the current system time as the last updated time 
		 * 	for the config file */
		if (_storedDataBridge != null) {
			_storedDataBridge.put("LAST_CFG_UPDATE_TIME", 
					Long.toString(System.currentTimeMillis()));
		} else {
			OLog.warn("Stored data bridge unavailable");
		}
		
		return Status.OK;
	}
	
	public String getConfigData(String id, String type) {
		if (_config == null) {
			OLog.err("No config files loaded");
			return null;
		}
		
		return (this.getConfigData(_config, id, type));
	}

	
	public String getConfigData(Configuration config, String id, String type) {
		if (config == null) {
			OLog.err("Invalid config");
			return null;
		}
		
		if ((id == null) || (type == null)) {
			OLog.err("Invalid data parameters");
			return null;
		}
		
		Data d = _config.getData(id);
		/* Ensure that the data object was found */
		if (d == null) {
			OLog.err("No matching data objects found: " + id + ", " + type);
			return null;
		}
		
		/* Ensure that the data types match */
		if ( d.getType().equals(type) == false ) {
			OLog.err("Data object types do not match: " 
						+ type + " vs " + d.getType() );
			return null;
		}
		
		return d.getValue();
	}
	
	/*********************/
	/** Private Methods **/
	/*********************/
	private String getPrevConfigFilePath() {
		if (_config == null) {
			return DEFAULT_CFG_PREV_FILE_PATH;
		}
		
		String path = this.getConfigData("config_file_prev_path", "string");
		if (path == null) {
			return DEFAULT_CFG_PREV_FILE_PATH;
		}
		
		return path;
	}
	
	private String getFullConfigFilePath() {
		if (_config == null) {
			return DEFAULT_CFG_FULL_FILE_PATH;
		}
		
		String path = this.getConfigData("config_file_full_path", "string");
		if (path == null) {
			return DEFAULT_CFG_FULL_FILE_PATH;
		}
		
		return path;
	}
	
	private long getConfigFileAgeLimit() {
		Long ageThreshold = 0l;
		
		if (_config == null) {
			return DEFAULT_CONFIG_FILE_AGE_LIMIT;
		}
		
		Data d = _config.getData("config_file_age_threshold");
		if (d == null) {
			return DEFAULT_CONFIG_FILE_AGE_LIMIT;
		}
		
		if (d.getType().equals("long") == false) {
			return DEFAULT_CONFIG_FILE_AGE_LIMIT;
		}
		
		String ageThrStr = d.getValue().replace("l", "");
		try {
			ageThreshold = Long.parseLong(ageThrStr);
		} catch (NumberFormatException e) {
			OLog.err("Could not parse age threshold: [" + d.getValue() + "]");
			ageThreshold = DEFAULT_CONFIG_FILE_AGE_LIMIT;
		} catch (Exception e) {
			OLog.err("Could not derive age threshold: " + d.getValue());
			ageThreshold = DEFAULT_CONFIG_FILE_AGE_LIMIT;
		}
		
		return ageThreshold;
	}
	
	private long getTimeSinceLastConfigUpdate() {
		String result = null;
		if (_storedDataBridge != null) {
			result = _storedDataBridge.get("LAST_CFG_UPDATE_TIME");
		} else {
			OLog.warn("Stored data bridge unavailable");
			return 0l;
		}
		
		if (result == null) {
			OLog.warn("Config file has never been updated");
			return 0l;
		}

		Long lastUpdated = 0l;
		try {
			lastUpdated = Long.parseLong(result.trim());
		} catch (NumberFormatException e) {
			OLog.err("Could not parse last config update time: " + result.trim());
			lastUpdated = 0l;
		} catch (Exception e) {
			OLog.err("Exception ocurred: " + e.getMessage());
			lastUpdated = 0l;
		}
		
		return (System.currentTimeMillis() - lastUpdated);
	}
	
	private String getDeviceConfigUrl() {
		String deviceConfigUrl = DEFAULT_DEVICE_CONFIG_URL_BASE
				+ "/" + DEFAULT_DEVICE_CONFIG_URL_ID;
		
		if (_config == null) {
			OLog.warn("Cannot get device config url: Invalid configuration");
			return deviceConfigUrl;
		}
		
		Data d = null;
		
		/* Obtain the base url from which the config files will be obtained */
		d = _config.getData("device_config_url_base");
		String url_base = DEFAULT_DEVICE_CONFIG_URL_BASE;
		if (d != null) {
			if (d.getType().equals("string") == false) {
				return deviceConfigUrl;
			}
			
			url_base = d.getValue();
			if (url_base == null) {
				return deviceConfigUrl;
			}
			
			return deviceConfigUrl;
		} else {
			OLog.warn("No custom base url defined");
		}

		/* OPTIONAL: Obtain the device id url from the config file.
		 * 	By default, the device will attempt to obtain a unique ID based
		 *  on its IMEI to use as its device id URL - the case below is 
		 *  only executed if a device id is defined in the fonfig file. */
		d = _config.getData("device_config_url_id");
		String url_device_id = this.getDeviceConfigUrlId();
		if (d != null) {
			
			if (d.getType().equals("string") == false) {
				return deviceConfigUrl;
			}
			
			url_device_id = d.getValue();
			if (url_device_id == null) {
				return deviceConfigUrl;
			}
			
			return deviceConfigUrl;
		} else {
			OLog.warn("No custom device id defined");
		}
		
		/* Generate the full device config url */
		deviceConfigUrl = url_base + "/" + url_device_id;
		OLog.info("ConfigUrl: " + deviceConfigUrl);
		
		return deviceConfigUrl;
	}
	
	private String getDeviceConfigUrlId() {
		if (_connBridge == null) {
			OLog.err("Device identity bridge unavailable");
			return DEFAULT_DEVICE_CONFIG_URL_ID;
		}
		
		return ("DV" + ((IDeviceInfoBridge)_connBridge).getDeviceId());
	}
	
	private Status saveConfigFileData(byte data[], String filePath, 
			String fileName) {
		if (FSMan.saveFileData(filePath, fileName, data) != Status.OK) {
			OLog.err("Save config file data failed");
			return Status.FAILED;
		}
		return Status.OK;
	}
	
	private Status downloadConfigFile(String savePath, String saveFileName) {
		if (_connBridge == null) {
			OLog.err("Connectivity bridge unavailable");
			return Status.FAILED;
		}
		
		/* Check connectivity */
		if (_connBridge.isConnected() == false) {
			OLog.err("Not connected");
			return Status.FAILED;
		}
		
		/* Download a new version of the config file from the remote server 
		 *   only if a connection is available */
		if (_internetBridge == null) {
			OLog.err("Internet bridge unavailable");
			return Status.FAILED;
		}
		
		/* Create a dummy GET request */
		String cfgUrl = this.getDeviceConfigUrl();
		SendableData getConfigRequest = new SendableData(cfgUrl, "GET",
				new HttpEncodableData() {
					@Override
					public HttpEntity encodeDataToHttpEntity() {
						return null;
					}
				}
		);
		
		/* Send the dummy request in order to receive a response */
		if (_internetBridge.send(getConfigRequest) != Status.OK) {
			OLog.err("Failed to download config file");
			return Status.FAILED;
		}
		
		/* Retrieve the response from the network bridge */
		byte[] configFileBin = _internetBridge.getResponse();
		
		/* Save data to file */
		this.saveConfigFileData(configFileBin,
				savePath, saveFileName);
		
		return Status.OK;
	}
}
