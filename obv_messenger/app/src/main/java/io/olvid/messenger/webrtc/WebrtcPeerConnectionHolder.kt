/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.olvid.messenger.webrtc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.webrtc.WebrtcCallService.CallParticipant
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.INTERNAL_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.PEER_CONNECTION_CREATION_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.SERVER_UNREACHABLE
import io.olvid.messenger.webrtc.WebrtcCallService.GatheringPolicy
import io.olvid.messenger.webrtc.WebrtcCallService.GatheringPolicy.GATHER_CONTINUOUSLY
import io.olvid.messenger.webrtc.json.JsonIceCandidate
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DataChannel.Buffer
import org.webrtc.DataChannel.Init
import org.webrtc.DataChannel.Observer
import org.webrtc.DataChannel.State.OPEN
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
import org.webrtc.PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.IceConnectionState.CHECKING
import org.webrtc.PeerConnection.IceConnectionState.COMPLETED
import org.webrtc.PeerConnection.IceConnectionState.CONNECTED
import org.webrtc.PeerConnection.IceConnectionState.DISCONNECTED
import org.webrtc.PeerConnection.IceConnectionState.FAILED
import org.webrtc.PeerConnection.IceConnectionState.NEW
import org.webrtc.PeerConnection.IceGatheringState
import org.webrtc.PeerConnection.IceGatheringState.COMPLETE
import org.webrtc.PeerConnection.IceGatheringState.GATHERING
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnection.IceTransportsType.RELAY
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnection.SdpSemantics.UNIFIED_PLAN
import org.webrtc.PeerConnection.SignalingState
import org.webrtc.PeerConnection.SignalingState.HAVE_LOCAL_OFFER
import org.webrtc.PeerConnection.SignalingState.HAVE_REMOTE_OFFER
import org.webrtc.PeerConnection.SignalingState.STABLE
import org.webrtc.PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK
import org.webrtc.PeerConnectionFactory
import org.webrtc.PeerConnectionFactory.InitializationOptions
import org.webrtc.PeerConnectionFactory.Options
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SessionDescription.Type
import org.webrtc.SessionDescription.Type.OFFER
import org.webrtc.SessionDescription.Type.ROLLBACK
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.BufferedReader
import java.io.StringReader
import java.net.InetSocketAddress
import java.net.Proxy.Type.HTTP
import java.net.ProxySelector
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Timer
import java.util.TimerTask
import java.util.regex.Pattern
import kotlin.text.RegexOption.MULTILINE

class WebrtcPeerConnectionHolder(
    private val webrtcCallService: WebrtcCallService,
    private val callParticipant: CallParticipant
) {
    val peerConnectionObserver = PeerConnectionObserver()
    private val sessionDescriptionObserver = SessionDescriptionObserver()
    private var peerSessionDescriptionType: String? = null
    private var peerSessionDescription: String? = null
    private var reconnectOfferCounter = 0
    private var reconnectAnswerCounter = 0
    private var turnUsername: String? = null
    private var turnPassword: String? = null
    private var gatheringPolicy: GatheringPolicy
    private var readyToProcessPeerIceCandidates = false
    private val pendingPeerIceCandidates: MutableList<JsonIceCandidate>
    var peerConnection: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    var remoteVideoTrack by mutableStateOf<VideoTrack?>(null)
    var remoteScreenTrack by mutableStateOf<VideoTrack?>(null)
    var dataChannel: DataChannel? = null
    var dataChannelMessageListener: DataChannelMessageListener? = null

    private var audioReceiver: RtpReceiver? = null
    private var peerAudioLevelListener: Timer? = null
    var peerAudioLevel by mutableDoubleStateOf(0.0)
        private set

    companion object {

        const val MAXIMUM_OTHER_PARTICIPANTS_FOR_VIDEO = 2
        const val OLVID_STREAM_ID = "OlvidStreamId"
        const val VIDEO_STREAM_ID = "video"
        const val SCREENCAST_STREAM_ID = "screencast"

        private val TURN_SCALED_SERVERS: List<String> = mutableListOf(
            "turn:turn-scaled.olvid.io:5349?transport=udp",
            "turn:turn-scaled.olvid.io:443?transport=tcp",
            "turns:turn-scaled.olvid.io:443?transport=tcp"
        )
        private val TURN_SCALED_SERVERS_EU: List<String> = mutableListOf(
            "turn:eu.turn-scaled.olvid.io:5349?transport=udp",
            "turn:eu.turn-scaled.olvid.io:443?transport=tcp",
            "turns:eu.turn-scaled.olvid.io:443?transport=tcp"
        )
        private val TURN_SCALED_SERVERS_US: List<String> = mutableListOf(
            "turn:us.turn-scaled.olvid.io:5349?transport=udp",
            "turn:us.turn-scaled.olvid.io:443?transport=tcp",
            "turns:us.turn-scaled.olvid.io:443?transport=tcp"
        )
        private val TURN_SCALED_SERVERS_AP: List<String> = mutableListOf(
            "turn:ap.turn-scaled.olvid.io:5349?transport=udp",
            "turn:ap.turn-scaled.olvid.io:443?transport=tcp",
            "turns:ap.turn-scaled.olvid.io:443?transport=tcp"
        )
        private val AUDIO_CODECS: Set<String> =
            HashSet(mutableListOf("opus", "PCMU", "PCMA", "telephone-event", "red"))
        private const val ADDITIONAL_OPUS_OPTIONS =
            ";cbr=1" // by default send and receive are mono, no need to add "stereo=0;sprop-stereo=0"
        private const val FIELD_TRIAL_INTEL_VP8 = "WebRTC-IntelVP8/Enabled/"
        private const val FIELD_TRIAL_H264_HIGH_PROFILE = "WebRTC-H264HighProfile/Enabled/"
        private const val FIELD_TRIAL_H264_SIMULCAST = "WebRTC-H264Simulcast/Enabled/"
        private const val FIELD_TRIAL_FLEX_FEC =
            "WebRTC-FlexFEC-03/Enabled/WebRTC-FlexFEC-03-Advertised/Enabled/"
        var eglBase: EglBase? = null
        var peerConnectionFactory: PeerConnectionFactory? = null
        var audioDeviceModule: AudioDeviceModule? = null
        var audioSource: AudioSource? = null
        var videoSource: VideoSource? = null
        var screenShareVideoSource: VideoSource? = null
        var localVideoTrack by mutableStateOf<VideoTrack?>(null)
        var localScreenTrack by mutableStateOf<VideoTrack?>(null)

        var localAudioLevel by mutableDoubleStateOf(0.0)
            private set

        private var localAudioLevelListener: Timer? = null

        fun initializePeerConnectionFactory() {
            val initBuilder = InitializationOptions.builder(App.getContext())
            initBuilder.setFieldTrials(FIELD_TRIAL_INTEL_VP8 + FIELD_TRIAL_H264_HIGH_PROFILE + FIELD_TRIAL_FLEX_FEC + FIELD_TRIAL_H264_SIMULCAST)
            PeerConnectionFactory.initialize(initBuilder.createInitializationOptions())
            audioDeviceModule = JavaAudioDeviceModule.builder(App.getContext())
                .setUseHardwareAcousticEchoCanceler(SettingsActivity.useHardwareEchoCanceler())
                .setUseHardwareNoiseSuppressor(SettingsActivity.useHardwareNoiseSuppressor())
                .createAudioDeviceModule()
            eglBase = EglBase.create()
            val videoEncoderFactory: VideoEncoderFactory =
                DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true)
            val videoDecoderFactory: VideoDecoderFactory =
                DefaultVideoDecoderFactory(eglBase?.eglBaseContext)


//            org.webrtc.Logging.enableLogToDebugOutput(org.webrtc.Logging.Severity.LS_WARNING);

            val builder = PeerConnectionFactory.builder()
                .setOptions(Options())
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(videoEncoderFactory)
                .setVideoDecoderFactory(videoDecoderFactory)

            // check if a proxy is required for WebRCT
            try {
                val proxies =
                    ProxySelector.getDefault().select(URI.create("https://turn-scaled.olvid.io/"))
                for (proxy in proxies) {
                    val type = proxy.type()
                    if (type == HTTP) {
                        val address = proxy.address()
                        if (address is InetSocketAddress) {
                            builder.setHttpsProxy(address.hostString, address.port)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val userAgentProperty = System.getProperty("http.agent")
            if (userAgentProperty != null) {
                builder.setUserAgent(userAgentProperty)
            }
            peerConnectionFactory = builder.createPeerConnectionFactory()
            audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
            videoSource = peerConnectionFactory?.createVideoSource(false)
            screenShareVideoSource = peerConnectionFactory?.createVideoSource(true)
        }

        private fun getIceServer(username: String?, password: String?): IceServer {
            val servers: List<String> = if (SettingsActivity.getScaledTurn() != null) {
                when (SettingsActivity.getScaledTurn()) {
                    "par" -> TURN_SCALED_SERVERS_EU
                    "nyc" -> TURN_SCALED_SERVERS_US
                    "sng" -> TURN_SCALED_SERVERS_AP
                    "global" -> TURN_SCALED_SERVERS
                    else -> TURN_SCALED_SERVERS
                }
            } else {
                TURN_SCALED_SERVERS
            }
            return IceServer.builder(servers)
                .setUsername(username)
                .setPassword(password)
                // Well, let's encrypt root is not correctly recognized by libwebrtc and there is no way to override the root CAs, so we disable certificate check...
                .setTlsCertPolicy(TLS_CERT_POLICY_INSECURE_NO_CHECK)
                .createIceServer()
        }

        fun globalCleanup() {
            localAudioLevelListener?.cancel()
            localAudioLevelListener = null
            try {
                localVideoTrack?.dispose()
            } catch (ignored: Exception) {
            }
            localVideoTrack = null
            try {
                localScreenTrack?.dispose()
            } catch (ignored: Exception) {
            }
            localScreenTrack = null
            audioSource?.dispose()
            audioSource = null
            videoSource?.dispose()
            videoSource = null
            screenShareVideoSource?.dispose()
            screenShareVideoSource = null
            audioDeviceModule?.release()
            audioDeviceModule = null
            eglBase?.release()
            eglBase = null
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
        }
    }

    fun setAudioEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
    }

    fun setTurnCredentials(turnUsername: String?, turnPassword: String?) {
        this.turnUsername = turnUsername
        this.turnPassword = turnPassword
    }

    fun setPeerSessionDescription(sessionDescriptionType: String?, sessionDescription: String) {
        if (peerConnection == null) {
            Logger.d("☎ Setting peer sdp\n$sessionDescription")
            peerSessionDescriptionType = sessionDescriptionType
            peerSessionDescription = sessionDescription
        } else {
            Logger.d("☎ Setting remote description:\n$sessionDescription")
            peerConnection?.setRemoteDescription(
                sessionDescriptionObserver, SessionDescription(
                    Type.fromCanonicalForm(sessionDescriptionType), sessionDescription
                )
            )
            readyToProcessPeerIceCandidates = true
            for (jsonIceCandidate in pendingPeerIceCandidates) {
                peerConnection?.addIceCandidate(
                    IceCandidate(
                        jsonIceCandidate.sdpMid,
                        jsonIceCandidate.sdpMLineIndex,
                        jsonIceCandidate.sdp
                    )
                )
            }
            pendingPeerIceCandidates.clear()
        }
    }

    fun setGatheringPolicy(gatheringPolicy: GatheringPolicy) {
        this.gatheringPolicy = gatheringPolicy
    }

    fun sendDataChannelMessage(message: String) {
        dataChannel?.send(
            Buffer(
                ByteBuffer.wrap(message.toByteArray(StandardCharsets.UTF_8)),
                false
            )
        )
    }

    private var audioSender: RtpSender? = null
    private fun createAudioTrack() {
        audioTrack = peerConnectionFactory?.createAudioTrack("audio0", audioSource)
        audioTrack?.setEnabled(!webrtcCallService.microphoneMuted)
        audioSender = peerConnection?.addTrack(audioTrack, listOf(OLVID_STREAM_ID))
        startLocalAudioLevelListener()
    }

    private fun startLocalAudioLevelListener() {
        if (localAudioLevelListener == null) {
            localAudioLevelListener = Timer().apply {
                scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        try {
                            peerConnection?.getStats(
                                audioSender
                            ) {
                                (it.statsMap.values.find { it.type == "media-source" }?.members?.getOrDefault(
                                    "audioLevel",
                                    null
                                ) as? Double)?.let {
                                    if (webrtcCallService.microphoneMuted && webrtcCallService.speakingWhileMuted.not() && it > 0.3) {
                                        webrtcCallService.speakingWhileMuted = true
                                    }
                                    localAudioLevel = if (webrtcCallService.microphoneMuted) 0.0 else it
                                }

                            }
                        } catch (ignored: Exception) {
                            cancel()
                        }
                    }
                }, 0, 200)
            }
        }
    }

    private fun startPeerAudioLevelListener() {
        if (peerAudioLevelListener == null) {
            peerAudioLevelListener = Timer().apply {
                scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        try {
                            peerConnection?.getStats(
                                audioReceiver
                            ) {
                                (it.statsMap.values.find { it.type == "inbound-rtp" }?.members?.getOrDefault(
                                    "audioLevel",
                                    null
                                ) as? Double)?.let {
                                    peerAudioLevel = it
                                }
                            }
                        } catch (ignored: Exception) {
                            cancel()
                        }
                    }
                }, 0, 200)
            }
        }
    }

    private fun createVideoTrack() {
        Logger.d("☎ Creating video track")
        localVideoTrack = peerConnectionFactory?.createVideoTrack("video0", videoSource)
        localVideoTrack?.setEnabled(false)
    }

    var videoSender: RtpSender? = null
    var screenSender: RtpSender? = null

    // returns true if a track was actually added
    fun enableVideoTrack() : Boolean {
        Logger.d("☎ Enabling video track")
        if (localVideoTrack == null) {
            createVideoTrack()
        }
        localVideoTrack?.let {
            try {
                it.setEnabled(true)
                if (videoSender != null) {
                    videoSender?.setTrack(localVideoTrack, false)
                } else {
                    videoSender = peerConnection?.addTrack(
                        localVideoTrack,
                        listOf(OLVID_STREAM_ID, VIDEO_STREAM_ID)
                    )
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private fun createScreenSharingTrack() {
        localScreenTrack =
            peerConnectionFactory?.createVideoTrack("screen0", screenShareVideoSource)
        localScreenTrack?.setEnabled(false)
    }

    // returns true if a track was actually added
    fun enableScreenSharingTrack() : Boolean {
        Logger.d("☎ Enabling screen share track")
        if (localScreenTrack == null) {
            createScreenSharingTrack()
        }
        localScreenTrack?.let {
            try {
                it.setEnabled(true)
                if (screenSender != null) {
                    screenSender?.setTrack(localScreenTrack, false)
                } else {
                    screenSender = peerConnection?.addTrack(
                        localScreenTrack,
                        listOf(SCREENCAST_STREAM_ID) // screencast does not need to be synchronized with voice
                    )
                    return true
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        return false
    }

    private fun createDataChannel() {
        val init = Init()
        init.ordered = true
        init.negotiated = true
        init.id = 1
        dataChannel = peerConnection?.createDataChannel("data0", init)
        val dataChannelObserver: Observer = object : Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                if (dataChannel?.state() == OPEN) {

                    dataChannelMessageListener?.onConnect()

                }
            }

            override fun onMessage(buffer: Buffer) {
                dataChannelMessageListener?.onMessage(buffer.data)
            }
        }
        dataChannel?.registerObserver(dataChannelObserver)
    }

    fun createPeerConnection() {
        val iceServer = getIceServer(turnUsername, turnPassword)
        val configuration = RTCConfiguration(listOf(iceServer))
        configuration.iceTransportsType = RELAY
        configuration.sdpSemantics = UNIFIED_PLAN
        configuration.continualGatheringPolicy =
            if (gatheringPolicy == GATHER_CONTINUOUSLY) GATHER_CONTINUALLY else GATHER_ONCE
        Logger.d("☎ Creating PeerConnection with GatheringPolicy: $gatheringPolicy")
        peerConnection =
            peerConnectionFactory?.createPeerConnection(configuration, peerConnectionObserver)
        if (peerConnection != null) {
            createAudioTrack()
            createDataChannel()
            createVideoTrack()
            createScreenSharingTrack()
            if (peerSessionDescription != null && peerSessionDescriptionType != null) {
                Logger.d("☎ Setting remote description:\n$peerSessionDescription")
                peerConnection?.setRemoteDescription(
                    sessionDescriptionObserver, SessionDescription(
                        Type.fromCanonicalForm(peerSessionDescriptionType), peerSessionDescription
                    )
                )
                peerSessionDescription = null
                peerSessionDescriptionType = null
                readyToProcessPeerIceCandidates = true
                for (jsonIceCandidate in pendingPeerIceCandidates) {
                    peerConnection?.addIceCandidate(
                        IceCandidate(
                            jsonIceCandidate.sdpMid,
                            jsonIceCandidate.sdpMLineIndex,
                            jsonIceCandidate.sdp
                        )
                    )
                }
                pendingPeerIceCandidates.clear()
            }
        }
    }

    fun createLocalDescription() {
        Logger.d("☎ createLocalDescription")
        // only called from onRenegotiationNeeded
        when (peerConnection?.signalingState()) {
            STABLE -> {
                reconnectOfferCounter++
                peerConnection?.createOffer(sessionDescriptionObserver, MediaConstraints())
            }

            HAVE_REMOTE_OFFER -> peerConnection?.createAnswer(
                sessionDescriptionObserver,
                MediaConstraints()
            )

            else -> {}
        }
    }

    //////
    // when reading this, have a look at
    // https://w3c.github.io/webrtc-pc/#rtcsignalingstate-enum
    fun handleReceivedRestartSdp(
        sessionDescriptionType: String?,
        sessionDescription: String,
        reconnectCounter: Int,
        peerReconnectCounterToOverride: Int
    ) {
        peerConnection?.let { peerConnection ->
            when (sessionDescriptionType) {
                "offer" -> {
                    if (reconnectCounter < reconnectAnswerCounter) {
                        Logger.i("☎ Received restart offer with counter too low $reconnectCounter vs. $reconnectAnswerCounter")
                        return
                    }
                    var shouldCreateLocalDescription = false
                    when (peerConnection.signalingState()) {
                        HAVE_REMOTE_OFFER -> {
                            Logger.d("☎ Received restart offer while already having one --> rollback")
                            // rollback to a stable set before handling the new restart offer
                            peerConnection.setLocalDescription(
                                sessionDescriptionObserver, SessionDescription(
                                    ROLLBACK, ""
                                )
                            )
                        }

                        HAVE_LOCAL_OFFER -> {
                            // we already sent an offer
                            // if we are the offer sender, do nothing, otherwise rollback and process the new offer
                            if (webrtcCallService.shouldISendTheOfferToCallParticipant(callParticipant)) {
                                if (peerReconnectCounterToOverride == reconnectOfferCounter) {
                                    Logger.d("☎ Received restart offer while already having created an offer. It specifies to override my current offer --> rollback")
                                    peerConnection.setLocalDescription(
                                        sessionDescriptionObserver, SessionDescription(
                                            ROLLBACK, ""
                                        )
                                    )
                                } else {
                                    Logger.d("☎ Received restart offer while already having created an offer. I am the offerer --> ignore this new offer")
                                    return
                                }
                            } else {
                                Logger.d("☎ Received restart offer while already having created an offer. I am not the offerer --> rollback")
                                peerConnection.setLocalDescription(
                                    sessionDescriptionObserver, SessionDescription(
                                        ROLLBACK, ""
                                    )
                                )
                            }
                        }

                        STABLE -> {
                            // make sure we send an answer after setting an offer
                            shouldCreateLocalDescription = true
                        }

                        SignalingState.HAVE_LOCAL_PRANSWER, SignalingState.HAVE_REMOTE_PRANSWER, SignalingState.CLOSED -> {}
                        else -> {}
                    }

                    reconnectAnswerCounter = reconnectCounter

                    // Before setting the remote description, we check if it contains a video track.
                    // If it is the case, we make sure we do have a video track too
                    if (sessionDescription.contains(regex = Regex("^m=video\\s+", MULTILINE))) {
                        enableVideoTrack()
                        enableScreenSharingTrack()
                    }

                    Logger.d("☎ Setting remote description:\n$sessionDescription")
                    peerConnection.setRemoteDescription(
                        sessionDescriptionObserver, SessionDescription(
                            Type.fromCanonicalForm(sessionDescriptionType), sessionDescription
                        )
                    )

                    if (shouldCreateLocalDescription) {
                        createLocalDescription()
                    }
                }

                "answer" -> {
                    if (reconnectCounter != reconnectOfferCounter) {
                        Logger.i("☎ Received restart answer with bad counter $reconnectCounter vs. $reconnectOfferCounter")
                        return
                    }
                    if (peerConnection.signalingState() != HAVE_LOCAL_OFFER) {
                        Logger.d("☎ Received restart answer while not in the HAVE_LOCAL_OFFER state --> ignore this restart answer")
                        return
                    }
                    Logger.d("☎ Applying received restart answer")
                    Logger.d("☎ Setting remote description:\n$sessionDescription")
                    peerConnection.setRemoteDescription(
                        sessionDescriptionObserver, SessionDescription(
                            Type.fromCanonicalForm(sessionDescriptionType), sessionDescription
                        )
                    )
                }
            }
        }
    }

    private var iceGatheringCompletedCalled = false

    init {
        gatheringPolicy = callParticipant.gatheringPolicy
        pendingPeerIceCandidates = ArrayList()
    }

    @Synchronized
    private fun iceGatheringCompleted() {
        if (!iceGatheringCompletedCalled && peerConnection != null) {
            iceGatheringCompletedCalled = true
            val sdp = peerConnection!!.localDescription
            // We no longer need to filter out non-relay connections manually
            //  String filteredDescription = filterSdpDescriptionKeepOnlyRelay(sdp.description);
            if (sdp.type == OFFER) {
                webrtcCallService.sendLocalDescriptionToPeer(
                    callParticipant,
                    sdp.type.canonicalForm(),
                    sdp.description,
                    reconnectOfferCounter,
                    reconnectAnswerCounter
                )
            } else {
                webrtcCallService.sendLocalDescriptionToPeer(
                    callParticipant,
                    sdp.type.canonicalForm(),
                    sdp.description,
                    reconnectAnswerCounter,
                    -1
                )
            }
        }
    }

    fun addIceCandidates(jsonIceCandidates: Collection<JsonIceCandidate>) {
        if (readyToProcessPeerIceCandidates) {
            if (peerConnection == null || gatheringPolicy != GATHER_CONTINUOUSLY) {
                return
            }
            for (jsonIceCandidate in jsonIceCandidates) {
                peerConnection?.addIceCandidate(
                    IceCandidate(
                        jsonIceCandidate.sdpMid,
                        jsonIceCandidate.sdpMLineIndex,
                        jsonIceCandidate.sdp
                    )
                )
            }
        } else {
            pendingPeerIceCandidates.addAll(jsonIceCandidates)
        }
    }

    fun removeIceCandidates(jsonIceCandidates: Array<JsonIceCandidate>) {
        if (readyToProcessPeerIceCandidates) {
            if (peerConnection == null || gatheringPolicy != GATHER_CONTINUOUSLY) {
                return
            }
            val iceCandidates = arrayOfNulls<IceCandidate>(jsonIceCandidates.size)
            for ((i, jsonIceCandidate) in jsonIceCandidates.withIndex()) {
                iceCandidates[i] = IceCandidate(
                    jsonIceCandidate.sdpMid,
                    jsonIceCandidate.sdpMLineIndex,
                    jsonIceCandidate.sdp
                )
            }
            peerConnection?.removeIceCandidates(iceCandidates)
        } else {
            pendingPeerIceCandidates.removeAll(listOf(*jsonIceCandidates))
        }
    }

    private fun filterSdpDescriptionCodec(description: String): String {
        val mediaStartAudio = Pattern.compile("^m=audio\\s+")
        val mediaStart = Pattern.compile("^m=")
        val br = BufferedReader(StringReader(description))
        val sb = StringBuilder()
        try {
            var audioStarted = false
            val audioLines = ArrayList<String>()
            br.forEachLine { line ->
                if (audioStarted) {
                    val m = mediaStart.matcher(line)
                    if (m.find()) {
                        audioStarted = false
                        processAudioLines(sb, audioLines)
                        sb.append(line)
                        sb.append("\n")
                    } else {
                        audioLines.add(line)
                    }
                } else {
                    val m = mediaStartAudio.matcher(line)
                    if (m.find()) {
                        audioStarted = true
                        audioLines.add(line)
                    } else {
                        sb.append(line)
                        sb.append("\n")
                    }
                }
            }
            if (audioStarted) {
                processAudioLines(sb, audioLines)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            webrtcCallService.peerConnectionHolderFailed(callParticipant, INTERNAL_ERROR)
            return ""
        }
        return sb.toString()
    }

    @Throws(Exception::class)
    private fun processAudioLines(sb: StringBuilder, audioLines: ArrayList<String>) {
        val rtpmapPattern = Pattern.compile("^a=rtpmap:([0-9]+)\\s+([^\\s/]+)")
        val formatsToKeep: MutableSet<String> = HashSet()
        var opusFormat: String? = null
        for (line in audioLines) {
            val m = rtpmapPattern.matcher(line)
            if (m.find()) {
                val codec = m.group(2) ?: ""
                if (AUDIO_CODECS.contains(codec)) {
                    formatsToKeep.add(m.group(1) ?: "")
                    if ("opus" == codec) {
                        opusFormat = m.group(1)
                    }
                }
            }
        }
        // rewrite the first line
        run {
            val firstLine = Pattern.compile("^(m=\\S+\\s+\\S+\\s+\\S+)\\s+(([0-9]+\\s*)+)$")
            val m = firstLine.matcher(audioLines[0])
            if (!m.find()) {
                throw Exception()
            }
            sb.append(m.group(1))
            for (fmt in (m.group(2) ?: "").split("\\s".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()) {
                if (formatsToKeep.contains(fmt)) {
                    sb.append(" ")
                    sb.append(fmt)
                }
            }
            sb.append("\n")
        }

        // filter subsequent lines
        val rtpmapOrOptionPattern = Pattern.compile("^a=(rtpmap|fmtp|rtcp-fb):([0-9]+)\\s+")
        for (i in 1 until audioLines.size) {
            val line = audioLines[i]
            val m = rtpmapOrOptionPattern.matcher(line)
            if (!m.find()) {
                sb.append(line)
                sb.append("\n")
            } else {
                val lineType = m.group(1)
                val fmt = m.group(2) ?: ""
                if (formatsToKeep.contains(fmt)) {
                    if (opusFormat != null && opusFormat == fmt && "ftmp" == lineType) {
                        sb.append(line)
                        sb.append(ADDITIONAL_OPUS_OPTIONS)
                        if (SettingsActivity.useLowBandwidthInCalls()) {
                            sb.append(";maxaveragebitrate=16000")
                        } else {
                            sb.append(";maxaveragebitrate=32000")
                        }
                    } else {
                        sb.append(line)
                    }
                    sb.append("\n")
                }
            }
        }
    }

    fun cleanUp() {
        peerAudioLevelListener?.cancel()
        peerAudioLevelListener = null
        audioTrack?.setEnabled(false)
        dataChannel?.dispose()
        dataChannel = null
        try {
            remoteVideoTrack?.dispose()
        } catch (ignored: Exception) {
        } finally {
            remoteVideoTrack = null
        }
        try {
            remoteScreenTrack?.dispose()
        } catch (ignored: Exception) {
        } finally {
            remoteScreenTrack = null
        }
        videoSender = null
        screenSender = null
        audioSender = null
        peerConnection?.close()
        peerConnection = null
    }

    inner class PeerConnectionObserver : PeerConnection.Observer {
        private var turnCandidates = 0
        private var connectionState: IceConnectionState? = null
        fun resetGatheringState() {
            turnCandidates = 0
            iceGatheringCompletedCalled = false
        }

        override fun onSignalingChange(newState: SignalingState) {
            if (newState == STABLE && connectionState == CONNECTED) {
                // we reach a stable state while begin connected --> any ongoing reconnection is finished
                webrtcCallService.peerConnectionConnected(callParticipant)
            }
        }

        override fun onIceConnectionChange(connectionState: IceConnectionState) {
            this.connectionState = connectionState
            Logger.d("☎ onIceConnectionChange $connectionState")
            when (connectionState) {
                NEW, CHECKING, COMPLETED, IceConnectionState.CLOSED -> {}
                CONNECTED -> {
                    webrtcCallService.peerConnectionConnected(callParticipant)
                }

                DISCONNECTED, FAILED -> {
                    webrtcCallService.markParticipantAsReconnecting(callParticipant)
                }
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Logger.d("☎ onIceConnectionReceivingChange")
        }

        override fun onIceGatheringChange(newState: IceGatheringState) {
            Logger.d("☎ onIceGatheringChange $newState")
            when (newState) {
                IceGatheringState.NEW -> {}
                GATHERING -> {

                    // we start gathering --> clear the turnCandidates list
                    resetGatheringState()
                    // if gathering continuously, start a timer to detect potentially invalid credentials
                    if (gatheringPolicy == GATHER_CONTINUOUSLY && webrtcCallService.isCaller) {
                        App.runThread {

                            // wait 5 seconds, then check if some candidates were found.
                            // --> there might be false positives, but this is not a big deal, worst case, we clear the cache...
                            try {
                                Thread.sleep(5000)
                            } catch (e: InterruptedException) {
                                return@runThread
                            }
                            if (turnCandidates == 0) {
                                webrtcCallService.clearCredentialsCache()
                            }
                        }
                    }
                }

                COMPLETE -> {
                    if (gatheringPolicy == GatheringPolicy.GATHER_ONCE) {
                        if (turnCandidates == 0 && connectionState == null) {
                            Logger.w("☎ No TURN candidate found")
                            webrtcCallService.clearCredentialsCache()
                            webrtcCallService.peerConnectionHolderFailed(
                                callParticipant,
                                SERVER_UNREACHABLE
                            )
                        } else {
                            iceGatheringCompleted()
                        }
                    }
                }
            }
        }

        override fun onIceCandidate(candidate: IceCandidate) {
            Logger.d("☎ onIceCandidate")
            when (gatheringPolicy) {
                GatheringPolicy.GATHER_ONCE -> {
                    if ("" != candidate.serverUrl) {
                        turnCandidates++
                        if (turnCandidates == 1) {
                            App.runThread {
                                try {
                                    Thread.sleep(2000)
                                } catch (e: InterruptedException) {
                                    // Nothing special to do
                                }
                                iceGatheringCompleted()
                            }
                        }
                    }
                }

                GATHER_CONTINUOUSLY -> {
                    if ("" != candidate.serverUrl) {
                        turnCandidates++
                        webrtcCallService.sendAddIceCandidateMessage(
                            callParticipant,
                            JsonIceCandidate(
                                candidate.sdp,
                                candidate.sdpMLineIndex,
                                candidate.sdpMid
                            )
                        )
                    }
                }
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
            Logger.d("☎ onIceCandidatesRemoved")
            when (gatheringPolicy) {
                GatheringPolicy.GATHER_ONCE -> {}
                GATHER_CONTINUOUSLY -> {
                    val jsonIceCandidates = arrayOfNulls<JsonIceCandidate>(candidates.size)
                    var i = 0
                    while (i < candidates.size) {
                        jsonIceCandidates[i] = JsonIceCandidate(
                            candidates[i].sdp,
                            candidates[i].sdpMLineIndex,
                            candidates[i].sdpMid
                        )
                        i++
                    }
                    webrtcCallService.sendRemoveIceCandidatesMessage(
                        callParticipant,
                        jsonIceCandidates
                    )
                }
            }
        }

        override fun onAddStream(stream: MediaStream) {
            Logger.d("☎ onAddStream")
        }

        override fun onRemoveStream(stream: MediaStream) {
            Logger.d("☎ onRemoveStream")
        }

        override fun onRemoveTrack(receiver: RtpReceiver?) {
            Logger.d("☎ onRemoveTrack")
            super.onRemoveTrack(receiver)
        }

        override fun onDataChannel(dataChannel: DataChannel) {
            Logger.d("☎ onDataChannel")
        }

        override fun onRenegotiationNeeded() {
            // called whenever a peerConnection is created, a track is added, or after a restartICE. May not be called if a negotiation is already in progress
            Logger.d("☎ onRenegotiationNeeded")
            webrtcCallService.synchronizeOnExecutor {
                createLocalDescription()
            }
        }

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
            Logger.d("☎ onAddTrack")
            val mediaStreamTrack = receiver.track()
            if (mediaStreamTrack != null) {
                if (mediaStreamTrack.kind() == "audio") {
                    audioReceiver = receiver
                    startPeerAudioLevelListener()
                }
                if (mediaStreamTrack.kind() == "video") {
                    mediaStreams.find { it.id == VIDEO_STREAM_ID }?.let {
                        remoteVideoTrack = it.videoTracks.first()
                        remoteVideoTrack?.setEnabled(true)
                    }
                    mediaStreams.find { it.id == SCREENCAST_STREAM_ID }?.let {
                        remoteScreenTrack = it.videoTracks.first()
                        remoteScreenTrack?.setEnabled(true)
                    }
                }
            }
        }
    }

    private abstract inner class SdpSetObserver : SdpObserver {
        override fun onSetFailure(error: String) {
            Logger.w("☎ onSetFailure $error")
            webrtcCallService.peerConnectionHolderFailed(
                callParticipant,
                PEER_CONNECTION_CREATION_ERROR
            )
        }

        override fun onCreateSuccess(sessionDescription: SessionDescription) {}
        override fun onCreateFailure(s: String) {}
    }

    inner class SessionDescriptionObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {
            if (peerConnection?.signalingState() != STABLE &&
                peerConnection?.signalingState() != HAVE_REMOTE_OFFER
            ) {
                // if we are neither in stable nor in have_remote_offer, we shouldn't be creating an offer or an answer
                // --> we don't set anything
                return
            }
            Logger.d("☎ onCreateSuccess. Filtering codecs.")
            val filteredSdpDescription = filterSdpDescriptionCodec(sdp.description)
            Logger.d("☎ Setting filtered local description:\n$filteredSdpDescription")
            when (gatheringPolicy) {
                GatheringPolicy.GATHER_ONCE -> {
                    peerConnectionObserver.resetGatheringState()
                    peerConnection?.setLocalDescription(
                        this,
                        SessionDescription(sdp.type, filteredSdpDescription)
                    )
                }

                GATHER_CONTINUOUSLY -> peerConnection?.setLocalDescription(object :
                    SdpSetObserver() {
                    override fun onSetSuccess() {
                        Logger.d("☎ onSetSuccess")
                        if (sdp.type == OFFER) {
                            webrtcCallService.sendLocalDescriptionToPeer(
                                callParticipant,
                                sdp.type.canonicalForm(),
                                filteredSdpDescription,
                                reconnectOfferCounter,
                                reconnectAnswerCounter
                            )
                        } else {
                            webrtcCallService.sendLocalDescriptionToPeer(
                                callParticipant,
                                sdp.type.canonicalForm(),
                                filteredSdpDescription,
                                reconnectAnswerCounter,
                                -1
                            )
                        }
                    }
                }, SessionDescription(sdp.type, filteredSdpDescription))
            }
        }

        override fun onSetSuccess() {
            Logger.d("☎ onSetSuccess")
            // called when local or remote description are set
            // This automatically triggers ICE gathering or connection establishment --> nothing to do for GATHER_ONCE
        }

        override fun onCreateFailure(error: String) {
            Logger.w("☎ onCreateFailure $error")
            webrtcCallService.peerConnectionHolderFailed(
                callParticipant,
                PEER_CONNECTION_CREATION_ERROR
            )
        }

        override fun onSetFailure(error: String) {
            Logger.w("☎ onSetFailure $error")
            webrtcCallService.peerConnectionHolderFailed(
                callParticipant,
                PEER_CONNECTION_CREATION_ERROR
            )
        }
    }

    interface DataChannelMessageListener {
        fun onConnect()
        fun onMessage(byteBuffer: ByteBuffer)
    }
}
