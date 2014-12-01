package net.oukranos.OreadPrototype;

import java.util.Random;

import android.util.Log;

/**
 * <b>RandomSensorDataFactory Class</b> 
 * </br>Handles random generation of sensor data. This is mostly used for testing purposes.
 */
public class RandomSensorDataFactory
{
	private static int iInvocations = 0;
	// private final static String[] sensorIds = { "site1a", "site2a", "site2b", "site2c", "site3" }; 
	private final static String[] sensorStatus = { "Info: Ready/Idle", "Warning: Low Power", "Warning: X Sensor Malfunctioning", "Info: Reboot" };
	private final static int[] sensorDRMultiplier = { 20, 10000, 14, 100 };
	
	/**
	 * Creates a Type 1 Sensor Data object and randomly generates values for it
	 * 
	 * @return a Type1SensorData object
	 */
	public static Type1SensorData createType1SensorData()
	{
		final String LOG_ID_STRING = "[createType1SensorData]";
		
		Random rand = new Random(4423);
		
		int sensId = rand.nextInt(4);
		String sensStatus = sensorStatus[rand.nextInt(3)];
		double sensData[] = new double[4];
		
		for (int i = 0; i < sensData.length; i++)
		{
			sensData[i] = rand.nextDouble() * sensorDRMultiplier[i];
		}
		
		iInvocations++;
		Log.i(LOG_ID_STRING, "Invocation Num: " + iInvocations +", Id: " + sensId + ", Status: " + sensStatus + ", Data: " + sensData.toString());
		
		return Type1SensorData.create(sensId, sensStatus, sensData);
	}
}
