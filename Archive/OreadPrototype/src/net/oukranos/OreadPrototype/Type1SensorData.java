package net.oukranos.OreadPrototype;


/**
 * <b>Type1SensorData Class</b> 
 * </br>A class for representing Type 1 sensor data. This is currently defined to 
 * contain dissolved oxygen, conductivity, pH, and temperature data.
 */
public class Type1SensorData
{
	public String status = "";
	public long timestamp = 0;
	public int sensor_id = 1; // Should 
	public double dissolved_oxygen = 0.0;
	public double conductivity = 0.0;
	public double ph = 0.0;
	public double temperature = 0.0;
	public double tds = 0.0; /* Total Dissolved Solids */
	public double salinity= 0.0;
	
	private static final int SENSOR_DATA_ARRAY_LEN = 6; 
	
	private Type1SensorData(int id, String status, double sensorData[])
	{
		if ( sensorData.length != SENSOR_DATA_ARRAY_LEN ) {
			return;
		}
		
		this.sensor_id = id;
		this.timestamp = System.currentTimeMillis();
		
		this.dissolved_oxygen = sensorData[0];
		this.conductivity = sensorData[1];
		this.ph = sensorData[2];
		this.temperature = sensorData[3];
		this.tds = sensorData[4];
		this.salinity = sensorData[5];
		
		this.status = status;
	}
	
	/* TODO: Fix this implementation later */
	public int addTimestamp() {
		this.timestamp = System.currentTimeMillis();
		return 1;
	}
	
	/**
	 * Creates a Type1SensorData object based on the arguments
	 * @param id - a String containing the sensor ID
	 * @param status - a String containing the sensor status
	 * @param sensorData - an array of Double values containing the sensor data
	 * @return a Type1SensorData object
	 */
	public static Type1SensorData create(int id, String status, double sensorData[])
	{
		/* Prevent null ids and empty string ids since those would be confusing */
		if (id < 0)
		{
			return null;
		}

		/* Check if the sensorData array is not null */
		if (sensorData == null)
		{
			return null;
		}
		
		/* Check if the sensorData array is of proper length */
		if (sensorData.length != SENSOR_DATA_ARRAY_LEN)
		{
			return null;
		}

		return new Type1SensorData(id, (status == null ? "" : status), sensorData);
	}
}
