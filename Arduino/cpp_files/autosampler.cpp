#include <Servo.h> 
#include <AccelStepper.h>
#include <Wire.h>
#include <Adafruit_MotorShield.h>
#include "utility/Adafruit_PWMServoDriver.h"
#include <LiquidCrystal.h>

// Create the motor shield object with the default I2C address
Adafruit_MotorShield AFMSbot = Adafruit_MotorShield();
Adafruit_MotorShield AFMStop = Adafruit_MotorShield(0x61); 

// Or, create it with a different I2C address (say for stacking)
// Adafruit_MotorShield AFMS = Adafruit_MotorShield(0x61); 

// Connect a stepper motor with 200 steps per revolution (1.8 degree)
// to motor port #2 (M3 and M4)
Adafruit_StepperMotor *myStepper_needle = AFMSbot.getStepper(513, 1);
Adafruit_DCMotor *myMotor_pump = AFMStop.getMotor(1);

void forwardstep_needle() {  
  myStepper_needle->onestep(FORWARD, SINGLE);
}
void backwardstep_needle() {  
  myStepper_needle->onestep(BACKWARD, SINGLE);
}

AccelStepper needle(forwardstep_needle, backwardstep_needle); // use functions to step

Servo carousel_servo;
Servo switch_servo;




int i;

int dir_up = 1;
int dir_down = 0;

int samplesol = 2;
int clean = 1;
int sample = 0;

int needle_home=0; //pin 4
int needle_end=0; //pin 2



int YES = 1;
int NO = 0;

int all_home = NO;
int initialize_process_done; //for while loop
int home_process_needle_done; //for while loop
int home_process_carousel_done = NO;


int pos_current;
int carousel_move_done; //for while loop
//int pos_carousel_array[30] = {936, 963, 984, 1006, 1025, 1051, 1079, 1103, 1130, 1147, 1170, 1190, 1208, 1229, 1253, 1269, 1288, 1312, 1025, 1051, 1079, 936, 963, 984, 1006, 1025, 1051, 1079, 936, 963};
//int pos_carousel_array[30] = {925, 952, 974, 994, 1012, 1038, 1068, 1090, 1117, 1137, 1158, 1178, 1193, 1213, 1232, 1257, 1275, 1295, 1323, 1349, 1370, 1390, 1411, 1432, 1455, 1477, 1498, 1522, 1549, 1555};
//int pos_carousel_array[30] = {925, 948, 971, 990, 1011, 1034, 1064, 1089, 1114, 1133, 1155, 1175, 1188, 1210, 1229, 1254, 1272, 1293, 1320, 1346, 1372, 1389, 1410, 1434, 1455, 1477, 1496, 1521, 1546, 1555};
//int pos_carousel_array[30] = {1264, 1258, 1252, 1264, 1258, 1252,1264, 1258, 1252,1264, 1258, 1252,1264, 1258, 1252,1264, 1258, 1252,1264, 1258, 1252,1264, 1258, 1252,1264, 1258, 1252,1264, 1258, 1252};
//int pos_carousel_array[30] = {1264, 1257, 1252, 1246, 1242, 1236, 1232, 1223, 1215, 1206, 1201, 1191, 1183, 1176, 1170, 1162, 1156, 1150, 1146, 1138, 1133, 1125, 1117, 1110, 1100, 1091, 1085, 1077, 1070, 1270};
int pos_carousel_array[30] = {1266, 1260, 1255, 1248, 1244, 1239, 1234, 1227, 1218, 1210, 1203, 1194, 1186, 1178, 1172, 1165, 1159, 1152, 1147, 1142, 1135, 1127, 1120, 1113, 1103, 1093, 1087, 1079, 1073, 1272};

//i2c1
char from_oread;
char state = '0'; 

LiquidCrystal lcd(13, 12, 8, 7, 6, 1);


void setup()
{  
   Serial.begin(9600);           // set up Serial library at 9600 bp
    lcd.begin(16, 2);
  
  AFMSbot.begin();  // create with the default frequency 1.6KHz44
  AFMStop.begin();
  
   needle.setMaxSpeed(2000.0);
   needle.setAcceleration(600.0);

   myStepper_needle->setSpeed(30);
   
   //pump
   myMotor_pump->setSpeed(150);
  myMotor_pump->run(FORWARD);
  // turn on motor
  myMotor_pump->run(RELEASE);
  
  //carousel_servo.attach(9);  // attaches the servo on pin 9 to the servo object
  switch_servo.attach(10);
  switch_servo.write(60);

   //wire functions
   Wire.begin(4);                // join i2c bus with address #4
   Wire.onReceive(receiveEvent); // register event
   Wire.onRequest(requestEvent);
   
   //pump
   motorctrl(0,2); //stop
}

void interrupt_func() 
{
  state = 'e'; //error
}

void receiveEvent(int howMany) {
    char cRead = '\0';
    int iPosStrIdx = 0;
    char posStr[3];
    
    if (Wire.available() <= 0)
    {
        // No data available!
        return;
    }

    from_oread = Wire.read(); // receive first byte as a character 
    //lcd.clear();
    //lcd.write(from_oread);
    Serial.println(from_oread); 
    
    /* If the cmd char is 'x', receive the next chars too
     *  Received data example: 
     *      "x 1" // trigger process 1 with pos = 1
     *      "x 24" // trigger process 1 with pos = 26
     */
    if (from_oread == 'x') 
    {
        if (Wire.available() < 2)
        {
            // i.e. "x" is sent w/o the pos number
            return;
        }
        
        /* Fill posStr with null characters */
        memset(posStr, '\0', sizeof(char)*sizeof(posStr));
        
        /* Store the the pos number into posStr */
        while(Wire.available() > 0)
        {
            cRead = Wire.read();
            
            // If the received char is not a digit, skip it
            if ((cRead < '0') || (cRead >'9'))
            {
              continue;
            }
            
            posStr[iPosStrIdx] = cRead;
            Serial.print("[");
            Serial.print(posStr[iPosStrIdx]);
            Serial.println("]");
            iPosStrIdx++;   // Increment the pos string index
                            // i.e. move to next empty char in posStr
                            
            // Stops attempting to read a pos number
            //  consisting of more than 2 chars
            // e.g. "x 1024" would only have "10" as its pos number
            if (iPosStrIdx == 2)
            {
                break;
            }
        }
        
        pos_current = atoi(posStr); // atoi() converts a string number to int
        if  ((pos_current < 1) || (pos_current > 30))
        pos_current = 1;
        //lcd.clear();
        //lcd.write(pos_current);
        Serial.println(pos_current);
         
    }
    
    /* Discard any remaining received chars */
    while(Wire.available() > 0) {
        Wire.read();
    }
    
    return;
}

void requestEvent()
{
    char cState = 0;
    char response[33];  // Assume that Wire.requestFrom() will have '32' as the
                        //  number of bytes to be requested by the I2C master
    
    /* Fill response with ' ' (space) characters */
    memset(response, ' ', sizeof(char)*sizeof(response));
    response[32] = '\0';  // terminate the string
                          // note that this character is not sent
    
    /* Copy the string, "State: " (7 chars), into response */
    strcpy(response, "State: ");
    
    /* Transform the state into a char and include it in the response */
    cState = state;
    response[7] = cState; // replace the next space in response w/ the state
    
    /* Write the response to I2C */
    Wire.write(response);
    //lcd.clear();
    //lcd.write("response to poll: "); 
    //lcd.write(response); 
    Serial.print("response to poll: "); 
    Serial.println(response); 
}

void loop()
{
  

switch (from_oread)
  {
  case 'x': //start
    from_oread = 'r';
    initialize_process();
    lcd.clear();
    lcd.write("break 1");
    Serial.println("break 1");
    break;
  case 'y'://continue process
    from_oread = 'r';
    chem_process2();
    lcd.clear();
    lcd.write("break 2");
    Serial.println("break 2");   
    break;
    case 'r':
      Serial.print(".");
      break;
    case '@':
      break;
    default:
      lcd.clear();
      lcd.write("Unknown command: ");
      lcd.write(from_oread);
      Serial.print("Unknown command: ");
      Serial.println(from_oread);
   
      if (digitalRead(2) == HIGH)
      {    
        switch_servo.write(160);
        while (1)
        {
        if (digitalRead(5) == LOW)
          {
          motorctrl(0,10);
          break;
          }
        motorctrl(250,0);
        }
      }
      else
      {
      switch_servo.write(60);
      while (1)
        {
        if (digitalRead(5) == LOW)
          {
          motorctrl(0,10);
          break;
          }
        motorctrl(250,0);
        }
      }
     ///put comment end here 
    break;  
  }
  delay(100);

}

void initialize_process()
{ 
  state = '1'; //homing
  lcd.clear();
  lcd.write("initialize");
  Serial.println("initialize");
  //reinitialization variables
  initialize_process_done = NO;  ///edit
  all_home = NO;
  home_process_carousel_done = NO;

  while(initialize_process_done == NO)
  {
    if (all_home == NO)
      home_process();
    else
      initialize_process_done = YES;
  
  }
  lcd.clear();
  lcd.write("home");
  Serial.println("home");
  chem_process1();
}

void home_process()
{
  lcd.clear();
  lcd.write("homeprocess");
  Serial.println("homeprocess");
  
  home_process_needle();
  home_process_carousel();
  all_home = ((home_process_needle_done)&&(home_process_carousel_done));     
}


void home_process_needle()
{
  home_process_needle_done = NO;
  while(home_process_needle_done == NO)
  {
  needle_home = digitalRead(4);
  if (needle_home == 0)
  { 
    //Serial.println("home process needle");
    myStepper_needle->onestep(BACKWARD, SINGLE);  
  }
  else
  {
   home_process_needle_done = YES;
  }
  }
   myStepper_needle->release();
   return;
}

void home_process_carousel()
{
 carousel_servo.attach(9);
 switch_servo.attach(10);
 lcd.clear();
 lcd.write("home process carousel");
 Serial.println("home process carousel");
 delay(1000); 
 switch_servo.write(60);
 carousel_servo.writeMicroseconds(1900);
 delay(15000);
 carousel_servo.writeMicroseconds(pos_carousel_array[29]);
 delay(10000);
 carousel_servo.detach();
 home_process_carousel_done = YES; 
}


void chem_process1()
{

 
 //step 1
 //Assume everything is home
 //Clean is removed because home
                                           
 //step 3
 lcd.clear();
 lcd.write("step 3"); 
  Serial.println("step 3"); 
 needle_move(dir_down);
 delay(3000);
 
 servo_switch(sample);
 
 //step 4-5
 //triple wash the sample "cleaning"
 lcd.clear();
 lcd.write("steps 4-5");
 Serial.println("steps 4-5"); 
 pump_move(samplesol); 
 delay(3000);
 
  state = '2'; //chem process 1
 //step 6
 Serial.println("step 6"); 
 needle_move(dir_up);
 delay(3000);
                                          
 //step 7
 lcd.clear();
 lcd.write("step 7");
 Serial.println("step 7"); 
  Serial.println(pos_current); 
 stepper_carousel_move(pos_current);                                             
 
 //step 8
 lcd.clear();
 lcd.write("step 8");
 Serial.println("step 8"); 
 needle_move(dir_down);
 delay(3000);

 //step 9
 lcd.clear();
 lcd.write("step 9");
 Serial.println("step 9"); 
 pump_move(sample);
 


 //step 11
 lcd.clear();
 lcd.write("step 11");
 Serial.println("step 11"); 
 needle_move(dir_up);
 delay(3000);
 
  //step 10
 lcd.clear();
 lcd.write("step 10");
 Serial.println("step 10 ");
 lcd.write("waiting");
 Serial.println("waiting"); 
 
 for (int mins = 1; mins < 11; mins++)
 {
    for (int secs = 1; secs < 61; secs++)
     {
     delay(1000);
     }
 }
 
 //step 13
 //take image
 lcd.clear();
 lcd.write("step 13");
 Serial.println("step 13"); 
 state = '3';
}

void chem_process2()
{   
 state = '4'; //chem process 2
 //step 14
 //cuvette zero
 lcd.clear();
 lcd.write("step 14");
 Serial.println("step 14");
 home_process_carousel();
                                     
 //step 15
 lcd.clear();
 lcd.write("step 15");
 Serial.println("step 15");
 needle_move(dir_down);
 delay(3000);
 
 //step 16
 lcd.clear();
 lcd.write("step 16");
 Serial.println("step 16");
 servo_switch(clean);
 
 //step 16-18
 lcd.clear();
 lcd.write("steps 17-19");
 Serial.println("step 17-19");
 pump_move(clean);
 delay(3000);
 
 lcd.clear();
 lcd.write("step 20");
 Serial.println("step 20");
 needle_move(dir_up);
 delay(3000);
                                      
 state = '5'; //done
 //store cur_pos 
}
  
void stepper_carousel_move(int pos_carousel)
{
  
  int microseconds;
  
  carousel_servo.attach(9);
  carousel_servo.writeMicroseconds(1900);
  delay(15000);
  Serial.print(pos_carousel); Serial.print(": "); Serial.println(pos_carousel_array[pos_carousel - 1]);
  if (pos_carousel_array[pos_carousel - 1] < 545)  microseconds = 1272;
  else microseconds = pos_carousel_array[pos_carousel - 1];
  carousel_servo.writeMicroseconds(microseconds);
  delay(5000);
  carousel_servo.detach();
  
  /*
  for (int xx = 1; xx < 31; xx++)
  {
  carousel_servo.attach(9);
  carousel_servo.writeMicroseconds(1900);
  delay(15000);
  Serial.print(xx); Serial.print(": "); Serial.println(pos_carousel_array[xx - 1]);
  if (pos_carousel_array[xx - 1] < 545)  microseconds = 1272;
  else microseconds = pos_carousel_array[xx - 1];
  carousel_servo.writeMicroseconds(microseconds);
  delay(5000);
  carousel_servo.detach();
  delay(5000);
  }
  */
return;

}

void needle_move(int dir_needle)
{
int needle_move_done = NO;
while (needle_move_done == NO)
 {
  needle_home = digitalRead(4);
  needle_end = digitalRead(2);
	if (dir_needle == dir_down)
	{
	 if (needle_end == HIGH)
         needle_move_done = YES;
	 else
	 {           
            myStepper_needle->onestep(FORWARD, SINGLE);  
	  } 
	}
	
	else
	{
	 if (needle_home == HIGH) 
         needle_move_done = YES;	  
	 else
         {           
            myStepper_needle->onestep(BACKWARD, SINGLE);  
	 }
	}
  
 }
 myStepper_needle->release();
return;
}

void pump_move(int pump_loop)
{
  int turns;
  int speedx;
  if (pump_loop == clean)
  {turns = 65; speedx = 222;}
  else if (pump_loop == samplesol)
  {turns = 130; speedx= 222;}
  else 
  {turns = 2; speedx = 120;}
  
     for (int ii = 1; ii <= turns; ii++)
     {
        motorticks(speedx,120);
        motorctrl(0,1000); //stop 
     }
   myMotor_pump->run(RELEASE);
   delay(1000);
 return; 
}


  
void motorctrl(int speed1,int mydelay )  {
  // motor1
  
  myMotor_pump->run(FORWARD);
 myMotor_pump->setSpeed(speed1);
  
  delay(mydelay);
}  

void motorticks(int speed2, int mydelay2)  {
    motorctrl(speed2, mydelay2);
    motorctrl(0,10); 
}   

void servo_switch (int servo_switch2)
{
  if (servo_switch2 == sample)
  {
    switch_servo.write(160);
  }
  else if (servo_switch2 == clean)
  {
    switch_servo.write(60);
  }  
}
