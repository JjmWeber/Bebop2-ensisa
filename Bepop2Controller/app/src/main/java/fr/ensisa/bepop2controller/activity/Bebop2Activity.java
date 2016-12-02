package fr.ensisa.bepop2controller.activity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

import fr.ensisa.bepop2controller.R;
import fr.ensisa.bepop2controller.drone.Bebop2Drone;
import fr.ensisa.bepop2controller.view.Bebop2VideoView;
import io.github.controlwear.virtual.joystick.android.JoystickView;

public class Bebop2Activity extends AppCompatActivity {

    private static final String TAG = "Bebop2Activity";

    private Bebop2Drone bebop2Drone;

    private ProgressDialog connectionProgressDialog;
    private ProgressDialog downloadProgressDialog;

    private Bebop2VideoView videoView;

    private ImageView batteryIconView;

    private TextView batteryTextView;
    private TextView altitudeTextView;

    private Button takeOffAndLandBt;
    private Button downloadBt;

    private JoystickView leftJoystick;
    private JoystickView rightJoystick;

    private int nbMaxDownload;
    private int currentDownloadIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bebop2);

        initIHM();

        ARDiscoveryDeviceService service = getIntent().getParcelableExtra(MainActivity.EXTRA_DEVICE_SERVICE);
        bebop2Drone = new Bebop2Drone(this, service);
        bebop2Drone.addListener(bebopListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if((bebop2Drone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(bebop2Drone.getConnectionState()))) {
            connectionProgressDialog = new ProgressDialog(this);
            connectionProgressDialog.setIndeterminate(true);
            connectionProgressDialog.setMessage(Bebop2Activity.this.getString(R.string.connecting));
            connectionProgressDialog.setCancelable(false);
            connectionProgressDialog.show();

            if(!bebop2Drone.connect())
                finish();
        }
    }

    @Override
    public void onBackPressed() {
        if(bebop2Drone != null) {
            connectionProgressDialog = new ProgressDialog(this);
            connectionProgressDialog.setIndeterminate(true);
            connectionProgressDialog.setMessage(Bebop2Activity.this.getString(R.string.disconnecting));
            connectionProgressDialog.setCancelable(false);
            connectionProgressDialog.show();

            if(!bebop2Drone.disconnect())
                finish();
        }
    }

    @Override
    public void onDestroy() {
        bebop2Drone.dispose();
        super.onDestroy();
    }

    private void initIHM() {
        videoView = (Bebop2VideoView) findViewById(R.id.videoView);

        findViewById(R.id.emergencyButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bebop2Drone.emergency();
            }
        });

        takeOffAndLandBt = (Button) findViewById(R.id.takeOffAndLandButton);
        takeOffAndLandBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (bebop2Drone.getFlyingState()) {
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                        bebop2Drone.takeOff();
                        break;
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                        bebop2Drone.land();
                        break;
                    default:
                }
            }
        });

        findViewById(R.id.cameraButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bebop2Drone.takePicture();
            }
        });

        downloadBt = (Button)findViewById(R.id.downloadButton);
        downloadBt.setEnabled(false);
        downloadBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bebop2Drone.getLastFlightMedias();

                downloadProgressDialog = new ProgressDialog(Bebop2Activity.this);
                downloadProgressDialog.setIndeterminate(true);
                downloadProgressDialog.setMessage(Bebop2Activity.this.getString(R.string.fetching_medias));
                downloadProgressDialog.setCancelable(false);
                downloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, Bebop2Activity.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        bebop2Drone.cancelGetLastFlightMedias();
                    }
                });
                downloadProgressDialog.show();
            }
        });

        leftJoystick = (JoystickView)findViewById(R.id.leftJoystick);
        leftJoystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {

            }
        });

        rightJoystick = (JoystickView)findViewById(R.id.rightJoystick);
        rightJoystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {

            }
        });

        /*findViewById(R.id.upwardButton).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        bebop2Drone.setGaz((byte) 50);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        bebop2Drone.setGaz((byte) 0);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        findViewById(R.id.downButton).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        bebop2Drone.setGaz((byte) -50);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        bebop2Drone.setGaz((byte) 0);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        findViewById(R.id.anticlockwiseButton).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        bebop2Drone.setYaw((byte) -50);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        bebop2Drone.setYaw((byte) 0);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        findViewById(R.id.clockwiseButton).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        bebop2Drone.setYaw((byte) 50);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        bebop2Drone.setYaw((byte) 0);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        findViewById(R.id.forwardButton).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        bebop2Drone.setPitch((byte) 50);
                        bebop2Drone.setFlag((byte) 1);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        bebop2Drone.setPitch((byte) 0);
                        bebop2Drone.setFlag((byte) 0);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        findViewById(R.id.backwardButton).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        bebop2Drone.setPitch((byte) -50);
                        bebop2Drone.setFlag((byte) 1);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        bebop2Drone.setPitch((byte) 0);
                        bebop2Drone.setFlag((byte) 0);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        findViewById(R.id.leftButton).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        bebop2Drone.setRoll((byte) -50);
                        bebop2Drone.setFlag((byte) 1);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        bebop2Drone.setRoll((byte) 0);
                        bebop2Drone.setFlag((byte) 0);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        findViewById(R.id.rightButton).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        bebop2Drone.setRoll((byte) 50);
                        bebop2Drone.setFlag((byte) 1);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        bebop2Drone.setRoll((byte) 0);
                        bebop2Drone.setFlag((byte) 0);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });*/

        batteryIconView = (ImageView) findViewById(R.id.batteryIconView);

        batteryTextView = (TextView) findViewById(R.id.batteryTextView);
        altitudeTextView = (TextView) findViewById(R.id.altitudeTextView);
    }

    private final Bebop2Drone.Listener bebopListener = new Bebop2Drone.Listener() {
        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state) {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    connectionProgressDialog.dismiss();
                    break;
                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    connectionProgressDialog.dismiss();
                    finish();
                    break;
                default:
                    break;
            }
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onBatteryChargeChanged(int batteryPercentage) {
            batteryTextView.setText(String.format(" %d%%", batteryPercentage));
            switch(batteryPercentage / 10) {
                case 10:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 9:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 8:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_80));
                    break;
                case 7:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_70));
                    break;
                case 6:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_60));
                    break;
                case 5:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_50));
                    break;
                case 4:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_40));
                    break;
                case 3:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_30));
                    break;
                case 2:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_20));
                    break;
                case 1:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_10));
                    break;
                case 0:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
                default:
                    batteryIconView.setImageDrawable(Bebop2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
            }
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onAltitudeChanged(double altitudeValue) {
            altitudeTextView.setText(String.format(" %.1f m", altitudeValue));
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    takeOffAndLandBt.setText(R.string.take_off);
                    takeOffAndLandBt.setEnabled(true);
                    downloadBt.setEnabled(true);
                    break;
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    takeOffAndLandBt.setText(R.string.land);
                    takeOffAndLandBt.setEnabled(true);
                    downloadBt.setEnabled(false);
                    break;
                default:
                    takeOffAndLandBt.setEnabled(false);
                    downloadBt.setEnabled(false);
            }
        }

        @Override
        public void onPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Log.i(TAG, "Picture has been taken");
        }

        @Override
        public void configureDecoder(ARControllerCodec codec) {
            videoView.configureDecoder(codec);
        }

        @Override
        public void onFrameReceived(ARFrame frame) {
            videoView.displayFrame(frame);
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {
            downloadProgressDialog.dismiss();

            nbMaxDownload = nbMedias;
            currentDownloadIndex = 1;

            if(nbMedias > 0) {
                downloadProgressDialog = new ProgressDialog(Bebop2Activity.this);
                downloadProgressDialog.setIndeterminate(false);
                downloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                downloadProgressDialog.setMessage(Bebop2Activity.this.getString(R.string.downloading_medias));
                downloadProgressDialog.setMax(nbMaxDownload * 100);
                downloadProgressDialog.setSecondaryProgress(currentDownloadIndex * 100);
                downloadProgressDialog.setProgress(0);
                downloadProgressDialog.setCancelable(false);
                downloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, Bebop2Activity.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        bebop2Drone.cancelGetLastFlightMedias();
                    }
                });
                downloadProgressDialog.show();
            }
        }

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {
            downloadProgressDialog.setProgress(((currentDownloadIndex - 1) * 100) + progress);
        }

        @Override
        public void onDownloadComplete(String mediaName) {
            currentDownloadIndex++;
            downloadProgressDialog.setSecondaryProgress(currentDownloadIndex * 100);

            if(currentDownloadIndex > nbMaxDownload) {
                downloadProgressDialog.dismiss();
                downloadProgressDialog = null;
            }
        }
    };

}
