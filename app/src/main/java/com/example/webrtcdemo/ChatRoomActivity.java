package com.example.webrtcdemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;

import com.example.webrtcdemo.webrtc.WebRTCManager;

import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Zach on 2021/11/12 16:47
 */
public class ChatRoomActivity extends Activity {
    private WebRTCManager webRTCManager;
    private EglBase rootEglBase;
    private VideoTrack localVideoTrack;
    private Map<String, SurfaceViewRenderer> videoViews = new HashMap<>();

    private List<String> persons = new ArrayList<>();
    private FrameLayout wrVideoLayout;


    public static void openActivity(Activity activity) {
        Intent intent = new Intent(activity, ChatRoomActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_room);
        initView();
    }

    private void initView() {
        wrVideoLayout = findViewById(R.id.wr_video_view);
        wrVideoLayout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rootEglBase = EglBase.create();
        webRTCManager = WebRTCManager.getInstance();
        if (!PermissionUtil.isNeedRequestPermission(this)) {
            webRTCManager.joinRoom(this, rootEglBase);
        }

    }

    public void onAddRemoteStream(MediaStream stream, String userId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addView(userId, stream);
            }
        });
    }

    public void onSetLocalStream(MediaStream stream, String userId) {
        if (stream.videoTracks.size() > 0) {
            localVideoTrack = stream.videoTracks.get(0);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addView(userId, stream);
            }
        });
    }

    private void addView(String userId, MediaStream stream) {
        SurfaceViewRenderer surfaceViewRenderer = new SurfaceViewRenderer(this);
        surfaceViewRenderer.init(rootEglBase.getEglBaseContext(), null);
        surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        surfaceViewRenderer.setMirror(true);

        if (stream.videoTracks.size() > 0) {
            stream.videoTracks.get(0).addSink(surfaceViewRenderer);
        }
        videoViews.put(userId, surfaceViewRenderer);
        persons.add(userId);
        wrVideoLayout.addView(surfaceViewRenderer);

        int size = videoViews.size();
        for (int i = 0; i < size; i++) {
            String peerId = persons.get(i);
            SurfaceViewRenderer renderer = videoViews.get(peerId);
            if (renderer != null) {
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                layoutParams.height = WindowUtils.getWidth(this, size);
                layoutParams.width = WindowUtils.getWidth(this, size);
                layoutParams.leftMargin = WindowUtils.getX(this, size, i);
                layoutParams.topMargin = WindowUtils.getY(this, size, i);
                renderer.setLayoutParams(layoutParams);
            }
        }
    }

}
