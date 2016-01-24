package com.example.makerecg;

import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;
import android.opengl.GLUtils;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.util.Log;

public class Texture {
	public float width = 0;
	public float height = 0;

	public int[] _textureBits;
	private int _nTextureID;
	private int[] _cropArray;
	private final BitmapFactory.Options _options;
	
	private static final String TAG = "Texture";

	public Texture( GL11 gl, Bitmap bitmap) {
		_textureBits = new int[1];
		_cropArray = new int[4];
		_options = new BitmapFactory.Options();
		_options.inPreferredConfig = Bitmap.Config.RGB_565;

		load ( gl, bitmap );
	}

	public int getTextureID( ) {
		return _nTextureID;
	}

	public boolean load(GL11 gl, Bitmap bitmap) { 
		int error = gl.glGetError();

		int textureName = -1;
		gl.glGenTextures(1, _textureBits, 0);
		textureName = _textureBits[0];
		
		width = bitmap.getWidth();
		height = bitmap.getHeight();

		gl.glBindTexture(GL11.GL_TEXTURE_2D, textureName);
		gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP_TO_EDGE);
		gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP_TO_EDGE);
		gl.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);

		GLUtils.texImage2D( GL11.GL_TEXTURE_2D, 0, bitmap, 0 );

		_cropArray[0] = 0;
		_cropArray[1] = bitmap.getHeight();
		_cropArray[2] = bitmap.getWidth();
		_cropArray[3] = -bitmap.getHeight();

		gl.glTexParameteriv( GL11.GL_TEXTURE_2D, 
				GL11Ext.GL_TEXTURE_CROP_RECT_OES, _cropArray, 0);

		error = gl.glGetError();
		if (error != GL11.GL_NO_ERROR)
		{ 
			Log.e(TAG, "GL Texture Load Error: " + error);
			return false;
		}
		return true;
	}
	
	public void drawSprite(GL11 gl, float x, float y, float z)
	{
		//Log.d( TAG, "Drawing...");
		gl.glEnable( GL11.GL_TEXTURE_2D );
		gl.glBindTexture( GL11.GL_TEXTURE_2D, _textureBits[0] );
		((GL11Ext)gl).glDrawTexfOES( x, y, z, width, height );
	}
}