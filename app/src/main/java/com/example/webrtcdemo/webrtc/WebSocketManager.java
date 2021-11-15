package com.example.webrtcdemo.webrtc;

import android.app.Activity;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.example.webrtcdemo.ChatRoomActivity;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.webrtc.IceCandidate;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
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

        if (wss.startsWith("wss")) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new TrustManagerImpl()}, new SecureRandom());
                SSLSocketFactory factory = null;
                factory = sslContext.getSocketFactory();

                if (factory != null) {
                    mWebSocketClient.setSocket(factory.createSocket());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mWebSocketClient.connect();
    }

    private void handleOffer(Map map) {
        Log.i(TAG, "handleOffer");
        Map data = (Map) map.get("data");
        Map sdpDic;

        if (data != null) {
            sdpDic = (Map) data.get("sdp");
            String socketId = (String) data.get("socketId");

            if (sdpDic != null) {
                String sdp = (String) sdpDic.get("sdp");
                peerConnectionManager.onReceiveOffer(socketId, sdp);
            }
        }
    }

    private void handleRemoteInRoom(Map map) {
        Log.i(TAG, "handleRemoteInRoom");

        Map data = (Map) map.get("data");
        String socketId;
        if (data != null) {
            socketId = (String) data.get("socketId");
            peerConnectionManager.onRemoteJoinRoom(socketId);
        }
    }

    private void handleRemoteCandidate(Map map) {
        Log.i(TAG, "handleRemoteCandidate");

        Map data = (Map) map.get("data");
        String socketId;
        if (data != null) {
            socketId = (String) data.get("socketId");
            String sdpMid = (String) data.get("id");
            sdpMid = (sdpMid == null) ? "video" : sdpMid;

            int sdpMLineIndex = (int) Double.parseDouble(String.valueOf(data.get("label")));
            String candidate = (String) data.get("candidate");
            IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
            peerConnectionManager.onRemoteIceCandidate(socketId, iceCandidate);
        }
    }

    private void handleAnswer(Map map) {
        Log.i(TAG, "handleAnswer");

        Map data = (Map) map.get("data");
        Map sdpDic;
        if (data != null) {
            sdpDic = (Map) data.get("sdp");
            String socketId = (String) data.get("socketId");

            if (sdpDic != null) {
                String sdp = (String) sdpDic.get("sdp");  // 对方响应的sdp
                peerConnectionManager.onReceiverAnswer(socketId, sdp);
            }
        }
    }

    private void handleJoinRoom(Map map) {
        Log.i(TAG, "handleJoinRoom");

        Map data = (Map) map.get("data");
        JSONArray arr;
        if (data != null) {
            arr = (JSONArray) data.get("connections");
            String js = JSONObject.toJSONString(arr, SerializerFeature.WriteClassName);
            List<String> connections = JSONObject.parseArray(js, String.class);

            String myId = (String) data.get("you");
            peerConnectionManager.joinRoom(this, connections, true, myId);
        }
    }

    public void sendOffer(String socketId, String description) {
        Log.i(TAG, "sendOffer socketId = [" + socketId + "], description = [" + description + "]");

        HashMap<String, Object> childMap1 = new HashMap<>();
        childMap1.put("type", "offer");
        childMap1.put("sdp", description);

        HashMap<String, Object> childMap2 = new HashMap<>();
        childMap2.put("socketId", socketId);
        childMap2.put("sdp", childMap1);

        HashMap<String, Object> map = new HashMap<>();
        map.put("eventName", "__offer");
        map.put("data", childMap2);

        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
        Log.i(TAG, "send-->" + jsonString);
        mWebSocketClient.send(jsonString);

    }

    public void sendAnswer(String socketId, String description) {
        Log.i(TAG, "sendAnswer socketId = [" + socketId + "], description = [" + description + "]");

        HashMap<String, Object> childMap1 = new HashMap<>();
        childMap1.put("type", "answer");
        childMap1.put("sdp", description);

        HashMap<String, Object> childMap2 = new HashMap<>();
        childMap2.put("socketId", socketId);
        childMap2.put("sdp", childMap1);

        HashMap<String, Object> map = new HashMap<>();
        map.put("eventName", "__answer");
        map.put("data", childMap2);

        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
        Log.i(TAG, "send-->" + jsonString);
        mWebSocketClient.send(jsonString);
    }

    public void sendIceCandidate(String socketId, IceCandidate iceCandidate) {
        Log.i(TAG, "sendIceCandidate socketId: " + socketId);

        HashMap<String, Object> childMap = new HashMap<>();
        childMap.put("id", iceCandidate.sdpMid);
        childMap.put("label", iceCandidate.sdpMLineIndex);
        childMap.put("candidate", iceCandidate.sdp);
        childMap.put("socketId", socketId);

        HashMap<String, Object> map = new HashMap<>();
        map.put("eventName", "__ice_candidate");
        map.put("data", childMap);

        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
        Log.i(TAG, "send-->" + jsonString);
        mWebSocketClient.send(jsonString);
    }

    public void joinRoom(String roomId) {
        Log.i(TAG, "joinRoom roomId = [" + roomId + "]");

        Map<String, String> childMap = new HashMap<>();
        childMap.put("room", roomId);

        Map<String, Object> map = new HashMap<>();
        map.put("eventName", "__join");
        map.put("data", childMap);

        JSONObject object = new JSONObject(map);
        String jsonString = object.toString();
        Log.i(TAG, "send-->" + jsonString);
        mWebSocketClient.send(jsonString);
    }


    // 忽略证书
    public static class TrustManagerImpl implements X509TrustManager {

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
