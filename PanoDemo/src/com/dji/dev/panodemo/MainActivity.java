/*
 * An Android Panorama demo for DJI Inspire1 and Phantom 3 Professional using DJI SDK and OpenCV
 * Develop environment:jdk 8u45 + eclipse mars + ADT 23.0.6 + ndk r10e + cdt8.7.0 + cygwin2.1.0 + OpenCV2.4.11 + DJI SDK 2.3.0
 */

package com.dji.dev.panodemo;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import com.dji.dev.panodemo.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import dji.midware.data.manager.P3.ServiceManager;
import dji.midware.usb.P3.DJIUsbAccessoryReceiver;
import dji.midware.usb.P3.UsbAccessoryService;
import dji.sdk.api.DJIDrone;
import dji.sdk.api.DJIDroneTypeDef.DJIDroneType;
import dji.sdk.api.Gimbal.DJIGimbalAttitude;
import dji.sdk.api.Gimbal.DJIGimbalCapacity;
import dji.sdk.api.Gimbal.DJIGimbalRotation;
import dji.sdk.api.GroundStation.DJIGroundStationExecutionPushInfo;
import dji.sdk.api.GroundStation.DJIGroundStationTask;
import dji.sdk.api.GroundStation.DJIGroundStationWaypoint;
import dji.sdk.api.GroundStation.DJIGroundStationTypeDef.DJIGroundStationFinishAction;
import dji.sdk.api.GroundStation.DJIGroundStationTypeDef.DJINavigationFlightControlCoordinateSystem;
import dji.sdk.api.GroundStation.DJIGroundStationTypeDef.DJINavigationFlightControlYawControlMode;
import dji.sdk.api.GroundStation.DJIGroundStationTypeDef.GroundStationExecutionPushType;
import dji.sdk.api.GroundStation.DJIGroundStationTypeDef.GroundStationOnWayPointAction;
import dji.sdk.api.GroundStation.DJIGroundStationTypeDef.GroundStationResult;
import dji.sdk.api.MainController.DJIMainControllerSystemState;
import dji.sdk.api.DJIError;
import dji.sdk.api.Battery.DJIBatteryProperty;
import dji.sdk.api.Camera.DJICameraPlaybackState;
import dji.sdk.api.Camera.DJICameraSettingsTypeDef.CameraCaptureMode;
import dji.sdk.api.Camera.DJICameraSettingsTypeDef.CameraMode;
import dji.sdk.interfaces.DJIBatteryUpdateInfoCallBack;
import dji.sdk.interfaces.DJICameraPlayBackStateCallBack;
import dji.sdk.interfaces.DJIExecuteResultCallback;
import dji.sdk.interfaces.DJIFileDownloadCallBack;
import dji.sdk.interfaces.DJIGerneralListener;
import dji.sdk.interfaces.DJIGimbalErrorCallBack;
import dji.sdk.interfaces.DJIGimbalUpdateAttitudeCallBack;
import dji.sdk.interfaces.DJIGroundStationExecuteCallBack;
import dji.sdk.interfaces.DJIGroundStationExecutionPushInfoCallBack;
import dji.sdk.interfaces.DJIMcuUpdateStateCallBack;
import dji.sdk.interfaces.DJIReceivedVideoDataCallBack;
import dji.sdk.widget.DjiGLSurfaceView;

public class MainActivity extends Activity implements OnClickListener {
    
	/*********************Config zone*********************/
    private final double STITCH_IMAGE_SCALE = 0.5;  //image scaling for stitching(0.1~1)
    private boolean isCheckCaptureImageFailure = false;  //check dji camera capture failure count
    private boolean isCheckDownloadImageFailure = false;  //check dji download image failure count
    private boolean isDIsableDJIVideoPreviewDuringStitching = true;  //disable dji video preview during stitching to reduce cpu and memory usage
    private boolean isPhantom3UseJoystick = true;  //true:use joystick;false:use waypoint action.we have two example to rotate phantom yaw and take picture 360 degrees
    /*********************Config zone*********************/
	
	//Load jni library
	static
	{
	    System.loadLibrary("PanoDemo");
	}
	//jni
	public native String testjni();  //just test jni function
	public native int jnistitching(String[] source,String result,double scale);  //jni stitching
	//Environment
	private static final String TAG = "PanoDemoMainActivity";  //debug TAG
	private final String STITCHING_SOURCE_IMAGES_DIRECTORY = Environment.getExternalStorageDirectory().getPath()+"/PanoDemo/";  //path:/storage/emulated/0/OpenCV_Panorama_Images/
	private final String STITCHING_RESULT_IMAGES_DIRECTORY = Environment.getExternalStorageDirectory().getPath()+"/PanoDemo/result/";  //path:/storage/emulated/0/OpenCV_Panorama_Images/result/
	private final int COMMON_MESSAGE_DURATION_TIME = 2500;  //in milliseconds
	//DJI part
	private DJIDroneType mDroneType;  //drone type:support inspire1 and phantom 3 professional now
	private boolean isGroundstationOpenSuccess = false;
	private static boolean isDJIAoaStarted = false;  //DJIAoa
	private DJIReceivedVideoDataCallBack mReceivedVideoDataCallBack;
	private DJIGimbalUpdateAttitudeCallBack mGimbalUpdateAttitudeCallBack;
	private DJIGimbalErrorCallBack mGimbalErrorCallBack;
	private DJICameraPlayBackStateCallBack mCameraPlayBackStateCallBack;  //to get current selected picture count
	private DJIBatteryUpdateInfoCallBack mBattryUpdateInfoCallBack;
	private DJIGimbalCapacity mDjiGimbalCapacity;  //BUG:return null in DJI SDK v2.1.0
	private DJIMcuUpdateStateCallBack mDjiMcuUpdateStateCallBack;
	private DJIGroundStationExecutionPushInfoCallBack mDjiGroundStationExecutionPushInfoCallBack;
	private double droneAltitude=0.0;  //drone altitude
	private double droneLocationLatitude = 0.0,droneLocationLongitude = 0.0;  //drone gps data
	//Others
    private final int CAPTURE_IMAGE_NUMBER = 8;  //image number for stitching
    private final int CAPTURE_IMAGE_GIMBAL_INIT_POSITION = -2300;  //-2300 for inspire1
    private final int HANDLER_SHOW_COMMON_MESSAGE =                   1000;
    private final int HANDLER_SET_STITCHING_BUTTON_TEXT =             1001;
    private final int HANDLER_START_STITCHING =                       1002;
    private final int HANDLER_ENABLE_STITCHING_BUTTON =               1003;
    private final int HANDLER_JAVA_SHOW_JNI_STITCHING_COST_TIME =     1004;
    private final int HANDLER_SHOW_STITCHING_OR_NOT_DIALOG =          1005;
    private final int HANDLER_SHOW_STITCHING_RESULT_IMAGEVIEW =       1006;
    private final int HANDLER_INSPIRE1_CAPTURE_IMAGES =               2000;
    private final int HANDLER_PHANTOM3PROFESSIONAL_CAPTURE_IMAGES =   2001;
    private final int HANDLER_PHANTOM3PROFESSIONAL_WA_CAPTURE_IMAGES =2002;  //V1.1.0 new feature,use Groundstaion waypoint action to take picture
    private final int HANDLER_SET_DJI_CAMERA_CAPTURE_MODE =           2003;
    private final int HANDLER_SET_DJI_CAMERA_PALYBACK_MODE =          2004;
    private final int HANDLER_SET_DJI_CAMERA_MULTI_PREVIEW_MODE =     2005;
    private final int HANDLER_SET_DJI_CAMERA_MULTI_EDIT_MODE =        2006;
    private final int HANDLER_SET_DJI_CAMERA_SELECT_PAGE =            2007;
    private final int HANDLER_SET_DJI_CAMERA_PREVIOUS_PAGE =          2008;
    private final int HANDLER_SET_DJI_CAMERA_SELECT_FILE_AT_INDEX =   2009;
    private final int HANDLER_SET_DJI_CAMERA_DOWNLOAD_SELECTED =      2010;
    private final int HANDLER_SET_DJI_CAMERA_FINISH_DOWNLOAD_FILES =  2011;
    private final int HANDLER_ENABLE_DJI_VIDEO_PREVIEW =              2012;
    private final int HANDLER_DISABLE_DJI_VIDEO_PREVIEW =             2013;
    private int numbersOfSelected = 0;  //update from mCameraPlayBackStateCallBack
    private int captureImageFailedCount = 0;
    private int downloadImageFailedCount = 0;
    private ProgressDialog mDownloadDialog;
    private String stitchingResultImagePath="";
    private boolean isStitchingCompleted =false;
	
	//print log message
	private void showLOG(String str)
	{
		Log.e(TAG, str);
	}
	
	//show Toast
	private void showToast(String str)
	{
		Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
	}
	
    //java show jniStitching cost time
    private void javaShowJniStitchingCostTime(double costTime)
    {
        handler.sendMessage(handler.obtainMessage(HANDLER_JAVA_SHOW_JNI_STITCHING_COST_TIME,""+costTime));  //change double to string
    }
	
	//init UI
    private DjiGLSurfaceView mDjiGLSurfaceView;
    private TextView commonMessageTextView;  //show common messages
    private LinearLayout centerLinearLayout;  //hold start button to center screen
    private Button startButton;  //start video preview
    private Button testButton;
    private Button stitchingButton;
    private TextView batteryTextView;
    private void initUIControls()
    {
        initDownloadProgressDialog();
        mDjiGLSurfaceView=(DjiGLSurfaceView)findViewById(R.id.mDjiSurfaceView);
        commonMessageTextView=(TextView)findViewById(R.id.commonMessageTextView);
        centerLinearLayout=(LinearLayout)findViewById(R.id.centerLinearLayout);
        startButton=(Button)findViewById(R.id.startButton);
        testButton=(Button)findViewById(R.id.testButton);
        stitchingButton=(Button)findViewById(R.id.stitchingButton);
        batteryTextView=(TextView)findViewById(R.id.batteryTextView);
        //Add Listener
        startButton.setOnClickListener(this);
        testButton.setOnClickListener(this);
        stitchingButton.setOnClickListener(this);
        //Customize controls
        commonMessageTextView.setText("");
        startButton.setClickable(false);
        stitchingButton.setEnabled(false);
        stitchingButton.setText(getString(R.string.one_key_panorama));
        testButton.setVisibility(View.INVISIBLE);
        batteryTextView.setText("");
    }
    
    //button click
    @Override
    public void onClick(View v)
    {
        switch(v.getId())
        {
        case R.id.startButton:
        {
            startDJICamera();
            centerLinearLayout.setVisibility(View.INVISIBLE);  //hide center region controls
            stitchingButton.setEnabled(true);
            break;
        }
        case R.id.testButton:
        {
            //test stitching
            new Thread()
            {
                public void run()
                {
                    String[] source=getDirectoryFilelist(STITCHING_SOURCE_IMAGES_DIRECTORY);
                    String result=STITCHING_RESULT_IMAGES_DIRECTORY+getCurrentDateTime()+"result.jpg";
                    if(jnistitching(source, result, STITCH_IMAGE_SCALE)==0)
                    {
                        handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,"Stitching success"));
                    }
                    else
                    {
                        handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,"Stitching error"));
                    }
                    handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_STITCHING_BUTTON,""));
                }
            }.start();
            break;
        }
        case R.id.stitchingButton:
        {
            //clean source folder
            cleanSourceFolder();
            stitchingButton.setEnabled(false);
            stitchingButton.setText(getString(R.string.one_key_panorama));
            if(mDroneType==DJIDroneType.DJIDrone_Inspire1)
            {
                handler.sendMessage(handler.obtainMessage(HANDLER_INSPIRE1_CAPTURE_IMAGES,""));
            }
            else if(mDroneType==DJIDroneType.DJIDrone_Phantom3_Professional)
            {
            	//we have two example:joystick and waypoint action
            	if(isPhantom3UseJoystick==true)
            	{
            		handler.sendMessage(handler.obtainMessage(HANDLER_PHANTOM3PROFESSIONAL_CAPTURE_IMAGES,""));
            	}
            	else
            	{
                    handler.sendMessage(handler.obtainMessage(HANDLER_PHANTOM3PROFESSIONAL_WA_CAPTURE_IMAGES,""));
            	}
            }
            else
            {
                showCommonMessage(getString(R.string.unsupported_drone));
            }
            break;
        }
        default:
        {
            break;
        }
        }
    }
    
	//handler message
    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg)
        {
            switch (msg.what)
            {
            case HANDLER_SHOW_COMMON_MESSAGE:
            {
            	showCommonMessage((String)msg.obj);
            	break;
            }
            case HANDLER_SET_STITCHING_BUTTON_TEXT:
            {
            	stitchingButton.setText((String)msg.obj);
            	break;
            }
            case HANDLER_ENABLE_STITCHING_BUTTON:
            {
                stitchingButton.setEnabled(true);
                break;
            }
            case HANDLER_SHOW_STITCHING_OR_NOT_DIALOG:
            {
                //capture complete,show dialog,user determing stitching or cancel
                DialogInterface.OnClickListener positiveButtonOnClickListener=new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        //set dji camera playback mode
                        handler.sendMessage(handler.obtainMessage(HANDLER_SET_DJI_CAMERA_PALYBACK_MODE, ""));
                    }
                };
                DialogInterface.OnClickListener negativeButtonOnClickListener=new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,getString(R.string.one_key_panorama)));
                        handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_STITCHING_BUTTON,""));
                    }
                };
                new AlertDialog.Builder(MainActivity.this).setTitle("Message").setMessage("Capture complete,stitching?").setPositiveButton("OK", positiveButtonOnClickListener).setNegativeButton("Cancel", negativeButtonOnClickListener).show();
                break;
            }
            case HANDLER_START_STITCHING:
            {
                if(isDIsableDJIVideoPreviewDuringStitching)
                {
                    new Thread()
                    {
                        public void run()
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_DISABLE_DJI_VIDEO_PREVIEW,""));  //disable dji video preview
                            while(isStitchingCompleted==false)
                            {
                                handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, getString(R.string.video_preview_disabled_during_stitching)));
                                try
                                {
                                    sleep(4000);
                                }
                                catch(InterruptedException e)
                                {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }.start();
                }
                handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,"Stitching...."));
                new Thread()
                {
                    public void run()
                    {
                        isStitchingCompleted=false;
                        String[] source=getDirectoryFilelist(STITCHING_SOURCE_IMAGES_DIRECTORY);
                        stitchingResultImagePath=STITCHING_RESULT_IMAGES_DIRECTORY+getCurrentDateTime()+"result.jpg";
                        if(jnistitching(source, stitchingResultImagePath, STITCH_IMAGE_SCALE)==0)
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,"Stitching success"));
                        }
                        else
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,"Stitching error"));
                        }
                        handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,getString(R.string.one_key_panorama)));
                        handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_STITCHING_BUTTON,""));
                        handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_DJI_VIDEO_PREVIEW,""));
                        isStitchingCompleted=true;
                    }
                }.start();
            	break;
            }
            case HANDLER_SHOW_STITCHING_RESULT_IMAGEVIEW:
            {
                //use android system imageview
                File file = new File(stitchingResultImagePath);
                if(file != null && file.isFile())
                {
                    Intent intent = new Intent();
                    intent.setAction(android.content.Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.fromFile(file), "image/*");
                    startActivity(intent);   
                }
                break;
            }
            case HANDLER_JAVA_SHOW_JNI_STITCHING_COST_TIME:
            {
                String costTime_str=(String)msg.obj;  //original str like this:19.492691
                costTime_str=costTime_str.substring(0, costTime_str.indexOf('.')+2);  //result like this:19.4
                showLOG("java:jniStitching cost time= "+costTime_str+" seconds");
                //show open or done dialog
                DialogInterface.OnClickListener positiveButtonOnClickListener=new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        //set dji camera playback mode
                        handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_STITCHING_RESULT_IMAGEVIEW, ""));
                    }
                    
                };
                new AlertDialog.Builder(MainActivity.this).setTitle("Stitching success").setMessage("Cost time "+costTime_str+" seconds").setPositiveButton("Open", positiveButtonOnClickListener).setNegativeButton("Done", null).show();
                break;
            }
            case HANDLER_INSPIRE1_CAPTURE_IMAGES:
            {
                new Thread()
                {
                    public void run()
                    {
                        //rotate gimble to take photos
                        int imgIndex=0;
                        showCommonMessage(getString(R.string.init_gimabal_yaw));
                        //init the gimbal yaw to Clockwise Min
                        while(DJIDrone.getDjiGimbal().getYawAngle()>CAPTURE_IMAGE_GIMBAL_INIT_POSITION)
                        {
                            DJIGimbalRotation mYaw_relative = new DJIGimbalRotation(true,false,false, 1000);
                            DJIDrone.getDjiGimbal().updateGimbalAttitude(null,null,mYaw_relative);
                            try
                            {
                                sleep(50);
                            }
                            catch(InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        DJIGimbalRotation mYaw_init_stop = new DJIGimbalRotation(true,false,false, 0);
                        DJIDrone.getDjiGimbal().updateGimbalAttitude(null,null,mYaw_init_stop);
                        try
                        {
                            sleep(3000);
                        }
                        catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        for(int i=-180;i<0;i+=(360/CAPTURE_IMAGE_NUMBER))
                        {
                            imgIndex++;
                            showCommonMessage(getString(R.string.capturing_image)+" "+imgIndex+"/"+CAPTURE_IMAGE_NUMBER);
                            DJIGimbalRotation mYaw = new DJIGimbalRotation(true,true,true, i);
                            DJIDrone.getDjiGimbal().updateGimbalAttitude(null,null,mYaw);
                            try
                            {
                                sleep(3000);
                            }
                            catch(InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                            DJICameraTakePhoto();
                            try
                            {
                                sleep(3000);
                            }
                            catch(InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        for(int i=0;i<180;i+=(360/CAPTURE_IMAGE_NUMBER))
                        {
                            imgIndex++;
                            showCommonMessage(getString(R.string.capturing_image)+imgIndex+"/"+CAPTURE_IMAGE_NUMBER);
                            DJIGimbalRotation mYaw = new DJIGimbalRotation(true,true,true, i);
                            DJIDrone.getDjiGimbal().updateGimbalAttitude(null,null,mYaw);
                            try
                            {
                                sleep(3000);
                            }
                            catch(InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                            DJICameraTakePhoto();
                            try
                            {
                                sleep(3000);
                            }
                            catch(InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        //gimbal yaw face front
                        showCommonMessage(getString(R.string.capture_image_complete));
                        DJIGimbalRotation mYaw_front = new DJIGimbalRotation(true,false,true, 0);
                        DJIDrone.getDjiGimbal().updateGimbalAttitude(null,null,mYaw_front);
                        try
                        {
                            Thread.sleep(3000);
                        }
                        catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        if(captureImageFailedCount!=0)
                        {
                            showCommonMessage("Check "+captureImageFailedCount+" images capture failed,Task Abort!");
                            captureImageFailedCount=0;
                            handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,getString(R.string.one_key_panorama)));
                            handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_STITCHING_BUTTON,""));
                        }
                        else
                        {
                            showCommonMessage("Check "+CAPTURE_IMAGE_NUMBER+" images capture all success,continue....");
                            try
                            {
                                Thread.sleep(3000);
                            }
                            catch (InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                            //show dialog
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_STITCHING_OR_NOT_DIALOG, ""));
                        }
                    }
                }.start();
            	break;
            }
            case HANDLER_PHANTOM3PROFESSIONAL_CAPTURE_IMAGES:
            {
                new Thread()
                {
                    public void run()
                    {
                        isGroundstationOpenSuccess=false;
                        DJIDrone.getDjiGroundStation().openGroundStation(new DJIGroundStationExecuteCallBack()
                        {
                            @Override
                            public void onResult(GroundStationResult result)
                            {
                                handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, result.toString()));
                                if(result==GroundStationResult.GS_Result_Success)
                                {
                                    isGroundstationOpenSuccess=true;
                                }
                            }
                        });
                        try
                        {
                            sleep(3000);
                        }
                        catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        if(isGroundstationOpenSuccess==false)
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,getString(R.string.one_key_panorama)));
                            handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_STITCHING_BUTTON,""));
                            return;
                        }
                        showCommonMessage(getString(R.string.groundstation_take_control));
                        try
                        {
                            sleep(3000);
                        }
                        catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        showCommonMessage("Set yaw control mode to angle");
                        DJIDrone.getDjiGroundStation().setYawControlMode(DJINavigationFlightControlYawControlMode.Navigation_Flight_Control_Yaw_Control_Angle);
                        try
                        {
                            sleep(3000);
                        }
                        catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        //rotate yaw to take photos
                        int imgIndex=0;
                        for(int i=0;i<180;i+=(360/CAPTURE_IMAGE_NUMBER))
                        {
                            imgIndex++;
                            showCommonMessage(getString(R.string.capturing_image)+" "+imgIndex+"/"+CAPTURE_IMAGE_NUMBER);
                            DJIDrone.getDjiGroundStation().sendFlightControlData(i, 0, 0, 0, new DJIExecuteResultCallback()
                            {
                                @Override
                                public void onResult(DJIError mErr)
                                {
                                    if(mErr.errorCode==DJIError.RESULT_OK)
                                    {
                                        
                                    }
                                    else
                                    {
                                        
                                    }
                                }
                            });
                            try
                            {
                                sleep(4000);
                            }
                            catch(InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                            DJICameraTakePhoto();
                            try
                            {
                                sleep(3000);
                            }
                            catch(InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        for(int i=-180;i<0;i+=(360/CAPTURE_IMAGE_NUMBER))
                        {
                            imgIndex++;
                            showCommonMessage(getString(R.string.capturing_image)+imgIndex+"/"+CAPTURE_IMAGE_NUMBER);
                            DJIDrone.getDjiGroundStation().sendFlightControlData(i, 0, 0, 0, new DJIExecuteResultCallback()
                            {
                                @Override
                                public void onResult(DJIError mErr)
                                {
                                    if(mErr.errorCode==DJIError.RESULT_OK)
                                    {
                                        
                                    }
                                    else
                                    {
                                        
                                    }
                                }
                            });
                            try
                            {
                                sleep(4000);
                            }
                            catch(InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                            DJICameraTakePhoto();
                            try
                            {
                                sleep(3000);
                            }
                            catch(InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        //yaw back to init
                        showCommonMessage(getString(R.string.capture_image_complete));
                        DJIDrone.getDjiGroundStation().sendFlightControlData(0, 0, 0, 0, new DJIExecuteResultCallback()
                        {
                            @Override
                            public void onResult(DJIError mErr)
                            {
                                if(mErr.errorCode==DJIError.RESULT_OK)
                                {
                                    
                                }
                                else
                                {
                                    
                                }
                            }
                        });
                        try
                        {
                            sleep(4000);
                        }
                        catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        //close groundstation
                        DJIDrone.getDjiGroundStation().closeGroundStation(new DJIGroundStationExecuteCallBack()
                        {
                            @Override
                            public void onResult(GroundStationResult result)
                            {
                                handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, result.toString()));
                            }
                        });
                        showCommonMessage(getString(R.string.capture_image_complete));
                        try
                        {
                            Thread.sleep(3000);
                        }
                        catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        if(captureImageFailedCount!=0)
                        {
                            showCommonMessage("Check "+captureImageFailedCount+" images capture failed,Task Abort!");
                            captureImageFailedCount=0;
                            handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,getString(R.string.one_key_panorama)));
                            handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_STITCHING_BUTTON,""));
                        }
                        else
                        {
                            showCommonMessage("Check "+CAPTURE_IMAGE_NUMBER+" images capture all success,continue....");
                            try
                            {
                                Thread.sleep(3000);
                            }
                            catch (InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                            //show dialog
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_STITCHING_OR_NOT_DIALOG, ""));
                        }
                    }
                }.start();
                break;
            }
            case HANDLER_PHANTOM3PROFESSIONAL_WA_CAPTURE_IMAGES:
            {
            	//use waypoint action
                new Thread()
                {
                    public void run()
                    {
                        isGroundstationOpenSuccess=false;
                        DJIDrone.getDjiGroundStation().openGroundStation(new DJIGroundStationExecuteCallBack()
                        {
                            @Override
                            public void onResult(GroundStationResult result)
                            {
                                handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, result.toString()));
                                if(result==GroundStationResult.GS_Result_Success)
                                {
                                    isGroundstationOpenSuccess=true;
                                }
                            }
                        });
                        try
                        {
                            sleep(3000);
                        }
                        catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        if(isGroundstationOpenSuccess==false)
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,getString(R.string.one_key_panorama)));
                            handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_STITCHING_BUTTON,""));
                            return;
                        }
                        showCommonMessage(getString(R.string.groundstation_take_control));
                        try
                        {
                            sleep(3000);
                        }
                        catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        showCommonMessage("Uploading GroundStation Task...");
                        DJIDrone.getDjiGroundStation().setHorizontalControlCoordinateSystem(DJINavigationFlightControlCoordinateSystem.Navigation_Flight_Control_Coordinate_System_Body);
                		//NOTE:new version sdk V2.3.0 need at least two waypoint;one waypoint no more than 16 action,largest is 15
                        //this waypoint use to init drone yaw to 0
                		DJIGroundStationWaypoint waypoint1=new DJIGroundStationWaypoint(droneLocationLatitude, droneLocationLongitude);
                		waypoint1.altitude=(float)droneAltitude+0.1f;  //the unit is meter  //why add 0.1,see this bug:http://forum.dji.com/thread-28359-1-1.html
                		waypoint1.action.actionRepeat=1;
                		waypoint1.hasAction = true;
                		waypoint1.heading = 0;
                		waypoint1.actionTimeout = 60*10;  //The unit is s
                		waypoint1.turnMode = 1;
                		waypoint1.dampingDistance = 0.5f;
                		waypoint1.addAction(GroundStationOnWayPointAction.Way_Point_Action_Craft_Yaw, 0);
                        //this waypoint use to rotate drone yaw and take picture
                		DJIGroundStationWaypoint waypoint2=new DJIGroundStationWaypoint(droneLocationLatitude, droneLocationLongitude);
                		waypoint2.altitude=(float)droneAltitude+0.2f;  //the unit is meter
                		waypoint2.action.actionRepeat=1;
                		waypoint2.hasAction = true;
                		waypoint2.heading = 0;
                		waypoint2.actionTimeout = 60*10;  //The unit is s
                		waypoint2.turnMode = 1;
                		waypoint2.dampingDistance = 0.5f;
                		for(int i=0;i<180;i+=(360/CAPTURE_IMAGE_NUMBER))
                		{
                			//ignore first yaw position 0,because we only have max 15 action,so bad
                			if(i!=0)
                			{
                				waypoint2.addAction(GroundStationOnWayPointAction.Way_Point_Action_Craft_Yaw, i);
                			}
                			waypoint2.addAction(GroundStationOnWayPointAction.Way_Point_Action_Simple_Shot, 1);
                		}
                		for(int i=-180;i<0;i+=(360/CAPTURE_IMAGE_NUMBER))
                		{
                			waypoint2.addAction(GroundStationOnWayPointAction.Way_Point_Action_Craft_Yaw, i);
                			waypoint2.addAction(GroundStationOnWayPointAction.Way_Point_Action_Simple_Shot, 1);
                		}
                		//this waypoint use to reset drone yaw position to 0
                		DJIGroundStationWaypoint waypoint3=new DJIGroundStationWaypoint(droneLocationLatitude, droneLocationLongitude);
                		waypoint3.altitude=(float)droneAltitude+0.3f;  //the unit is meter
                		waypoint3.action.actionRepeat=1;
                		waypoint3.hasAction = true;
                		waypoint3.heading = 0;
                		waypoint3.actionTimeout = 60*10;  //The unit is s
                		waypoint3.turnMode = 1;
                		waypoint3.dampingDistance = 0.5f;
                		waypoint3.addAction(GroundStationOnWayPointAction.Way_Point_Action_Craft_Yaw, 0);  //reset drone yaw to 0
                		//generate gs task
                		DJIGroundStationTask task=new DJIGroundStationTask();
                		task.addWaypoint(waypoint1);
                		task.addWaypoint(waypoint2);
                		task.addWaypoint(waypoint3);
                		task.finishAction=DJIGroundStationFinishAction.None;
                		//upload gs task to drone
                		DJIDrone.getDjiGroundStation().uploadGroundStationTask(task, new DJIGroundStationExecuteCallBack()
                		{
                			@Override
                			public void onResult(GroundStationResult result)
                			{
                				if(result==GroundStationResult.GS_Result_Success)
                				{
                					showCommonMessage("Upload GroundStation Task success");
                				}
                				else
                				{
                					showCommonMessage("Upload GroundStation Task failed:"+result.toString());
                                    handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,getString(R.string.one_key_panorama)));
                                    handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_STITCHING_BUTTON,""));
                                    return;
                				}
                			}
                		});
                        try
                        {
                            sleep(5000);
                        }
                        catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        //start gs task
        				DJIDrone.getDjiGroundStation().startGroundStationTask(new DJIGroundStationExecuteCallBack()
        				{
        					@Override
        					public void onResult(GroundStationResult result)
        					{
        						if(result==GroundStationResult.GS_Result_Success)
        						{
        							showCommonMessage("Start GroundStation Task success");
        						}
        						else
        						{
        							showCommonMessage("Start GroundStation Task failed:"+result.toString());
                                    handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,getString(R.string.one_key_panorama)));
                                    handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_STITCHING_BUTTON,""));
                                    return;
        						}
        					}
        				});
                        try
                        {
                            sleep(3000);
                        }
                        catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }.start();
                break;
            }
            case HANDLER_SET_DJI_CAMERA_CAPTURE_MODE:
            {
                CameraMode mode=CameraMode.Camera_Capture_Mode;
                DJIDrone.getDjiCamera().setCameraMode(mode, new DJIExecuteResultCallback()
                {
                    @Override
                    public void onResult(DJIError mErr)
                    {
                        if(mErr.errorCode==DJIError.RESULT_OK)
                        {
                            
                        }
                        else
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Set camera capture mode failed"));
                        }
                    }
                });
            	break;
            }
            case HANDLER_SET_DJI_CAMERA_PALYBACK_MODE:
            {
                //set camera playback mode to pull back images
                showCommonMessage("Set camera playback mode");
                CameraMode mode_playback = CameraMode.Camera_PlayBack_Mode;
                DJIDrone.getDjiCamera().setCameraMode(mode_playback, new DJIExecuteResultCallback()
                {
                    @Override
                    public void onResult(DJIError mErr)
                    {
                        if(mErr.errorCode==DJIError.RESULT_OK)
                        {
                            //enter multi preview mode
                            new Thread()
                            {
                                public void run()
                                {
                                    try
                                    {
                                        Thread.sleep(3000);
                                    }
                                    catch(InterruptedException e)
                                    {
                                        e.printStackTrace();
                                    }
                                    //enter multi preview mode
                                    handler.sendMessage(handler.obtainMessage(HANDLER_SET_DJI_CAMERA_MULTI_PREVIEW_MODE, ""));
                                }
                            }.start();
                        }
                        else
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Set camera playback mode failed"));
                        }
                    }
                });
            	break;
            }
            case HANDLER_SET_DJI_CAMERA_MULTI_PREVIEW_MODE:
            {
                //enter multi preview mode
                showCommonMessage("Enter multi preview mode");
                DJIDrone.getDjiCamera().enterMultiplePreviewMode(new DJIExecuteResultCallback()
                {
                    @Override
                    public void onResult(DJIError mErr)
                    {
                        if(mErr.errorCode==DJIError.RESULT_OK)
                        {
                            new Thread()
                            {
                                public void run()
                                {
                                    try
                                    {
                                        Thread.sleep(3000);
                                    }
                                    catch(InterruptedException e)
                                    {
                                        e.printStackTrace();
                                    }
                                    //enter multi edit mode
                                    handler.sendMessage(handler.obtainMessage(HANDLER_SET_DJI_CAMERA_MULTI_EDIT_MODE, ""));
                                }
                            }.start();
                        }
                        else
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Enter multi preview mode failed"));
                        }
                    }
                });
            	break;
            }
            case HANDLER_SET_DJI_CAMERA_MULTI_EDIT_MODE:
            {
                //enter multi edit mode
                showCommonMessage("Enter multi edit mode");
                DJIDrone.getDjiCamera().enterMultipleEditMode(new DJIExecuteResultCallback()
                {
                    @Override
                    public void onResult(DJIError mErr)
                    {
                        if(mErr.errorCode==DJIError.RESULT_OK)
                        {
                            new Thread()
                            {
                                public void run()
                                {
                                    try
                                    {
                                        Thread.sleep(3000);
                                    }
                                    catch(InterruptedException e)
                                    {
                                        e.printStackTrace();
                                    }
                                    //select page(max 8)
                                    handler.sendMessage(handler.obtainMessage(HANDLER_SET_DJI_CAMERA_SELECT_PAGE, ""));
                                }
                            }.start();
                        }
                        else
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Enter multi edit mode failed"));
                        }
                    }
                });
            	break;
            }
            case HANDLER_SET_DJI_CAMERA_SELECT_PAGE:
            {
                //select page(max 8)
                showCommonMessage("Select all file in page");
                DJIDrone.getDjiCamera().selectAllFilesInPage(new DJIExecuteResultCallback()
                {
                    @Override
                    public void onResult(DJIError mErr)
                    {
                        if(mErr.errorCode==DJIError.RESULT_OK)
                        {
                            new Thread()
                            {
                                public void run()
                                {
                                    try
                                    {
                                        Thread.sleep(3000);
                                    }
                                    catch(InterruptedException e)
                                    {
                                        e.printStackTrace();
                                    }
                                    if(numbersOfSelected<CAPTURE_IMAGE_NUMBER)
                                    {
                                        //enter previous page
                                        handler.sendMessage(handler.obtainMessage(HANDLER_SET_DJI_CAMERA_PREVIOUS_PAGE, ""));
                                    }
                                    else
                                    {
                                        //download selected
                                        handler.sendMessage(handler.obtainMessage(HANDLER_SET_DJI_CAMERA_DOWNLOAD_SELECTED, ""));
                                    }
                                }
                            }.start();
                        }
                        else
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Select all file in page failed"));
                        }
                    }
                    
                });
            	break;
            }
            case HANDLER_SET_DJI_CAMERA_PREVIOUS_PAGE:
            {
                //if no enough in this page,go back previous page
                showCommonMessage("No enough images,go back previous page");
                DJIDrone.getDjiCamera().multiplePreviewPreviousPage(new DJIExecuteResultCallback()
                {
                    @Override
                    public void onResult(DJIError mErr)
                    {
                        if(mErr.errorCode==DJIError.RESULT_OK)
                        {
                            new Thread()
                            {
                                public void run()
                                {
                                    try
                                    {
                                        Thread.sleep(3000);
                                    }
                                    catch(InterruptedException e)
                                    {
                                        e.printStackTrace();
                                    }
                                    //go back previous page
                                    handler.sendMessage(handler.obtainMessage(HANDLER_SET_DJI_CAMERA_SELECT_FILE_AT_INDEX, ""));
                                }
                            }.start();
                        }
                        else
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Go back previous page failed"));
                        }
                    }
                });
            	break;
            }
            case HANDLER_SET_DJI_CAMERA_SELECT_FILE_AT_INDEX:
            {
                new Thread()
                {
                    public void run()
                    {
                        showCommonMessage("Select rest "+(CAPTURE_IMAGE_NUMBER-numbersOfSelected)+" images");
                        for(int i=numbersOfSelected;i<CAPTURE_IMAGE_NUMBER;i++)
                        {
                            //select single file
                            DJIDrone.getDjiCamera().selectFileAtIndex(i, new DJIExecuteResultCallback()
                            {
                                @Override
                                public void onResult(DJIError mErr)
                                {
                                    if(mErr.errorCode==DJIError.RESULT_OK)
                                    {
                                        
                                    }
                                    else
                                    {
                                        
                                    }
                                }
                            });
                            try
                            {
                                Thread.sleep(1000);
                            }
                            catch (InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        try
                        {
                            Thread.sleep(1000);
                        }
                        catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        //download selected
                        handler.sendMessage(handler.obtainMessage(HANDLER_SET_DJI_CAMERA_DOWNLOAD_SELECTED, ""));
                    }
                }.start();
            	break;
            }
            case HANDLER_SET_DJI_CAMERA_DOWNLOAD_SELECTED:
            {
                //download file
            	File downloadPath=new File(STITCHING_SOURCE_IMAGES_DIRECTORY);
                DJIDrone.getDjiCamera().downloadAllSelectedFiles(downloadPath,new DJIFileDownloadCallBack()
                {
                    @Override
                    public void OnStart()
                    {
                        runOnUiThread(new Runnable()
                        {               
                            @Override
                            public void run()
                            {
                                showDownloadProgressDialog();
                            }
                        });
                    }
                    
                    @Override
                    public void OnError(Exception exception)
                    {
                        if(isCheckDownloadImageFailure)
                        {
                            downloadImageFailedCount++;
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Downloading images on error"));
                        }
                    }
                    
                    @Override
                    public void OnEnd()
                    {
                        new Thread()
                        {
                            public void run()
                            {
                                handler.sendMessage(handler.obtainMessage(HANDLER_SET_DJI_CAMERA_FINISH_DOWNLOAD_FILES,""));
                                handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Download finished"));
                                try
                                {
                                    Thread.sleep(3000);
                                }
                                catch (InterruptedException e)
                                {
                                    e.printStackTrace();
                                }
                                handler.sendMessage(handler.obtainMessage(HANDLER_SET_DJI_CAMERA_CAPTURE_MODE,""));
                                try
                                {
                                    Thread.sleep(3000);
                                }
                                catch (InterruptedException e)
                                {
                                    e.printStackTrace();
                                }
                                //some images download failed
                                if(downloadImageFailedCount!=0)
                                {
                                    handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Check "+downloadImageFailedCount+" images download failed,Task Abort!"));
                                    downloadImageFailedCount=0;
                                    handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,getString(R.string.one_key_panorama)));
                                    handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_STITCHING_BUTTON,""));
                                }
                                else
                                {
                                    handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Check "+CAPTURE_IMAGE_NUMBER+" images download all success,stitching...."));
                                    try
                                    {
                                        Thread.sleep(3000);
                                    }
                                    catch (InterruptedException e)
                                    {
                                        e.printStackTrace();
                                    }
                                    handler.sendMessage(handler.obtainMessage(HANDLER_START_STITCHING,""));
                                }
                            }
                        }.start();
                    }
                    
                    @Override
                    public void OnProgressUpdate(final int progress)
                    {
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                if(mDownloadDialog!=null)
                                {
                                    mDownloadDialog.setProgress(progress);
                                }
                                if(progress>=100)
                                {
                                    hideDownloadProgressDialog();
                                }
                            }
                        });
                    }
                });
            	break;
            }
            case HANDLER_SET_DJI_CAMERA_FINISH_DOWNLOAD_FILES:
            {
                //finish download
                DJIDrone.getDjiCamera().finishDownloadAllSelectedFiles(new DJIExecuteResultCallback()
                {
                    @Override
                    public void onResult(DJIError mErr)
                    {
                        if(mErr.errorCode==DJIError.RESULT_OK)
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Finished download"));
                        }
                        else
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Finished download failed"));
                        }
                    }
                });
            	break;
            }
            case HANDLER_ENABLE_DJI_VIDEO_PREVIEW:
            {
                DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(mReceivedVideoDataCallBack);
                break;
            }
            case HANDLER_DISABLE_DJI_VIDEO_PREVIEW:
            {
                DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(null);
                break;
            }
            default:
            {
                break;
            }
            }
            return false;
        }
    });
	
    //init download progress dialog
    private void initDownloadProgressDialog()
    {
        mDownloadDialog = new ProgressDialog(MainActivity.this);
        mDownloadDialog.setTitle(R.string.downloading);
        mDownloadDialog.setIcon(android.R.drawable.ic_dialog_info);
        mDownloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDownloadDialog.setCanceledOnTouchOutside(false);
        mDownloadDialog.setCancelable(false);
    }
    
    //show download progress dialog
    private void showDownloadProgressDialog()
    {
        if(mDownloadDialog != null)
        {
            mDownloadDialog.show();
            mDownloadDialog.setProgress(0);
        }
    }
    
    //hide download progress dialog
    private void hideDownloadProgressDialog() {
        if (null != mDownloadDialog && mDownloadDialog.isShowing())
        {
            mDownloadDialog.dismiss();
        }
    }
	
	//show common message
	private Timer commonMessageTimer = new Timer();
	class commonMessageCleanTask extends TimerTask
	{
		@Override
		public void run()
		{
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					commonMessageTextView.setText("");
				}
			});
		}
	}
    private void showCommonMessage(final String message)
    {
    	runOnUiThread(new Runnable()
    	{			
			@Override
			public void run()
			{
				if(message.equals(commonMessageTextView.getText()))
				{
					//filter same message
					return;
				}
				commonMessageTextView.setText(message);
				commonMessageTimer.schedule(new commonMessageCleanTask(), COMMON_MESSAGE_DURATION_TIME);
			}
		});
    }
	
	//init stitching image folder
	private void initStitchingImageDirectory()
	{
		//check exists,if not,create
		File sourceDirectory = new File(STITCHING_SOURCE_IMAGES_DIRECTORY);
		if(!sourceDirectory.exists())
		{
			sourceDirectory.mkdirs();
		}
		File resultDirectory = new File(STITCHING_RESULT_IMAGES_DIRECTORY);
		if(!resultDirectory.exists())
		{
			resultDirectory.mkdirs();
		}
	}
	
	//clean source folder
	private void cleanSourceFolder()
	{
		File sourceDirectory = new File(STITCHING_SOURCE_IMAGES_DIRECTORY);
		//clean source file,except folders
		for(File file : sourceDirectory.listFiles())
		{
			if(!file.isDirectory())
			{
				file.delete();
			}
		}
	}
	
	//get directory filelist
	private String[] getDirectoryFilelist(String directory)
	{
		String[] filelist;
		File sourceDirectory = new File(STITCHING_SOURCE_IMAGES_DIRECTORY);
		int index=0;
		int folderCount=0;
		//except folders
		for(File file : sourceDirectory.listFiles())
		{
			if(file.isDirectory())
			{
				folderCount++;
			}
		}
		filelist=new String[sourceDirectory.listFiles().length-folderCount];
		for(File file : sourceDirectory.listFiles())
		{
			if(!file.isDirectory())
			{
				//showLOG("getFilelist file:"+file.getPath());
				filelist[index]=file.getPath();
				index++;
			}
		}
		return filelist;
	}
	
	//OpenCVLoader callback
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    showLOG("OpenCV Manager loaded successfully");
                    break;
                }
                default:
                {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };
	
	//init OpenCVLoader
	private boolean initOpenCVLoader()
	{
        if (!OpenCVLoader.initDebug())
        {
            // Handle initialization error
        	showLOG("init buildin OpenCVLoader error,going to use OpenCV Manager");
        	OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
        	return false;
        }
        else
        {
        	showLOG("init buildin OpenCVLoader success");
        	return true;
        }
	}
	
	//activate DJI SDK(if not,DJI SDK can't use)
	private void activateDJISDK()
	{
        new Thread()
        {
            public void run()
            {
                try
                {
                    DJIDrone.checkPermission(getApplicationContext(), new DJIGerneralListener()
                    {
                        @Override
                        public void onGetPermissionResult(final int result)
                        {
                        	//result=0 is success
                            showLOG("DJI SDK onGetPermissionResult = "+result);
                            showLOG("DJI SDK onGetPermissionResultDescription = "+DJIError.getCheckPermissionErrorDescription(result));
                            if(result!=0)
                            {
                            	runOnUiThread(new Runnable()
                            	{
									public void run()
									{
		                            	showToast(getString(R.string.dji_sdk_activate_error)+":"+DJIError.getCheckPermissionErrorDescription(result));
									}
								});
                            }
                        }
                    });
                }
                catch(Exception e)
                {
                	showLOG("activateDJISDK() Exception");
                    e.printStackTrace();
                }
            }
        }.start();
	}
	
	//start DJIAoa
	private void startDJIAoa()
	{
        if(isDJIAoaStarted)
        {
            //Do nothing
        	showLOG("DJIAoa aready started");
        }
        else
        {
            ServiceManager.getInstance();
            UsbAccessoryService.registerAoaReceiver(this);
        	isDJIAoaStarted = true;
        	showLOG("DJIAoa start success");
        }
        Intent aoaIntent = getIntent();
        if(aoaIntent != null)
        {
            String action = aoaIntent.getAction();
            if(action==UsbManager.ACTION_USB_ACCESSORY_ATTACHED || action == Intent.ACTION_MAIN)
            {
                Intent attachedIntent = new Intent();
                attachedIntent.setAction(DJIUsbAccessoryReceiver.ACTION_USB_ACCESSORY_ATTACHED);
                sendBroadcast(attachedIntent);
            }
        }
	}
	
	//init DJI SDK
    private void initDJISDK()
    {
    	startDJIAoa();
    	activateDJISDK();
        // The SDK initiation for Inspire 1 or Phantom 3 Professional.
        DJIDrone.initWithType(this.getApplicationContext(), DJIDroneType.DJIDrone_Inspire1);  //here we use DJIDrone_Inspire1,it's compatible with Phantom 3 Professional
        DJIDrone.connectToDrone(); // Connect to the drone
    }
	
    //check DJI camera connect status
	private Timer checkCameraConnectionTimer = new Timer();
	class CheckCameraConnectionTask extends TimerTask
	{
		@Override
		public void run()
		{
			if(checkCameraConnectState()==true)
			{
				runOnUiThread(new Runnable() {
					public void run() {
						startButton.setBackgroundResource(R.drawable.start_green);
						startButton.setClickable(true);
					}
				});
			}
			else
			{
				runOnUiThread(new Runnable() {
					public void run() {
						startButton.setBackgroundResource(R.drawable.start_gray);
						startButton.setClickable(false);
						stitchingButton.setEnabled(false);
					}
				});
			}
		}
	}
    private boolean checkCameraConnectState(){
        //check connection
        boolean cameraConnectState = DJIDrone.getDjiCamera().getCameraConnectIsOk();
        if(cameraConnectState)
        {
        	//showLOG("DJI Camera connect ok");
        	return true;
        }
        else
        {
        	//showLOG("DJI Camera connect failed");
        	return false;
        }
    }
    
    //init DJI camera
    private void initDJICamera()
    {
        //check camera status every 3 seconds
        checkCameraConnectionTimer.schedule(new CheckCameraConnectionTask(), 1000, 3000);
    }
    
	//start DJI camera
	private void startDJICamera()
	{
        //check drone type
	    mDroneType=DJIDrone.getDroneType();
    	mDjiGLSurfaceView.start();
    	//decode video data
    	mReceivedVideoDataCallBack=new DJIReceivedVideoDataCallBack()
    	{
			@Override
			public void onResult(byte[] videoBuffer,int size)
			{
				//showLOG("videoData received");
				//showLOG(videoBuffer.toString()+"  "+size);
				mDjiGLSurfaceView.setDataToDecoder(videoBuffer, size);
			}
		};
		//gimbal attitude
        mGimbalUpdateAttitudeCallBack = new DJIGimbalUpdateAttitudeCallBack()
        {
            @Override
            public void onResult(DJIGimbalAttitude attitude)
            {
            	
            }
        };
        //gimbal error
        mGimbalErrorCallBack = new DJIGimbalErrorCallBack(){
            @Override
            public void onError(final int error)
            {
            	if(error!=DJIError.RESULT_OK)
            	{
                	runOnUiThread(new Runnable()
                	{
    					public void run()
    					{
    						showCommonMessage("Gimbal error code="+error);
    					}
    				});
            	}
            }    
        };
        //camera playback state
        mCameraPlayBackStateCallBack = new DJICameraPlayBackStateCallBack()
        {
            @Override
            public void onResult(DJICameraPlaybackState mState)
            {
            	numbersOfSelected=mState.numbersOfSelected;
            }
        };
        //battry
        mBattryUpdateInfoCallBack = new DJIBatteryUpdateInfoCallBack(){
            @Override
            public void onResult(final DJIBatteryProperty state)
            {
    			runOnUiThread(new Runnable()
    			{
					public void run()
					{
						batteryTextView.setText(getString(R.string.battery)+":"+state.remainPowerPercent+"%");
					}
				});
            }
        };
        
        //main controller
        mDjiMcuUpdateStateCallBack=new DJIMcuUpdateStateCallBack()
        {
			@Override
			public void onResult(DJIMainControllerSystemState state)
			{
				droneAltitude=state.altitude;
				droneLocationLatitude=state.droneLocationLatitude;
				droneLocationLongitude=state.droneLocationLongitude;
			}
		};
		//gs execute info
		mDjiGroundStationExecutionPushInfoCallBack=new DJIGroundStationExecutionPushInfoCallBack()
		{
			@Override
			public void onResult(final DJIGroundStationExecutionPushInfo info)
			{
				showCommonMessage(info.eventType.toString());
				//gs task finished
				if(info.eventType==GroundStationExecutionPushType.Navi_Mission_Finish)
				{
                    //close groundstation
                    DJIDrone.getDjiGroundStation().closeGroundStation(new DJIGroundStationExecuteCallBack()
                    {
                        @Override
                        public void onResult(GroundStationResult result)
                        {
                            handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, result.toString()));
                        }
                    });
                    showCommonMessage(getString(R.string.capture_image_complete));
                    try
                    {
                        Thread.sleep(3000);
                    }
                    catch(InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    if(captureImageFailedCount!=0)
                    {
                        showCommonMessage("Check "+captureImageFailedCount+" images capture failed,Task Abort!");
                        captureImageFailedCount=0;
                        handler.sendMessage(handler.obtainMessage(HANDLER_SET_STITCHING_BUTTON_TEXT,getString(R.string.one_key_panorama)));
                        handler.sendMessage(handler.obtainMessage(HANDLER_ENABLE_STITCHING_BUTTON,""));
                    }
                    else
                    {
                        showCommonMessage("Check "+CAPTURE_IMAGE_NUMBER+" images capture all success,continue....");
                        try
                        {
                            Thread.sleep(3000);
                        }
                        catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                        //show dialog
                        handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_STITCHING_OR_NOT_DIALOG, ""));
                    }
				}
			}
		};
        
		DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(mReceivedVideoDataCallBack);
        DJIDrone.getDjiGimbal().setGimbalUpdateAttitudeCallBack(mGimbalUpdateAttitudeCallBack);
        DJIDrone.getDjiGimbal().setGimbalErrorCallBack(mGimbalErrorCallBack);
        DJIDrone.getDjiCamera().setDJICameraPlayBackStateCallBack(mCameraPlayBackStateCallBack);
        DJIDrone.getDjiBattery().setBatteryUpdateInfoCallBack(mBattryUpdateInfoCallBack);
        DJIDrone.getDjiMainController().setMcuUpdateStateCallBack(mDjiMcuUpdateStateCallBack);
        DJIDrone.getDjiGroundStation().setGroundStationExecutionPushInfoCallBack(mDjiGroundStationExecutionPushInfoCallBack);
        
        DJIDrone.getDjiGimbal().startUpdateTimer(1000);
        DJIDrone.getDjiBattery().startUpdateTimer(2000);
        DJIDrone.getDjiMainController().startUpdateTimer(1000);
        DJIDrone.getDjiGroundStation().startUpdateTimer(1000);
	}
    
	//destroy DJI camera
	private void destroyDJICamera()
	{
		checkCameraConnectionTimer.cancel();
		if(DJIDrone.getDjiCamera()!=null)
		{
            DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(null);
            mDjiGLSurfaceView.destroy();
            DJIDrone.getDjiGimbal().setGimbalUpdateAttitudeCallBack(null);
            DJIDrone.getDjiGimbal().setGimbalErrorCallBack(null);
            DJIDrone.getDjiBattery().setBatteryUpdateInfoCallBack(null);
            DJIDrone.getDjiMainController().setMcuUpdateStateCallBack(null);
            DJIDrone.getDjiGroundStation().setGroundStationExecutionPushInfoCallBack(null);
            
            DJIDrone.getDjiGimbal().stopUpdateTimer();
            DJIDrone.getDjiBattery().stopUpdateTimer();
            DJIDrone.getDjiMainController().stopUpdateTimer();
            DJIDrone.getDjiGroundStation().stopUpdateTimer();
		}
	}
	
	//DJI camera take photo
	private void DJICameraTakePhoto()
	{
		CameraCaptureMode mode = CameraCaptureMode.Camera_Single_Capture;
        DJIDrone.getDjiCamera().startTakePhoto(mode, new DJIExecuteResultCallback()
        {
            @Override
            public void onResult(DJIError mErr)
            {
            	if(mErr.errorCode==DJIError.RESULT_OK)
            	{
            		showLOG("take photo success");
            	}
            	else
            	{
            	    if(isCheckCaptureImageFailure)
            	    {
                        captureImageFailedCount++;
                        handler.sendMessage(handler.obtainMessage(HANDLER_SHOW_COMMON_MESSAGE, "Capture image on error"));
            	    }
					showLOG("take photo failed");
            	}
            } 
        });
	}
	
	//get current datetime
	private String getCurrentDateTime()
	{
        Calendar c = Calendar .getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss",Locale.getDefault());
        return df.format(c.getTime());
	}
    
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_main);
		//onCreate init
		showLOG(testjni());
		initUIControls();
		initStitchingImageDirectory();
		initOpenCVLoader();
		initDJISDK();
		initDJICamera();
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		ServiceManager.getInstance().pauseService(false); // Resume the DJIAoa service
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
		ServiceManager.getInstance().pauseService(true); // Pause the DJIAoa service
	}
	
	@Override
	protected void onDestroy()
	{
		destroyDJICamera();
        DJIDrone.disconnectToDrone();
        showLOG("MainActivity onDestroy()");
		super.onDestroy();
	}
	
	//press again to exit
	private static boolean needPressAgain = false;
	private Timer ExitTimer = new Timer();
	class ExitCleanTask extends TimerTask
	{
		@Override
		public void run()
		{               
			needPressAgain = false;
		}
	}
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (needPressAgain) {
            	needPressAgain = false;
                finish();
            } 
            else 
            {
            	needPressAgain = true;
            	showToast(getString(R.string.pressAgainExitString));
                ExitTimer.schedule(new ExitCleanTask(), 2000);
            }
            return true;
        }
		return super.onKeyDown(keyCode, event);
	}
}
