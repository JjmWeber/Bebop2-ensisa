package fr.ensisa.bepop2controller.activities;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.parrot.arsdk.ARSDK;

import fr.ensisa.bepop2controller.R;
import fr.ensisa.bepop2controller.discovery.DroneDiscoverer;

public class MainActivity extends AppCompatActivity {

    static {
        ARSDK.loadSDKLibs();
    }

    private DroneDiscoverer droneDiscoverer;
    private TextView feedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        droneDiscoverer = new DroneDiscoverer(getApplicationContext());
        feedback = (TextView) findViewById(R.id.feedback);
    }

    private void feedback(String s) {
        feedback.setText(s);
    }

    public void onClick(View v) {
        feedback("doing...");
        droneDiscoverer.initDiscoveryService();
        feedback("done");
    }

}
