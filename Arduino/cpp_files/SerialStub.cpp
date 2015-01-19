class SerialStub 
{
	public:
	// Primitive Creator
	SerialStub();
	SerialStub(int index);
	
	int available();
	void begin(long lValue);
	int read();
	int write(char* pBytes, int iLen);
	void print(const char* pStr);
	void println(const char* pStr);
	void flush();
	
	void readUserInput();
	
	void set_index(int index);
	
	private:
	int _serialIndex;
	int _serialAvailableCtr;
	char _aSerialData[64];
	int _bReadTillEnd;
	int _serialReadCtr;
	int _bResponseNeeded;
	
};

unsigned int seed = 1;
void stub_sanitize(const char* pSrc, char* pDest);

SerialStub::SerialStub()
{
	_serialIndex = rand_r(&seed);
	_serialAvailableCtr = 7;
	_bReadTillEnd = FALSE;
	_bResponseNeeded = FALSE;
	_serialReadCtr = 0;
	
	memset(_aSerialData, 0, 64);
	strncpy(_aSerialData, "Test \r", 7);
	return;
}

SerialStub::SerialStub(int index): _serialIndex(-1)
{
	_serialIndex = index;
	_serialAvailableCtr = 7;
	_bReadTillEnd = FALSE;
	_bResponseNeeded = 0;
	_serialReadCtr = 0;
	
	memset(_aSerialData, 0, 64);
	strncpy(_aSerialData, "Test \r", 7);
	_aSerialData[4] = (char)(48 + _serialIndex);
	
	return;
}

int SerialStub::available()
{
	if (_bReadTillEnd)
	{
		/* Next available() call will have reset values */
		_serialAvailableCtr = 7;
		_bReadTillEnd = FALSE;
		_bResponseNeeded = FALSE;
		_serialReadCtr = 0;
		return 0;
	}
	
	if ((_serialIndex != 0) && !(_bResponseNeeded))
	{
		return 0;
	}
	
	return _serialAvailableCtr;
}

void SerialStub::begin(long lValue)
{
	printf("Serial %d begin: %li\n", _serialIndex, lValue);
	return;
}

int SerialStub::read()
{
	if (_serialAvailableCtr > 0)
	{
		#ifdef __DEBUG_READ__
		printf("[DEBUG] Readout char: %c\n", _aSerialData[_serialReadCtr]);
		#endif
		_serialAvailableCtr--;
		
		if (_serialAvailableCtr == 0)
		{
			_bReadTillEnd = TRUE;
		}
		
		return _aSerialData[_serialReadCtr++];
	}
}

int SerialStub::write(char* pBytes, int iLen)
{
	int i;
	printf("Write:\n");
	for (i = 0; i < iLen; i++)
	{
		printf("[%d] ", pBytes[i]);
	}
	printf("\n");

    printf("{%s}\n", pBytes);
	
	if ((_serialIndex != 0) &&
		(iLen > 0) &&
		(pBytes[0] == 'R'))
	{
		_bResponseNeeded = TRUE;
	}
	
	return iLen;
}

void SerialStub::print(const char* pStr)
{
	int iOrigLen = (strlen(pStr) >= 0) ? strlen(pStr) : 1;
	
	if (_serialIndex != 0)
	{
		char aStr[iOrigLen + 1];
		
		memset(aStr, 0, (iOrigLen + 1));
		
		stub_sanitize(pStr, aStr);
		printf("    <%s>\n", aStr);
		
		if ((strlen(aStr) > 0) &&
			(pStr[0] == 'R'))
		{
			_bResponseNeeded = TRUE;
		}
	}
	else
	{
		printf("%s", pStr);
	}
	
	return;
}

void SerialStub::println(const char* pStr)
{
	SerialStub::print(pStr);
	printf("\n");
	
	if ((_serialIndex != 0) &&
		(strlen(pStr) > 0) &&
		(pStr[0] == 'R'))
	{
		_bResponseNeeded = TRUE;
	}
	
	return;
}

void SerialStub::flush()
{
	return;
}

void SerialStub::set_index(int index)
{
	printf("triggered: %d = %d\n", _serialIndex, index);
	_serialIndex = index;
	_aSerialData[4] = (char)(48 + _serialIndex);
	printf("exit: %d = %d\n", _serialIndex, index);
	return;
}

void SerialStub::readUserInput()
{
	int i;
	int c;
	
	for (i = 0; i < 64; i++)
	{
		_aSerialData[i] = 0;
	}
	
	i = 0;
	while((c = getchar()) != EOF)
	{	
		if (c == '\n')
		{
			break;
		}
		
		if (i == 63)
		{
			break;
		}
		
		_aSerialData[i++] = c;
	}
	
	_serialReadCtr = 0;
	_serialAvailableCtr = strlen(_aSerialData);
}

void stub_sanitize(const char* pSrc, char* pDest)
{
	int i;
	int iSrcLen = 0;
	
	if ((pSrc == NULL) || (pDest == NULL))
	{
		return;
	}
	
	if ((iSrcLen = strlen(pSrc)) <= 0) 
	{
		return;
	}
	
	for (i = 0; i < iSrcLen; i++)
	{
		if ((*(pSrc + i) < 20) || (*(pSrc + i) > 176))
		{
			*(pDest + i) = ' ';
		}
		else
		{
			*(pDest + i) = *(pSrc + i);
		}
	}
	
	return;
}

void stub_delay(long lValue)
{
	usleep(lValue*1000);
	return;
}

long stub_millis()
{
	timeval_t time;
	
	gettimeofday(&time, NULL);
	
	return ((time.tv_sec & (-4096 ^ -1)) * 1000) + (time.tv_usec / 1000);
}

void stub_pinMode(int pin, int mode)
{
	return;
}


