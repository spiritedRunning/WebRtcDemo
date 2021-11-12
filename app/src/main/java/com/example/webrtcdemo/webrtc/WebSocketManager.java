package com.example.webrtcdemo.webrtc;

import android.app.Activity;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.example.webrtcdemo.ChatRoomActivity;
import com.example.webrtcdemo.MainActivity;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.webrtc.IceCandidate;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.X509TrustManager;


/**
 * Created by Zach on 2021/11/10 8:46
 */
public class WebSocketManager {
    private static final String TAG = "WebSocketManager";

    private PeerConnectionManager peerConnectionManager;
    private Activity activity;
    private WebSocketClient mWebSocketClient;

    public WebSocketManager(Activity activity, PeerConnectionManager peerConnectionManager) {
        this.activity = activity;
        this.peerConnectionManager = peerConnectionManager;
    }

    public void connect(String wss) {
        URI uri = null;
        try {
            uri = new URI(wss);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                Log.i(TAG, "onOpen");
                ChatRoomActivity.openActivity(activity);
            }

            @Override
            public void onMessage(String message) {
                Log.d(TAG, "onMessage() called with: message = [" + message + "]");

                Map map = JSON.parseObject(message, Map.class);
                String eventName = (String) map.get("eventName");
                if ("_peers".equals(eventName)) {
                    handleJoinRoom(map);
                }
                if ("_answer".equals(eventName)) {
                    handleAnswer(map);
                }
                if ("_ice_candidate".equals(eventName)) {
                    handleRemoteCandidate(map);
                }
                if ("_new_peer".equals(eventName)) {
                    handleRemoteInRoom(map);
                }
                if ("_offer".equals(eventName)) {
                    handleOffer(map);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.d(TAG, "onClose() called with: code = [" + code + "], reason = [" + reason + "], remote = [" + remote + "]");
            }

            @Override
            public void onError(Exception ex) {
                Log.d(TAG, "onError() called with: ex = [" + ex.getMessage() + "]");
            }
        };
    }

    private void handleOffer(Map map) {
    }

    private void handleRemoteInRoom(Map map) {
    }

    private void handleRemoteCandidate(Map map) {
    }

    private void handleAnswer(Map map) {
    }

    private void handleJoinRoom(Map map) {
    }

    public void sendOffer(String socketId, String description) {
    }

    public void sendAnswer(String socketId, String description) {
    }

    public void sendIceCandidate(String socketId, IceCandidate iceCandidate) {
    }

    public void joinRoom(String roomId) {
    }


    // 忽略证书
    public static class TrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
