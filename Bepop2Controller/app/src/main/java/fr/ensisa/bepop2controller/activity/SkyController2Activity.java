package fr.ensisa.bepop2controller.activity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

import fr.ensisa.bepop2controller.R;
import fr.ensisa.bepop2controller.drone.SkyController2Drone;
import fr.ensisa.bepop2controller.view.Bebop2VideoView;

public class SkyController2Activity extends AppCompatActivity {

    private static final String TAG = "SkyController2Activity";

    private SkyController2Drone skyController2Drone;

    private ProgressDialog connectionProgressDialog;
    private ProgressDialog downloadProgressDialog;

    private Bebop2VideoView videoView;

    private ImageView droneBatteryIconView;
    private ImageView controllerBatteryIconView;
    private ImageView horizonImageView;

    private TextView droneBatteryTextView;
    private TextView controllerBatteryTextView;
    private TextView altitudeTextView;
    private TextView speedTextView;
    private ProgressBar loadingAnimation;

    private Button takeOffAndLandBt;
    private FloatingActionButton downloadBt;

    private int nbMaxDownload;
    private int currentDownloadIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skycontroller2);

        initIHM();

        Intent intent = getIntent();
        ARDiscoveryDeviceService service = intent.getParcelableExtra(MainActivity.EXTRA_DEVICE_SERVICE);
        skyController2Drone = new SkyController2Drone(this, service);
        skyController2Drone.addListener(mSkyController2Listener);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if ((skyController2Drone != null) &&
                !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(skyController2Drone.getSkyController2ConnectionState()))) {
            connectionProgressDialog = new ProgressDialog(this);
            connectionProgressDialog.setIndeterminate(true);
            connectionProgressDialog.setMessage(SkyController2Activity.this.getString(R.string.connecting));
            connectionProgressDialog.setCancelable(false);
            connectionProgressDialog.show();

            if (!skyController2Drone.connect())
                finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (skyController2Drone != null) {
            connectionProgressDialog = new ProgressDialog(this);
            connectionProgressDialog.setIndeterminate(true);
            connectionProgressDialog.setMessage(SkyController2Activity.this.getString(R.string.disconnecting));
            connectionProgressDialog.setCancelable(false);
            connectionProgressDialog.show();

            if (!skyController2Drone.disconnect())
                finish();
        } else
                finish();
    }

    @Override
    public void onDestroy() {
        skyController2Drone.dispose();
        super.onDestroy();
    }

    private void initIHM() {
        videoView = (Bebop2VideoView) findViewById(R.id.videoView);

        findViewById(R.id.emergencyButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                skyController2Drone.emergency();
            }
        });

        takeOffAndLandBt = (Button) findViewById(R.id.takeOffAndLandButton);
        takeOffAndLandBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (skyController2Drone.getFlyingState()) {
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                        skyController2Drone.takeOff();
                        break;
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                        skyController2Drone.land();
                        break;
                    default:
                }
            }
        });

        findViewById(R.id.cameraButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                skyController2Drone.takePicture();
            }
        });

        downloadBt = (FloatingActionButton)findViewById(R.id.downloadButton);
        downloadBt.setEnabled(false);
        downloadBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                skyController2Drone.getLastFlightMedias();

                downloadProgressDialog = new ProgressDialog(SkyController2Activity.this);
                downloadProgressDialog.setIndeterminate(true);
                downloadProgressDialog.setMessage(SkyController2Activity.this.getString(R.string.fetching_medias));
                downloadProgressDialog.setCancelable(false);
                downloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, SkyController2Activity.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        skyController2Drone.cancelGetLastFlightMedias();
                    }
                });
                downloadProgressDialog.show();
            }
        });

        droneBatteryIconView = (ImageView) findViewById(R.id.droneBatteryIconView);
        controllerBatteryIconView = (ImageView) findViewById(R.id.controllerBatteryIconView);
        horizonImageView = (ImageView) findViewById(R.id.horizonImageView);

        controllerBatteryTextView = (TextView) findViewById(R.id.controllerBatteryTextView);
        droneBatteryTextView = (TextView) findViewById(R.id.droneBatteryTextView);
        altitudeTextView = (TextView) findViewById(R.id.altitudeTextView);
        speedTextView = (TextView) findViewById(R.id.speedTextView);

        loadingAnimation = (ProgressBar)findViewById(R.id.loadingAnimation);
    }

    @SuppressLint("DefaultLocale")
    private final SkyController2Drone.Listener mSkyController2Listener = new SkyController2Drone.Listener() {
        @Override
        public void onSkyController2ConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state) {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    connectionProgressDialog.dismiss();
                    if (!ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(skyController2Drone.getDroneConnectionState()))
                        loadingAnimation.setVisibility(View.VISIBLE);
                    break;
                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    connectionProgressDialog.dismiss();
                    finish();
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state) {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    loadingAnimation.setVisibility(View.GONE);
                    break;
                default:
                    loadingAnimation.setVisibility(View.VISIBLE);
                    break;
            }
        }

        @Override
        public void onSkyController2BatteryChargeChanged(int batteryPercentage) {
            controllerBatteryTextView.setText(String.format(" %d%%", batteryPercentage));
            switch(batteryPercentage / 10) {
                case 10:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 9:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 8:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_80));
                    break;
                case 7:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_70));
                    break;
                case 6:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_60));
                    break;
                case 5:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_50));
                    break;
                case 4:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_40));
                    break;
                case 3:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_30));
                    break;
                case 2:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_20));
                    break;
                case 1:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_10));
                    break;
                case 0:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
                default:
                    controllerBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
            }
        }

        @Override
        public void onDroneBatteryChargeChanged(int batteryPercentage) {
            droneBatteryTextView.setText(String.format(" %d%%", batteryPercentage));
            switch(batteryPercentage / 10) {
                case 10:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 9:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_100));
                    break;
                case 8:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_80));
                    break;
                case 7:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_70));
                    break;
                case 6:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_60));
                    break;
                case 5:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_50));
                    break;
                case 4:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_40));
                    break;
                case 3:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_30));
                    break;
                case 2:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_20));
                    break;
                case 1:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_10));
                    break;
                case 0:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
                default:
                    droneBatteryIconView.setImageDrawable(SkyController2Activity.this.getDrawable(R.drawable.ic_battery_alert));
                    break;
            }
        }

        @Override
        public void onSpeedChanged(float speed) {
            speedTextView.setText(String.format("  %.1f m/s", speed));
        }

        @Override
        public void horizonChanged(float roll) {
            final RotateAnimation rotateAnim = new RotateAnimation(0.0f, roll*25,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f);

            rotateAnim.setDuration(0);
            rotateAnim.setFillAfter(true);
            horizonImageView.startAnimation(rotateAnim);
        }

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
            Toast.makeText(getApplicationContext(), R.string.picture_taken, Toast.LENGTH_SHORT).show();
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

            if (nbMedias > 0) {
                downloadProgressDialog = new ProgressDialog(SkyController2Activity.this);
                downloadProgressDialog.setIndeterminate(false);
                downloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                downloadProgressDialog.setMessage(SkyController2Activity.this.getString(R.string.downloading_medias));
                downloadProgressDialog.setMax(nbMaxDownload * 100);
                downloadProgressDialog.setSecondaryProgress(currentDownloadIndex * 100);
                downloadProgressDialog.setProgress(0);
                downloadProgressDialog.setCancelable(false);
                downloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, SkyController2Activity.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        skyController2Drone.cancelGetLastFlightMedias();
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

            if (currentDownloadIndex > nbMaxDownload) {
                downloadProgressDialog.dismiss();
                downloadProgressDialog = null;
            }
        }
    };

}
