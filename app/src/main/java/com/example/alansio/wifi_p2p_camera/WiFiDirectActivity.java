package com.example.alansio.wifi_p2p_camera;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;


public class WiFiDirectActivity extends AppCompatActivity implements WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;
    Button scanButton, connectButton, controlButton;
    ListView peerListView;
    TextView connectingNum;
    ArrayAdapter<String> listAdapter;
    ArrayList<String> device_list;
    //ControlAsync controlAsync;
    ProgressDialog progressDialog = null;
    int choice_position = 0;
    String thisDeviceName;

    Thread receiver;
    String role;
    ServerSocket mServerSocket;
    ArrayList<Socket> mClientSocket;
    Socket ownSocket;
    static BufferedReader reader ;
    static ArrayList<BufferedWriter> writer ;
    String ServerAddr;
    Handler mHandler;
    int port = 8888;
    int DeviceNum;
    Boolean THREAD_CLOSE = false;

    //Control parameter
    static Boolean isTakePhoto = false;
    static int feq = 0;
    static Boolean isAutoTake = false;
    static Boolean isStopAuto = false;
    static Boolean isStartRecord = false;
    static Boolean isStopRecord = false;

    Intent camera_intent;
    Intent control_intent;

    //permission
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static final int REQUEST_CAMERA = 1;
    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    public static final String CAMERA_OPEN = "open_camera";
    public static final String CAMERA_TAKE_PHOTO = "take_photo";
    public static final String CAMERA_AUTO_TAKE = "auto_take ";
    public static final String CAMERA_STOP_AUTO = "stop_auto";
    public static final String CAMERA_START_RECORD = "start_record";
    public static final String CAMERA_STOP_RECORD = "stop_record";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //widget
        scanButton = (Button) findViewById(R.id.scan);
        connectButton = (Button) findViewById(R.id.connect);
        controlButton = (Button) findViewById(R.id.control);
        peerListView = (ListView) findViewById(R.id.peerlist);
        connectingNum = (TextView) findViewById(R.id.connecting) ;
        device_list = new ArrayList<>();
        listAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, device_list);
        peerListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        peerListView.setAdapter(listAdapter);
        peerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick (AdapterView<?> parent, View view, int position, long id)
            {
                Toast.makeText(getApplicationContext(), "You have chosen: " + peerList.get(position).deviceName, Toast.LENGTH_SHORT).show();
                connectButton.setVisibility(View.VISIBLE);
                choice_position = position;
            }
        });

        //intent
        camera_intent = new Intent();
        camera_intent.setClass(WiFiDirectActivity.this, Camera.class);
        control_intent = new Intent();
        control_intent.setClass(WiFiDirectActivity.this, ControlActivity.class);

        //Disconnect to wifi AP
        WifiManager wifi_disconnect = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifi_disconnect.disconnect();

        //enable wifi
        WifiManager wifi_enabled = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if(!wifi_enabled.isWifiEnabled())
        {
            wifi_enabled.setWifiEnabled(true);
            Toast.makeText(getApplicationContext(), "WIFI enabled.", Toast.LENGTH_SHORT).show();
        }


        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        mManager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);

        //Scan for peer
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener()
                {
                    @Override
                    public void onSuccess() {
                        Log.d("notice", "Success");
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d("notice", "Fail");
                    }
                });
                onInitiateDiscovery();
            }
        });

        //Connect to peer
        connectButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                int index = choice_position;
                WifiP2pDevice device = peerList.get(index);
                WifiP2pConfig config = new WifiP2pConfig();
                config.wps.setup = WpsInfo.PBC;
                config.deviceAddress = device.deviceAddress;

                mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d("ERROR", "peer connected");
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d("ERROR", Integer.toString(reason));
                    }
                });
            }
        });

        //control peer
        controlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ControlAsync().execute(CAMERA_OPEN);
                startActivity(control_intent);
            }
        });

        //for receive msg
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg)
            {
                super.handleMessage(msg);
                String command;
                command = msg.getData().getString("command");
                Toast.makeText(getApplicationContext(), "msg Received: " + command, Toast.LENGTH_SHORT).show();
                Log.d("ERROR", "receiver success") ;
            }
        };
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        verifyCameraPermissions(this);
        verifyStoragePermissions(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }


    @Override
    public void onDestroy()
    {
        super.onDestroy();
        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onFailure(int reasonCode) {
                Log.d("clean","Disconnect failed. Reason :" + reasonCode);
            }

            @Override
            public void onSuccess() {
            }

        });
    }

    public static ArrayList<BufferedWriter> getWriter()
    {
        return writer;
    }

    public static BufferedReader getReader()
    {
        return reader;
    }

    //permission for storage
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED ) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );

        }
    }

    //permission for camera
    public static void verifyCameraPermissions(Activity camera_activity) {
        // Check if we have write permission

        int camera_permission= ActivityCompat.checkSelfPermission(camera_activity, Manifest.permission.CAMERA);
        if (camera_permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user

            ActivityCompat.requestPermissions(
                    camera_activity,
                    PERMISSIONS_CAMERA,
                    REQUEST_CAMERA
            );
        }
    }

    private ArrayList<WifiP2pDevice> peerList = new ArrayList<>();

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        Log.d("OnPeerAvailable", "peers");

        String deviceInfo;

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        //for connection setup
        peerList.clear();
        peerList.addAll(peers.getDeviceList());

        //for list view content
        device_list.clear();
        for (WifiP2pDevice d : peers.getDeviceList())
        {
            deviceInfo = "Name: " + d.deviceName + "\nAddress: " + d.deviceAddress;
            device_list.add(deviceInfo);
        }

        if (peerList.size() == 0)
        {
            connectButton.setVisibility(View.INVISIBLE);
            Toast.makeText(getApplicationContext(), "No device found.", Toast.LENGTH_SHORT).show();
        }

        listAdapter.notifyDataSetChanged();
    }

    public void onInitiateDiscovery() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(this, "Press back to cancel", "Finding peers", true,
                true, new DialogInterface.OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {

                    }
                });
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info)
    {
        ServerAddr = info.groupOwnerAddress.getHostAddress();
        DeviceNum = 0;
        port = 8888;

        Log.d("ERROR", "start connection");

        if(info.isGroupOwner)
        {
            Log.d("ERROR", "GO");

            controlButton.setVisibility(View.VISIBLE);
            connectingNum.setVisibility(View.VISIBLE);
            Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();

            //get client information
            mManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    for(WifiP2pDevice d : group.getClientList())
                    {
                        Toast.makeText(getApplicationContext(), d.deviceName, Toast.LENGTH_SHORT).show();
                        DeviceNum = DeviceNum + 1;
                        connectingNum.setText("Connecting Device: " + DeviceNum);
                    }
                }
            });

            role = "server";
            receiver = new ReceiverThread();
            receiver.start();
        }
        else if(!info.isGroupOwner)
        {
            Log.d("ERROR", "not GO");
            Toast.makeText(getApplicationContext(), "connection started", Toast.LENGTH_SHORT).show();
            role = "client";
            receiver = new ReceiverThread();
            receiver.start();
            Log.d("ERROR", "receiver started");
        }
    }


    //for sending control msg by server
    public class ControlAsync extends AsyncTask<String, Void, Void>
    {
        @Override
        protected Void doInBackground(String... params) {
            String action = params[0];

            if (action.equals(CAMERA_OPEN))
            {
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
            }
            return null;
        }
    }

    //receiver control msg by client
    public class ReceiverThread extends Thread {

        @Override
        public void run() {
            super.run();
            try {
                if (role.equals("client")) {
                    Log.d("ERROR", ServerAddr + port);
                    ownSocket = new Socket(ServerAddr, port);
                } else if (role.equals("server")) {
                    mServerSocket = new ServerSocket(port);

                    Socket s;
                    mClientSocket = new ArrayList<Socket>();
                    writer = new ArrayList<BufferedWriter>();
                    while (true)
                    {
                        Log.d("SETUP", "waiting connection");
                        s = mServerSocket.accept();
                        Log.d("SETUP", "connection done");
                        mClientSocket.add(s);
                        writer.add(createWriter(mClientSocket.get(mClientSocket.size() - 1)));

                    }

                } else {
                    Log.d("ERROR", "setup connection error");
                }

                if (role.equals("client")) {
                    reader = new BufferedReader(new InputStreamReader(ownSocket.getInputStream()));
                    while (!THREAD_CLOSE) {
                        String s;
                        if ((s = reader.readLine()) != null) {
                            Message m = new Message();
                            Bundle b = new Bundle();
                            b.putString("command", s);
                            m.setData(b);
                            mHandler.sendMessage(m);
                            if(s.equals(CAMERA_OPEN))
                            {
                                camera_intent.putExtras(b);
                                startActivity(camera_intent);
                            }
                            else if(s.equals(CAMERA_TAKE_PHOTO))
                            {
                                isTakePhoto = true;
                            }
                            else if(s.contains(CAMERA_AUTO_TAKE))
                            {
                                String[] cmd = s.split(" ");
                                feq = Integer.valueOf(cmd[1]);
                                isAutoTake = true;
                            }
                            else if(s.equals(CAMERA_STOP_AUTO))
                            {
                                isStopAuto = true;
                            }
                            else if(s.equals(CAMERA_START_RECORD))
                            {
                                isStartRecord = true;
                            }
                            else if(s.equals(CAMERA_STOP_RECORD))
                            {
                                isStopRecord = true;
                            }
                        }
                    }
                }
                else if (role.equals("server"))
                {
                    /*
                    writer = new BufferedWriter(new OutputStreamWriter(mClientSocket.getOutputStream()));
                    Log.d("ERROR", "add writer");
                    */
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        BufferedWriter createWriter(Socket client) {
            BufferedWriter w = null;
            try {
                w = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return  w;
        }

    }
}
