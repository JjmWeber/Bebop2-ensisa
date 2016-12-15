package fr.ensisa.bepop2controller.activity;

import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import fr.ensisa.bepop2controller.R;

public class ControllerTestActivity extends AppCompatActivity {

    private FloatingActionButton frontFlipBt;
    private FloatingActionButton backFlipBt;
    private FloatingActionButton leftFlipBt;
    private FloatingActionButton rightFlipBt;
    private FloatingActionButton flipBt;

    private boolean isFlipping = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skycontroller2);

        frontFlipBt = (FloatingActionButton) findViewById(R.id.frontFlipButton);
        backFlipBt = (FloatingActionButton) findViewById(R.id.backFlipButton);
        leftFlipBt = (FloatingActionButton) findViewById(R.id.leftFlipButton);
        rightFlipBt = (FloatingActionButton) findViewById(R.id.rightFlipButton);

        flipBt = (FloatingActionButton) findViewById(R.id.flipButton);
        flipBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isFlipping) {
                    frontFlipBt.setVisibility(View.VISIBLE);
                    backFlipBt.setVisibility(View.VISIBLE);
                    leftFlipBt.setVisibility(View.VISIBLE);
                    rightFlipBt.setVisibility(View.VISIBLE);
                    flipBt.setImageDrawable(ControllerTestActivity.this.getDrawable(R.drawable.ic_cancel));
                } else {
                    frontFlipBt.setVisibility(View.INVISIBLE);
                    backFlipBt.setVisibility(View.INVISIBLE);
                    leftFlipBt.setVisibility(View.INVISIBLE);
                    rightFlipBt.setVisibility(View.INVISIBLE);
                    flipBt.setImageDrawable(ControllerTestActivity.this.getDrawable(R.drawable.ic_flip));
                }
                isFlipping = !isFlipping;
            }
        });
    }
}
