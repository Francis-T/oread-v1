## Makefile for oread_simple
CC = g++
CFLAGS = -Wall -pedantic -g
EXE = oread_arduino
SRCS = oread_arduino.cpp
STUBS = SerialStub.h SerialStub.cpp

OBJS = $(SRCS:.cpp=.o)

all : $(EXE)
$(EXE) : $(OBJS)
	$(CC) $(CFLAGS) $(OBJS) -o $(EXE)
oread_arduino.o : oread_arduino.h oread_arduino.cpp $(STUBS)
clean :
	rm -f core $(EXE) $(OBJS) *~



