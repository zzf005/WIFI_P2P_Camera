package com.example.alansio.wifi_p2p_camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Camera extends AppCompatActivity {

    private SurfaceView surfaceView;
    private SurfaceHolder holder;

    private CameraManager manager;
    private CaptureRequest.Builder builder;
    private CameraDevice cameraDevice;
    private CameraCaptureSession mPreviewSession;

    private HandlerThread handlerThread;
    private Handler handler;

    BufferedReader reader;
    Boolean THREAD_CLOSE = false;
    ReceiverThread receiver;

    private Size mSize;
    private MediaRecorder mMediaRecorder;

    public Timer timer;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }


    private ImageReader imageReader;

    private static final int REQUEST_CAMERA = 1;
    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    public static void verifyCameraPermissions(Activity camera_activity) {
        // Check if we have write permission

        int camera_permission=ActivityCompat.checkSelfPermission(camera_activity,Manifest.permission.CAMERA);
        if (camera_permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user

            ActivityCompat.requestPermissions(
                    camera_activity,
                    PERMISSIONS_CAMERA,
                    REQUEST_CAMERA
            );
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        verifyCameraPermissions(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        surfaceView = (SurfaceView) findViewById(R.id.camera_preview);

        // 取得Holder
        holder = surfaceView.getHolder();

        // 設定預覽大小
        holder.setFixedSize(surfaceView.getWidth(),surfaceView.getHeight());

        // 設定事件
        holder.addCallback(surfaceCallback);

        //
        mSize = new Size(1920, 1080);

        reader = WiFiDirectActivity.getReader();

        receiver = new Camera.ReceiverThread();
        receiver.start();
    }



    //receiver control msg by client
    public class ReceiverThread extends Thread {
        @Override
        public void run() {
            super.run();
            try {
                while (!THREAD_CLOSE) {
                    if (WiFiDirectActivity.isTakePhoto) {
                            Log.d("", "take photo");
                            takePicture();
                            WiFiDirectActivity.isTakePhoto = false;
                    }
                    else if(WiFiDirectActivity.isAutoTake)
                    {
                        Log.d("", "auto take");
                        timer = new Timer();
                        autoTake(timer, WiFiDirectActivity.feq);
                        WiFiDirectActivity.isAutoTake = false;
                    }
                    else if(WiFiDirectActivity.isStopAuto)
                    {
                        timer.cancel();
                        WiFiDirectActivity.isStopAuto = false;
                    }
                    else if(WiFiDirectActivity.isStartRecord)
                    {
                        startRecording();
                        WiFiDirectActivity.isStartRecord = false;
                    }
                    else if(WiFiDirectActivity.isStopRecord)
                    {
                        stopRecordingVideo();
                        WiFiDirectActivity.isStopRecord = false;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public class AutoTakeTask extends TimerTask
    {
        @Override
        public void run()
        {
            takePicture();
        }
    }

    public void autoTake(Timer timer, int feq)
    {
        timer.scheduleAtFixedRate(new AutoTakeTask(), 1000, feq * 1000);
    }

    public void takePicture()
    {
        try{

            ImageReader reader = ImageReader.newInstance(1920,1080,ImageFormat.JPEG,1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(1);
            outputSurfaces.add(reader.getSurface());

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener(){
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    ByteBuffer imagebuffer=image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[imagebuffer.capacity()];
                    imagebuffer.get(bytes);
                    BitmapFactory.Options option = new BitmapFactory.Options();
                    option.inSampleSize = getImageScale(bytes);
                    Bitmap bm = BitmapFactory.decodeByteArray(bytes,0,bytes.length,option);
                    new Filter().execute(bm);
                }
            };

            reader.setOnImageAvailableListener(readerListener,handler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    // 設定預覽模式
                    mPreviewSession = session;
                    try {
                        builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        // 將影像設定置SurfaceView
                        builder.addTarget(holder.getSurface());

                        // 建立影像傳輸
                        cameraDevice.createCaptureSession(Arrays.asList(holder.getSurface(), imageReader.getSurface()),
                                CameraCaptureCallback, handler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }


                }
            };

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mPreviewSession = session;
                    try {
                        session.capture(captureBuilder.build(), captureListener, handler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, handler);

        }catch(CameraAccessException e){
            e.printStackTrace();
        }
    }

    //setup MediaRecorder
    private void setUpMediaRecorder() throws  IOException
    {
        final Activity activity = this;
        if (null == activity) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mMediaRecorder.setOutputFile(getOutputVideoFile().getAbsolutePath());
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(25);
        mMediaRecorder.setVideoSize(mSize.getWidth(), mSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        /*
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        */
        mMediaRecorder.prepare();
    }

    private void closePreviewSession()
    {
        if(mPreviewSession != null)
        {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    //start recording
    private void startRecording()
    {
        try
        {
            closePreviewSession();
            setUpMediaRecorder();
            builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            surfaces.add(holder.getSurface());
            builder.addTarget(holder.getSurface());

            surfaces.add(mMediaRecorder.getSurface());
            builder.addTarget(mMediaRecorder.getSurface());

            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mPreviewSession = session;
                    updatePreview();
                    mMediaRecorder.start();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }


            }, handler);
        }
        catch (CameraAccessException | IOException e)
        {

        }
    }

    private void stopRecordingVideo()
    {
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        try {
            // 設定預覽模式
            builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // 將影像設定置SurfaceView
            builder.addTarget(holder.getSurface());

            // 建立影像傳輸
            cameraDevice.createCaptureSession(Arrays.asList(holder.getSurface(), imageReader.getSurface()),
                    CameraCaptureCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private class Filter extends AsyncTask<Bitmap, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Bitmap... params)
        {
            Bitmap result,src=params[0];
            int pixel;
            int r;
            int g;
            int b;

            result = src.copy(Bitmap.Config.ARGB_4444,true);
            storeImage_before(src);
            for(int i=0;i<src.getWidth();i++)
            {
                for(int j=0;j<src.getHeight();j++)
                {
                    pixel=src.getPixel(i,j);

                    r = ( pixel >> 16 ) & 0xFF;
                    g = ( pixel >> 8 ) & 0xFF;
                    b = ( pixel ) & 0xFF;

                    if((g < b || g < r) ||(r>0xE0&&g>0xE0&&b>0xE0))
                    {
                        result.setPixel(i,j, Color.WHITE);
                    }
                }
            }
            return result;
        }
        @Override
        protected void onPostExecute(Bitmap result)
        {
            storeImage(result);
        }
    }

    private static int getImageScale(byte[] picture_byte) {       //compress shots of camera to adjust window frame size
        BitmapFactory.Options option = new BitmapFactory.Options();
        // set inJustDecodeBounds to true, allowing the caller to query the bitmap info without having to allocate the
        // memory for its pixels.
        option.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(picture_byte,0,picture_byte.length,option);

        int scale = 1;
        while (option.outWidth / scale >= 1920 || option.outHeight / scale >= 1080) {
            scale *= 2;
        }
        return scale;
    }

    private File getOutputMediaFile(){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        //=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();


        String directoryTimestamp = new SimpleDateFormat("ddMMyyyy").format(new Date());
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM.toString())
                + "/"+"/"+directoryTimestamp);


        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
        File mediaFile;
        String mImageName="MI_"+ timeStamp +".jpg";
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        return mediaFile;
    }

    private File getOutputVideoFile(){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        //=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();


        String directoryTimestamp = new SimpleDateFormat("ddMMyyyy").format(new Date());
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM.toString())
                + "/"+"/"+directoryTimestamp);


        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
        File mediaFile;
        String mImageName="MI_"+ timeStamp +".mp4";
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        return mediaFile;
    }

    /** Create a File for saving an image or video */
    private File getOutputMediaFile_before(){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        //=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();

        String directoryTimestamp = new SimpleDateFormat("ddMMyyyy").format(new Date());
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM.toString())
                + "/"+"/"+directoryTimestamp);


        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
        File mediaFile;
        String mImageName="Before_"+ timeStamp +".jpg";
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        return mediaFile;
    }



    private void storeImage(Bitmap image) {
        File pictureFile = getOutputMediaFile();
        if (pictureFile == null) {
            Log.d("",
                    "Error creating media file, check storage permissions: ");// e.getMessage());
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            image.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d("", "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d("", "Error accessing file: " + e.getMessage());
        }
    }

    private void storeImage_before(Bitmap image) {
        File pictureFile = getOutputMediaFile_before();
        if (pictureFile == null) {
            Log.d("",
                    "Error creating media file, check storage permissions: ");// e.getMessage());
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            image.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d("", "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d("", "Error accessing file: " + e.getMessage());
        }
    }

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (cameraDevice != null) {
                cameraDevice.close();
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            handlerThread = new HandlerThread("HT");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());

            // 創建影像物件
            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.RGB_565, 3);
            try {

                // 取得相機鏡頭清單,可用於判斷該相機是否具備雙鏡頭或是單鏡頭
                //String cameraId = manager.getCameraIdList()[0];

                // 開啟相機
                // 0是後面鏡頭 = CaptureRequest.LENS_FACING_BACK
                // 1是前置鏡頭 = CaptureRequest.LENS_FACING_FRONT
                mMediaRecorder = new MediaRecorder();


                manager.openCamera("0", DeviceStateCallback, null);


            } catch (CameraAccessException e) {
                Log.e("CameraAccessException", e.getMessage());
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }
    };

    // 相機裝置狀態
    private CameraDevice.StateCallback DeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            try {
                cameraDevice = camera;

                // 設定預覽模式
                builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                // 將影像設定置SurfaceView
                builder.addTarget(holder.getSurface());

                // 建立影像傳輸
                cameraDevice.createCaptureSession(Arrays.asList(holder.getSurface(), imageReader.getSurface()),
                        CameraCaptureCallback, handler);
            } catch (CameraAccessException e) {
                Log.e("CameraAccessException", e.getMessage());
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {

        }
    };

    private void updatePreview()
    {
        try {
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(builder.build(), null, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 相機擷取中及完成的事件
    private CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {

        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureResult partialResult) {

        }

    };

    // 相機擷取狀態
    private CameraCaptureSession.StateCallback CameraCaptureCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            try {
                mPreviewSession = session;
                updatePreview();
                //session.setRepeatingRequest(builder.build(), CaptureCallback, handler);
            } catch (Exception e) {
                Log.e("CameraAccessException", e.getMessage());
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };


}

