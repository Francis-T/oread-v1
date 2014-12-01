#ifndef __STUB_H__
#define __STUB_H__
#include <sys/time.h>
#include <unistd.h>

#define delay(x) stub_delay(x)
#define millis stub_millis
#define byte char
#define digitalWrite(x,y) stub_pinMode(x,y)
#define pinMode(x,y) stub_pinMode(x,y)
#define HardwareSerial tSerialStub

#define LOW		0
#define HIGH	1

#define OUTPUT	1
#define INPUT	2

#define TRUE				1
#define FALSE				0

typedef struct timeval timeval_t;

typedef void (*fLongFunc_t) (long lValue);
typedef void (*fBeginFunc_t) (long lValue);
typedef int (*fReadFunc_t) ();
typedef int (*fWriteFunc_t) (byte* pBytes, int iLen);
typedef int (*fAvailFunc_t) ();
typedef void (*fPrintFunc_t) (const char* pStr);
typedef void (*fFlushFunc_t) ();

typedef struct serialStub_t
{
	fBeginFunc_t begin;
	fAvailFunc_t available;
	fReadFunc_t read;
	fWriteFunc_t write;
	fPrintFunc_t print;
	fPrintFunc_t println;
	fFlushFunc_t flush;
} tSerialStub;

int stub_available();
void stub_begin(long lValue);
void stub_readUserInput();
int stub_read();
int stub_write(byte* pBytes, int iLen);
void stub_print(const char* pStr);
void stub_println(const char* pStr);
void stub_delay(long lValue);
long stub_millis();
void stub_flush();

int stub_serialAvailable();
int stub_serialRead();

void stub_pinMode(int pin, int mode);

char _aUserInput[64];
int _iReadCtr;
int _iAvailableCtr;

int _iSerialAvailableCtr = 5;
const char* _aSerialData = "Test\r";
int _bReadTillEnd = FALSE;
int _iSerialReadCtr = 0;

#define Serial1 { stub_begin, stub_serialAvailable, stub_serialRead, stub_write, stub_print, stub_println, stub_flush }
#define Serial2 { stub_begin, stub_serialAvailable, stub_serialRead, stub_write, stub_print, stub_println, stub_flush }
#define Serial3 { stub_begin, stub_serialAvailable, stub_serialRead, stub_write, stub_print, stub_println, stub_flush }

tSerialStub Serial = { stub_begin, 
					   stub_available, 
					   stub_read, 
					   stub_write, 
					   stub_print, 
					   stub_println,
					   stub_flush };

#endif /* __STUB_H__ */
