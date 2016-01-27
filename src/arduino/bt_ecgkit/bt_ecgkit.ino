//#include <Max3421e.h>
//#include <Usb.h>
//#include <AndroidAccessory.h>

#include <SPP.h>
#include <usbhub.h>

// Satisfy the IDE, which needs to see the include statment in the ino too.
#ifdef dobogusinclude
#include <spi4teensy3.h>
#include <SPI.h>
#endif

USB Usb;
//USBHub Hub1(&Usb); // Some dongles have a hub inside

BTD Btd(&Usb); // You have to create the Bluetooth Dongle instance like so
/* You can create the instance of the class in two ways */
SPP SerialBT(&Btd); // This will set the name to the defaults: "Arduino" and the pin to "0000"
//SPP SerialBT(&Btd, "Lauszus's Arduino", "1234"); // You can also set the name and pin like so

bool firstMessage = true;

/*
 * Copyright (c) 2012-2016, Riley Rainey, All Rights Reserved
  From samples at
  http://www.arduino.cc/cgi-bin/yabb2/YaBB.pl?num=1208715493/11
 */

#define FASTADC 0

#define SAMPLES 200                   // samples take in the span of each data transmission block
#define COLLECT_INTERVAL 20           // collect and send every Nth sample taken
#define TELEMETRY_MAX_BLOCK_SIZE 1018 // 2 * SAMPLES + 18
#define INBOUND_MAX_BLOCK_SIZE 64     // based on the protocol we've defined

#define POLL_FLAG_AVERAGE  0x80

#define ADC_CHANNEL 0                 // Sampling channel (this version support sampling one channel)

// Origin time of sampling session, "millis()" value at start of sampling
unsigned long g_ulStartTime_ms;
// Time of First Sample (relative to Start Time)
unsigned long g_ulFrameStartTime_ms;
// Time of last sample
unsigned long g_ulFrameEndTime_ms;
// TODO: not used yet
unsigned short g_usPollRate;
// Sample storage (Mega ADK collect 10-bit resolution)
unsigned short g_usSampleCount;
int g_nSample[SAMPLES];
// "1" when commanded to collect samples
int g_nCollecting;

// collect every Nth sample taken
int g_nCollectInterval = COLLECT_INTERVAL;
int g_nCurCollect;

// defines for setting and clearing register bits
#ifndef cbi
#define cbi(sfr, bit) (_SFR_BYTE(sfr) &= ~_BV(bit))
#endif
#ifndef sbi
#define sbi(sfr, bit) (_SFR_BYTE(sfr) |= _BV(bit))
#endif

#define CMD_X_TELEMETRY_STATE 17
#define CMD_X_TELEMETRY_BLOCK 18

/*
 * LEDs available on the Mega ADK and Arduino ProtoShield.
 * Lines must be run from pins 11 and 12 to the two ProtoShield LEDs.
 */
#define  LED_YELLOW 13   // Yellow LED located near PWMH Header; indicates remote connection state; "on" == connected
#define  LED_JC2    12   // flashes with each telemetry block write to Android
#define  LED_JC3    11   // illuminated with first message (poll) received from Android

/*
AndroidAccessory acc("Web Simulations, Inc.",
		     "ECGKit",
		     "Mega ADK with ECG Shield",
		     "2.0",
		     "http://www.websimulations.com",
		     "0000000012345678");
*/

void setup();
void loop();

void init_leds()
{
  digitalWrite(LED_YELLOW, LOW);
  digitalWrite(LED_JC2, LOW);
  digitalWrite(LED_JC3, LOW);

  pinMode(LED_YELLOW, OUTPUT);
  pinMode(LED_JC2, OUTPUT);
  pinMode(LED_JC3, OUTPUT);

  digitalWrite(LED_YELLOW, LOW);
  digitalWrite(LED_JC2, LOW);
  digitalWrite(LED_JC3, LOW);

}


void setup()
{
  
  Serial.begin(115200);
  
#if !defined(__MIPSEL__)
  while (!Serial); // Wait for serial port to connect - used on Leonardo, Teensy and other boards with built-in USB CDC serial connection
#endif
  if (Usb.Init() == -1) {
    Serial.print(F("\r\nOSC did not start"));
    while (1); //halt
  }
  //Serial.print(F("\r\nSPP Bluetooth Library Started"));

  init_leds();

  //acc.powerOn();

  g_ulStartTime_ms = millis();
  g_usSampleCount = 0;
  g_nCollecting = 0;
  g_usPollRate = 0;
  
#if FASTADC
  // set prescale to 16
  sbi(ADCSRA,ADPS2) ;
  cbi(ADCSRA,ADPS1) ;
  cbi(ADCSRA,ADPS0) ;
#endif
}

void collectAndSend();

/*
 * Collect one block of samples and transmit them to the Android device
 */
void collectAndSend( int nCount ) 
{
  int i;
  unsigned long nTotal = 0;
  
  g_ulFrameStartTime_ms = millis() - g_ulStartTime_ms;
  g_usSampleCount = 0;
  
  for (i=0;i<nCount;i++)
  {
    // take sample
    int nSample = analogRead(ADC_CHANNEL);
    // Averaging?
    if ( g_nCollecting & POLL_FLAG_AVERAGE ) {
      nTotal += nSample;
      if ( --g_nCurCollect <= 0 ) {
        g_nSample[g_usSampleCount++] = nTotal / g_nCollectInterval;
        nTotal = 0;
        g_nCurCollect = g_nCollectInterval;
      }
    }
    // not averaging
    else {
      if ( --g_nCurCollect <= 0 ) {
        g_nSample[g_usSampleCount++] = nSample;
        g_nCurCollect = g_nCollectInterval;
      }
    }
  } 
  
  g_ulFrameEndTime_ms = millis() - g_ulStartTime_ms;

  writeTelemetryMessage();
}

// u16 (poll rate), u32 (timestamp_ms), u16(channel mask), u16 (sample count, N), num channels * N * i16 (sample array)

void writeTelemetryMessage() {
  
  digitalWrite(LED_JC2, HIGH);
  
  byte msg[TELEMETRY_MAX_BLOCK_SIZE];
  int n;
  int j;
  
  msg[0] = CMD_X_TELEMETRY_BLOCK;
  msg[1] = 0;
  
  // Compute "reply size", excludes first four bytes of message
  int nSize = 14 + (g_usSampleCount << 1);
  
  msg[2] = nSize & 0xff;
  msg[3] = nSize >> 8;
  
  // Poll Rate; Reserved for future use
  msg[4] = g_usPollRate & 0xff;
  msg[5] = g_usPollRate >> 8;
  
  msg[9] = (g_ulFrameStartTime_ms >> 24);
  msg[8] = (g_ulFrameStartTime_ms >> 16) & 0xff;
  msg[7] = (g_ulFrameStartTime_ms >> 8) & 0xff;
  msg[6] = g_ulFrameStartTime_ms & 0xff;
  
  msg[13] = (g_ulFrameEndTime_ms >> 24);
  msg[12] = (g_ulFrameEndTime_ms >> 16) & 0xff;
  msg[11] = (g_ulFrameEndTime_ms >> 8) & 0xff;
  msg[10] = g_ulFrameEndTime_ms & 0xff;
  
  // Channel Mask; Reserved for future use
  msg[14] = 0;
  msg[15] = 0;
  
  msg[16] = g_usSampleCount & 0xff;
  msg[17] = g_usSampleCount >> 8;
  
  n = 18;
  
  for (j=0; j< g_usSampleCount; ++j) {
    msg[n++] = g_nSample[j] & 0xff;
    msg[n++] = g_nSample[j] >> 8;
  }
  
  SerialBT.write( msg, n );
  
  digitalWrite( LED_JC2, LOW );

}

void writeStateResponseMessage(int nState) {
  
  byte msg[16];
  
  msg[0] = CMD_X_TELEMETRY_STATE | 0x80;
  msg[1] = 0;
  
  // Compute "reply size", excludes first four bytes of message
  int nSize = 1;
  
  msg[2] = nSize & 0xff;
  msg[3] = nSize >> 8;
  
  // Poll Rate; Reserved for future use
  msg[4] = nState & 0xff;

  SerialBT.write( msg, 5 );

}

void loop() {
  
  byte msg[INBOUND_MAX_BLOCK_SIZE];
  
  Usb.Task(); // The SPP data is actually not send until this is called, one could call SerialBT.send() directly as well

  if ( SerialBT.connected ) {
    
    // JC2 LED indicate the USB connection state; LED ON is CONNECTED
    digitalWrite(LED_YELLOW, HIGH);

    if (SerialBT.available()) {
      
      int len = SerialBT.readBytes(msg, sizeof(msg));
  
      if (len > 0) {
        // JC3 signals at least one message received from Android
        digitalWrite(LED_JC3, HIGH);
        
        // assumes only one command per packet
        if ( msg[0] == CMD_X_TELEMETRY_STATE ) {
          
          writeStateResponseMessage( msg[4] );
          
          // starting?
          if (g_nCollecting == 0 ) {
            if ( msg[4] != 0 ) {
              g_ulStartTime_ms = millis();
              g_nCurCollect = COLLECT_INTERVAL;
            }
          }
          g_nCollecting = msg[4];
        }
      }
    }
    if ( g_nCollecting ) {
      collectAndSend( SAMPLES );
    }
  } 
  /*
   * Not connected
   */
  else {
    digitalWrite(LED_YELLOW, LOW);
    digitalWrite(LED_JC2, LOW);
    digitalWrite(LED_JC3, LOW);

    g_nCollecting = 0;

  }

}
