package com.example.alansio.wifi_p2p_camera;

import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;

public class ControlActivity extends AppCompatActivity {

    Button takeButton;
    Button autotakeButton;
    Button stopautoButton;
    Button startrecordButton;
    Button stoprecordButton;
    SeekBar timeBar;
    TextView timeBarValue;
    public static final String CAMERA_TAKE_PHOTO = "take_photo";
    public static final String CAMERA_OPEN = "open_camera";
    public static final String CAMERA_AUTO_TAKE = "auto_take ";
    public static final String CAMERA_STOP_AUTO = "stop_auto";
    public static final String CAMERA_START_RECORD = "start_record";
    public static final String CAMERA_STOP_RECORD = "stop_record";
    ArrayList<BufferedWriter> writer ;
    int feq = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        takeButton = (Button) findViewById(R.id.take_button);
        autotakeButton = (Button) findViewById(R.id.autotakeBtn);
        stopautoButton = (Button) findViewById(R.id.stopautoBtn);
        startrecordButton = (Button) findViewById(R.id.startRecordBtn);
        stoprecordButton = (Button) findViewById(R.id.stopRecordBtn);
        timeBar = (SeekBar) findViewById(R.id.timebar);
        timeBarValue = (TextView) findViewById(R.id.timebarValue) ;

        writer = WiFiDirectActivity.getWriter();

        timeBar.setProgress(3);
        timeBarValue.setText("Frequency : " + String.valueOf(timeBar.getProgress() + 1));
        feq = timeBar.getProgress() + 1;
        timeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int tureProgress = progress + 1;
                timeBarValue.setText("Frequency : " + String.valueOf(tureProgress));
                feq = progress + 1;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        autotakeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ControlActivity.ControlAsync().execute(CAMERA_AUTO_TAKE + String.valueOf(feq));
                stopautoButton.setVisibility(View.VISIBLE);
                autotakeButton.setVisibility(View.INVISIBLE);

            }
        });

        stopautoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ControlActivity.ControlAsync().execute(CAMERA_STOP_AUTO);
                stopautoButton.setVisibility(View.INVISIBLE);
                autotakeButton.setVisibility(View.VISIBLE);
            }
        });

        takeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ControlActivity.ControlAsync().execute(CAMERA_TAKE_PHOTO);
            }
        });

        startrecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ControlActivity.ControlAsync().execute(CAMERA_START_RECORD);
                stoprecordButton.setVisibility(View.VISIBLE);
                startrecordButton.setVisibility(View.INVISIBLE);
            }
        });

        stoprecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ControlActivity.ControlAsync().execute(CAMERA_STOP_RECORD);
                stoprecordButton.setVisibility(View.INVISIBLE);
                startrecordButton.setVisibility(View.VISIBLE);
            }
        });
    }

    public class ControlAsync extends AsyncTask<String, Void, Void>
    {
        @Override
        protected Void doInBackground(String... params) {
            String action = params[0];

            //if (action.equals(CAMERA_OPEN))
            //{
                try
                {
                    for(BufferedWriter w : writer)
                    {
                        w.write(action + "\n");
                        w.flush();
                    }
                    if(writer == null)
                        Log.d("ERROR", "NO writer") ;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            //}
            return null;
        }
    }
}