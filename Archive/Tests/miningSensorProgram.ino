#define NULL_CHAR        '\0'
#define CAR_RET_CHAR    '\r'
#define FIRST_SENS_IDX  '0'
#define LAST_SENS_IDX   '2'
#define LOOP_INTERVAL    250
#define RECV_BUF_LEN    100
#define USER_BUF_LEN    100
#define NO_SENSOR        -1

typedef enum State {
    STATE_UNKNOWN,
    STATE_READ_TARGET_SENSOR,
    STATE_READ_SENSOR_COMMAND,
    STATE_BUSY
} State_t;

typedef struct tSensorMapping {
    const char* sensorName;
    HardwareSerial hwSerial;
} Sensor_t;

/* Sensor Mapping Array */
Sensor_t _sensorMap[3] = 
{
    {"pH", Serial1},
    {"DO", Serial2},
    {"EC", Serial3}
};

/* Global Variables */
State_t _programState       = STATE_UNKNOWN;
int     _sensorTarget       = NO_SENSOR;
boolean _shouldShowMessage  = true;
char    _recvBuffer[RECV_BUF_LEN];
char    _userBuffer[USER_BUF_LEN];
int     _userBufLen = 0;

/** ************************************************************************ **/
/** Section 01: Standard Arduino Functions                                   **/
/** ************************************************************************ **/
void setup() {
    Serial.begin(9600);
    
    initializeSensors();
}

void loop() {
    if ( _shouldShowMessage == true ) {
        showProgramMessage();
        _shouldShowMessage = false;
    }
    readUserInput();
    processUserInput();
    delay(100);
}

// Function: serialEvent1()
// - Receives events from the pH sensor (id = 0)
//
void serialEvent1() {
    serialEventRead(_sensorMap[0].sensorName, _sensorMap[0].hwSerial);
}

// Function: serialEvent2()
// - Receives events from the Dissolved Oxygen sensor (id = 1)
//
void serialEvent2() {
    serialEventRead(_sensorMap[1].sensorName, _sensorMap[1].hwSerial);
}

// Function: serialEvent3()
// - Receives events from the Conductivity sensor (id = 2)
//
void serialEvent3() {
    serialEventRead(_sensorMap[2].sensorName, _sensorMap[2].hwSerial);
}

/** ************************************************************************ **/
/** Section 02: Internal Functions                                           **/
/** ************************************************************************ **/
// Function: initializeSensors()
// - Initializes all sensors
//
void initializeSensors() {
    Serial1.begin(38400);
    Serial2.begin(38400);
    Serial3.begin(38400);
    
    Serial.println("Sensors Initialized.");
}

// Function: showProgramMessage()
// - Displays a message for each program state
//
void showProgramMessage() {
    switch (_programState) {
        case STATE_UNKNOWN:
            Serial.println("Initializing, please wait...");
            break;
        case STATE_READ_TARGET_SENSOR:
            Serial.println("Choose a target sensor (0 = pH, 1 = DO, 2 = EC): ");
            break;
        case STATE_READ_SENSOR_COMMAND:
            Serial.println("Enter command to be sent: ");
            break;
        case STATE_BUSY:
            Serial.println("Device Busy...");
            break;
        default:
            Serial.println("Error: Invalid Program State!");
            break;
    }
    return;    
}

// Function: readUserInput()
// - Reads user input from Serial
// 
void readUserInput() {
    int offset = 0;
    char readChar = NULL_CHAR;
    
    if ( Serial.available() <= 0 ) {
        return;
    }
    
    while ( Serial.available() > 0 ) {
        if (offset == 0) {
            clearBuffer(_userBuffer, USER_BUF_LEN);
            _userBufLen = 0;
        }

        /* Check if the next read will overflow the user buffer */
        if ( (offset + 1) >= USER_BUF_LEN ) {
            _userBuffer[offset] = NULL_CHAR;
            break;
        }
    
        readChar = Serial.read();    
        
        /* If '\r' is received, then break the loop early too */
        if (readChar == CAR_RET_CHAR) {
            _userBuffer[offset] = NULL_CHAR;
            break;
        }        
        
        /* Skip non-human-readable chars */
        if ( isReadableChar(readChar) == false ) {
            continue;
        }
        
        /* Write the char to the buffer and increase the offset */
        _userBuffer[offset] = readChar;
        offset++;
        
        readChar = NULL_CHAR;
        
        delay(50);
    }
    
    /* Save the length of the user input string */
    _userBufLen = strlen(_userBuffer);
    
    return;
}

// Function: processUserInput()
// - Interprets the user input based on the state of the program
// - Cycle:
//      STATE_UNKNOWN ---> STATE_READ_TARGET_SENSOR
//            ^                       |
//            |                       V
//            +--------- - STATE_READ_SENSOR_COMMAND
//
void processUserInput() {
    switch (_programState) {
        case STATE_UNKNOWN:
            _programState = STATE_READ_TARGET_SENSOR;
            _shouldShowMessage = true;
            break;
        case STATE_READ_TARGET_SENSOR:
            if ( _userBufLen <= 0 ) {
                /* Nothing read yet */
                break;
            }
            /* Read the user's input as the sensor ID */
            _sensorTarget = _userBuffer[0] - FIRST_SENS_IDX;
            
            /* Check if the input is valid */
            if ((_sensorTarget < 0) || (_sensorTarget > LAST_SENS_IDX)) {
                Serial.println("Error: Invalid sensor target");
                _sensorTarget = NO_SENSOR;
                _programState = STATE_UNKNOWN;
                _shouldShowMessage = true;
                _userBufLen = 0;
                break;
            }
            
            _programState = STATE_READ_SENSOR_COMMAND;
            _shouldShowMessage = true;
            break;
        case STATE_READ_SENSOR_COMMAND:
            if ( _userBufLen <= 0 ) {
                /* Nothing read yet */
                break;
            }
            
            /* Send the user's command directly to the sensor */
            sendCommand(_sensorTarget);
            _programState = STATE_UNKNOWN;
            _shouldShowMessage = true;
            _userBufLen = 0;
            break;
        case STATE_BUSY:
            break;
        default:
            break;
    }
    return;
}
// Function: sendCommand()
// Sends a command to the target sensor
void sendCommand(unsigned int sensId) {
    if( sensId < 0 || sensId > 2 ) {
        Serial.println("Error: Invalid sensor target");
        return;
    }
    _sensorMap[sensId].hwSerial.print(_userBuffer);
    _sensorMap[sensId].hwSerial.print("\r");
    _sensorMap[sensId].hwSerial.flush();
    
    return;
}

// Function: serialEventRead()
// - Generic function for handling data coming in from the sensors, identified
//   by the HardwareSerial they use
// - See: 
//      serialEvent1(), serialEvent2(), serialEvent3()
//
void serialEventRead(const char* sensorName, HardwareSerial hwSerial) {
    if ( hwSerial.available() <= 0 ) {
        return;
    }
    
    /* Read */
    readHWSerialToBuffer(_recvBuffer, RECV_BUF_LEN, hwSerial);
    
    /* Display what we received */
    Serial.println();
    Serial.print(sensorName);
    Serial.print(": [");
    Serial.print(_recvBuffer);
    Serial.println("]");
    Serial.println();
    
    
    hwSerial.flush();
    return;
}

// Function: readHWSerialToBuffer()
// - Reads the contents (if available) of the hardware serial into the buffer
//
void readHWSerialToBuffer(char* buffer, unsigned int maxBufferLen, HardwareSerial hwSerial) {
    int offset = 0;
    char readChar = NULL_CHAR;
    
    while ( hwSerial.available() > 0 ) {
        if (offset == 0) {
            clearBuffer(buffer, maxBufferLen);
        }

        /* Check if the next read will overflow the user buffer */
        if ( (offset + 1) >= RECV_BUF_LEN ) {
            buffer[offset] = CAR_RET_CHAR;
            break;
        }
    
        readChar = hwSerial.read();    
        
        /* If '\r' is received, then break the loop early too */
        if (readChar == CAR_RET_CHAR) {
            buffer[offset] = readChar;
            break;
        }        
        
        /* Skip non-human-readable chars */
        if ( isReadableChar(readChar) == false ) {
            continue;
        }
        
        /* Write the char to the buffer and increase the offset */
        buffer[offset] = readChar;
        offset++;
        
        readChar = NULL_CHAR;
        
        delay(50);
    }
    return;
}

/** ************************************************************************ **/
/** Section 3: Utility Functions                                             **/
/** ************************************************************************ **/
// Function: isReadable()
// - A utility function which checks if a character is human-readable
// - Returns true or false
boolean isReadableChar(char c) {
    /* In ASCII, anything between these two characters are readable */
    if (!(c < ' ') && !(c > '~')) {
        return true;
    }
    return false;
}

// Function: clearBuffer()
// - A utility function to clear pre-allocated char buffers
//
void clearBuffer(char* pBuffer, unsigned int length) {
    memset(pBuffer, NULL_CHAR, (sizeof(char)*length));
}

