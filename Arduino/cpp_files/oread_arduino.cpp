#ifndef __OREAD_ARDUINO_C__
#define __OREAD_ARDUINO_C__

#define __xUSE_ARDUINO__

#ifndef __USE_ARDUINO__
#include "oread_arduino.h"
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
#define SENSOR_LIST_SIZE    5
#define SENS_BUF_MAX        32

#define DUMMY_SENSOR_ID     0

/** Type Definitions **/
/* enum for the different Sensor States */
typedef enum sensorStates {  SENSOR_STARTED,
                             SENSOR_READING,
                             SENSOR_STOPPED } sensorState_t;

/* typedef for sensor read functions */
typedef status_t (*fSensReadFunc_t)(int sensId);
/* typedef for sensor calibrate functions */
typedef status_t (*fSensCalibrateFunc_t)(int sensId, char param);
/* typedef for sensor  info */
typedef status_t (*fSensInfoFunc_t)(int sensId);

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
status_t proc_processInput(char* pMsg, int iLen);

status_t sens_atlasRead(int iSensId);
status_t sens_atlasInfo(int iSensId);
status_t sens_atlasCalibrate(int iSensId, char cParam);
status_t sens_atlasSendCmd(tSensor_t* pSensor, const char* pCmd);
void sens_dataFinished(tSensor_t* pSensor);
int sens_initSensors(void);

status_t com_receiveUserInput();
status_t com_sendResponse();

status_t tmr_manageSensorTimeouts();
status_t tmr_updateTimeout(tSensor_t* pSensor);

int utl_atoi(const char* s);
int utl_getField(char* s1, char* s2, int iTgtField, char cDelim);
int utl_compare(const char* s1, const char* s2, int iLen);
void utl_clearBuffer(void* pBuf, int iSize,  int iLen);
int utl_strLen(const char* s1);
int utl_strCpy(char* pDest, const char* pSrc, int iLen);
int utl_strCat(char* pDest, const char* pSrc, int iLen);

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
    { "TM",  9, SENSOR_STOPPED,  Serial, &aSensDataBuf[3][0], 0, FALSE, 0 }
};

/* Lookup table for the sensor read functions */
fSensReadFunc_t _fSensorReadFunc[SENSOR_LIST_SIZE] =
{
    NULL,
    sens_atlasRead,
    sens_atlasRead,
    sens_atlasRead,
    NULL /* later, sens_temperatureRead() */
};

/* Lookup table for the sensor info functions */
fSensInfoFunc_t _fSensorInfoFunc[SENSOR_LIST_SIZE] =
{
    NULL,
    sens_atlasInfo,
    sens_atlasInfo,
    sens_atlasInfo,
    NULL /* later, sens_temperatureInfo() */
};

fSensCalibrateFunc_t _fSensorCalibrateFunc[SENSOR_LIST_SIZE] =
{
    NULL,
    sens_atlasCalibrate,
    sens_atlasCalibrate,
    sens_atlasCalibrate,
    NULL /* later, sens_temperatureCalibrate() */
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

    while ((pSensor->aSerial).available() > 0)
    {
        if (iOffs == 0)
        {
            utl_clearBuffer(pSensor->aBuf, sizeof(char), SENS_BUF_MAX);
            utl_strCpy(pSensor->aBuf, pSensor->aName, 2);
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
    char cCalParam;
    char aParams[5];
    char* pCmd = NULL;

    /* Extract the Sensor Id target for this read */
    utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
    utl_getField(pMsg, aParams, 2, ' ');
    iSensId = utl_atoi(aParams);

    /* Ensure that we're not accessing invalid sensor IDs */
    if ( (iSensId <= DUMMY_SENSOR_ID) || (iSensId >= SENSOR_LIST_SIZE) ) {
        dbg_print(MOD_NAME, "Error", "Invalid Sensor Id");
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

void sens_dataFinished(tSensor_t* pSensor) {
    int iBufLen = 0;

    if ( pSensor == NULL ) {
        return;
    }

    dbg_print(MOD_NAME, "Sensor data completed", pSensor->aName);
    utl_clearBuffer(_aTxBuffer, sizeof(_aTxBuffer[0]), _iTxBufferLen);

    /* Copy into Tx Buf */
    iBufLen = utl_strLen(pSensor->aBuf);
    utl_strCpy( _aTxBuffer, pSensor->aBuf, iBufLen );

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
		pinMode(_tSensor[iIdx].iPin, OUTPUT);
		digitalWrite(_tSensor[iIdx].iPin, HIGH);

		_tSensor[iIdx].aSerial.begin(38400);
	}

	return STATUS_OK;
}

#undef MOD_NAME

/******************************************************************************/
/* SEC04: Communication Modules                                               */
/******************************************************************************/
#define MOD_NAME "com"
/*
 * @function     com_receiveUserInput()
 * @description  Receives user input or commands from Serial
 * @returns      exit status
 */
status_t com_receiveUserInput() {
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
status_t com_sendResponse() {
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

    /* Clear the send buffer once finished transmitting */
    utl_clearBuffer(_aTxBuffer, sizeof(_aTxBuffer[0]), _iTxBufferLen);
    _iTxBufferLen = 0;

    return STATUS_OK;
}
#undef MOD_NAME

/******************************************************************************/
/* SEC05: Timing Modules                                                      */
/******************************************************************************/
#define MOD_NAME "tmr"
status_t tmr_manageSensorTimeouts() {
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

status_t tmr_updateTimeout(tSensor_t* pSensor) {
    long lElapsedTime = 0;

    if (pSensor == NULL) {
        dbg_print(MOD_NAME, "Error", "Invalid sensor ptr");
        return STATUS_FAILED;
    }

    if (pSensor->lReadStartTime == 0) {
        dbg_print(MOD_NAME, "Warning", "Read start time is zero");
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
/* SEC05: Utility Modules                                                     */
/******************************************************************************/
#define MOD_NAME "utl"
/*
 * @function     utl_compare()
 * @description  Wrapper function for strncmp()
 * @returns      none
 */
int utl_compare(const char* s1, const char* s2, int iLen) {
    return ((strncmp(s1, s2, iLen) == 0) ? MATCHED : NOT_MATCHED);
}

/*
 * @function     utl_clearBuffer()
 * @description  Erases the contents of a buffer up to the specified length
 * @returns      none
 */
void utl_clearBuffer(void* pBuf, int iSize,  int iLen) {
    memset(pBuf, 0, iLen * iSize);
}

/*
 * @function    utl_atoi()
 * @description Converts the given char string to an integer
 * @returns     Integer equivalent of the char string
 */
int utl_atoi(const char* s) {
    return atoi(s);
}

/*
 * @function    utl_getField()
 * @description Extracts a specific 'field' given a delimited string
 * @returns     An integer status code
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

int utl_strCpy(char* pDest, const char* pSrc, int iLen)
{
    strncpy(pDest, pSrc, iLen);

    return STATUS_OK;
}

int utl_strCat(char* pDest, const char* pSrc, int iLen)
{
    strncat(pDest, pSrc, iLen);

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

