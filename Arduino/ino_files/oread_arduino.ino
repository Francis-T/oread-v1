#ifndef __OREAD_ARDUINO_C__
#define __OREAD_ARDUINO_C__

#define __USE_ARDUINO__

#ifndef __USE_ARDUINO__
#include "oread_arduino.h"
#else
#include <OneWire.h>
#include <Wire.h>
#include <HardwareSerial.h>
#endif

#ifndef TRUE
#define TRUE    1
#endif

#ifndef FALSE
#define FALSE   0
#endif

#define SZ_RX_BUFFER    100
#define SZ_TX_BUFFER    100
#define EOS             '\0'
#define MATCHED         1
#define NOT_MATCHED     0
#define STATUS_OK_INC   2
#define STATUS_OK       1
#define STATUS_FAILED   0
#define status_t        int

#define SENSOR_READ_TIMEOUT 5000
#define SENSOR_LIST_SIZE    7
#define DEVICE_LIST_SIZE    7
#define SENS_BUF_MAX        32

#define DUMMY_SENSOR_ID     0
#define DUMMY_DEVICE_ID     0
#define MASTER_I2C_ADDR     0
#define HIGHEST_I2C_ADDR    127

#define REQUEST_BYTE_COUNT  32

#define WATER_LVL_MON 8
#define VALVE_CTRL    9
#define PUMP_CTRL     7
#define FILL_VALVE    11
#define DRAIN_VALVE   12
#define FILL2_VALVE   13

/** Type Definitions **/
/* enum for the different Sensor States */
typedef enum sensorStates {  SENSOR_STARTED,
                             SENSOR_READING,
                             SENSOR_STOPPED } sensorState_t;

/* enum for the different Device States */
typedef enum deviceStates {  DEVICE_STARTED,
                             DEVICE_RUNNING,
                             DEVICE_STOPPED } deviceState_t;

/* typedef for sensor read functions */
typedef status_t (*fSensReadFunc_t)(int sensId);
/* typedef for sensor calibrate functions */
typedef status_t (*fSensCalibrateFunc_t)(int sensId, char param);
/* typedef for sensor  info */
typedef status_t (*fSensInfoFunc_t)(int sensId);

/* typedef for device activate functions */
typedef status_t (*fDeviceActivateFunc_t)(int deviceId);
/* typedef for device update functions */
typedef status_t (*fDeviceUpdateFunc_t)(int deviceId);
/* typedef for device deactivate functions */
typedef status_t (*fDeviceDeactivateFunc_t)(int deviceId);

/* typedef for the Sensor Info struct */
typedef struct sensorInfo
{
    const char* aName;
    int iPin;
    sensorState_t eState;
    HardwareSerial aSerial;
    char* aBuf;
    int iBufOffset;
    int bIsComplete;
    long lReadStartTime;
} tSensor_t;

/* typedef for the Device Info struct */
typedef struct deviceInfo
{
    const char* aName;
    int iPin;
    deviceState_t eState;
    long lActivatedTime;
    long lTimeout;
} tDevice_t;

/** Global Variables **/
int  _debugMode = FALSE;
char aSensDataBuf[SENSOR_LIST_SIZE][SENS_BUF_MAX];

/* Tx/Rx Buffer */
char _aRxBuffer[SZ_RX_BUFFER];
int  _iRxBufferLen = 0;
char _aTxBuffer[SZ_TX_BUFFER];
int  _iTxBufferLen = 0;

// #define dbg_print(x,y,z) \
// { \
//    if ( (_debugMode == 1) && (x != NULL) && (y != NULL) && (z != NULL) ) { \
//      char aMessage[200]; \
//      memset(aMessage, EOS, sizeof(char) * 200); \
//      sprintf(aMessage, "[%s] %s: %s", x, y, z); \
//      Serial.println(aMessage); \
//    } \
// }

/* Function declarations */
void setup();
void loop();

void serialEventRead(tSensor_t* pSensor, const char* aSerialName);
void serialEvent1();
void serialEvent2();
void serialEvent3();

status_t proc_readSensor(char* pMsg);
status_t proc_calibrateSensor(char* pMsg);
status_t proc_getSensorInfo(char* pMsg);
status_t proc_forceSensor(char* pMsg);
status_t proc_activateDevice(char* pMsg);
status_t proc_deactivateDevice(char* pMsg);
status_t proc_i2cCommand(char* pMsg);
status_t proc_processInput(char* pMsg, int iLen);

int sens_getSensorId(char* aStr);
status_t sens_atlasRead(int iSensId);
status_t sens_atlasInfo(int iSensId);
status_t sens_atlasCalibrate(int iSensId, char cParam);
status_t sens_atlasSendCmd(tSensor_t* pSensor, const char* pCmd);
status_t sens_turbidityRead(int iSensId);
status_t sens_turbidityInfo(int iSensId);
status_t sens_turbidityCalibrate(int iSensId, char cParam);
void sens_dataFinished(tSensor_t* pSensor);
int sens_initSensors(void);

int auto_getDeviceId(char* aStr);
status_t auto_valveActivate(int iDeviceId);
status_t auto_valveDeactivate(int iDeviceId);
status_t auto_pumpActivate(int iDeviceId);
status_t auto_pumpUpdate(int iDeviceId);
status_t auto_pumpDeactivate(int iDeviceId);

status_t com_receiveUserInput();
status_t com_sendResponse();

status_t tmr_manageSensorTimeouts();
status_t tmr_manageDeviceUpdates();
status_t tmr_updateTimeout(tSensor_t* pSensor);

int utl_atoi(const char* s);
int utl_getField(char* s1, char* s2, int iTgtField, char cDelim);
int utl_compare(const char* s1, const char* s2, int iLen);
void utl_clearBuffer(void* pBuf, int iSize,  int iLen);
int utl_strLen(const char* s1);
int utl_strNCpy(char* pDest, const char* pSrc, int iLen);
int utl_strCpy(char* pDest, const char* pSrc);
int utl_strNCat(char* pDest, const char* pSrc, int iLen);
int utl_strCat(char* pDest, const char* pSrc);

void dbg_print(const char* pTag, const char* pMsg, const char* pExtra);
void dbg_printUL(const char* pTag, const char* pMsg, unsigned long ulVal);
void dbg_printChar(const char* pTag, const char* pMsg, char cVal);

/* Lookup table for the sensors */
tSensor_t _tSensor[SENSOR_LIST_SIZE] =
{
    { "XX",  0, SENSOR_STOPPED,  Serial,                NULL, 0, FALSE, 0 }, /* Dummy Sensor */
    { "pH", 10, SENSOR_STOPPED, Serial1, &aSensDataBuf[0][0], 0, FALSE, 0 },
    { "DO", 11, SENSOR_STOPPED, Serial2, &aSensDataBuf[1][0], 0, FALSE, 0 },
    { "EC", 12, SENSOR_STOPPED, Serial3, &aSensDataBuf[2][0], 0, FALSE, 0 },
    { "TM",  9, SENSOR_STOPPED,  Serial, &aSensDataBuf[3][0], 0, FALSE, 0 },
    { "TU",  0, SENSOR_STOPPED,  Serial, &aSensDataBuf[4][0], 0, FALSE, 0 },
    { "L1",  8, SENSOR_STOPPED,  Serial, &aSensDataBuf[5][0], 0, FALSE, 0 }  /* Water Level Sensor (High) */
};

/* Lookup table for the sensor read functions */
fSensReadFunc_t _fSensorReadFunc[SENSOR_LIST_SIZE] =
{
    NULL,
    sens_atlasRead,
    sens_atlasRead,
    sens_atlasRead,
    sens_temperatureRead,
    sens_turbidityRead,
    sens_levelRead
};

/* Lookup table for the sensor info functions */
fSensInfoFunc_t _fSensorInfoFunc[SENSOR_LIST_SIZE] =
{
    NULL,
    sens_atlasInfo,
    sens_atlasInfo,
    sens_atlasInfo,
    sens_temperatureInfo,
    sens_turbidityInfo,
    NULL
};

/* Lookup table for the sensor calibrate functions */
fSensCalibrateFunc_t _fSensorCalibrateFunc[SENSOR_LIST_SIZE] =
{
    NULL,
    sens_atlasCalibrate,
    sens_atlasCalibrate,
    sens_atlasCalibrate,
    sens_temperatureCalibrate,
    sens_turbidityCalibrate,
    NULL
};

/* Lookup table for the devices */
tDevice_t _tDevice[DEVICE_LIST_SIZE] =
{
    { "XX",  0, DEVICE_STOPPED, 0,     0 },
    { "V1", 12, DEVICE_STOPPED, 0,     0 },
    { "S1", 11, DEVICE_STOPPED, 0, 60000 },
    { "L1",  8, DEVICE_STOPPED, 0,     0 },
    { "P1", 10, DEVICE_STOPPED, 0, 60000 },
    { "P2",  7, DEVICE_STOPPED, 0, 60000 },
    { "P3",  9, DEVICE_STOPPED, 0, 60000 }
};

/* Lookup table for the device activation functions */
fDeviceActivateFunc_t _fDeviceActivateFunc[DEVICE_LIST_SIZE] =
{
    NULL,
    auto_valveActivate,
    auto_pumpActivate,
    NULL,
    auto_pumpActivate,
    auto_pumpActivate,
    auto_pumpActivate
};

/* Lookup table for the device update functions */
fDeviceUpdateFunc_t _fDeviceUpdateFunc[DEVICE_LIST_SIZE] =
{
    NULL,
    NULL,
    auto_pumpUpdate,
    NULL,
    auto_pumpUpdate,
    auto_pumpUpdate,
    auto_pumpUpdate
};

/* Lookup table for the device deactivation functions */
fDeviceDeactivateFunc_t _fDeviceDeactivateFunc[DEVICE_LIST_SIZE] =
{
    NULL,
    auto_valveDeactivate,
    auto_pumpDeactivate,
    NULL,
    auto_pumpDeactivate,
    auto_pumpDeactivate,
    auto_pumpDeactivate
};

/* Input/Output:
 * e.g.
 * COMMAND              RESPONSE
 *  FILL RESERVOIR  -->  OK / ERROR
 *  DRAIN RESERVOIR -->  OK / ERROR
 */
/******************************************************************************/
/* SEC01: Main Arduino Modules                                                */
/******************************************************************************/
#define MOD_NAME "main"
void setup() {
    Serial.begin(9600);
    Wire.begin();
//    pinMode(VALVE_CTRL, OUTPUT);
//    pinMode(PUMP_CTRL, OUTPUT);
//    pinMode(FILL_VALVE, OUTPUT);
//    pinMode(FILL2_VALVE, OUTPUT);
//    pinMode(DRAIN_VALVE, OUTPUT);
//    pinMode(WATER_LVL_MON, INPUT);
    
    for (int i = 7; i <= 12; i++)
    {
      if (i == WATER_LVL_MON) 
      {
        pinMode(i, INPUT);
        continue;
      }
      pinMode(i, OUTPUT);
    }
    
    utl_clearBuffer(_aRxBuffer, sizeof(char), SZ_RX_BUFFER);
    utl_clearBuffer(_aTxBuffer, sizeof(char), SZ_TX_BUFFER);

    sens_initSensors();
}

void loop() {
    if (com_receiveUserInput() != STATUS_OK) {
        dbg_print(MOD_NAME, "Error", "Failed to receive user input!");
        return;
    }

    if (proc_processInput(_aRxBuffer, _iRxBufferLen) != STATUS_OK) {
        dbg_print(MOD_NAME, "Error", "Failed to process user input!");
        return;
    }

    if (com_sendResponse() != STATUS_OK) {
        dbg_print(MOD_NAME, "Error", "Failed to send response!");
        return;
    }

    if (tmr_manageSensorTimeouts() != STATUS_OK) {
        dbg_print(MOD_NAME, "Error", "Failed to manage sensor timeouts!");
        return;
    }

    if (tmr_manageDeviceUpdates() != STATUS_OK) {
        dbg_print(MOD_NAME, "Error", "Failed to manage device updates!");
        return;
    }

    delay(100);
}

#ifndef __USE_ARDUINO__
int _iLoopCountdown = 500;
/** C's Main Function -- for off-device testing **/
int main(void)
{
    Serial.set_index(0);
    _tSensor[1].aSerial.set_index(1);
    _tSensor[2].aSerial.set_index(2);
    _tSensor[3].aSerial.set_index(3);

    setup();
    while(_iLoopCountdown > 0)
    {
        loop();
        serialEvent1();
        serialEvent2();
        serialEvent3();
        _iLoopCountdown--;
    }
    return 1;
}
#endif /* __USE_ARDUINO__ */
#undef MOD_NAME

#define MOD_NAME "sevt"
void serialEventRead(tSensor_t* pSensor, const char* aSerialName)
{
    int iOffs = pSensor->iBufOffset;
    char cRead = '\0';
    char cIsSensorActive = FALSE;
    
    if (pSensor->eState == SENSOR_READING) {
      cIsSensorActive = TRUE;
    }

    while ((pSensor->aSerial).available() > 0)
    {
        if (iOffs == 0)
        {
            utl_clearBuffer(pSensor->aBuf, sizeof(char), SENS_BUF_MAX);
            utl_strNCpy(pSensor->aBuf, pSensor->aName, 2);
            pSensor->aBuf[2] = ':';
            pSensor->aBuf[3] = ' ';
            iOffs += 4;
        }
        if (iOffs >= SENS_BUF_MAX)
        {
            dbg_print(aSerialName, "Sensor buffer is full", pSensor->aName);
            break;
        }

        cRead = (pSensor->aSerial).read();
        
        /* Flush received data if the current sensor is not actively reading */
        //if (cIsSensorActive != TRUE)
        //{
        //  cRead = '\0';
        //  continue;
        //}

        if (!(cRead < ' ') && !(cRead > '~'))
        {
            pSensor->aBuf[iOffs] = cRead;
              pSensor->iBufOffset = iOffs;
              iOffs++;
        }

        dbg_printChar(aSerialName, "Read Char", cRead);

        if (cRead == '\r')
        {
            dbg_print(aSerialName, "Sensor data completed", pSensor->aBuf);
            pSensor->bIsComplete = TRUE;
            iOffs = 0;
            break;
        }
    }

    if (pSensor->bIsComplete)
    {
        sens_dataFinished(pSensor);
        return;
    }
}

void serialEvent1()
{
    const int iSensIdx = 1;    /* Sensor Index should match Serial1 */
    serialEventRead(&_tSensor[iSensIdx], "serialEvent1");
    return;
}

void serialEvent2()
{
    const int iSensIdx = 2;    /* Sensor Index should match Serial2 */
    serialEventRead(&_tSensor[iSensIdx], "serialEvent2");
    return;
}

void serialEvent3()
{
    const int iSensIdx = 3;    /* Sensor Index should match Serial3 */
    serialEventRead(&_tSensor[iSensIdx], "serialEvent3");
    return;
}
#undef MOD_NAME

/******************************************************************************/
/* SEC02: Processing Modules                                                  */
/******************************************************************************/
#define MOD_NAME "proc"
/*
 * @function     proc_readSensor()
 * @description  Example function
 * @returns      exit status
 */
status_t proc_readSensor(char* pMsg) {
    int iSensId;
    char aParams[5];

    /* Extract the Sensor Id target for this read */
    utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
    utl_getField(pMsg, aParams, 2, ' ');
    iSensId = utl_atoi(aParams);

    /* Attempt to match the sensor id by matching the sensor name */
    if (iSensId == DUMMY_SENSOR_ID)
    {
        iSensId = sens_getSensorId(aParams);
    }

    /* Ensure that we're not accessing invalid sensor IDs */
    if ( (iSensId <= DUMMY_SENSOR_ID) || (iSensId >= SENSOR_LIST_SIZE) ) {
        dbg_print(MOD_NAME, "Error", "Invalid Sensor Id");
        return STATUS_FAILED;
    }

    /* Prepare the sensor */
    _tSensor[iSensId].eState = SENSOR_READING;
    _tSensor[iSensId].bIsComplete = FALSE;

    /* Call the read function for this sensor */
    if (_fSensorReadFunc[iSensId] != NULL) {
        _fSensorReadFunc[iSensId](iSensId);
    }

    return STATUS_OK;
}

status_t proc_calibrateSensor(char* pMsg) 
{
    int iSensId;
    char cCalParam;
    char aParams[5];

    /* Extract the Sensor Id target for this read */
    utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
    utl_getField(pMsg, aParams, 2, ' ');
    iSensId = utl_atoi(aParams);

    /* Attempt to match the sensor id by matching the sensor name */
    if (iSensId == DUMMY_SENSOR_ID)
    {
        iSensId = sens_getSensorId(aParams);
    }

    /* Ensure that we're not accessing invalid sensor IDs */
    if ( (iSensId <= DUMMY_SENSOR_ID) || (iSensId >= SENSOR_LIST_SIZE) ) {
        dbg_print(MOD_NAME, "Error", "Invalid Sensor Id");
        return STATUS_FAILED;
    }

    /* Extract the Calibration Param for this read */
    utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
    utl_getField(pMsg, aParams, 1, ' ');
    cCalParam = aParams[0];

    /* Calibration parameter should be an uppercase letter */ 
    if ( (cCalParam < 'A') || (cCalParam > 'Z' ) ) {
        dbg_print(MOD_NAME, "Error", "Invalid Calibration Param");
        return STATUS_FAILED;
    }

    /* Prepare the sensor */
    _tSensor[iSensId].eState = SENSOR_READING;
    _tSensor[iSensId].bIsComplete = FALSE;

    /* Call the calibrate function for this sensor */
    if (_fSensorCalibrateFunc[iSensId] != NULL) {
        _fSensorCalibrateFunc[iSensId](iSensId, cCalParam);
    }

    return STATUS_OK;
}

status_t proc_getSensorInfo(char* pMsg)
{
    int iSensId;
    char cCalParam;
    char aParams[5];

    /* Extract the Sensor Id target for this read */
    utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
    utl_getField(pMsg, aParams, 2, ' ');
    iSensId = utl_atoi(aParams);

    /* Attempt to match the sensor id by matching the sensor name */
    if (iSensId == DUMMY_SENSOR_ID)
    {
        iSensId = sens_getSensorId(aParams);
    }

    /* Ensure that we're not accessing invalid sensor IDs */
    if ( (iSensId <= DUMMY_SENSOR_ID) || (iSensId >= SENSOR_LIST_SIZE) ) {
        dbg_print(MOD_NAME, "Error", "Invalid Sensor Id");
        return STATUS_FAILED;
    }

    /* Prepare the sensor */
    _tSensor[iSensId].eState = SENSOR_READING;
    _tSensor[iSensId].bIsComplete = FALSE;

    /* Call the info function for this sensor */
    if (_fSensorInfoFunc[iSensId] != NULL) {
        _fSensorInfoFunc[iSensId](iSensId);
    }

    return STATUS_OK;
}

status_t proc_forceSensor(char* pMsg)
{
    int iSpaceCount;
    int iSensId;
    char aParams[5];
    char* pCmd = NULL;

    /* Extract the Sensor Id target for this read */
    utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
    utl_getField(pMsg, aParams, 2, ' ');
    iSensId = utl_atoi(aParams);

    /* Attempt to match the sensor id by matching the sensor name */
    if (iSensId == DUMMY_SENSOR_ID)
    {
        iSensId = sens_getSensorId(aParams);
    }

    /* Ensure that we're not accessing invalid sensor IDs */
    if ( (iSensId <= DUMMY_SENSOR_ID) || (iSensId >= SENSOR_LIST_SIZE) ) {
        dbg_print(MOD_NAME, "Error", "Invalid Sensor Id");
        return STATUS_FAILED;
    }
    
    /* Ensure that we're only doing this for Atlas sensors */
    if ( (iSensId < 1) || (iSensId > 3) )
    {
        dbg_print(MOD_NAME, "Error", "Not an Atlas Scientific Sensor");
        return STATUS_FAILED;
    }

    pCmd = pMsg;
    for (iSpaceCount = 0; iSpaceCount < 2; iSpaceCount++) {
        pCmd = strchr(pCmd, ' ') + 1;
        if (pCmd == NULL) {
            dbg_print(MOD_NAME, "Error", "Invalid Sensor Command");
            return STATUS_FAILED;
        } 
    }

    /* Prepare the sensor */
    _tSensor[iSensId].eState = SENSOR_READING;
    _tSensor[iSensId].bIsComplete = FALSE;

    /* Call the send function for this sensor */
    sens_atlasSendCmd(&_tSensor[iSensId], pCmd);

    return STATUS_OK;
}

/*
 * @function     proc_activateDevice()
 * @description  Activates a device
 * @returns      exit status
 */
status_t proc_activateDevice(char* pMsg)
{
    int  iDeviceId = -1;
    char aParams[5];

    /* Extract the Device Id target for this activation */
    utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
    utl_getField(pMsg, aParams, 2, ' ');
    iDeviceId = utl_atoi(aParams);

    /* Attempt to match the device id by matching the device name */
    if (iDeviceId == DUMMY_DEVICE_ID)
    {
        dbg_print(MOD_NAME, "Info", "Looking for device name instead");
        iDeviceId = auto_getDeviceId(aParams);
    }

    if ((iDeviceId < DUMMY_DEVICE_ID) || (iDeviceId > DEVICE_LIST_SIZE))
    {
        return STATUS_FAILED;
    }

    if (_fDeviceActivateFunc[iDeviceId] != NULL)
    {
        return _fDeviceActivateFunc[iDeviceId](iDeviceId);
    }

    return STATUS_OK;
}

/*
 * @function     proc_deactivateDevice()
 * @description  Deactivates a device
 * @returns      exit status
 */
status_t proc_deactivateDevice(char* pMsg)
{
    int  iDeviceId = -1;
    char aParams[5];

    /* Extract the Device Id target for this activation */
    utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
    utl_getField(pMsg, aParams, 2, ' ');
    iDeviceId = utl_atoi(aParams);

    /* Attempt to match the device id by matching the device name */
    if (iDeviceId == DUMMY_DEVICE_ID)
    {
        iDeviceId = auto_getDeviceId(aParams);
    }

    if ((iDeviceId < DUMMY_DEVICE_ID) || (iDeviceId > DEVICE_LIST_SIZE))
    {
        return STATUS_FAILED;
    }

    if (_fDeviceDeactivateFunc[iDeviceId] != NULL)
    {
        return _fDeviceDeactivateFunc[iDeviceId](iDeviceId);
    }

    return STATUS_OK;
}

/*
 * @function     proc_i2cCommand()
 * @description  Relays a command to a child environment node connected
 *                 to this one via I2C
 * @returns      exit status
 */
status_t proc_i2cCommand(char* pMsg)
{
    int iRet = -1;
    int iBufIdx = 0;
    int iAddr;
    char aParams[5];
    char aCmd[5];
    char aMsg[10];
    char aResp[33];
    char* pCmd = NULL;
    boolean expectResponse = false;

    /* Extract the target address */
    dbg_print(MOD_NAME, "Info", "Extracting Address...");
    utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
    utl_getField(pMsg, aParams, 2, ' ');
    iAddr = utl_atoi(aParams);

    /* Ensure that we're not accessing invalid I2C Addresses */
    if ( (iAddr <= MASTER_I2C_ADDR) || (iAddr > HIGHEST_I2C_ADDR) ) {
        dbg_print(MOD_NAME, "Error", "Invalid Address");
        return STATUS_FAILED;
    }

    /* Extract the request flag */
    dbg_print(MOD_NAME, "Info", "Extracting Request Flag...");
    utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
    utl_getField(pMsg, aParams, 3, ' ');
    if ((aParams[0] == 'Y') || (aParams[0] == 'y'))
    {
      expectResponse = true;
    }
    
    /* Extract the command string */
    dbg_print(MOD_NAME, "Info", "Extracting Command...");
    utl_clearBuffer(aCmd, sizeof(aCmd[0]), 5);
    utl_getField(pMsg, aCmd, 4, ' ');

    /* Extract the param string */
    dbg_print(MOD_NAME, "Info", "Extracting Param...");
    utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
    utl_getField(pMsg, aParams, 5, ' ');

    /* Construct the message */
    dbg_print(MOD_NAME, "Info", "Constructing Message...");
    utl_clearBuffer(aMsg, sizeof(aMsg[0]), 10);
    utl_strCpy(aMsg, aCmd);
    utl_strCat(aMsg, " ");
    utl_strCat(aMsg, aParams);

    /* Send through I2C */
    dbg_print(MOD_NAME, "Info", "Transmitting Message...");
    Wire.beginTransmission(iAddr);
    Wire.write(aMsg);
    iRet = Wire.endTransmission();
    if (iRet != 0)
    {
        Serial.print("Failed to send to I2C device(");
        Serial.print(iAddr);
        Serial.println(")");
        return STATUS_FAILED;
    }
    
    /* If this command has no expected responses, then we can return at this point */
    if (expectResponse == false)
    {
      return STATUS_OK;
    }
    /* Get the response */
    dbg_print(MOD_NAME, "Info", "Loading Response...");
    if (_debugMode)
    {
      Serial.print("Bytes Available: ");
      Serial.print(Wire.available());
      Serial.print("\r\n");
    }
    
    if (aCmd[0] != 'x')
    {
      /* Delay for a bit before attempting to get the response, just in case */
      delayMicroseconds(500);
  
      /* Request for a response */
      dbg_print(MOD_NAME, "Info", "Requesting Response...");
      Wire.requestFrom(iAddr, REQUEST_BYTE_COUNT, true);
  
      /* Delay for a bit before attempting to get the response, just in case */
      delayMicroseconds(1000);
  
      /* Clear the response buffer */
      utl_clearBuffer(aResp, sizeof(aResp[0]), 33);
      while (Wire.available() > 0)
      {
          if (iBufIdx > REQUEST_BYTE_COUNT)
          {
              /* Error: Received message is too long */
              dbg_print(MOD_NAME, "Error", "Received message too long");
              return STATUS_FAILED;
          }
  
          /* Read a byte into the Rx buffer */
          aResp[iBufIdx] = Wire.read();
  
          iBufIdx++;
          delay(50);
      }
    }
    else
    {
      for (int i=0; i < 32; i++)
      {
        /* Delay for a bit before attempting to get the response, just in case */
        delayMicroseconds(500);
    
        /* Request for a response */
        dbg_print(MOD_NAME, "Info", "Requesting Response...");
        Wire.requestFrom(iAddr, REQUEST_BYTE_COUNT, true);
    
        /* Delay for a bit before attempting to get the response, just in case */
        delayMicroseconds(1000);
    
        /* Clear the response buffer */
        utl_clearBuffer(aResp, sizeof(aResp[0]), 33);
 
        while (Wire.available() > 0)
        {
            if (iBufIdx > REQUEST_BYTE_COUNT)
            {
                /* Error: Received message is too long */
                dbg_print(MOD_NAME, "Error", "Received message too long");
                return STATUS_FAILED;
            }
    
            /* Read a byte into the Rx buffer */
            aResp[iBufIdx] = Wire.read();
    
            iBufIdx++;
            delay(50);
        }
        
        if (aResp[0] >= '3') {
          break;
        }
        delay(10000);
      }
    }


    dbg_print(MOD_NAME, "Info", "Finished. Contents: ");
//    Serial.print("Node[");
//    Serial.print(iAddr);
//    Serial.print("]: ");
//    Serial.print("DONE");
//    Serial.print("\r\n");
    if (_debugMode)
    {
      for (int i = 0; i < 32; i++)
      {
        Serial.print("[");
        Serial.print(aResp[i]);
        Serial.print("] ");
        if (((i+1)%16) == 0) {
          Serial.print("\r\n");
        }
      }
    }
    Serial.print(aResp);
    Serial.print("\r\n");

    return STATUS_OK;
}

/*
 * @function     proc_processInput()
 * @description  Processes the user input or command
 * @returns      exit status
 */
status_t proc_processInput(char* pMsg, int iLen) {
    if (iLen <= 0) {
      return STATUS_OK;
    }

    if (utl_compare(pMsg, "READ", 4) == MATCHED) {
       proc_readSensor(pMsg);
    } else if (utl_compare(pMsg, "DEBUG", 5) == MATCHED) {
        _debugMode = !_debugMode;
    } else if (utl_compare(pMsg, "INFO", 4) == MATCHED) {
        proc_getSensorInfo(pMsg); /* TODO */
    } else if (utl_compare(pMsg, "CALIB", 5) == MATCHED) {
        proc_calibrateSensor(pMsg); /* TODO */
    } else if (utl_compare(pMsg, "FORCE", 5) == MATCHED) {
        proc_forceSensor(pMsg); /* TODO */
    } else if  (utl_compare(pMsg, "ACTV", 4) == MATCHED) {
        proc_activateDevice(pMsg);
    } else if  (utl_compare(pMsg, "DEAC", 4) == MATCHED) {
        proc_deactivateDevice(pMsg);
    } else if (utl_compare(pMsg, "I2C", 3) == MATCHED) {
        proc_i2cCommand(pMsg); /* TODO */
    #ifndef __USE_ARDUINO__
    } else if (utl_compare(pMsg, "QUIT", 4)) {
        _iLoopCountdown = 0;
    #endif /* __USE_ARDUINO__ */
    }  else {
        dbg_print(MOD_NAME, "Error", "Invalid command input!");
        return STATUS_FAILED;
    }
    return STATUS_OK;
}
#undef MOD_NAME

/******************************************************************************/
/* SEC03: Sensor Handling Modules                                             */
/******************************************************************************/
#define MOD_NAME "sens"
int sens_getSensorId(char* aStr)
{
    for (int iSensorIdx = 1; iSensorIdx < DEVICE_LIST_SIZE; iSensorIdx++)
    {
        if( utl_compare(aStr, _tSensor[iSensorIdx].aName, 2) == MATCHED )
        {
            return iSensorIdx;
        }
    }

    return -1;
}

status_t sens_atlasRead(int iSensId)
{
    _tSensor[iSensId].lReadStartTime = millis();
    return sens_atlasSendCmd(&_tSensor[iSensId], "R");
}

status_t sens_atlasInfo(int iSensId)
{
    _tSensor[iSensId].lReadStartTime = millis();
    return sens_atlasSendCmd(&_tSensor[iSensId], "I");
}

status_t sens_atlasCalibrate(int iSensId, char cParam)
{
    char aCalStr[3];

    utl_clearBuffer(aCalStr, sizeof(aCalStr[0]), 3);

    /* TODO */

    return sens_atlasSendCmd(&_tSensor[iSensId], "I");
}

status_t sens_atlasSendCmd(tSensor_t* pSensor, const char* pCmd)
{
    if (pCmd == NULL)
    {
        return STATUS_FAILED;
    }

    dbg_print(MOD_NAME, "Sent Command", pCmd);

    pSensor->aSerial.print(pCmd);
    pSensor->aSerial.print("\r");
    pSensor->aSerial.flush();

    return STATUS_OK;
}

status_t sens_turbidityRead(int iSensId)
{
    int TU_samplesize = 4;
    int TU_array[TU_samplesize];
    int TU_sum;
    float TU_average;
    float TU_value;
    int i;
    char aTuValStr[8];
    char aTuAveStr[8];
    
    /* Read the input on analog pin 0 */
    /* Puts 4 values into an array */
    for (i = 0; i < TU_samplesize; i++ )
    {
#ifdef __USE_ARDUINO__ 
        TU_array[i] = analogRead(A0);
//        Serial.print("Val#");
//        Serial.print(i);
//        Serial.print(": ");
//        Serial.println(TU_array[i]);
#else
        TU_array[i] = rand()*100;
#endif
    }
    
    // y = -5.5582101515x + 5058.2061439338
  	
    /* Sum of the values in the array   */
    for (int i = 0; i < TU_samplesize; ++i)
    {
       TU_sum += TU_array[i];
    }
    
//    Serial.print("Sum: ");
//    Serial.println(TU_sum);
    
    /* Average */
    TU_average = TU_sum / TU_samplesize;
    
//    Serial.print("Ave: ");
//    Serial.println(TU_average);
    
    /* Turbidity Equation */
    TU_value = ((-0.1766* (TU_average))+ 154.38);

//    Serial.print("Val: ");
//    Serial.println(TU_value);
    
//    /* Set the ceiling value to 999.00 */
//    if (TU_value > 999.00)
//    {
//        TU_value = 999.00;
//    }

    utl_clearBuffer(aTuValStr, sizeof(char), 8);
    dtostrf(TU_value, 5, 2, aTuValStr);
    
    utl_clearBuffer(aTuAveStr, sizeof(char), 8);
    dtostrf(TU_average, 5, 2, aTuAveStr);
    
    /* Write to buffer */
    utl_clearBuffer(_tSensor[iSensId].aBuf, sizeof(char), SENS_BUF_MAX);
    utl_strNCpy(_tSensor[iSensId].aBuf, _tSensor[iSensId].aName, 2);
    utl_strNCat(_tSensor[iSensId].aBuf, ": ", 2);
    utl_strNCat(_tSensor[iSensId].aBuf, aTuValStr, 7);
    utl_strNCat(_tSensor[iSensId].aBuf, ",", 1);
    utl_strNCat(_tSensor[iSensId].aBuf, aTuAveStr, 7);

    _tSensor[iSensId].bIsComplete = TRUE;
    sens_dataFinished(&_tSensor[iSensId]);

    return STATUS_OK;
}

status_t sens_turbidityCalibrate(int iSensId, char cParam)
{
    /* TODO: Perform turbidity calibrate operations here */
    return STATUS_OK;
}

status_t sens_turbidityInfo(int iSensId)
{
    /* TODO: Perform turbidity info operations here */
    return STATUS_OK;
}

status_t sens_temperatureRead(int iSensId)
{
  OneWire tTempSensor(_tSensor[iSensId].iPin);
  char aTempValStr[8];
  char aRawValStr[8];
  byte aData[12];
  byte aAddr[8];
  byte bType = 0;
  byte bPresent = 0;
  float fCelsius = 0.0f;
  float fFahrenheit = 0.0f;
  int16_t raw = 0;
  byte bCfg = 0;
  int i = 0;
  
  if ( !tTempSensor.search(aAddr) ) { 
      // If sensor is not connected or broken, address will not be detected
      dbg_print(MOD_NAME, "No more addresses", _tSensor[iSensId].aName);
      tTempSensor.reset_search();
      
      /* Close the sensor properly */
      utl_clearBuffer(_tSensor[iSensId].aBuf, sizeof(char), SENS_BUF_MAX);
      _tSensor[iSensId].bIsComplete = TRUE;
      sens_dataFinished(&_tSensor[iSensId]);
      
      delay(250);
      return STATUS_FAILED;
  }

  /* Check if the address is valid */    
  if (OneWire::crc8(aAddr, 7) != aAddr[7]) {
      dbg_print(MOD_NAME, "CRC is not valid", _tSensor[iSensId].aName);
      
      /* Close the sensor properly */
      utl_clearBuffer(_tSensor[iSensId].aBuf, sizeof(char), SENS_BUF_MAX);
      _tSensor[iSensId].bIsComplete = TRUE;
      sens_dataFinished(&_tSensor[iSensId]);
      
      return STATUS_FAILED;
  }
  
  /* Check the type of transistor
   *  The first ROM byte indicates which chip */
  switch (aAddr[0]) {
      case 0x10:
          // @LOG "  Chip = DS18S20"
          bType = 1;
          break;
      case 0x28:
          // @LOG "  Chip = DS18B20"
          bType = 0;
          break;
      case 0x22:
          // @LOG "  Chip = DS1822"
          bType = 0;
          break;
      default:
          dbg_print(MOD_NAME, "Device is not a DS18x20 family device.", _tSensor[iSensId].aName);
          
          /* Close the sensor properly */
          utl_clearBuffer(_tSensor[iSensId].aBuf, sizeof(char), SENS_BUF_MAX);
          _tSensor[iSensId].bIsComplete = TRUE;
          sens_dataFinished(&_tSensor[iSensId]);
      
          return STATUS_FAILED;
  }
  
  tTempSensor.reset();
  tTempSensor.select(aAddr);  // obtains address of sensor 
  tTempSensor.write(0x44, 1); // start conversion, with parasite power on at the end

  delay(1000);     // maybe 750ms is enough, maybe not
  // we might do a tTempSensor.depower() here, but the reset will take care of it.

  bPresent = tTempSensor.reset();
  tTempSensor.select(aAddr);    
  tTempSensor.write(0xBE);         // Read Scratchpad


  for ( i = 0; i < 9; i++) {         // we need 9 bytes
      aData[i] = tTempSensor.read(); // gets aData from sensor
  }

  // Convert the aData to actual temperature
  // because the result is a 16 bit signed integer, it should
  // be stored to an "int16_t" type, which is always 16 bits
  // even when compiled on a 32 bit processor.
  raw = (aData[1] << 8) | aData[0];
  if (bType) {
      raw = raw << 3; // 9 bit resolution default
      if (aData[7] == 0x10) {
          // "count remain" gives full 12 bit resolution
          raw = (raw & 0xFFF0) + 12 - aData[6];
      }
  } else {
      bCfg = (aData[4] & 0x60);
      // at lower res, the low bits are undefined, so let's zero them
      if (bCfg == 0x00) raw = raw & ~7;  // 9 bit resolution, 93.75 ms
      else if (bCfg == 0x20) raw = raw & ~3; // 10 bit res, 187.5 ms
      else if (bCfg == 0x40) raw = raw & ~1; // 11 bit res, 375 ms
      //// default is 12 bit resolution, 750 ms conversion time
  }
  fCelsius = (float)raw / 16.0; // prints out temperature
  fFahrenheit = fCelsius * 1.8 + 32.0; // converts
  
  utl_clearBuffer(aTempValStr, sizeof(char), 8);
  dtostrf(fCelsius, 5, 2, aTempValStr);
  itoa(raw, aRawValStr, 10);
  
  /* Output the temperature to the receive buffer */
//  utl_clearBuffer(_tSensor[iSensId].aBuf, sizeof(char), SENS_BUF_MAX);
//  sprintf(_tSensor[iSensId].aBuf, "%s: %s C", _tSensor[iSensId].aName, aTempValStr);

  /* Write to buffer */
  utl_clearBuffer(_tSensor[iSensId].aBuf, sizeof(char), SENS_BUF_MAX);
  utl_strNCpy(_tSensor[iSensId].aBuf, _tSensor[iSensId].aName, 2);
  utl_strNCat(_tSensor[iSensId].aBuf, ": ", 2);
  utl_strNCat(_tSensor[iSensId].aBuf, aTempValStr, 7);
  utl_strNCat(_tSensor[iSensId].aBuf, ",", 1);
  utl_strNCat(_tSensor[iSensId].aBuf, aRawValStr, 7);

  _tSensor[iSensId].bIsComplete = TRUE;
  sens_dataFinished(&_tSensor[iSensId]);
  
  return STATUS_OK;
}

status_t sens_temperatureCalibrate(int iSensId, char cParam)
{
  return STATUS_OK;
}

status_t sens_temperatureInfo(int iSensId)
{
  return STATUS_OK;
}

status_t sens_levelRead(int iSensId)
{
  int iSensVal;

  /* Read the water level from the sensor's pin */
  iSensVal = digitalRead(_tSensor[iSensId].iPin);

  utl_clearBuffer(_tSensor[iSensId].aBuf, sizeof(char), SENS_BUF_MAX);
  utl_strNCpy(_tSensor[iSensId].aBuf, _tSensor[iSensId].aName, 2);
  utl_strNCat(_tSensor[iSensId].aBuf, ": ", 2);
  if (iSensVal > 0)
  {
    utl_strNCat(_tSensor[iSensId].aBuf, "LOW", 3);
  }
  else
  {
    utl_strNCat(_tSensor[iSensId].aBuf, "HIGH", 4);
  }

  _tSensor[iSensId].bIsComplete = TRUE;
  sens_dataFinished(&_tSensor[iSensId]);

  return STATUS_OK;
}

void sens_dataFinished(tSensor_t* pSensor) {
    int iBufLen = 0;

    if ( pSensor == NULL ) {
        return;
    }

    dbg_print(MOD_NAME, "Sensor data completed", pSensor->aName);
    utl_clearBuffer(_aTxBuffer, sizeof(_aTxBuffer[0]), _iTxBufferLen);

    /* Copy into Tx Buf */
    iBufLen = utl_strLen(pSensor->aBuf);
    utl_strNCpy( _aTxBuffer, pSensor->aBuf, iBufLen );

    pSensor->lReadStartTime = 0;
    pSensor->iBufOffset = 0;
    pSensor->bIsComplete = FALSE;
    pSensor->eState = SENSOR_STARTED;

    /* Update the current known Rx Buffer Length */
    _iTxBufferLen = iBufLen;
    /* When a non-zero value is written here, the send response
        function, com_sendResponse(), will be called */

    return;
}

int sens_initSensors(void)
{
	int iIdx;

	dbg_print(MOD_NAME, "Initializing sensors...", NULL);

	for (iIdx = 1; iIdx < 4; iIdx++)
	{
		//pinMode(_tSensor[iIdx].iPin, OUTPUT);
		//digitalWrite(_tSensor[iIdx].iPin, HIGH);

		_tSensor[iIdx].aSerial.begin(38400);
	}

	return STATUS_OK;
}

#undef MOD_NAME
/******************************************************************************/
/* SEC04: Automation Modules                                                  */
/******************************************************************************/
#define MOD_NAME "AUTO"
int auto_getDeviceId(char* aStr)
{
    for (int iDeviceIdx = 1; iDeviceIdx < DEVICE_LIST_SIZE; iDeviceIdx++)
    {
        if( utl_compare(aStr, _tDevice[iDeviceIdx].aName, 2) == MATCHED )
        {
            return iDeviceIdx;
        }
    }
    
    dbg_print(MOD_NAME, "Error", "No device matched");
    return -1;
}

status_t auto_valveActivate(int iDeviceId)
{
    int iDevicePin = _tDevice[iDeviceId].iPin;

    digitalWrite(iDevicePin, HIGH);

    /* Write to buffer */
    utl_clearBuffer(_aTxBuffer, sizeof(char), SZ_TX_BUFFER);
    utl_strNCpy(_aTxBuffer, _tDevice[iDeviceId].aName, 2);
    utl_strNCat(_aTxBuffer, ": ", 2);
    utl_strNCat(_aTxBuffer, "OPENED", 6);
    _iTxBufferLen = utl_strLen(_aTxBuffer);
    
    return STATUS_OK;
}

status_t auto_valveDeactivate(int iDeviceId)
{
    int iDevicePin = _tDevice[iDeviceId].iPin;

    digitalWrite(iDevicePin, LOW);

    /* Write to buffer */
    utl_clearBuffer(_aTxBuffer, sizeof(char), SZ_TX_BUFFER);
    utl_strNCpy(_aTxBuffer, _tDevice[iDeviceId].aName, 2);
    utl_strNCat(_aTxBuffer, ": ", 2);
    utl_strNCat(_aTxBuffer, "CLOSED", 6);
    _iTxBufferLen = utl_strLen(_aTxBuffer);
    
    return STATUS_OK;
}

status_t auto_pumpActivate(int iDeviceId)
{
    int iDevicePin = _tDevice[iDeviceId].iPin;

    digitalWrite(iDevicePin, HIGH);

    _tDevice[iDeviceId].lActivatedTime = millis();

    /* Write to buffer */
    utl_clearBuffer(_aTxBuffer, sizeof(char), SZ_TX_BUFFER);
    utl_strNCpy(_aTxBuffer, _tDevice[iDeviceId].aName, 2);
    utl_strNCat(_aTxBuffer, ": ", 2);
    utl_strNCat(_aTxBuffer, "STARTED", 7);
    _iTxBufferLen = utl_strLen(_aTxBuffer);
    
    return STATUS_OK;
}

status_t auto_pumpUpdate(int iDeviceId)
{
    int iCutoff = 1;
    int iDevicePin = _tDevice[iDeviceId].iPin;
    long lTimeActivated = _tDevice[iDeviceId].lActivatedTime;
    long lElapsedTime = 0;

    /* An activated time value of zero means this device
     *  has never been activated so we can safely skip it */
    if (lTimeActivated <= 0)
    {
        return STATUS_OK;
    }

    /* Check if the elapsed time exceeds the device's timeout value */
    lElapsedTime = millis() - lTimeActivated;
    if (lElapsedTime >= _tDevice[iDeviceId].lTimeout)
    {
        dbg_print(MOD_NAME, "Info", "Pump Timeout");
        return auto_pumpDeactivate(iDeviceId);
    }

    /* Otherwise, check the water level sensor */
    iCutoff = digitalRead(WATER_LVL_MON);
    if (iCutoff <= 0)
    {
        dbg_print(MOD_NAME, "Info", "Water Level Reached");
        return auto_pumpDeactivate(iDeviceId);
    }

    return STATUS_OK;
}

status_t auto_pumpDeactivate(int iDeviceId)
{
    int iDevicePin = _tDevice[iDeviceId].iPin;

    digitalWrite(iDevicePin, LOW);

    _tDevice[iDeviceId].lActivatedTime = 0; 

    /* Write to buffer */
    utl_clearBuffer(_aTxBuffer, sizeof(char), SZ_TX_BUFFER);
    utl_strNCpy(_aTxBuffer, _tDevice[iDeviceId].aName, 2);
    utl_strNCat(_aTxBuffer, ": ", 2);
    utl_strNCat(_aTxBuffer, "STOPPED", 7);
    _iTxBufferLen = utl_strLen(_aTxBuffer);
    
    return STATUS_OK;
}

/******************************************************************************/
/* SEC05: Communication Modules                                               */
/******************************************************************************/
#define MOD_NAME "com"
/*
 * @function     com_receiveUserInput()
 * @description  Receives user input or commands from Serial
 * @returns      exit status
 */
status_t com_receiveUserInput()
{
    int iBufIdx = 0;

    #ifndef __USE_ARDUINO__
    Serial.readUserInput();
    #endif /* __USE_ARDUINO__ */

    utl_clearBuffer(_aRxBuffer, sizeof(char), SZ_RX_BUFFER);

    while (Serial.available() > 0)
    {
        if (iBufIdx > SZ_RX_BUFFER)
        {
            /* Error: Received message is too long */
            dbg_print(MOD_NAME, "Error", "Received message too long!");
            return STATUS_FAILED;
        }

        /* Read a byte from Serial into the Rx buffer */
        _aRxBuffer[iBufIdx] = Serial.read();

        iBufIdx++;
        delay(50);
    }

    /* Update the current known Rx Buffer Length */
    _iRxBufferLen = iBufIdx;

    /*printf("\n<<RECEPTION START>>\n%s\n<<RECEPTION END>>\n\n\n", _aRxBuf);*/

    return STATUS_OK;
}

/*
 * @function     com_sendResponse()
 * @description  Sends a response through Serial
 * @returns      exit status
 */
status_t com_sendResponse()
{
    int iBytesWritten;

    if (_iTxBufferLen <= 0) {
      return STATUS_OK;
    }

    dbg_print(MOD_NAME, "Sending a message...", NULL);

    iBytesWritten = Serial.write((byte*) _aTxBuffer, _iTxBufferLen);

    if (iBytesWritten != _iTxBufferLen) {
        /* Error: Whole message not transmitted */
        return STATUS_FAILED;
    }
    
    Serial.write("\n");

    /* Clear the send buffer once finished transmitting */
    utl_clearBuffer(_aTxBuffer, sizeof(_aTxBuffer[0]), _iTxBufferLen);
    _iTxBufferLen = 0;

    return STATUS_OK;
}
#undef MOD_NAME

/******************************************************************************/
/* SEC06: Timing Modules                                                      */
/******************************************************************************/
#define MOD_NAME "tmr"
status_t tmr_manageSensorTimeouts()
{
    int iSensIdx;

    for (iSensIdx = 1; iSensIdx < SENSOR_LIST_SIZE; iSensIdx++) {
        /* If the sensor state is SENSOR_READING and is not yet
            marked as complete, then check if the SENSOR_READ_TIMEOUT
            duration has been reached */
        if ( (_tSensor[iSensIdx].eState == SENSOR_READING) &&
                (_tSensor[iSensIdx].bIsComplete == FALSE) ) {
            if ( tmr_updateTimeout(&_tSensor[iSensIdx]) != STATUS_OK ) {
                dbg_print(MOD_NAME, "Error", "Update timeout failed");
            }
        }
    }
    return STATUS_OK;
}

status_t tmr_manageDeviceUpdates()
{
    int iDeviceIdx;
    status_t tRet = STATUS_FAILED;

    for (iDeviceIdx = 1; iDeviceIdx < DEVICE_LIST_SIZE; iDeviceIdx++)
    {
        if (_fDeviceUpdateFunc[iDeviceIdx] != NULL)
        {
            tRet = _fDeviceUpdateFunc[iDeviceIdx](iDeviceIdx);
            if (tRet != STATUS_OK)
            {
                dbg_print(MOD_NAME, "Warn", "Device update failed");
                continue;
            }
        }
    }
    return STATUS_OK;
}

status_t tmr_updateTimeout(tSensor_t* pSensor)
{
    long lElapsedTime = 0;

    if (pSensor == NULL) {
        dbg_print(MOD_NAME, "Error", "Invalid sensor ptr");
        return STATUS_FAILED;
    }

    if (pSensor->lReadStartTime == 0) {
        dbg_print(MOD_NAME, "Warning", "Read start time is zero");
        
        /* Force the sensor read to finish */
        utl_clearBuffer(pSensor->aBuf, sizeof(pSensor->aBuf[0]), SENS_BUF_MAX);
        pSensor->iBufOffset = 0;
        sens_dataFinished(pSensor);
        
        return STATUS_OK;
    }

    lElapsedTime = millis() - pSensor->lReadStartTime;
    if ( lElapsedTime >= SENSOR_READ_TIMEOUT ) {
        /* Force the sensor read to finish */
        utl_clearBuffer(pSensor->aBuf, sizeof(pSensor->aBuf[0]), SENS_BUF_MAX);
        pSensor->iBufOffset = 0;
        sens_dataFinished(pSensor);
        return STATUS_OK;
    }

    return STATUS_OK;
}

#undef MOD_NAME

/******************************************************************************/
/* SEC07: Utility Modules                                                     */
/******************************************************************************/
#define MOD_NAME "utl"
/*
 * @function     utl_compare()
 * @description  Wrapper function for strncmp()
 * @returns      none
 */
int utl_compare(const char* s1, const char* s2, int iLen)
{
    return ((strncmp(s1, s2, iLen) == 0) ? MATCHED : NOT_MATCHED);
}

/*
 * @function     utl_clearBuffer()
 * @description  Erases the contents of a buffer up to the specified length
 * @returns      none
 */
void utl_clearBuffer(void* pBuf, int iSize,  int iLen)
{
    memset(pBuf, 0, iLen * iSize);
}

/*
 * @function    utl_atoi()
 * @description Converts the given char string to an integer
 * @returns     Integer equivalent of the char string
 */
int utl_atoi(const char* s) 
{
    return atoi(s);
}

/*
 * @function    utl_getField()
 * @description Extracts a specific 'field' given a delimited string
 * @params      s1        - pointer to the string to be searched
 *              s2        - pointer to the string to write results to
 *              iTgtField - which field to extract
 *              cDelim    - delimiter for extracting fields
 * @returns     An integer status code 
 * @usage       Given the string testStr = "CALIB SENSOR PH 8.00" and an
 *               empty char array, outStr:
 *
 *              utl_getField(testStr, outStr, 4, ' ');    // returns "8.00" in outStr
 *              utl_getField(testStr, outStr, 3, ' ');    // returns "PH"   in outStr
 *              utl_getField("READ ALL", outStr, 2, ' '); // returns "ALL"  in outStr
 *
 * @note        For safety, the pointer s2 should point to a char array with
 *               a large _enough_ size to contain the result; otherwise,
 *               memory issues might occur
 */
int utl_getField(char* s1, char* s2, int iTgtField, char cDelim)
{
    char* c1;
    int iCurrField = 1;
    int iOutIdx = 0;

    if (s1 == NULL) {
        return STATUS_FAILED;
    }

    c1 = s1;

    while (*c1 != '\0')
    {
        if (iTgtField == iCurrField)
        {
            s2[iOutIdx] = *c1;

            iOutIdx++;
        }

        if (*c1 == cDelim)
        {
            iCurrField++;
        }

        c1++;
    }

    return STATUS_OK;
}

int utl_strLen(const char* s1)
{
    return strlen(s1);
}

int utl_strNCpy(char* pDest, const char* pSrc, int iLen)
{
    strncpy(pDest, pSrc, iLen);

    return STATUS_OK;
}

int utl_strCpy(char* pDest, const char* pSrc)
{
    strcpy(pDest, pSrc);

    return STATUS_OK;
}

int utl_strNCat(char* pDest, const char* pSrc, int iLen)
{
    strncat(pDest, pSrc, iLen);

    return STATUS_OK;
}

int utl_strCat(char* pDest, const char* pSrc)
{
    strcat(pDest, pSrc);

    return STATUS_OK;
}


#undef MOD_NAME

/******************************************************************************/
/* SEC06: Debug Modules                                                       */
/******************************************************************************/
void dbg_print(const char* pTag, const char* pMsg, const char* pExtra)
{
    if (!_debugMode)
    {
        return;
    }

    if ((pMsg == NULL) || (utl_strLen(pMsg) <= 0))
    {
        return;
    }

    Serial.print("[");
    if ((pTag == NULL) || (utl_strLen(pTag) <= 0))
    {
        Serial.print("DEBUG");
    }
    else
    {
        Serial.print(pTag);
    }
    Serial.print("] ");

    Serial.print(pMsg);

    if ((pExtra != NULL) && (utl_strLen(pExtra) > 0))
    {
        Serial.print(": ");
        Serial.print(pExtra);
    }
    Serial.print("\n");

    return;
}

void dbg_printUL(const char* pTag, const char* pMsg, unsigned long ulVal)
{
    char aLongStr[10];

    if (!_debugMode)
    {
        return;
    }

    utl_clearBuffer(aLongStr, sizeof(aLongStr[0]), 10);

    if (snprintf(aLongStr, 10, "%li", ulVal) < 0)
    {
        dbg_print(pTag, "Message Printing Error", pMsg);
        return;
    }

    dbg_print(pTag, pMsg, aLongStr);
    return;
}

void dbg_printChar(const char* pTag, const char* pMsg, char cVal)
{
    char aCharStr[10];

    if (!_debugMode)
    {
        return;
    }

    if ((cVal < 20) || (cVal > 176))
    {
        if (snprintf(aCharStr, 10, "{%d}", cVal) < 0)
        {
            dbg_print(pTag, "Message Printing Error", pMsg);
            return;
        }
    }
    else
    {
        if (snprintf(aCharStr, 10, "%c", cVal) < 0)
        {
            dbg_print(pTag, "Message Printing Error", pMsg);
            return;
        }
    }

    dbg_print(pTag, pMsg, aCharStr);
    return;
}


#endif /* __OREAD_ARDUINO_C__ */


