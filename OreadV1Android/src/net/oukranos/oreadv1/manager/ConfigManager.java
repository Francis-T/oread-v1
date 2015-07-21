package net.oukranos.oreadv1.manager;

import org.apache.http.HttpEntity;

import android.content.Context;
import net.oukranos.oreadv1.android.AndroidConnectivityBridge;
import net.oukranos.oreadv1.android.AndroidInternetBridge;
import net.oukranos.oreadv1.interfaces.ConnectivityBridgeIntf;
import net.oukranos.oreadv1.interfaces.DeviceIdentityIntf;
import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.interfaces.InternetBridgeIntf;
import net.oukranos.oreadv1.manager.FilesystemManager.FSMan;
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
	private static final String DEFAULT_CFG_FULL_FILE_PATH = DEFAULT_CFG_FILE_PATH + "/" + DEFAULT_CFG_FILE_NAME;
	private static final String DEFAULT_DEVICE_CONFIG_URL_BASE = "http://miningsensors.info/deviceconf";
	private static final String DEFAULT_DEVICE_CONFIG_URL_ID = "TEST_DEVICE";
	private static final long 	DEFAULT_CONFIG_FILE_AGE_LIMIT = (8 * 60 * 60 * 1000); // ~8 hours old
	
	private static ConfigManager _configMgr = null;
	private Configuration _config = null;
	
	private ConfigManager() {
		return;
	}
	
	public static ConfigManager getInstance() {
		if (_configMgr == null) {
			_configMgr = new ConfigManager();
		}
		return _configMgr; 
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
			return Status.FAILED;
		}
		
		_config = this.getConfig(file);
		if (_config == null) {
			OLog.err("Failed to get config file: " + file);
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	public Status runConfigFileUpdate(Object updateObj) {
		if (updateObj == null) {
			OLog.err("Invalid Update Object");
			return Status.FAILED;
		}
		
		/* Cast the update object as a Context object **/
		Context context = null;
		
		try {
			context = (Context) updateObj;
		} catch (Exception e) {
			OLog.err("Exception occurred: " + e.getMessage());
			return Status.FAILED;
		}
		
		/* Get the old config file path */
		String cfgFilePath = this.getFullConfigFilePath();
		
		/* Get the old config file */
		Configuration oldConfig = this.getConfig(cfgFilePath);
		if (oldConfig == null) {
			OLog.err("Failed to load old config file");
			return Status.FAILED;
		}

		/* Get the old config file age limit and time since last update */
		long ageLimit = this.getConfigFileAgeLimit();
		long lastUpdateAge = this.getConfigFileLastUpdateAge();
		
		/* If the elapsed time since the last config file update has hit the
		 *   age limit, then a new config file should be downloaded */
		if (lastUpdateAge > ageLimit) {
			OLog.info("Config file still up-to-date: " 
					+ Long.toString(lastUpdateAge));
			return Status.OK;
		}
		
		/* Attempt to download the new config file */
		if (this.downloadConfigFile(context, DEFAULT_CFG_FILE_PATH, 
				DEFAULT_CFG_FILE_TEMP_NAME) == Status.OK) {
			
			/* Attempt to reload the config file */
			if ( this.loadConfigFile(cfgFilePath) != Status.OK ) {
				OLog.err("Failed to load config file");
				return Status.FAILED;
			}
		} else {
			OLog.err("Failed to download config file");
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
	
	private Status downloadConfigFile(Context context, 
			String savePath, String saveFileName) {
		ConnectivityBridgeIntf connBridge = 
				AndroidConnectivityBridge.getInstance();
		connBridge.initialize(context);
		
		/* Check connectivity */
		if (connBridge.isConnected() == false) {
			OLog.err("Not connected");
			return Status.FAILED;
		}
		
		/* Download a new version of the config file from the remote server 
		 *   only if a connection is available */
		InternetBridgeIntf netBridge = AndroidInternetBridge.getInstance();
		netBridge.initialize(context);
		
		/* Create a dummy GET request */
		String cfgUrl = this.getDeviceConfigUrl(context);
		SendableData getConfigRequest = new SendableData(cfgUrl, "GET",
				new HttpEncodableData() {
					@Override
					public HttpEntity encodeDataToHttpEntity() {
						return null;
					}
				}
		);
		
		/* Send the dummy request in order to receive a response */
		if (netBridge.send(getConfigRequest) != Status.OK) {
			OLog.err("Failed to download config file");
			return Status.FAILED;
		}
		
		/* Retrieve the response from the network bridge */
		String configFileStr = netBridge.getResponse();
		if (configFileStr == null) {
			OLog.err("Invalid config file content");
			return Status.FAILED;
		}
		
		/* Save data to file */
		this.saveConfigFileData(configFileStr.getBytes(),
				savePath, saveFileName);
		
		OLog.info("Response: " + configFileStr); // DEBUG TODO
		
		return Status.OK;
	}
	
	/*********************/
	/** Private Methods **/
	/*********************/
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
		
		try {
			ageThreshold = Long.decode(d.getValue());
		} catch (NumberFormatException e) {
			OLog.err(e.getMessage());
			ageThreshold = DEFAULT_CONFIG_FILE_AGE_LIMIT;
		}
		
		return ageThreshold;
	}
	
	private long getConfigFileLastUpdateAge() {
		Long lastUpdated = 0l; 
		try {
			lastUpdated = (System.currentTimeMillis() - 
					Long.decode(_config.getCreationDate()));
		} catch (Exception e) {
			OLog.err(e.getMessage());
			lastUpdated = 0l;
		}
		
		return lastUpdated;
	}
	
	private String getDeviceConfigUrl(Context context) {
		String deviceConfigUrl = DEFAULT_DEVICE_CONFIG_URL_BASE
				+ "/" + DEFAULT_DEVICE_CONFIG_URL_ID;
		
		if (context == null) {
			return deviceConfigUrl;
		}
		
		if (_config == null) {
			return deviceConfigUrl;
		}
		
		Data d = null;
		
		/* Obtain the base url from which the config files will be obtained */
		d = _config.getData("device_config_url_base");
		if (d == null) {
			return deviceConfigUrl;
		}
		
		if (d.getType().equals("string") == false) {
			return deviceConfigUrl;
		}
		
		String url_base = d.getValue();
		if (url_base == null) {
			return deviceConfigUrl;
		}
		
		/* Save the base url to the device config url */
		deviceConfigUrl = url_base + "/" + this.getDeviceConfigUrlId(context);

		OLog.info("ConfigUrl: " + deviceConfigUrl);
		/* OPTIONAL: Obtain the device id url from the config file.
		 * 	By default, the device will attempt to obtain a unique ID based
		 *  on its IMEI to use as its device id URL - the case below is 
		 *  only executed if a device id is defined in the fonfig file. */
		d = _config.getData("device_config_url_id");
		if (d == null) {
			return deviceConfigUrl;
		}
		
		if (d.getType().equals("string") == false) {
			return deviceConfigUrl;
		}
		
		String url_device_id = d.getValue();
		if (url_device_id == null) {
			return deviceConfigUrl;
		}
		
		/* Override the device id url based on the phone's IMEI */
		deviceConfigUrl = url_base + "/" + url_device_id;
		OLog.info("ConfigUrl: " + deviceConfigUrl);
		
		return deviceConfigUrl;
	}
	
	private String getDeviceConfigUrlId(Context context) {
		DeviceIdentityIntf deviceIdBridge = 
				AndroidConnectivityBridge.getInstance();
		if (deviceIdBridge == null) {
			return DEFAULT_DEVICE_CONFIG_URL_ID;
		}
		
		return ("DV" + deviceIdBridge.getDeviceId());
	}
	
	private Status saveConfigFileData(byte data[], String filePath, 
			String fileName) {
		if (FSMan.saveFileData(filePath, fileName, data) != Status.OK) {
			OLog.err("Save config file data failed");
			return Status.FAILED;
		}
		return Status.OK;
	}
}
