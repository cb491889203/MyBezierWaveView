package com.example.chenb.mybezierwaveview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

	private WaveView wv;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		wv = (WaveView) findViewById(R.id.wv);
	}

	public void addcount(View v) {
		wv.setmWaveCount(wv.getmWaveCount() + 1);
		wv.startWave();
	}

	public void addspeed(View v) {
		wv.setmWaveSpeed(wv.getmWaveSpeed() + 10);
		wv.startWave();
	}
	public void stop(View v) {
		wv.stopWave();
	}

	public void changeY(View v) {
		wv.setmLevelLine(wv.getmLevelLine() + 100f);
		wv.startWave();
	}
	public void start(View v) {
		wv.startWave();
	}

}
