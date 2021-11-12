package com.example.webrtcdemo.webrtc;

import android.app.Activity;
import android.util.Log;

import com.example.webrtcdemo.ChatRoomActivity;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Zach on 2021/11/10 8:45
 */
public class PeerConnectionManager {
    private static final String TAG = "PeerConnectionManager";

    enum Role {
        Caller,
        Receiver,
    }

    private Role role;
    private String myId;
    private boolean isVideoEnable;

    private Activity mInstance;

    private ExecutorService executor;
    private PeerConnectionFactory factory;
    private EglBase rootEglBase;
    private MediaStream mediaStream;

    private WebSocketManager webSocketManager;
    private ArrayList<String> connectionIDArray;
    private Map<String, Peer> connectionPeerDic;
    private ArrayList<PeerConnection.IceServer> ICEServers;

    public PeerConnectionManager() {
        executor = Executors.newSingleThreadExecutor();
        connectionIDArray = new ArrayList<>();
        connectionPeerDic = new HashMap<>();
        ICEServers = new ArrayList<>();
        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder("turn:8.210.xx.xx:3478?transport=udp").
                setUsername("zachliu").setPassword("123456").createIceServer();

        ICEServers.add(iceServer);
    }

    public void initContext(Activity activity, EglBase rootEglBase) {
        this.mInstance = activity;
        this.rootEglBase = rootEglBase;
    }

    private PeerConnectionFactory createConnectionFactory() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(mInstance).createInitializationOptions());
        VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true);
        VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder().setOptions(options).
                setAudioDeviceModule(JavaAudioDeviceModule.builder(mInstance).createAudioDeviceModule()).
                setVideoDecoderFactory(decoderFactory).
                setVideoEncoderFactory(encoderFactory).
                createPeerConnectionFactory();

        return peerConnectionFactory;
    }


    public void joinRoom(WebSocketManager webSocket, ArrayList<String> connections, boolean isVideoEnable, String id) {
        this.myId = id;
        this.isVideoEnable = isVideoEnable;
        this.webSocketManager = webSocket;

        connectionIDArray.addAll(connections);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (factory == null) {
                    factory = createConnectionFactory();
                }

                if (mediaStream == null) {
                    createLocalStream();
                }
                createPeerConnection();

                addStream();

                // 给房间服务器的其他人， 发送一个offer
                createOffers();
            }
        });

    }

    public void onReceiverAnswer(String socketId, String sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Peer mPeer = connectionPeerDic.get(socketId);
                if (mPeer != null) {
                    SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                    mPeer.pc.setRemoteDescription(mPeer, sessionDescription);
                }
            }
        });
    }

    public void onRemoteIceCandidate(String socketId, IceCandidate iceCandidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Peer peer =connectionPeerDic.get(socketId);
                if (peer != null) {
                    peer.pc.addIceCandidate(iceCandidate);
                }
            }
        });
    }

    public void onRemoteJoinRoom(String socketId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (mediaStream == null) {
                    createLocalStream();
                }

                Peer mPeer = new Peer(socketId);
                mPeer.pc.addStream(mediaStream);
                connectionIDArray.add(socketId);
                connectionPeerDic.put(socketId, mPeer);
            }
        });
    }

    public void onReceiveOffer(String socketId, String description) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                role = Role.Receiver;
                Peer mPeer = connectionPeerDic.get(socketId);
                SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, description);
                if (mPeer != null) {
                    mPeer.pc.setRemoteDescription(mPeer, sdp);
                }
            }
        });
    }


    private void createOffers() {
        for (Map.Entry<String, Peer> entry : connectionPeerDic.entrySet()) {
            role = Role.Caller;
            Peer mPeer = entry.getValue();
            mPeer.pc.createOffer(mPeer, offerOrAnswerConstraint());
        }
    }


    private void addStream() {
        for (Map.Entry<String, Peer> entry : connectionPeerDic.entrySet()) {
            if (mediaStream == null) {
                createLocalStream();
            }
            entry.getValue().pc.addStream(mediaStream);
        }
    }

    private void createPeerConnection() {
        for (String str : connectionIDArray) {
            Peer peer = new Peer(str);
            connectionPeerDic.put(str, peer);
        }
    }



    private void createLocalStream() {
        mediaStream = factory.createLocalMediaStream("ARDAMS");
        AudioSource audioSource = factory.createAudioSource(createAudioConstraints());
        AudioTrack audioTrack = factory.createAudioTrack("ARDAMSa0", audioSource);
        mediaStream.addTrack(audioTrack);

        if (isVideoEnable) {
            VideoCapturer videoCapturer = createVideoCapture();
            VideoSource videoSource = factory.createVideoSource(videoCapturer.isScreencast());
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
            videoCapturer.initialize(surfaceTextureHelper, mInstance, videoSource.getCapturerObserver());
            videoCapturer.startCapture(320, 240, 10);
            VideoTrack videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);
            mediaStream.addTrack(videoTrack);

            if (mInstance != null) {
                ((ChatRoomActivity) mInstance).onSetLocalStream(mediaStream, myId);
            }
        }
    }

    private VideoCapturer createVideoCapture() {
        VideoCapturer videoCapturer = null;
        if (Camera2Enumerator.isSupported(mInstance)) {
            Camera2Enumerator enumerator = new Camera2Enumerator(mInstance);
            videoCapturer = createCameraCapture(enumerator);
        } else {
            Camera1Enumerator enumerator = new Camera1Enumerator(true);
            videoCapturer = createCameraCapture(enumerator);
        }
        return videoCapturer;
    }

    /**
     * 获取前置或后置摄像头
     */
    private VideoCapturer createCameraCapture(CameraEnumerator enumerator) {
        String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        for (String deviceName : deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    // 回音消除
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    // 噪声抑制
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
    // 自动增益控制
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    // 高通滤波器
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";

    private MediaConstraints createAudioConstraints() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"));

        return audioConstraints;
    }

    private MediaConstraints offerOrAnswerConstraint() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        List<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(isVideoEnable)));
        mediaConstraints.mandatory.addAll(keyValuePairs);

        return mediaConstraints;
    }


    private class Peer implements SdpObserver, PeerConnection.Observer {
        private PeerConnection pc;
        private String socketId;

        public Peer(String socketId) {
            this.socketId = socketId;
            pc = createPeerConnection();
        }

        private PeerConnection createPeerConnection() {
            if (factory == null) {
                factory = createConnectionFactory();
            }

            PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(ICEServers);
            return factory.createPeerConnection(rtcConfiguration, this);
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.d(TAG, "onIceCandidate() = [" + iceCandidate.toString() + "]");
            webSocketManager.sendIceCandidate(socketId, iceCandidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {  // ICE交换完回调
            ((ChatRoomActivity) mInstance).onAddRemoteStream(mediaStream, socketId);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.d(TAG, "onCreateSuccess() called with: sessionDescription = [" + sessionDescription.description + "]");

            pc.setLocalDescription(Peer.this, sessionDescription);
        }

        @Override
        public void onSetSuccess() {
            if (pc.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                if (role == Role.Caller) {
                    webSocketManager.sendOffer(socketId, pc.getLocalDescription().description);
                } else if (role == Role.Receiver) {
                    webSocketManager.sendAnswer(socketId, pc.getLocalDescription().description);
                }
            } else if (pc.signalingState() == PeerConnection.SignalingState.HAVE_REMOTE_OFFER) {
                pc.createAnswer(Peer.this, offerOrAnswerConstraint());
            } else if (pc.signalingState() == PeerConnection.SignalingState.STABLE) {
                if (role == Role.Receiver) {
                    webSocketManager.sendAnswer(socketId, pc.getLocalDescription().description);
                }
            }
        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }
    }
}
