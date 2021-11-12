package com.example.webrtcdemo.webrtc;

import android.app.Activity;

import com.example.webrtcdemo.MainActivity;

import org.webrtc.EglBase;

/**
 * Created by Zach on 2021/11/10 8:41
 */
public class WebRTCManager {

    private static final WebRTCManager ourInstance = new WebRTCManager();
    private PeerConnectionManager peerConnectionManager;
    private WebSocketManager webSocketManager;

    private String roomId = "";

    public static WebRTCManager getInstance() {
        return ourInstance;
    }

    public void connect(MainActivity activity, String roomId) {
        this.roomId = roomId;
        peerConnectionManager = new PeerConnectionManager();
        webSocketManager = new WebSocketManager(activity, peerConnectionManager);
        webSocketManager.connect("wss://");  // todo

    }

    public void joinRoom(Activity activity, EglBase eglBase) {
        peerConnectionManager.initContext(activity, eglBase);
        webSocketManager.joinRoom(roomId);
    }
}
