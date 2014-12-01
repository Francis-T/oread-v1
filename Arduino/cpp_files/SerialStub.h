#ifndef __STUB_H__
#define __STUB_H__
#include <sys/time.h>
#include <unistd.h>

#define LOW		0
#define HIGH	1

#define OUTPUT	1
#define INPUT	2

#define TRUE				1
#define FALSE				0

typedef struct timeval timeval_t;

#include "SerialStub.cpp"

#define delay(x) stub_delay(x)
#define millis stub_millis
#define byte char
#define digitalWrite(x,y) stub_pinMode(x,y)
#define pinMode(x,y) stub_pinMode(x,y)
#define HardwareSerial SerialStub

void stub_pinMode(int pin, int mode);

SerialStub Serial;
SerialStub Serial1;
SerialStub Serial2;
SerialStub Serial3;

#endif /* __STUB_H__ */
