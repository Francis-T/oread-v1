#ifndef __CONTROLLER_TEST_C__
#define __CONTROLLER_TEST_C__

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
    
    /* TODO : Perform other recurring tasks for the controller here */
    
    delay(100);
}
#undef MOD_NAME

/******************************************************************************/
/* SEC02: Processing Modules                                                  */
/******************************************************************************/
#define MOD_NAME "proc"
/* 
 * @function     proc_fillWaterReservoir()
 * @description  Example function
 * @returns      exit status
 */
status_t proc_fillWaterReservoir() {
    
    /* TODO: Perform tasks to fill the water reservoir here */
    
    /* Clear the transmit buffer first prior to writing */
    utl_clearBuffer(_aTxBuffer, sizeof(char), SZ_TX_BUFFER);
    
    /* Write a response to the transmit buffer, _aTxBuffer.
     *  Basically, whatever we write here will be sent backs as our response
     *  to the initial command. */
    _iTxBufferLen = sprintf(_aTxBuffer, "OK\r\n");
    
    return STATUS_OK;
}

/* 
 * @function     proc_drainWaterReservoir()
 * @description  Example function
 * @returns      exit status
 */
status_t proc_drainWaterReservoir() {
    
    /* TODO: Perform tasks to drain the water reservoir here */
    
    /* Clear the transmit buffer first prior to writing */
    utl_clearBuffer(_aTxBuffer, sizeof(char), SZ_TX_BUFFER);
    
    /* Write a response to the transmit buffer, _aTxBuffer.
     *  Basically, whatever we write here will be sent backs as our response
     *  to the initial command. */
    _iTxBufferLen = sprintf(_aTxBuffer, "OK\r\n");
    
    return STATUS_OK;
}

/* 
 * @function     proc_processInput()
 * @description  Processes the user input or command
 * @returns      exit status
 */
status_t proc_processInput(const char* pMsg, int iLen) {
    if (iLen <= 0) {
      return STATUS_OK;
    }
  
    if (utl_compare(pMsg, "FILL RESERVOIR", 14) == MATCHED) {
        
       proc_fillWaterReservoir();
       
    } else if (utl_compare(pMsg, "DRAIN RESERVOIR", 15) == MATCHED) {
        
       proc_drainWaterReservoir();
     
    } else {
        dbg_print(MOD_NAME, "Error", "Invalid command input!");
        return STATUS_FAILED;
    }
    return STATUS_OK;
}
#undef MOD_NAME

/******************************************************************************/
/* SEC03: Communication Modules                                               */
/******************************************************************************/
#define MOD_NAME "com"
/* 
 * @function     com_receiveUserInput()
 * @description  Receives user input or commands from Serial 
 * @returns      exit status
 */
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
/* SEC04: Utility Modules                                                     */
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
#undef MOD_NAME
#endif /* __CONTROLLER_TEST_C__ */

