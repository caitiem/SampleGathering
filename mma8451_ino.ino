/**************************************************************************/
/*!
 @file     Adafruit_MMA8451.h
 @author   K. Townsend (Adafruit Industries)
 @license  BSD (see license.txt)
 
 This is an example for the Adafruit MMA8451 Accel breakout board
 ----> https://www.adafruit.com/products/2019
 
 Adafruit invests time and resources providing this open source code,
 please support Adafruit and open-source hardware by purchasing
 products from Adafruit!
 
 @section  HISTORY
 
 v1.0  - First release
 */
/**************************************************************************/

/* RTCLib Code by JeeLabs http://news.jeelabs.org/code/
*/

#include <Wire.h>
#include <Adafruit_MMA8451.h>
#include <Adafruit_Sensor.h>
#include <SoftwareSerial.h>
#include <RTClib.h>

#define bluetoothRxPin 2
#define bluetoothTxPin 3

SoftwareSerial mySerial(bluetoothRxPin, bluetoothTxPin); // RX, TX

Adafruit_MMA8451 mma = Adafruit_MMA8451();

byte end = '#';
boolean isSampling = false;
long sample_count;
long unsigned int ms;
int ms_delay = 50;

RTC_DS1307 RTC;
DateTime now;

void setup(void) {
  Serial.begin(9600);

  Serial.println("Adafruit MMA8451 test!");

  mySerial.begin(9600);

  if (! mma.begin()) {
    Serial.println("Couldnt start");
    while (1);
  }
  randomSeed(analogRead(0));
  Serial.println("MMA8451 found!");

  mma.setRange(MMA8451_RANGE_4_G);

  Serial.print("Range = "); 
  Serial.print(2 << mma.getRange());  
  Serial.println("G");
  sample_count = 0;
  isSampling = false;
  
  RTC.begin();
 
  // Check if the RTC is running.
  if (! RTC.isrunning()) {
    Serial.println("RTC is NOT running");
  }

  // This section grabs the current datetime and compares it to
  // the compilation time.  If necessary, the RTC is updated.
  DateTime now = RTC.now();
  DateTime compiled = DateTime(__DATE__, __TIME__);
  if (now.unixtime() < compiled.unixtime()) {
    Serial.println("RTC is older than compile time! Updating");
    RTC.adjust(DateTime(__DATE__, __TIME__));
  }
  
  Serial.println("Setup is complete.");
}

void loop() {
  
  String content = "";
  char character;

  // read in message from bluetooth
  while(mySerial.available()) {
      character = mySerial.read();
      content.concat(character);
  }

  // print message on screen
  if (content != "") {
    Serial.println(content);
    content.trim();
  }
  
  if (content.substring(0, 12) == "start_sample") {
    isSampling = true;
    Serial.println("Starting sampling");
    sample_count = 0;
    ms_delay = content.substring(content.lastIndexOf("_")+1).toInt();
  } else if (content == "stop_sample") {
    isSampling = false;
    Serial.println("Stopping sampling");
    mySerial.print("stopped_sample");
    mySerial.write(end);
    sample_count = 0;
  }
  
  if (isSampling) {
    // Read the 'raw' data in 14-bit counts
    mma.read();
    // Get the current time
    now = RTC.now();
    ms = millis();
    
    mySerial.print("S");
    mySerial.print(sample_count);
    mySerial.print("D");
    mySerial.print(now.year(), DEC);
    mySerial.print('/');
    mySerial.print(now.month(), DEC);
    mySerial.print('/');
    mySerial.print(now.day(), DEC);
    mySerial.print("T");
    mySerial.print(now.hour(), DEC);
    mySerial.print(':');
    mySerial.print(now.minute(), DEC);
    mySerial.print(':');
    mySerial.print(now.second(), DEC);
    mySerial.print(':');
    mySerial.print(ms);
    mySerial.print("X"); 
    mySerial.print(mma.x); 
    mySerial.print("Y"); 
    mySerial.print(mma.y); 
    mySerial.print("Z"); 
    mySerial.print(mma.z); 
    mySerial.write(end);
    Serial.print("S");
    Serial.print(sample_count);
    Serial.print("D");
    Serial.print(now.year(), DEC);
    Serial.print('/');
    Serial.print(now.month(), DEC);
    Serial.print('/');
    Serial.print(now.day(), DEC);
    Serial.print("T");
    Serial.print(now.hour(), DEC);
    Serial.print(':');
    Serial.print(now.minute(), DEC);
    Serial.print(':');
    Serial.print(now.second(), DEC);
    Serial.print(':');
    Serial.print(ms);
    Serial.print("X");
    Serial.print(mma.x);
    Serial.print("Y");
    Serial.print(mma.y);
    Serial.print("Z");
    Serial.println(mma.z);
    sample_count++;
  }
  
  delay(ms_delay);

}

