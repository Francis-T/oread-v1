#ifndef __STUB_C__
#define __STUB_C__

void stub_begin(long lValue)
{
	printf("Serial begin: %li\n", lValue);
	return;
}

int stub_available()
{
	return _iAvailableCtr;
}

int stub_serialAvailable()
{
	if (_bReadTillEnd)
	{
		/* Next available() call will have reset values */
		_iSerialAvailableCtr = 5;
		_bReadTillEnd = FALSE;
		_iSerialReadCtr = 0;
		return 0;
	}
	return _iSerialAvailableCtr;
}

int stub_serialRead()
{
	if (_iSerialAvailableCtr > 0)
	{
		#ifdef __DEBUG_READ__
		printf("[DEBUG] Readout char: %c\n", _aSerialData[_iSerialReadCtr]);
		#endif
		_iSerialAvailableCtr--;
		
		if (_iSerialAvailableCtr == 0)
		{
			_bReadTillEnd = TRUE;
		}
		
		return _aSerialData[_iSerialReadCtr++];
	}
	
	return 0;
}

void stub_readUserInput()
{
	int i;
	int c;
	
	for (i = 0; i < 64; i++)
	{
		_aUserInput[i] = 0;
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
		
		_aUserInput[i++] = c;
	}
	
	_iReadCtr = 0;
	_iAvailableCtr = strlen(_aUserInput);
}

int stub_read()
{
	if (_iAvailableCtr > 0)
	{
		#ifdef __DEBUG_READ__
		printf("[DEBUG] Readout char: %c\n", _aUserInput[_iReadCtr]);
		#endif
		_iAvailableCtr--;
		return _aUserInput[_iReadCtr++];
	}
	return 0;
}

int stub_write(byte* pBytes, int iLen)
{
	int i;
	printf("Write:\n");
	for (i = 0; i < iLen; i++)
	{
		printf("[%d] ", pBytes[i]);
	}
	printf("\n");
	
	return iLen;
}

void stub_print(const char* pStr)
{
	printf("%s", pStr);
}

void stub_println(const char* pStr)
{
	stub_print(pStr);
	printf("\n");
}

void stub_pinMode(int pin, int mode)
{
	return;
}

void stub_delay(long lValue)
{
	usleep(lValue*1000);
	return;
}

void stub_flush()
{
	return;
}

long stub_millis()
{
	timeval_t time;
	
	gettimeofday(&time, NULL);
	
	return ((time.tv_sec & (-4096 ^ -1)) * 1000) + (time.tv_usec / 1000);
}

#endif /* __STUB_C__ */
