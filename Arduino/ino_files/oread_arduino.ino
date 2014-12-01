#ifndef __OREAD_CPP__
#define __OREAD_CPP__
#define __USE_ARDUINO__

#ifndef __USE_ARDUINO__
#include "oread_arduino.h"
#else
/** ************************************************************************ **/
/** ****************************** ARDUINO HEADER ************************** **/
/** ************************************************************************ **/
#ifndef TRUE
#define TRUE				1
#endif

#ifndef FALSE
#define FALSE				0
#endif

#define TASK_QUEUE_SIZE		5
#define SENSOR_LIST_SIZE	3

#define SENS_NAME_LEN_MAX	10
#define SENS_BUF_MAX		32
#define RX_BUF_MAX	 		100
#define TX_BUF_MAX	 		512

#define SENSOR_TIMEOUT		5

#define STATUS_OK		0
#define STATUS_OK_INC	1
#define STATUS_FAILED	-1

#define SENS_DATA_TERM_CHAR	'\r'

#define RETRY_COUNT		5

typedef enum taskTypes {	TASK_UNKNOWN,
							TASK_USER_READ,
							TASK_SENS_READ,
							TASK_USER_SEND,
							TASK_SENS_SEND,
							TASK_SENS_CALIB,
							TASK_SENS_ACTIVATE,
							TASK_SENS_DEACTIVATE,
							TASK_DEBUG_MODE_TOGGLE } taskType_t;
				
typedef enum sensorStates {	SENSOR_STARTED,
					 		SENSOR_READING,
					 		SENSOR_STOPPED } sensorState_t;

typedef struct queueTask
{
	int iTaskType;
	void* vParams;
	struct queueTask *pNext;
} tQueueTask_t;

typedef struct taskQueue
{
	tQueueTask_t* pBegin;
	int iLen;
} tTaskQueue_t;

typedef struct sensorInfo
{
	const char* aName;
	int iPin;
	sensorState_t eState;
	HardwareSerial aSerial;
	char* aBuf;
	int iBufOffset;
	int bIsComplete;
	char* aCalCmdStr;
} tSensor_t;

char _aRxBuf[RX_BUF_MAX];
int _iRxBufLen = 0;
char _aTxBuf[TX_BUF_MAX];
int _iTxBufLen = 0;

char* taskType_tbl[9] = { 	"Unknown", 
							"User Read",
							"Sensor Read",
							"User Send",
							"Sensor Send",
							"Sensor Calibrate",
							"Sensor Activate",
							"Sensor Deactivate",
							"Debug Mode Toggle" };

int cmh_recvMessage();

void msh_handleMessage(char* pMsg);
int msh_handleSensRead(char* pMsg);
int msh_handleSensCalibrate(char* pMsg);
int msh_handleSensCmd(char* pMsg);

int sens_initSensors(void);
int sens_activateSensor(int iSensId);
int sens_deactivateSensor(int iSensId);
int sens_send(int iSensId, char* pCmd);
int sens_read(int iSensId);

int tsk_initQueue(tTaskQueue_t* pQueue);
void tsk_displayQueue(tTaskQueue_t* pQueue);
int tsk_addTask(tTaskQueue_t *pTaskQueue, int iTaskType, void* vParams);
int tsk_removeTask(tTaskQueue_t *pTaskQueue);

void utl_clearBuffer(void* pBuf, int iSize,  int iLen);
int utl_compare(const char* s1, const char* s2, int iLen);
int utl_strLen(const char* s1);
int utl_strCpy(char* pDest, const char* pSrc, int iLen);
int utl_atoi(const char* s);
int utl_getField(char* s1, char* s2, int iTgtField, char cDelim);
/** ************************************************************************ **/
/** *********************** END OF ARDUINO HEADER ************************** **/
/** ************************************************************************ **/
void setup();
void loop();
void serialEventRead(tSensor_t* pSensor, const char* aSerialName);

void dbg_print(const char* pTag, const char* pMsg, const char* pExtra);
void dbg_printUL(const char* pTag, const char* pMsg, unsigned long ulVal);
void dbg_printChar(const char* pTag, const char* pMsg, char cVal);
#endif

unsigned long lLastCommandStart = 0;
int _iSensTimeout = SENSOR_TIMEOUT;
int _iRetryCount = RETRY_COUNT;
int _bIsDebugMode = FALSE;

char aSensDataBuf[SENSOR_LIST_SIZE][SENS_BUF_MAX];
tQueueTask_t _tTasks[TASK_QUEUE_SIZE];
tTaskQueue_t _tTaskQueue;
tSensor_t _tSensor[SENSOR_LIST_SIZE] = 
{
	{ "pH", 10, SENSOR_STOPPED, Serial1, &aSensDataBuf[0][0], 0, FALSE, "S F T R" },
	{ "DO", 11, SENSOR_STOPPED, Serial2, &aSensDataBuf[1][0], 0, FALSE, "M R" },
	{ "EC", 12, SENSOR_STOPPED, Serial3, &aSensDataBuf[2][0], 0, FALSE, "Z30 Z2 R" }
};

void setup()
{
	Serial.begin(9600);
	
	utl_clearBuffer(_tTasks, sizeof(tQueueTask_t), TASK_QUEUE_SIZE);
	
	tsk_initQueue(&_tTaskQueue);
	
	sens_initSensors();
}

#define FUNC_NAME "execTask"
int execTask(int iTaskType, void* vParams)
{
	#ifdef __USE_ARDUINO__
	if (iTaskType != TASK_USER_READ)
	{
	#endif
		dbg_print(FUNC_NAME, "Executing Task", taskType_tbl[iTaskType]);
		lLastCommandStart = millis();
	#ifdef __USE_ARDUINO__
	}
	#endif
	
	switch(iTaskType)
	{
		case TASK_USER_READ:
			cmh_recvMessage();
			
			# ifdef __USE_ARDUINO__
			/** Possibly applicable to non-arduino as well **/
			if (_iRxBufLen <= 0)
			{
				break;
			}
			#endif
			dbg_print(FUNC_NAME, "Receiving User Message", _aRxBuf);
			
			msh_handleMessage(_aRxBuf);
			break;
		case TASK_SENS_READ:
			if (exec_sensReadTask(*((int*) vParams)) != STATUS_OK)
			{
				dbg_print(FUNC_NAME, "Sensor Read Task Failed!", NULL);
			}
			break;
		case TASK_USER_SEND:
			break;
		case TASK_SENS_SEND:
			if (exec_sensSendTask(*((int*) vParams)) != STATUS_OK)
			{
				dbg_print(FUNC_NAME, "Sensor Send Task Failed!", NULL);
			}
			break;
		case TASK_SENS_ACTIVATE:
			if (exec_sensActivateTask(*((int*) vParams)) != STATUS_OK)
			{
				dbg_print(FUNC_NAME, "Sensor Activation Failed!", NULL);
			}
			break;
		case TASK_SENS_DEACTIVATE:
			if (exec_sensDeactivateTask(*((int*) vParams)) != STATUS_OK)
			{
				dbg_print(FUNC_NAME, "Sensor Deactivation Failed!", NULL);
			}
			break;
		case TASK_DEBUG_MODE_TOGGLE:
			_bIsDebugMode = !_bIsDebugMode;
			printf("[%s] Debug Mode Set: %s\n", FUNC_NAME, 
					((_bIsDebugMode > 0) ? "ON" : "OFF" ));
			break;
		default:
			break;
	}
	
	#ifdef __USE_ARDUINO__
	if ((iTaskType != TASK_USER_READ) || (_iRxBufLen > 0))
	{
	#endif
			dbg_printUL(FUNC_NAME, "Command Execution Time", 
								   (millis() - lLastCommandStart));
	#ifdef __USE_ARDUINO__
	}
	#endif
		
	return STATUS_OK;
}
#undef FUNC_NAME

void loop()
{
	/* Check if queue contains any tasks */
	if (_tTaskQueue.pBegin == NULL)
	{
		/* If not, then add a READ task */
		tsk_addTask(&_tTaskQueue, TASK_USER_READ, NULL);
	}
	
	/* Execute a task from the queue */
	if (execTask((_tTaskQueue.pBegin)->iTaskType,
				 (_tTaskQueue.pBegin)->vParams) == STATUS_OK)
	{
		tsk_removeTask(&_tTaskQueue);
	}
	
	/* Sanity delay */
	delay(100);
}

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
	
	return;
}

void serialEvent1()
{
	const int iSensIdx = 0;	/* Sensor Index should match SerialN */
	serialEventRead(&_tSensor[iSensIdx], "serialEvent1");
	return;
}

void serialEvent2()
{
	const int iSensIdx = 1;	/* Sensor Index should match SerialN */
	serialEventRead(&_tSensor[iSensIdx], "serialEvent2");
	return;
}

void serialEvent3()
{
	const int iSensIdx = 2;	/* Sensor Index should match SerialN */
	serialEventRead(&_tSensor[iSensIdx], "serialEvent3");
	return;
}

/** Task Execution Functions **/
#define FUNC_NAME "exec_sensReadTask"
int exec_sensReadTask(int iSensIdx)
{
	int *pParam;
	int iRet = STATUS_FAILED;
	
	if ((iSensIdx < 0) || (iSensIdx >= SENSOR_LIST_SIZE))
	{
		dbg_print(FUNC_NAME, "Error", "No such sensor index!");
		
		return STATUS_FAILED;
	}
	
	dbg_print(FUNC_NAME, "Reading from sensor", _tSensor[iSensIdx].aName);
	
	/* Check the sensor state first */
	/* If already started, then we can proceed with reading */
	/* Otherwise, we must activate it first */
	if (_tSensor[iSensIdx].eState == SENSOR_STOPPED)
	{
		dbg_print(FUNC_NAME, "Info", "Activating sensor first...");
		pParam = (int*) malloc(sizeof(int));
		*pParam = iSensIdx;
		tsk_addTask(&_tTaskQueue, TASK_SENS_ACTIVATE, pParam);
		
		pParam = (int*) malloc(sizeof(int));
		*pParam = iSensIdx;
		tsk_addTask(&_tTaskQueue, TASK_SENS_READ, pParam);
		
		return STATUS_OK;
	}
	
	iRet = sens_read(iSensIdx);
	if (iRet == STATUS_OK_INC)
	{
		dbg_print(FUNC_NAME, "Partial sensor read", NULL);
		pParam = (int*) malloc(sizeof(int));
		*pParam = iSensIdx;
		tsk_addTask(&_tTaskQueue, TASK_SENS_READ, pParam);
	
		dbg_print(FUNC_NAME, "Sensor Data", _aRxBuf);
		
		return STATUS_OK;
	}
	else if (iRet == STATUS_FAILED)
	{
		dbg_print(FUNC_NAME, "Error", "Sensor Read Failed!");
		if (_iRetryCount > 0)
		{
			dbg_print(FUNC_NAME, "Resending last transmission...", NULL);
			if (sens_send(iSensIdx, _aTxBuf) != STATUS_OK)
			{
				dbg_print(FUNC_NAME, "Error", "Failed to resend transmission!");
			}
			delay(250);

			_iRetryCount--;

			pParam = (int*) malloc(sizeof(int));
			*pParam = iSensIdx;
			tsk_addTask(&_tTaskQueue, TASK_SENS_READ, pParam);

			return STATUS_OK;
		}
		_iRetryCount = RETRY_COUNT;
		dbg_print(FUNC_NAME, "Error", "No retries left!");
	}
	else
	{			
		dbg_print(FUNC_NAME, "Sensor Data", _aRxBuf);
	}

	pParam = (int*) malloc(sizeof(int));
	*pParam = iSensIdx;
	tsk_addTask(&_tTaskQueue, TASK_SENS_DEACTIVATE, pParam);
			
	return STATUS_OK;
}
#undef FUNC_NAME

#define FUNC_NAME "exec_sensSendTask"
int exec_sensSendTask(int iSensIdx)
{
	int *pParam;
	
	if ((iSensIdx < 0) || (iSensIdx >= SENSOR_LIST_SIZE))
	{
		dbg_print(FUNC_NAME, "Error", "No such sensor index!");
		return STATUS_FAILED;
	}


	dbg_print(FUNC_NAME, "Sending command to sensor", _tSensor[iSensIdx].aName);

	/* Check the sensor state first
	 * If already started, then we can proceed with sending
	 * the command. Otherwise, we must activate it first */
	if (_tSensor[iSensIdx].eState == SENSOR_STOPPED)
	{
		dbg_print(FUNC_NAME, "Info", "Activating sensor first...");
		pParam = (int*) malloc(sizeof(int));
		*pParam = iSensIdx;
		tsk_addTask(&_tTaskQueue, TASK_SENS_ACTIVATE, pParam);
	
		pParam = (int*) malloc(sizeof(int));
		*pParam = iSensIdx;
		tsk_addTask(&_tTaskQueue, TASK_SENS_SEND, pParam);
	
		return STATUS_OK;
	}

	if (sens_send(iSensIdx, _aTxBuf) == STATUS_OK)
	{
		pParam = (int*) malloc(sizeof(int));
		*pParam = iSensIdx;
		tsk_addTask(&_tTaskQueue, TASK_SENS_READ, pParam);
	
		if (_aTxBuf[0] == 'R')
		{
			_tSensor[iSensIdx].eState = SENSOR_READING;
		}
	}
	
	return STATUS_OK;
}
#undef FUNC_NAME

#define FUNC_NAME "exec_sensActivateTask"
int exec_sensActivateTask(int iSensIdx)
{
	sens_activateSensor(iSensIdx);
	dbg_print(FUNC_NAME, "Activated Sensor", _tSensor[iSensIdx].aName);
	return STATUS_OK;
}
#undef FUNC_NAME

#define FUNC_NAME "exec_sensDeactivateTask"
int exec_sensDeactivateTask(int iSensIdx)
{
	sens_deactivateSensor(iSensIdx);
	dbg_print(FUNC_NAME, "Deactivated Sensor", _tSensor[iSensIdx].aName);
	return STATUS_OK;
}
#undef FUNC_NAME

/** Communication Functions **/
#define MOD_NAME "cmh"
int cmh_recvMessage()
{
	int iBufIdx = 0;
	
	#ifndef __USE_ARDUINO__
	Serial.readUserInput();
	#endif /* __USE_ARDUINO__ */
	utl_clearBuffer(_aRxBuf, sizeof(char), RX_BUF_MAX);
	
	while (Serial.available() > 0)
	{
		if (iBufIdx > RX_BUF_MAX)
		{
			/* Error: Received message is too long */
			dbg_print(MOD_NAME, "Error", "Received message too long!");
			return STATUS_FAILED;
		}
		
		/* Read a byte from Serial into the Rx buffer */
		_aRxBuf[iBufIdx] = Serial.read();
		
		iBufIdx++;
		delay(50);
	}
	
	/* Update the current known Rx Buffer Length */
	_iRxBufLen = iBufIdx;
	
	/*printf("\n<<RECEPTION START>>\n%s\n<<RECEPTION END>>\n\n\n", _aRxBuf);*/
	
	return STATUS_OK;
}

int cmh_sendMessage()
{
	int iBytesWritten;
	
	dbg_print(MOD_NAME, "Sending a message...", NULL);
	
	iBytesWritten = Serial.write((byte*) _aTxBuf, _iTxBufLen);
	
	if (iBytesWritten != _iTxBufLen)
	{
		/* Error: Whole message not transmitted */
		return STATUS_FAILED;
	}
	
	/* Clear the send buffer once finished transmitting */
	utl_clearBuffer(_aTxBuf, sizeof(_aTxBuf[0]), _iTxBufLen);
	_iTxBufLen = 0;
	
	
	return STATUS_OK;
}
#undef MOD_NAME

/** Sensor Functions **/

#define MOD_NAME "sens"
int sens_initSensors(void)
{
	int iIdx;
	
	dbg_print(MOD_NAME, "Initializing sensors...", NULL);

	for (iIdx = 0; iIdx < SENSOR_LIST_SIZE; iIdx++)
	{
		pinMode(_tSensor[iIdx].iPin, OUTPUT);
		digitalWrite(_tSensor[iIdx].iPin, HIGH);
		
		_tSensor[iIdx].aSerial.begin(38400);
	}
	
	return STATUS_OK;
}
int sens_activateSensor(int iSensId)
{
	/* Power up the chosen sensor by triggering the transistor switch */
	/* digitalWrite(_tSensor[iSensId].iPin, HIGH);
	 *
	 * delay(1000);
	 */

	_tSensor[iSensId].eState = SENSOR_STARTED;
	
	return STATUS_OK;	
}

int sens_deactivateSensor(int iSensId)
{	
	/* Power down the chosen sensor by triggering the transistor switch */
	/* digitalWrite(_tSensor[iSensId].iPin, LOW);
	 *
	 * delay(250);
	 */

	_tSensor[iSensId].eState = SENSOR_STOPPED;
	
	return STATUS_OK;
}

int sens_send(int iSensId, char* pCmd)
{	
	if (pCmd == NULL)
	{
		return STATUS_FAILED;
	}
	
	dbg_print(MOD_NAME, "Sent Command", pCmd);
	
	_tSensor[iSensId].aSerial.print(pCmd);
	_tSensor[iSensId].aSerial.print("\r");
	_tSensor[iSensId].aSerial.flush();
	
	return STATUS_OK;
}

int sens_read(int iSensId)
{
	int iBufIdx = 0;
	
	if (_tSensor[iSensId].bIsComplete)
	{
		dbg_print(MOD_NAME, "Sensor data completed", _tSensor[iSensId].aName);
		utl_clearBuffer(_aRxBuf, sizeof(_aRxBuf[0]), _iRxBufLen);
		
		/* Copy into Rx Buf */
		utl_strCpy(_aRxBuf,
				   _tSensor[iSensId].aBuf,
				   utl_strLen(_tSensor[iSensId].aBuf));
				   
		_tSensor[iSensId].iBufOffset = 0;
		_tSensor[iSensId].bIsComplete = FALSE;
		
		/* Update the current known Rx Buffer Length */
		_iRxBufLen = iBufIdx;
		
		/* Display the data to the user */
		Serial.println(_aRxBuf);
		
		return STATUS_OK;
	}
	_iSensTimeout--;
	
	if (_iSensTimeout <= 0)
	{
		dbg_print(MOD_NAME, "Sensor timeout occurred", _tSensor[iSensId].aName);
		
		_iSensTimeout = SENSOR_TIMEOUT;
		
		return STATUS_FAILED;
	}
	
	delay(250);
	
	return STATUS_OK_INC;
}
#undef MOD_NAME

#ifndef __USE_ARDUINO__
int _iLoopCountdown = 500;
/** C's Main Function -- for off-device testing **/
int main(void)
{
	Serial.set_index(0);
	_tSensor[0].aSerial.set_index(1);
	_tSensor[1].aSerial.set_index(2);
	_tSensor[2].aSerial.set_index(3);

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

/** Message Handling Functions **/
#define MOD_NAME "msh"
void msh_handleMessage(char* pMsg)
{
	if (utl_compare(pMsg, "QUIT", 4) == 0)
	{
		#ifndef __USE_ARDUINO__
		_iLoopCountdown = 0;
		#endif
	}
	else if (utl_compare(pMsg, "READ", 4) == 0)
	{
		dbg_print(MOD_NAME, "Handling READ message...", NULL);
		msh_handleSensRead(pMsg);
	}
	else if (utl_compare(pMsg, "CALIBRATE", 9) == 0)
	{
		dbg_print(MOD_NAME, "Handling CALIBRATE message...", NULL);
		msh_handleSensCalibrate(pMsg);
	}
	else if (utl_compare(pMsg, "CMD", 3) == 0)
	{
		dbg_print(MOD_NAME, "Handling COMMAND message...", NULL);
		msh_handleSensCmd(pMsg);
	}
	else if (utl_compare(pMsg, "DEBUG", 3) == 0)
	{
		dbg_print(MOD_NAME, "Handling DEBUG message...", NULL);
		msh_handleDebugMode();
	}
	
	return;
}

int msh_handleSensRead(char* pMsg)
{
	int* pSensId;
	char aParams[5];
	
	utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
	
	utl_getField(pMsg, aParams, 2, ' ');
	
	pSensId = (int*) malloc(sizeof(int));
	*pSensId = utl_atoi(aParams);
	
	utl_strCpy(_aTxBuf, "R", 1);
	
	dbg_print(MOD_NAME, "Send Buffer Contents", _aTxBuf);
	tsk_addTask(&_tTaskQueue, TASK_SENS_SEND, pSensId);
	
	return STATUS_OK;
}


int msh_handleSensCalibrate(char* pMsg)
{
	int* pSensId;
	int* pCalCmdId;
        int iCmdLen;
	char aCalCmd[3];
	char aParams[5];
	
	utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
	
	/* Get the Sensor Id target */
	utl_getField(pMsg, aParams, 2, ' ');
	pSensId = (int*) malloc(sizeof(int));
	*pSensId = utl_atoi(aParams);
	
	/* Get the Calibration Command target */
	utl_getField(pMsg, aParams, 3, ' ');
	pCalCmdId = (int*) malloc(sizeof(int));
	*pCalCmdId = utl_atoi(aParams);
	
	if ((*pSensId < 0) || (*pSensId >= SENSOR_LIST_SIZE))
	{
		dbg_print(MOD_NAME, "Error", "No such sensor index!");
		return STATUS_FAILED;
	}

	utl_clearBuffer(aParams, sizeof(aParams[0]), 5);
	utl_getField(_tSensor[*pSensId].aCalCmdStr, aParams, *pCalCmdId, ' ');
        iCmdLen = utl_strLen(aParams);
	utl_strCpy(_aTxBuf, aParams, iCmdLen);
	
	dbg_print(MOD_NAME, "Send Buffer Contents", _aTxBuf);
	tsk_addTask(&_tTaskQueue, TASK_SENS_SEND, pSensId);
	
	return STATUS_OK;
}


int msh_handleSensCmd(char* pMsg)
{
	int* pSensId;
	char aParams[32];
	char aCmdStr[16];
	
	utl_clearBuffer(aParams, sizeof(aParams[0]), 32);
	utl_clearBuffer(aCmdStr, sizeof(aCmdStr[0]), 16);
	
	utl_getField(pMsg, aParams, 2, ' ');
	utl_getField(pMsg, aCmdStr, 3, ' ');
	dbg_print(MOD_NAME, "Command String", aCmdStr);
	
	pSensId = (int*) malloc(sizeof(int));
	*pSensId = utl_atoi(aParams);
	
	utl_strCpy(_aTxBuf, aCmdStr, utl_strLen(aCmdStr));

	dbg_print(MOD_NAME, "Send Buffer Contents", _aTxBuf);
	tsk_addTask(&_tTaskQueue, TASK_SENS_SEND, pSensId);
	
	return STATUS_OK;
}

int msh_handleDebugMode()
{
	tsk_addTask(&_tTaskQueue, TASK_DEBUG_MODE_TOGGLE, NULL);
	return STATUS_OK;
}

#undef MOD_NAME

/** Task Queue Functions **/
#define MOD_NAME "tsk"
int tsk_initQueue(tTaskQueue_t* pQueue)
{
	utl_clearBuffer(pQueue, sizeof(tTaskQueue_t), 1);
	
	return STATUS_OK;
}

void tsk_displayQueue(tTaskQueue_t* pQueue)
{
	int i = 0;
	tQueueTask_t* pTask = pQueue->pBegin;
	
	printf("\n");
	
	while(pTask != NULL)
	{
		printf("Task #%d: %s\n", i, taskType_tbl[pTask->iTaskType]);
		pTask = pTask->pNext;
		i++;
	}
}

int tsk_addTask(tTaskQueue_t *pTaskQueue, int iTaskType, void* vParams)
{
	int i;
	tQueueTask_t* pTask;
	
	if (pTaskQueue->iLen >= TASK_QUEUE_SIZE)
	{
		dbg_print(MOD_NAME, "Error", "Queue full.");
		return STATUS_OK;
	}
	
	if (pTaskQueue->iLen < 0)
	{
		dbg_print(MOD_NAME, "Error", "Invalid number of tasks!");
		return STATUS_FAILED;
	}
	
	for (i = 0; i < TASK_QUEUE_SIZE; i++)
	{
		if (_tTasks[i].iTaskType == 0)
		{
			break;
		}
	}
	
	if (i >= TASK_QUEUE_SIZE)
	{
		dbg_print(MOD_NAME, "Error", "Unable to create new task!");
	}
	
	_tTasks[i].iTaskType = iTaskType;
	_tTasks[i].vParams = vParams;
	
	if (pTaskQueue->pBegin == NULL)
	{
		pTaskQueue->pBegin = &_tTasks[i];
	}
	else
	{
		pTask = pTaskQueue->pBegin;
		while(pTask != NULL)
		{
			if (pTask->pNext == NULL)
			{
				pTask->pNext = &(_tTasks[i]);
				break;
			}
			
			pTask = pTask->pNext;
		}
	}
	
	pTaskQueue->iLen++;
	
	return STATUS_OK;
}

int tsk_removeTask(tTaskQueue_t *pTaskQueue)
{
	/* NOTE: This will always remove the first task only */
	tQueueTask_t* pNewBegin;
	
	if (pTaskQueue->pBegin == NULL)
	{
		dbg_print(MOD_NAME, "Error", "Task queue is already empty!");
		return STATUS_FAILED;
	}
	
	pNewBegin = (pTaskQueue->pBegin)->pNext;
	
	if (((pTaskQueue->pBegin)->vParams) != NULL)
	{
		free((pTaskQueue->pBegin)->vParams);
	}

	utl_clearBuffer(pTaskQueue->pBegin, sizeof(tQueueTask_t), 1);
	
	pTaskQueue->pBegin = pNewBegin;
	
	if(pTaskQueue->iLen > 0)
	{	
		pTaskQueue->iLen--;
	}
	
	return STATUS_OK;
}
#undef MOD_NAME

/** Utility Functions **/
#define MOD_NAME "utl"
void utl_clearBuffer(void* pBuf, int iSize,  int iLen)
{
	memset(pBuf, 0, iLen * iSize);
}

int utl_compare(const char* s1, const char* s2, int iLen)
{
	
	return strncmp(s1, s2, iLen);
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

int utl_atoi(const char* s)
{
	return atoi(s);
}

int utl_getField(char* s1, char* s2, int iTgtField, char cDelim)
{
	char* c1;
	int iCurrField = 1;
	int iOutIdx = 0;
	
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
#undef MOD_NAME

/** Debug Mode Functions **/
void dbg_print(const char* pTag, const char* pMsg, const char* pExtra)
{
	if (!_bIsDebugMode)
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
	
	if (!_bIsDebugMode)
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
	
	if (!_bIsDebugMode)
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

#endif /* __OREAD_CPP__ */


