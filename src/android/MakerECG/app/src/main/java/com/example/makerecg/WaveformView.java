package com.example.makerecg;
/***************************************
*
* Android Bluetooth Oscilloscope
* yus  -       projectproto.blogspot.com
* September 2010
*  
***************************************/

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class WaveformView extends SurfaceView implements SurfaceHolder.Callback {
      
       // plot area size
       private final static int WIDTH = 320;
       private final static int HEIGHT = 240;
      
       private static int[] ch1_data = new int[WIDTH];
       private static int[] ch2_data = new int[WIDTH];
       private static int ch1_pos = HEIGHT/2 - HEIGHT/4;
       private static int ch2_pos = HEIGHT/2 + HEIGHT/4;
      
       private WaveformPlotThread plot_thread;
      
       private Paint ch1_color = new Paint();
       private Paint ch2_color = new Paint();
       private Paint grid_paint = new Paint();
       private Paint cross_paint = new Paint();
       private Paint outline_paint = new Paint();

       public WaveformView(Context context, AttributeSet attrs){
               super(context, attrs);
              
               getHolder().addCallback(this);
              
               // initial values
               for(int x=0; x<WIDTH; x++){
                       ch1_data[x] = ch1_pos;
                       ch2_data[x] = ch2_pos;
               }
              
               plot_thread = new WaveformPlotThread(getHolder(), this);
              
               ch1_color.setColor(Color.YELLOW);
               ch2_color.setColor(Color.RED);
               grid_paint.setColor(Color.rgb(100, 100, 100));
               cross_paint.setColor(Color.rgb(70, 100, 70));
               outline_paint.setColor(Color.GREEN);
       }
      
       public void surfaceCreated(SurfaceHolder holder){
               plot_thread.setRunning(true);
               plot_thread.start();
       }
      
       public void surfaceDestroyed(SurfaceHolder holder){
               boolean retry = true;
               plot_thread.setRunning(false);
               while (retry){
                       try{
                               plot_thread.join();
                               retry = false;
                       }catch(InterruptedException e){
                              
                       }
               }
       }
      
       @Override
       public void onDraw(Canvas canvas){
               PlotPoints(canvas);
       }
      
       public void set_data(int[] data1, int[] data2 ){
              
               plot_thread.setRunning(false);
              
               for(int x=0; x<WIDTH; x++){
                       // channel 1
                       if(x<(data1.length)){
                               ch1_data[x] = HEIGHT-data1[x]+1;
                       }else{
                               ch1_data[x] = ch1_pos;
                       }
                       // channel 2
                       if(x<(data1.length)){
                               ch2_data[x] = HEIGHT-data2[x]+1;
                       }else{
                               ch2_data[x] = ch2_pos;
                       }
               }
               plot_thread.setRunning(true);
       }
      
       public void PlotPoints(Canvas canvas){
               // clear screen
               canvas.drawColor(Color.rgb(20, 20, 20));
               // draw vertical grids
           for(int vertical = 1; vertical<10; vertical++){
               canvas.drawLine(
                               vertical*(WIDTH/10)+1, 1,
                               vertical*(WIDTH/10)+1, HEIGHT+1,
                               grid_paint);
           }
           // draw horizontal grids
           for(int horizontal = 1; horizontal<10; horizontal++){
               canvas.drawLine(
                               1, horizontal*(HEIGHT/10)+1,
                               WIDTH+1, horizontal*(HEIGHT/10)+1,
                               grid_paint);
           }
           // draw outline
               canvas.drawLine(0, 0, (WIDTH+1), 0, outline_paint);     // top
               canvas.drawLine((WIDTH+1), 0, (WIDTH+1), (HEIGHT+1), outline_paint); //right
               canvas.drawLine(0, (HEIGHT+1), (WIDTH+1), (HEIGHT+1), outline_paint); // bottom
               canvas.drawLine(0, 0, 0, (HEIGHT+1), outline_paint); //left
              
               // plot data
               for(int x=0; x<(WIDTH-1); x++){                
                       canvas.drawLine(x+1, ch2_data[x], x+2, ch2_data[x+1], ch2_color);
                       canvas.drawLine(x+1, ch1_data[x], x+2, ch1_data[x+1], ch1_color);
               }
       }

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub
		
	}
}

