package com.example.webrtcdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.example.webrtcdemo.webrtc.WebRTCManager;

public class MainActivity extends AppCompatActivity {

    private EditText etRoom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etRoom = findViewById(R.id.et_room);

    }

    public void JoinRoom(View view) {
        WebRTCManager.getInstance().connect(this, etRoom.getText().toString());
    }
}