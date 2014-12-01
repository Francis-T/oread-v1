#ifndef __OREAD_TEST_C__
#define __OREAD_TEST_C__

#define SZ_RX_BUFFER    100
#define SZ_TX_BUFFER    100
#define EOS             '\0'
#define MATCHED         1
#define NOT_MATCHED     0
#define STATUS_OK       1
#define STATUS_FAILED   0
#define status_t        int
//typedef enum statusTypes { STATUS_OK, STATUS_FAILED } status_t;

int  _debugMode = 1;
/* Tx/Rx Buffer */
char _aRxBuffer[SZ_RX_BUFFER];
int  _iRxBufferLen = 0;
char _aTxBuffer[SZ_TX_BUFFER];
int  _iTxBufferLen = 0;

#define dbg_print(x,y,z) \
{ \
    if ( (_debugMode == 1) && (x != NULL) && (y != NULL) && (z != NULL) ) { \
      char aMessage[200]; \
      memset(aMessage, EOS, sizeof(char) * 200); \
      sprintf(aMessage, "[%s] %s: %s", x, y, z); \
      Serial.println(aMessage); \
    } \
}


/* Function declarations */
void setup();
void loop();
status_t com_receiveUserInput();
status_t proc_processInput(const char* pMsg, int iLen);
int utl_compare(const char* s1, const char* s2, int iLen);
void utl_clearBuffer(void* pBuf, int iSize,  int iLen);


/* Input:
 *  READ 0 --> "pH: [0.00 - 14.00]"
 *  READ 1 --> "DO: [0.00 - 20.00]"
 *  READ 2 --> "EC: [12880 - 50000],[0.0],[0.0]
 */
#define MOD_NAME "main"
void setup() {
    Serial.begin(9600);
    utl_clearBuffer(_aRxBuffer, sizeof(char), SZ_RX_BUFFER);
    utl_clearBuffer(_aTxBuffer, sizeof(char), SZ_TX_BUFFER);
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
    delay(100);
}
#undef MOD_NAME

/******************************************************************************/
/* SEC0X: Processing Modules                                                  */
/******************************************************************************/
#define MOD_NAME "proc"
status_t proc_writePHSensorData() {
    char aDataBuffer[6];
    
    utl_clearBuffer(aDataBuffer, sizeof(char), 6);
    sprintf(aDataBuffer, "%-2.2d.00", random(0,15));
    
    utl_clearBuffer(_aTxBuffer, sizeof(char), SZ_TX_BUFFER);
    _iTxBufferLen = sprintf(_aTxBuffer, "%s: %s\r\n", "pH", aDataBuffer);
    
    return STATUS_OK;
}

status_t proc_writeDOSensorData() {
    char aDataBuffer[6];
    
    utl_clearBuffer(aDataBuffer, sizeof(char), 6);
    sprintf(aDataBuffer, "%-2.2d.00", random(0,21));
    
    utl_clearBuffer(_aTxBuffer, sizeof(char), SZ_TX_BUFFER);
    _iTxBufferLen = sprintf(_aTxBuffer, "%s: %s\r\n", "DO", aDataBuffer);
    
    return STATUS_OK;
}


status_t proc_writeECSensorData() {
    char aDataBuffer[6];
    
    utl_clearBuffer(aDataBuffer, sizeof(char), 6);
    sprintf(aDataBuffer, "%-2.2d000", random(13,51));
    
    utl_clearBuffer(_aTxBuffer, sizeof(char), SZ_TX_BUFFER);
    _iTxBufferLen = sprintf(_aTxBuffer, "%s: %s,%s,%s\r\n", "EC", aDataBuffer, "0", "0");
    
    return STATUS_OK;
}

status_t proc_processInput(const char* pMsg, int iLen) {
    if (iLen <= 0) {
      return STATUS_OK;
    }
  
    if (utl_compare(pMsg, "READ", 4) == MATCHED) {
        switch (pMsg[5]) {
            case '0':
                /* Return pH sensor reading */
                if (proc_writePHSensorData() != STATUS_OK) {
                    dbg_print(MOD_NAME, "Error", "Cannot read pH sensor!");
                    return STATUS_FAILED;
                }
                break;
            case '1':
                /* Return DO sensor reading */
                if (proc_writeDOSensorData() != STATUS_OK) {
                    dbg_print(MOD_NAME, "Error", "Cannot read DO sensor!");
                    return STATUS_FAILED;
                }
                break;
            case '2':
                /* Return EC sensor reading */
                if (proc_writeECSensorData() != STATUS_OK) {
                    dbg_print(MOD_NAME, "Error", "Cannot read EC sensor!");
                    return STATUS_FAILED;
                }
                break;
            default:
                dbg_print(MOD_NAME, "Error","Invalid sensor ID value!");
                break;
        }
    } else {
        dbg_print(MOD_NAME, "Error", "Invalid command input!");
        return STATUS_FAILED;
    }
    return STATUS_OK;
}
#undef MOD_NAME

/******************************************************************************/
/* SEC0X: Communication Modules                                               */
/******************************************************************************/
#define MOD_NAME "com"
status_t com_receiveUserInput() {
	int iBufIdx = 0;
	
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

status_t com_sendResponse() {
	int iBytesWritten;

        if (_iTxBufferLen <= 0) {
          return STATUS_OK;
        }
        
	//dbg_print(MOD_NAME, "Sending a message...", NULL);
	
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
/* SEC0X: Utility Modules                                                     */
/******************************************************************************/
#define MOD_NAME "utl"
int utl_compare(const char* s1, const char* s2, int iLen)
{
	return ((strncmp(s1, s2, iLen) == 0) ? MATCHED : NOT_MATCHED);
}

void utl_clearBuffer(void* pBuf, int iSize,  int iLen)
{
	memset(pBuf, 0, iLen * iSize);
}
#undef MOD_NAME
#endif /* __OREAD_TEST_C__ */

