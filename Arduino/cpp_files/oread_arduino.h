#ifndef __OREAD_SIMPLE_H__
#define __OREAD_SIMPLE_H__
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef __USE_ARDUINO__
#include "SerialStub.h"
#endif

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

#define SENSOR_TIMEOUT		25

#define STATUS_OK		0
#define STATUS_OK_INC	1
#define STATUS_FAILED	-1

#define SENS_DATA_TERM_CHAR	'\r'

#define RETRY_COUNT		0

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

byte _aRxBuf[RX_BUF_MAX];
int _iRxBufLen = 0;
byte _aTxBuf[TX_BUF_MAX];
int _iTxBufLen = 0;

const char* taskType_tbl[9] = { 	"Unknown", 
									"User Read",
									"Sensor Read",
									"User Send",
									"Sensor Send",
									"Sensor Calibrate",
									"Sensor Activate",
									"Sensor Deactivate",
									"Debug Mode Toggle" };

int cmh_recvMessage();

int exec_sensReadTask(int iSensIdx);
int exec_sensSendTask(int iSensIdx);
int exec_sensActivateTask(int iSensIdx);
int exec_sensDeactivateTask(int iSensIdx);

void msh_handleMessage(char* pMsg);
int msh_handleSensRead(char* pMsg);
int msh_handleSensCalibrate(char* pMsg);
int msh_handleSensCmd(char* pMsg);
int msh_handleDebugMode();

int sens_initSensors(void);
int sens_activateSensor(int iSensId);
int sens_deactivateSensor(int iSensId);
int sens_send(int iSensId, char* pCmd);
int sens_read(int iSensId);

int tsk_initQueue(tTaskQueue_t* pQueue);
void tsk_displayQueue();
int tsk_addTask(tTaskQueue_t *pTaskQueue, int iTaskType, void* vParams);
int tsk_removeTask(tTaskQueue_t *pTaskQueue);

void utl_clearBuffer(void* pBuf, int iSize,  int iLen);
int utl_compare(const char* s1, const char* s2, int iLen);
int utl_strLen(const char* s1);
int utl_strCpy(char* pDest, const char* pSrc, int iLen);
int utl_atoi(const char* s);
int utl_getField(char* s1, char* s2, int iTgtField, char cDelim);

void dbg_print(const char* pTag, const char* pMsg, const char* pExtra);
void dbg_printUL(const char* pTag, const char* pMsg, unsigned long ulVal);
void dbg_printChar(const char* pTag, const char* pMsg, char cVal);

#endif /* __OREAD_SIMPLE_H__ */

