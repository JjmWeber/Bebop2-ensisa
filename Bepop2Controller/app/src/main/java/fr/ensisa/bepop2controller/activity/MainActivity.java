package fr.ensisa.bepop2controller.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
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

import fr.ensisa.bepop2controller.R;
import fr.ensisa.bepop2controller.discovery.DroneDiscoverer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final String EXTRA_DEVICE_SERVICE = "EXTRA_DEVICE_SERVICE";

    private static final String[] PERMISSIONS_NEEDED = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };

    private static final int REQUEST_CODE_PERMISSIONS_REQUEST = 1;

    public DroneDiscoverer droneDiscoverer;

    private final List<ARDiscoveryDeviceService> dronesList = new ArrayList<>();
    private DeviceAdapter adapter;

    static {
        ARSDK.loadSDKLibs();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final ListView listView = (ListView) findViewById(R.id.deviceList);

        adapter = new DeviceAdapter(this, dronesList);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = null;

                ARDiscoveryDeviceService service = (ARDiscoveryDeviceService) adapter.getItem(position);
                ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(service.getProductID());
                switch (product) {
                    case ARDISCOVERY_PRODUCT_BEBOP_2:
                        intent = new Intent(MainActivity.this, Bebop2Activity.class);
                        break;
                    case ARDISCOVERY_PRODUCT_SKYCONTROLLER_2:
                        intent = new Intent(MainActivity.this, SkyController2Activity.class);
                        break;
                    default:
                        Log.e(TAG, "The type " + product + " is not supported by this sample");
                }

                if(intent != null) {
                    intent.putExtra(EXTRA_DEVICE_SERVICE, service);
                    startActivity(intent);
                }
            }
        });

        droneDiscoverer = new DroneDiscoverer(this);

        Set<String> permissionsToRequest = new HashSet<>();
        for (String permission : PERMISSIONS_NEEDED) {
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                if(ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    Toast.makeText(this, "Please allow permission " + permission, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                } else {
                    permissionsToRequest.add(permission);
                }
            }
        }

        if(permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[permissionsToRequest.size()]),
                    REQUEST_CODE_PERMISSIONS_REQUEST);
        }
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
        if(permissions.length == 0)
            denied = true;
        else
            for (int i = 0; i < permissions.length; i++)
                if(grantResults[i] == PackageManager.PERMISSION_DENIED)
                    denied = true;

        if(denied) {
            Toast.makeText(this, "At least one permission is missing.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private final DroneDiscoverer.Listener mDiscovererListener = new  DroneDiscoverer.Listener() {
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
            ImageView icon;
        }

        public DeviceAdapter(Context context, List<ARDiscoveryDeviceService> mDronesList) {
            super(context, R.layout.item_device, mDronesList);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder;
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.item_device, null);
                holder = new ViewHolder();
                holder.type = (TextView) convertView.findViewById(R.id.deviceTypeTextView);
                holder.name = (TextView) convertView.findViewById(R.id.deviceNameTextView);
                holder.icon = (ImageView) convertView.findViewById(R.id.deviceTypeImageView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ARDiscoveryDeviceService device = (ARDiscoveryDeviceService) getItem(position);
            ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(device.getProductID());
            switch (product) {
                case ARDISCOVERY_PRODUCT_BEBOP_2:
                    holder.type.setText("Bebop2 Drone");
                    holder.icon.setImageResource(R.drawable.ic_drone_device);
                    break;
                case ARDISCOVERY_PRODUCT_SKYCONTROLLER_2:
                    holder.type.setText("SkyController 2");
                    holder.icon.setImageResource(R.drawable.ic_controller_device);
                    break;
                default:
                    holder.type.setText(R.string.device_not_supported);
                    holder.icon.setImageResource(R.drawable.ic_device_not_supported);
            }
            holder.name.setText(device.getName());
            return convertView;

        }

    }

}
