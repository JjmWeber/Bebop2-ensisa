package fr.ensisa.bebop2controller.activity;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ContextThemeWrapper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.parrot.arsdk.ARSDK;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fr.ensisa.bebop2controller.R;
import fr.ensisa.bebop2controller.discovery.DroneDiscoverer;
import fr.ensisa.bebop2controller.view.Bebop2VideoView;

public class MainActivity extends AppCompatActivity {

    static { ARSDK.loadSDKLibs(); }

    private static final String TAG = "MainActivity";

    private static final int STEP_LINEAR_SPEED = 1, MIN_LINEAR_SPEED = 20, MAX_LINEAR_SPEED = 100;
    private static final int STEP_ROTATION_SPEED = 1, MIN_ROTATION_SPEED = 20, MAX_ROTATION_SPEED = 200;
    private static final int STEP_ANGLE = 1, MIN_ANGLE = 90, MAX_ANGLE = 360;
    private static final int REQUEST_CODE_PERMISSIONS_REQUEST = 1;
    private static final String[] PERMISSIONS_NEEDED = new String[] {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };

    public static final String EXTRA_DEVICE_SERVICE = "EXTRA_DEVICE_SERVICE";

    private final List<ARDiscoveryDeviceService> dronesList = new ArrayList<>();

    private boolean autoRecord = true;
    private boolean joysticksFixed = true;
    private boolean joysticksInverted = false;
    private boolean optionsHasBeenSet = false;
    private boolean panoramaPhotos = true;
    private boolean panoramaVideo = true;
    private boolean commandsInverted = false;
    private int layoutResourceID;
    private float linearSpeed = 20, rotationSpeed = 20;
    private double panoramaAngle = 360;
    private DeviceAdapter adapter;
    private Intent intent;

    private TextView linearSpeedText, rotationSpeedText, panoramaAngleText;
    private Switch autoRecordSwitch, commandsInvertedSwitch, joysticksFixedSwitch,
            joysticksInvertedSwitch, panoramaPhotosSwitch, panoramaVideoSwitch;

    public DroneDiscoverer droneDiscoverer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        droneDiscoverer = new DroneDiscoverer(this);

        Set<String> permissionsToRequest = new HashSet<>();
        for (String permission : PERMISSIONS_NEEDED) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    Toast.makeText(this, R.string.allow_permission + permission, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                } else
                    permissionsToRequest.add(permission);
        }

        if (permissionsToRequest.size() > 0)
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[permissionsToRequest.size()]),
                    REQUEST_CODE_PERMISSIONS_REQUEST);

        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        Bebop2VideoView.setVideoFormatDimensions(size.x, size.y);

        TextView aboutTextView = (TextView) findViewById(R.id.aboutTextView);
        try {
            aboutTextView.setText(MainActivity.this.getString(R.string.about, getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
        } catch (Exception e) {
            aboutTextView.setText(MainActivity.this.getString(R.string.aboutNoVersion));
        }

        final ListView listView = (ListView) findViewById(R.id.deviceList);

        adapter = new DeviceAdapter(this, dronesList);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                intent = null;
                layoutResourceID = 0;

                final ARDiscoveryDeviceService service = (ARDiscoveryDeviceService) adapter.getItem(position);
                if (service != null) {
                    ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(service.getProductID());
                    switch (product) {
                        case ARDISCOVERY_PRODUCT_BEBOP_2:
                            intent = new Intent(MainActivity.this, Bebop2Activity.class);
                            layoutResourceID = R.layout.drone_options;
                            break;
                        case ARDISCOVERY_PRODUCT_SKYCONTROLLER_2:
                            intent = new Intent(MainActivity.this, SkyController2Activity.class);
                            layoutResourceID = R.layout.controller_options;
                            break;
                        default:
                            Log.e(TAG, "The type " + product + " is not supported by this sample");
                    }
                }

                if(optionsHasBeenSet && intent != null && layoutResourceID != 0) {
                    intent.putExtra(EXTRA_DEVICE_SERVICE, service);
                    intent.putExtra("autoRecord", autoRecord);
                    intent.putExtra("commandsInverted", commandsInverted);
                    intent.putExtra("joysticksInverted", joysticksInverted);
                    intent.putExtra("joysticksFixed", joysticksFixed);
                    intent.putExtra("linearSpeed", linearSpeed);
                    intent.putExtra("panoramaAngle", panoramaAngle);
                    intent.putExtra("panoramaPhotos", panoramaPhotos);
                    intent.putExtra("panoramaVideo", panoramaVideo);
                    intent.putExtra("rotationSpeed", rotationSpeed);

                    startActivity(intent);
                } else {
                    Builder dialogBuilder = new Builder(new ContextThemeWrapper(MainActivity.this, R.style.AppTheme));
                    LayoutInflater inflater = MainActivity.this.getLayoutInflater();
                    final View dialogView = inflater.inflate(layoutResourceID, null);
                    dialogBuilder.setView(dialogView);

                    autoRecordSwitch = (Switch) dialogView.findViewById(R.id.autoRecordSwitch);
                    panoramaPhotosSwitch = (Switch) dialogView.findViewById(R.id.panoramaPhotosSwitch);
                    panoramaVideoSwitch = (Switch) dialogView.findViewById(R.id.panoramaVideoSwitch);

                    autoRecordSwitch.setChecked(autoRecord);
                    panoramaPhotosSwitch.setChecked(panoramaPhotos);
                    panoramaVideoSwitch.setChecked(panoramaVideo);

                    SeekBar linearSpeedBar = (SeekBar) dialogView.findViewById(R.id.linearSpeedBar);
                    SeekBar rotationSpeedBar = (SeekBar) dialogView.findViewById(R.id.rotationSpeedBar);
                    SeekBar panoramaAngleBar = (SeekBar) dialogView.findViewById(R.id.panoramaAngleBar);

                    linearSpeedText = (TextView) dialogView.findViewById(R.id.linearSpeedText);
                    rotationSpeedText = (TextView) dialogView.findViewById(R.id.rotationSpeedText);
                    panoramaAngleText = (TextView) dialogView.findViewById(R.id.panoramaAngleText);

                    linearSpeedText.setText(getString(R.string.percentage, (int) linearSpeed));
                    rotationSpeedText.setText(getString(R.string.percentage, (int) rotationSpeed));
                    panoramaAngleText.setText(getString(R.string.angle, (int) panoramaAngle));

                    linearSpeedBar.setMax((MAX_LINEAR_SPEED - MIN_LINEAR_SPEED) / STEP_LINEAR_SPEED);
                    linearSpeedBar.setProgress((int) linearSpeed - MIN_LINEAR_SPEED);
                    linearSpeedBar.setOnSeekBarChangeListener(
                            new SeekBar.OnSeekBarChangeListener() {
                                @Override
                                public void onStopTrackingTouch(SeekBar seekBar) {

                                }

                                @Override
                                public void onStartTrackingTouch(SeekBar seekBar) {

                                }

                                @Override
                                public void onProgressChanged(SeekBar seekBar, int progress,
                                                              boolean fromUser) {
                                    linearSpeed = (MIN_LINEAR_SPEED + (progress * STEP_LINEAR_SPEED));
                                    linearSpeedText.setText(getString(R.string.percentage, (int) linearSpeed));
                                }
                            }
                    );

                    rotationSpeedBar.setMax((MAX_ROTATION_SPEED - MIN_ROTATION_SPEED) / STEP_ROTATION_SPEED);
                    rotationSpeedBar.setProgress((int) rotationSpeed - MIN_ROTATION_SPEED);
                    rotationSpeedBar.setOnSeekBarChangeListener(
                            new SeekBar.OnSeekBarChangeListener() {
                                @Override
                                public void onStopTrackingTouch(SeekBar seekBar) {

                                }

                                @Override
                                public void onStartTrackingTouch(SeekBar seekBar) {

                                }

                                @Override
                                public void onProgressChanged(SeekBar seekBar, int progress,
                                                              boolean fromUser) {
                                    rotationSpeed = (MIN_ROTATION_SPEED + (progress * STEP_ROTATION_SPEED));
                                    rotationSpeedText.setText(getString(R.string.percentage, (int) rotationSpeed));
                                }
                            }
                    );

                    panoramaAngleBar.setMax((MAX_ANGLE - MIN_ANGLE) / STEP_ANGLE);
                    panoramaAngleBar.setProgress((int) panoramaAngle - MIN_ANGLE);
                    panoramaAngleBar.setOnSeekBarChangeListener(
                            new SeekBar.OnSeekBarChangeListener() {
                                @Override
                                public void onStopTrackingTouch(SeekBar seekBar) {

                                }

                                @Override
                                public void onStartTrackingTouch(SeekBar seekBar) {

                                }

                                @Override
                                public void onProgressChanged(SeekBar seekBar, int progress,
                                                              boolean fromUser) {
                                    panoramaAngle = (MIN_ANGLE + (progress * STEP_ANGLE));
                                    panoramaAngleText.setText(getString(R.string.angle, (int) panoramaAngle));
                                }
                            }
                    );

                    if(layoutResourceID == R.layout.drone_options) {
                        commandsInvertedSwitch = (Switch) dialogView.findViewById(R.id.commandsInverted);
                        joysticksFixedSwitch = (Switch) dialogView.findViewById(R.id.joysticksFixed);
                        joysticksInvertedSwitch = (Switch) dialogView.findViewById(R.id.joysticksInverted);

                        commandsInvertedSwitch.setChecked(commandsInverted);
                        joysticksFixedSwitch.setChecked(joysticksFixed);
                        joysticksInvertedSwitch.setChecked(joysticksInverted);
                    }

                    dialogBuilder.setPositiveButton(R.string.validate, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            optionsHasBeenSet = true;

                            autoRecord = autoRecordSwitch.isChecked();
                            panoramaPhotos = panoramaPhotosSwitch.isChecked();
                            panoramaVideo = panoramaVideoSwitch.isChecked();

                            if(layoutResourceID == R.layout.drone_options) {
                                commandsInverted = commandsInvertedSwitch.isChecked();
                                joysticksFixed = joysticksFixedSwitch.isChecked();
                                joysticksInverted = joysticksInvertedSwitch.isChecked();
                            }

                            intent.putExtra(EXTRA_DEVICE_SERVICE, service);
                            intent.putExtra("autoRecord", autoRecord);
                            intent.putExtra("commandsInverted", commandsInverted);
                            intent.putExtra("joysticksInverted", joysticksInverted);
                            intent.putExtra("joysticksFixed", joysticksFixed);
                            intent.putExtra("linearSpeed", linearSpeed);
                            intent.putExtra("panoramaAngle", panoramaAngle);
                            intent.putExtra("panoramaPhotos", panoramaPhotos);
                            intent.putExtra("panoramaVideo", panoramaVideo);
                            intent.putExtra("rotationSpeed", rotationSpeed);

                            startActivity(intent);
                        }
                    });

                    AlertDialog alertDialog = dialogBuilder.create();
                    alertDialog.show();
                    alertDialog.getWindow().setBackgroundDrawableResource(R.drawable.style_dialogs);
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.primary_high));
                }
            }
        });

        findViewById(R.id.optionsButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Builder dialogBuilder = new Builder(new ContextThemeWrapper(MainActivity.this, R.style.AppTheme));
                LayoutInflater inflater = MainActivity.this.getLayoutInflater();
                final View dialogView = inflater.inflate(R.layout.drone_options, null);
                dialogBuilder.setView(dialogView);

                final Switch autoRecordSwitch = (Switch) dialogView.findViewById(R.id.autoRecordSwitch);
                final Switch commandsInvertedSwitch = (Switch) dialogView.findViewById(R.id.commandsInverted);
                final Switch joysticksFixedSwitch = (Switch) dialogView.findViewById(R.id.joysticksFixed);
                final Switch joysticksInvertedSwitch = (Switch) dialogView.findViewById(R.id.joysticksInverted);
                final Switch panoramaPhotosSwitch = (Switch) dialogView.findViewById(R.id.panoramaPhotosSwitch);
                final Switch panoramaVideoSwitch = (Switch) dialogView.findViewById(R.id.panoramaVideoSwitch);

                autoRecordSwitch.setChecked(autoRecord);
                commandsInvertedSwitch.setChecked(commandsInverted);
                joysticksFixedSwitch.setChecked(joysticksFixed);
                joysticksInvertedSwitch.setChecked(joysticksInverted);
                panoramaPhotosSwitch.setChecked(panoramaPhotos);
                panoramaVideoSwitch.setChecked(panoramaVideo);

                dialogBuilder.setPositiveButton(R.string.validate, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        optionsHasBeenSet = true;

                        autoRecord = autoRecordSwitch.isChecked();
                        commandsInverted = commandsInvertedSwitch.isChecked();
                        joysticksFixed = joysticksFixedSwitch.isChecked();
                        joysticksInverted = joysticksInvertedSwitch.isChecked();
                        panoramaPhotos = panoramaPhotosSwitch.isChecked();
                        panoramaVideo = panoramaVideoSwitch.isChecked();
                    }
                });

                SeekBar linearSpeedBar = (SeekBar) dialogView.findViewById(R.id.linearSpeedBar);
                SeekBar rotationSpeedBar = (SeekBar) dialogView.findViewById(R.id.rotationSpeedBar);
                SeekBar panoramaAngleBar = (SeekBar) dialogView.findViewById(R.id.panoramaAngleBar);

                linearSpeedText = (TextView) dialogView.findViewById(R.id.linearSpeedText);
                rotationSpeedText = (TextView) dialogView.findViewById(R.id.rotationSpeedText);
                panoramaAngleText = (TextView) dialogView.findViewById(R.id.panoramaAngleText);

                linearSpeedText.setText(getString(R.string.percentage, (int) linearSpeed));
                rotationSpeedText.setText(getString(R.string.percentage, (int) rotationSpeed));
                panoramaAngleText.setText(getString(R.string.angle, (int) panoramaAngle));

                linearSpeedBar.setMax((MAX_LINEAR_SPEED - MIN_LINEAR_SPEED) / STEP_LINEAR_SPEED);
                linearSpeedBar.setProgress((int) linearSpeed - MIN_LINEAR_SPEED);
                linearSpeedBar.setOnSeekBarChangeListener(
                        new SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onStopTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onStartTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onProgressChanged(SeekBar seekBar, int progress,
                                                          boolean fromUser) {
                                linearSpeed = (MIN_LINEAR_SPEED + (progress * STEP_LINEAR_SPEED));
                                linearSpeedText.setText(getString(R.string.percentage, (int) linearSpeed));
                            }
                        }
                );

                rotationSpeedBar.setMax((MAX_ROTATION_SPEED - MIN_ROTATION_SPEED) / STEP_ROTATION_SPEED);
                rotationSpeedBar.setProgress((int) rotationSpeed - MIN_ROTATION_SPEED);
                rotationSpeedBar.setOnSeekBarChangeListener(
                        new SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onStopTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onStartTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onProgressChanged(SeekBar seekBar, int progress,
                                                          boolean fromUser) {
                                rotationSpeed = (MIN_ROTATION_SPEED + (progress * STEP_ROTATION_SPEED));
                                rotationSpeedText.setText(getString(R.string.percentage, (int) rotationSpeed));
                            }
                        }
                );

                panoramaAngleBar.setMax((MAX_ANGLE - MIN_ANGLE) / STEP_ANGLE);
                panoramaAngleBar.setProgress((int) panoramaAngle - MIN_ANGLE);
                panoramaAngleBar.setOnSeekBarChangeListener(
                        new SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onStopTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onStartTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onProgressChanged(SeekBar seekBar, int progress,
                                                          boolean fromUser) {
                                panoramaAngle = (MIN_ANGLE + (progress * STEP_ANGLE));
                                panoramaAngleText.setText(getString(R.string.angle, (int) panoramaAngle));
                            }
                        }
                );

                AlertDialog alertDialog = dialogBuilder.create();
                alertDialog.show();
                alertDialog.getWindow().setBackgroundDrawableResource(R.drawable.style_dialogs);
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.primary_high));
            }
        });

        findViewById(R.id.helpButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Builder dialogBuilder = new Builder(new ContextThemeWrapper(MainActivity.this, R.style.AppTheme));
                LayoutInflater inflater = MainActivity.this.getLayoutInflater();
                final View dialogView = inflater.inflate(R.layout.help, null);
                dialogBuilder.setView(dialogView);

                dialogBuilder.setPositiveButton(R.string.validate, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) { }
                });

                AlertDialog alertDialog = dialogBuilder.create();
                alertDialog.show();
                alertDialog.getWindow().setBackgroundDrawableResource(R.drawable.style_dialogs);
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.primary_high));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        droneDiscoverer.setup();
        droneDiscoverer.addListener(mDiscovererListener);
        droneDiscoverer.startDiscovering();
    }

    @Override
    protected void onPause() {
        super.onPause();
        droneDiscoverer.stopDiscovering();
        droneDiscoverer.cleanup();
        droneDiscoverer.removeListener(mDiscovererListener);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean denied = false;
        if (permissions.length == 0)
            denied = true;
        else
            for (int i = 0; i < permissions.length; i++)
                if (grantResults[i] == PackageManager.PERMISSION_DENIED)
                    denied = true;

        if (denied) {
            Toast.makeText(this, R.string.missing_permission, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private final DroneDiscoverer.Listener mDiscovererListener = new DroneDiscoverer.Listener() {
        @Override
        public void onDronesListUpdated(List<ARDiscoveryDeviceService> dronesList) {
            MainActivity.this.dronesList.clear();
            MainActivity.this.dronesList.addAll(dronesList);
            adapter.notifyDataSetChanged();
        }
    };

    private class DeviceAdapter extends ArrayAdapter {

        class ViewHolder {
            TextView type;
            TextView name;
        }

        @SuppressWarnings("unchecked")
        DeviceAdapter(Context context, List<ARDiscoveryDeviceService> mDronesList) {
            super(context, R.layout.item_device, mDronesList);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {

            ViewHolder holder;
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.item_device, null);
                holder = new ViewHolder();
                holder.type = (TextView) convertView.findViewById(R.id.deviceTypeTextView);
                holder.name = (TextView) convertView.findViewById(R.id.deviceNameTextView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ARDiscoveryDeviceService device = (ARDiscoveryDeviceService) getItem(position);
            if (device != null) {
                ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(device.getProductID());
                switch (product) {
                    case ARDISCOVERY_PRODUCT_BEBOP_2:
                        holder.type.setText(R.string.bebop2_drone);
                        break;
                    case ARDISCOVERY_PRODUCT_SKYCONTROLLER:
                    case ARDISCOVERY_PRODUCT_SKYCONTROLLER_2:
                        holder.type.setText(R.string.skycontroller2);
                        break;
                    default:
                        holder.type.setText(R.string.device_not_supported);
                }
                holder.name.setText(device.getName());
            } else {
                holder.type.setText(R.string.device_not_supported);
                holder.name.setText(R.string.device_not_recognized);
            }
            return convertView;

        }

    }

}
