/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Notification
import android.app.Notification.CallStyle
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioAttributes.Builder
import android.media.AudioManager
import android.media.SoundPool
import android.media.projection.MediaProjection
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Binder
import android.os.Build
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.fasterxml.jackson.core.JsonProcessingException
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor
import io.olvid.engine.engine.types.EngineAPI.ApiKeyPermission.CALL
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.ObvTurnCredentialsFailedReason
import io.olvid.engine.engine.types.ObvTurnCredentialsFailedReason.BAD_SERVER_SESSION
import io.olvid.engine.engine.types.ObvTurnCredentialsFailedReason.CALLS_NOT_SUPPORTED_ON_SERVER
import io.olvid.engine.engine.types.ObvTurnCredentialsFailedReason.PERMISSION_DENIED
import io.olvid.engine.engine.types.ObvTurnCredentialsFailedReason.UNABLE_TO_CONTACT_SERVER
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R.color
import io.olvid.messenger.R.dimen
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.plurals
import io.olvid.messenger.R.raw
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.CallLogItem
import io.olvid.messenger.databases.entity.CallLogItemContactJoin
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonPayload
import io.olvid.messenger.databases.entity.jsons.JsonWebrtcMessage
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.webrtc.BluetoothHeadsetManager.State.HEADSET_UNAVAILABLE
import io.olvid.messenger.webrtc.OutgoingCallRinger.Type
import io.olvid.messenger.webrtc.OutgoingCallRinger.Type.RING
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.BLUETOOTH
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.HEADSET
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.LOUDSPEAKER
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.MUTED
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.PHONE
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.CALL_INITIATION_NOT_SUPPORTED
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.CONTACT_NOT_FOUND
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.INTERNAL_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.PEER_CONNECTION_CREATION_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.SERVER_AUTHENTICATION_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.SERVER_UNREACHABLE
import io.olvid.messenger.webrtc.WebrtcCallService.GatheringPolicy.GATHER_CONTINUOUSLY
import io.olvid.messenger.webrtc.WebrtcCallService.GatheringPolicy.GATHER_ONCE
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.CALL_REJECTED
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.CONNECTED
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.CONNECTING_TO_PEER
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.HANGED_UP
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.KICKED
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.RECONNECTING
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.RINGING
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.START_CALL_MESSAGE_SENT
import io.olvid.messenger.webrtc.WebrtcCallService.Role.CALLER
import io.olvid.messenger.webrtc.WebrtcCallService.Role.NONE
import io.olvid.messenger.webrtc.WebrtcCallService.Role.RECIPIENT
import io.olvid.messenger.webrtc.WebrtcCallService.State.BUSY
import io.olvid.messenger.webrtc.WebrtcCallService.State.CALL_ENDED
import io.olvid.messenger.webrtc.WebrtcCallService.State.CALL_IN_PROGRESS
import io.olvid.messenger.webrtc.WebrtcCallService.State.FAILED
import io.olvid.messenger.webrtc.WebrtcCallService.State.GETTING_TURN_CREDENTIALS
import io.olvid.messenger.webrtc.WebrtcCallService.State.INITIAL
import io.olvid.messenger.webrtc.WebrtcCallService.State.INITIALIZING_CALL
import io.olvid.messenger.webrtc.WebrtcCallService.State.WAITING_FOR_AUDIO_PERMISSION
import io.olvid.messenger.webrtc.WebrtcCallService.WakeLock.ALL
import io.olvid.messenger.webrtc.WebrtcCallService.WakeLock.PROXIMITY
import io.olvid.messenger.webrtc.WebrtcCallService.WakeLock.WIFI
import io.olvid.messenger.webrtc.WebrtcPeerConnectionHolder.Companion.audioDeviceModule
import io.olvid.messenger.webrtc.WebrtcPeerConnectionHolder.Companion.localScreenTrack
import io.olvid.messenger.webrtc.WebrtcPeerConnectionHolder.Companion.localVideoTrack
import io.olvid.messenger.webrtc.WebrtcPeerConnectionHolder.DataChannelMessageListener
import io.olvid.messenger.webrtc.json.JsonAnswerCallMessage
import io.olvid.messenger.webrtc.json.JsonAnsweredOrRejectedOnOtherDeviceMessage
import io.olvid.messenger.webrtc.json.JsonBusyMessage
import io.olvid.messenger.webrtc.json.JsonDataChannelInnerMessage
import io.olvid.messenger.webrtc.json.JsonDataChannelMessage
import io.olvid.messenger.webrtc.json.JsonHangedUpInnerMessage
import io.olvid.messenger.webrtc.json.JsonHangedUpMessage
import io.olvid.messenger.webrtc.json.JsonIceCandidate
import io.olvid.messenger.webrtc.json.JsonKickMessage
import io.olvid.messenger.webrtc.json.JsonMutedInnerMessage
import io.olvid.messenger.webrtc.json.JsonNewIceCandidateMessage
import io.olvid.messenger.webrtc.json.JsonNewParticipantAnswerMessage
import io.olvid.messenger.webrtc.json.JsonNewParticipantOfferMessage
import io.olvid.messenger.webrtc.json.JsonReconnectCallMessage
import io.olvid.messenger.webrtc.json.JsonRejectCallMessage
import io.olvid.messenger.webrtc.json.JsonRelayInnerMessage
import io.olvid.messenger.webrtc.json.JsonRelayedInnerMessage
import io.olvid.messenger.webrtc.json.JsonRemoveIceCandidatesMessage
import io.olvid.messenger.webrtc.json.JsonRingingMessage
import io.olvid.messenger.webrtc.json.JsonScreenSharingInnerMessage
import io.olvid.messenger.webrtc.json.JsonStartCallMessage
import io.olvid.messenger.webrtc.json.JsonUpdateParticipantsInnerMessage
import io.olvid.messenger.webrtc.json.JsonVideoSharingInnerMessage
import io.olvid.messenger.webrtc.json.JsonVideoSupportedInnerMessage
import io.olvid.messenger.webrtc.json.JsonWebrtcProtocolMessage
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerationAndroid
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Timer
import java.util.TimerTask
import java.util.TreeMap
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

class WebrtcCallService : Service() {
    enum class Role {
        NONE,
        CALLER,
        RECIPIENT
    }

    enum class State {
        INITIAL,
        WAITING_FOR_AUDIO_PERMISSION,
        GETTING_TURN_CREDENTIALS,
        INITIALIZING_CALL,
        RINGING,
        BUSY,
        CALL_IN_PROGRESS,
        CALL_ENDED,
        FAILED
    }

    enum class PeerState {
        INITIAL,

        ///////
        // the following states are caller-only states --> the recipient stays in INITIAL during this time
        START_CALL_MESSAGE_SENT,
        RINGING,
        BUSY,
        CALL_REJECTED,

        ///////
        CONNECTING_TO_PEER,
        CONNECTED,
        RECONNECTING,
        HANGED_UP,
        KICKED,
        FAILED
    }

    enum class FailReason {
        NONE,
        CONTACT_NOT_FOUND,
        SERVER_UNREACHABLE,
        PEER_CONNECTION_CREATION_ERROR,
        INTERNAL_ERROR,
        ICE_SERVER_CREDENTIALS_CREATION_ERROR,
        COULD_NOT_SEND,
        PERMISSION_DENIED,
        SERVER_AUTHENTICATION_ERROR,
        ICE_CONNECTION_ERROR,
        CALL_INITIATION_NOT_SUPPORTED,
        KICKED
    }

    enum class WakeLock {
        ALL,
        WIFI,
        PROXIMITY
    }

    enum class AudioOutput {
        PHONE,
        HEADSET,
        LOUDSPEAKER,
        BLUETOOTH,
        MUTED
    }

    enum class GatheringPolicy {
        GATHER_ONCE,
        GATHER_CONTINUOUSLY
    }

    private val webrtcCallServiceBinder = WebrtcCallServiceBinder()
    private val wiredHeadsetReceiver: WiredHeadsetReceiver = WiredHeadsetReceiver()
    private val objectMapper = AppSingleton.getJsonObjectMapper()
    private var role = NONE

    @JvmField
    var callIdentifier: UUID? = null

    @JvmField
    var bytesOwnedIdentity: ByteArray? = null

    @JvmField
    var discussionType =
        Discussion.TYPE_CONTACT // updated whenever bytesGroupOwnerAndUidOrIdentifier is set

    @JvmField
    var bytesGroupOwnerAndUidOrIdentifier: ByteArray? = null
    private var _state = INITIAL
    var failReason = FailReason.NONE
        set(failReason) {
            if (this.failReason == FailReason.NONE) {
                field = failReason
            }
        }
    private val stateLiveData = MutableLiveData(_state)

    // audio
    @JvmField
    var microphoneMuted = false
    private val microphoneMutedLiveData = MutableLiveData(false)
    var selectedAudioOutput by mutableStateOf(PHONE)
        private set
    private var bluetoothAutoConnect = true
    var wiredHeadsetConnected = false
    var availableAudioOutputs = if (VERSION.SDK_INT >= VERSION_CODES.M) mutableStateListOf(PHONE, LOUDSPEAKER, MUTED) else mutableStateListOf(PHONE, LOUDSPEAKER)
        private set

    var selectedParticipant: ByteArray? by mutableStateOf(bytesOwnedIdentity)

    // video
    var requestingScreenCast = false
    var screenShareActive by mutableStateOf(false)
    var cameraEnabled by mutableStateOf(false)
    private var availableCameras = emptyList<CameraAndFormat>()
    val availableCamerasLiveData = MutableLiveData(availableCameras)
    private var selectedCamera: CameraAndFormat? = null
    val selectedCameraLiveData = MutableLiveData(selectedCamera)
    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920


    // call duration
    private var callDurationTimer: Timer? = null
    private val callDuration = MutableLiveData<Int?>(null)
    private val receivedOfferMessages = HashMap<BytesKey, JsonNewParticipantOfferMessage>()
    private var callParticipantIndex = 0
    private val callParticipantIndexes: MutableMap<BytesKey, Int?> = HashMap()
    private val callParticipants: MutableMap<Int, CallParticipant> = TreeMap()
    private val callParticipantsLiveData = MutableLiveData<List<CallParticipantPojo>>(ArrayList(0))
    val timeoutTimer = Timer()
    private var timeoutTimerTask: TimerTask? = null
    private var proximityLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiLock? = null
    private val executor = NoExceptionSingleThreadExecutor("WebRTCCallService-Executor")
    private var initialized = false
    private var savedAudioManagerMode = 0

    @JvmField
    var audioManager: AudioManager? = null
    private var incomingCallRinger: IncomingCallRinger? = null
    private var outgoingCallRinger: OutgoingCallRinger? = null
    private var soundPool: SoundPool? = null
    private var connectSound = 0
    private var disconnectSound = 0
    private var reconnectingSound = 0
    private var reconnectingStreamId: Int? = null
    private var phoneCallStateListener: PhoneCallStateListener? = null
    private var screenOffReceiver: ScreenOffReceiver? = null
    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var bluetoothHeadsetManager: BluetoothHeadsetManager? = null
    private var callLogItem: CallLogItem? = null
    private var webrtcMessageReceivedBroadcastReceiver: WebrtcMessageReceivedBroadcastReceiver? =
        null
    private var engineTurnCredentialsReceiver: EngineTurnCredentialsReceiver? = null
    private var turnUserName: String? = null
    private var turnPassword: String? = null
    private var turnServers: List<String>? = null
    var incomingParticipantCount = 0
        private set
    private var recipientTurnUserName: String? = null
    private var recipientTurnPassword: String? = null
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action != null) {
            initialize()
            intent.apply {
                when (action) {
                    ACTION_START_CALL -> {
                        if (!intent.hasExtra(CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA) || !intent.hasExtra(
                                BYTES_OWNED_IDENTITY_INTENT_EXTRA
                            )
                        ) {
                            return@apply
                        }
                        val bytesOwnedIdentity = intent.getByteArrayExtra(
                            BYTES_OWNED_IDENTITY_INTENT_EXTRA
                        )
                        val contactIdentitiesBundle = intent.getBundleExtra(
                            CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA
                        )
                        if (bytesOwnedIdentity == null || contactIdentitiesBundle == null) {
                            return@apply
                        }
                        val bytesContactIdentities: MutableList<ByteArray?> =
                            ArrayList(contactIdentitiesBundle.size())
                        for (key in contactIdentitiesBundle.keySet()) {
                            bytesContactIdentities.add(contactIdentitiesBundle.getByteArray(key))
                        }
                        val bytesGroupOwnerAndUid = intent.getByteArrayExtra(
                            BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA
                        )
                        val groupV2 = intent.getBooleanExtra(GROUP_V2_INTENT_EXTRA, false)
                        if (ContextCompat.checkSelfPermission(
                                this@WebrtcCallService,
                                permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            callerStartCall(
                                bytesOwnedIdentity,
                                bytesContactIdentities,
                                bytesGroupOwnerAndUid,
                                groupV2
                            )
                        } else {
                            callerWaitForAudioPermission(
                                bytesOwnedIdentity,
                                bytesContactIdentities,
                                bytesGroupOwnerAndUid,
                                groupV2
                            )
                        }
                        return START_NOT_STICKY
                    }

                    ACTION_MESSAGE -> {
                        if (!intent.hasExtra(BYTES_CONTACT_IDENTITY_INTENT_EXTRA) || !intent.hasExtra(
                                BYTES_OWNED_IDENTITY_INTENT_EXTRA
                            )
                            || !intent.hasExtra(CALL_IDENTIFIER_INTENT_EXTRA) || !intent.hasExtra(
                                MESSAGE_TYPE_INTENT_EXTRA
                            )
                            || !intent.hasExtra(SERIALIZED_MESSAGE_PAYLOAD_INTENT_EXTRA)
                        ) {
                            return@apply
                        }
                        val messageType = intent.getIntExtra(MESSAGE_TYPE_INTENT_EXTRA, -1)
                        if (messageType != START_CALL_MESSAGE_TYPE && messageType != NEW_ICE_CANDIDATE_MESSAGE_TYPE && messageType != REMOVE_ICE_CANDIDATES_MESSAGE_TYPE && messageType != ANSWERED_OR_REJECTED_ON_OTHER_DEVICE_MESSAGE_TYPE) {
                            return@apply
                        }
                        val bytesOwnedIdentity = intent.getByteArrayExtra(
                            BYTES_OWNED_IDENTITY_INTENT_EXTRA
                        )
                        val bytesContactIdentity = intent.getByteArrayExtra(
                            BYTES_CONTACT_IDENTITY_INTENT_EXTRA
                        )
                        val callIdentifier = UUID.fromString(
                            intent.getStringExtra(
                                CALL_IDENTIFIER_INTENT_EXTRA
                            )
                        )
                        val serializedMessagePayload = intent.getStringExtra(
                            SERIALIZED_MESSAGE_PAYLOAD_INTENT_EXTRA
                        )
                        if (serializedMessagePayload == null || callIdentifier == null) {
                            return@apply
                        }
                        try {
                            when (messageType) {
                                START_CALL_MESSAGE_TYPE -> {
                                    val startCallMessage = objectMapper.readValue(
                                        serializedMessagePayload,
                                        JsonStartCallMessage::class.java
                                    )
                                    recipientReceiveCall(
                                        bytesOwnedIdentity,
                                        bytesContactIdentity,
                                        callIdentifier,
                                        startCallMessage.sessionDescriptionType,
                                        startCallMessage.gzippedSessionDescription,
                                        startCallMessage.turnUserName,
                                        startCallMessage.turnPassword /*, startCallMessage.turnServers*/,
                                        startCallMessage.participantCount,
                                        startCallMessage.bytesGroupOwnerAndUid,
                                        startCallMessage.gatheringPolicy
                                    )
                                    return START_NOT_STICKY
                                }

                                NEW_ICE_CANDIDATE_MESSAGE_TYPE -> {
                                    if (bytesOwnedIdentity != null && bytesContactIdentity != null) {
                                        val jsonNewIceCandidateMessage = objectMapper.readValue(
                                            serializedMessagePayload,
                                            JsonNewIceCandidateMessage::class.java
                                        )
                                        handleNewIceCandidateMessage(
                                            callIdentifier,
                                            bytesOwnedIdentity,
                                            bytesContactIdentity,
                                            JsonIceCandidate(
                                                jsonNewIceCandidateMessage.sdp,
                                                jsonNewIceCandidateMessage.sdpMLineIndex,
                                                jsonNewIceCandidateMessage.sdpMid
                                            )
                                        )
                                    }
                                    return START_NOT_STICKY
                                }

                                REMOVE_ICE_CANDIDATES_MESSAGE_TYPE -> {
                                    if (bytesOwnedIdentity != null && bytesContactIdentity != null) {
                                        val jsonRemoveIceCandidatesMessage = objectMapper.readValue(
                                            serializedMessagePayload,
                                            JsonRemoveIceCandidatesMessage::class.java
                                        )
                                        handleRemoveIceCandidatesMessage(
                                            callIdentifier,
                                            bytesOwnedIdentity,
                                            bytesContactIdentity,
                                            jsonRemoveIceCandidatesMessage.candidates
                                        )
                                    }
                                    return START_NOT_STICKY
                                }

                                ANSWERED_OR_REJECTED_ON_OTHER_DEVICE_MESSAGE_TYPE -> {

                                    // only accept this type of message from other owned devices
                                    if (bytesOwnedIdentity != null && bytesContactIdentity != null && Arrays.equals(
                                            bytesOwnedIdentity,
                                            bytesContactIdentity
                                        )
                                    ) {
                                        val jsonAnsweredOrRejectedOnOtherDeviceMessage =
                                            objectMapper.readValue(
                                                serializedMessagePayload,
                                                JsonAnsweredOrRejectedOnOtherDeviceMessage::class.java
                                            )
                                        handleAnsweredOrRejectedOnOtherDeviceMessage(
                                            callIdentifier,
                                            bytesOwnedIdentity,
                                            jsonAnsweredOrRejectedOnOtherDeviceMessage.isAnswered
                                        )
                                    }
                                    return START_NOT_STICKY
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    ACTION_ANSWER_CALL -> {
                        if (!intent.hasExtra(CALL_IDENTIFIER_INTENT_EXTRA)) {
                            return@apply
                        }
                        val callIdentifier = UUID.fromString(
                            intent.getStringExtra(
                                CALL_IDENTIFIER_INTENT_EXTRA
                            )
                        )
                        val audioPermissionGranted = ContextCompat.checkSelfPermission(
                            this@WebrtcCallService,
                            permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        recipientAnswerCall(callIdentifier, !audioPermissionGranted)
                        return START_NOT_STICKY
                    }

                    ACTION_REJECT_CALL -> {
                        if (!intent.hasExtra(CALL_IDENTIFIER_INTENT_EXTRA)) {
                            return@apply
                        }
                        val callIdentifier = UUID.fromString(
                            intent.getStringExtra(
                                CALL_IDENTIFIER_INTENT_EXTRA
                            )
                        )
                        recipientRejectCall(callIdentifier)
                        return START_NOT_STICKY
                    }

                    ACTION_HANG_UP -> {
                        if (!intent.hasExtra(CALL_IDENTIFIER_INTENT_EXTRA)) {
                            return@apply
                        }
                        val callIdentifier = UUID.fromString(
                            intent.getStringExtra(
                                CALL_IDENTIFIER_INTENT_EXTRA
                            )
                        )
                        hangUpCall(callIdentifier)
                        return START_NOT_STICKY
                    }
                }
            }
        }
        handleUnknownOrInvalidIntent()
        return START_NOT_STICKY
    }

    private fun handleMessageIntent(intent: Intent) {
        executor.execute {
            val bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)
            val bytesContactIdentity = intent.getByteArrayExtra(BYTES_CONTACT_IDENTITY_INTENT_EXTRA)
            val callIdentifier =
                UUID.fromString(intent.getStringExtra(CALL_IDENTIFIER_INTENT_EXTRA))
            // if the message is for another call, ignore it
            if (!Arrays.equals(bytesOwnedIdentity, this.bytesOwnedIdentity) ||
                callIdentifier != this.callIdentifier
            ) {
                return@execute
            }
            val messageType = intent.getIntExtra(MESSAGE_TYPE_INTENT_EXTRA, -1)
            val serializedMessagePayload = intent.getStringExtra(
                SERIALIZED_MESSAGE_PAYLOAD_INTENT_EXTRA
            )
                ?: return@execute
            // if message does not contain a payload, ignore it
            handleMessage(bytesContactIdentity, messageType, serializedMessagePayload)
        }
    }

    private fun handleMessage(
        bytesContactIdentity: ByteArray?,
        messageType: Int,
        serializedMessagePayload: String
    ) {
        try {
            when (messageType) {
                ANSWER_CALL_MESSAGE_TYPE -> {
                    val callParticipant = getCallParticipant(bytesContactIdentity)
                    if (isCaller && callParticipant != null) {
                        val jsonAnswerCallMessage = objectMapper.readValue(
                            serializedMessagePayload,
                            JsonAnswerCallMessage::class.java
                        )
                        callerHandleAnswerCallMessage(
                            callParticipant,
                            jsonAnswerCallMessage.sessionDescriptionType,
                            jsonAnswerCallMessage.gzippedSessionDescription
                        )
                    }
                }

                RINGING_MESSAGE_TYPE -> {
                    val callParticipant = getCallParticipant(bytesContactIdentity)
                    if (isCaller && callParticipant != null) {
                        callerHandleRingingMessage(callParticipant)
                    }
                }

                REJECT_CALL_MESSAGE_TYPE -> {
                    val callParticipant = getCallParticipant(bytesContactIdentity)
                    if (isCaller && callParticipant != null) {
                        callerHandleRejectCallMessage(callParticipant)
                    }
                }

                HANGED_UP_MESSAGE_TYPE -> {
                    val callParticipant = getCallParticipant(bytesContactIdentity)
                    callParticipant?.let { handleHangedUpMessage(it) }
                }

                BUSY_MESSAGE_TYPE -> {
                    val callParticipant = getCallParticipant(bytesContactIdentity)
                    if (isCaller && callParticipant != null) {
                        callerHandleBusyMessage(callParticipant)
                    }
                }

                RECONNECT_CALL_MESSAGE_TYPE -> {
                    val callParticipant = getCallParticipant(bytesContactIdentity)
                    if (callParticipant != null) {
                        val jsonReconnectCallMessage = objectMapper.readValue(
                            serializedMessagePayload,
                            JsonReconnectCallMessage::class.java
                        )
                        handleReconnectCallMessage(
                            callParticipant,
                            jsonReconnectCallMessage.sessionDescriptionType,
                            jsonReconnectCallMessage.gzippedSessionDescription,
                            jsonReconnectCallMessage.reconnectCounter,
                            jsonReconnectCallMessage.peerReconnectCounterToOverride
                        )
                    }
                }

                NEW_PARTICIPANT_OFFER_MESSAGE_TYPE -> {
                    val callParticipant = getCallParticipant(bytesContactIdentity)
                    val newParticipantOfferMessage = objectMapper.readValue(
                        serializedMessagePayload,
                        JsonNewParticipantOfferMessage::class.java
                    )
                    if (callParticipant == null) {
                        // put the message in queue as we might simply receive the update call participant message later
                        receivedOfferMessages[BytesKey(bytesContactIdentity)] =
                            newParticipantOfferMessage
                    } else {
                        handleNewParticipantOfferMessage(
                            callParticipant,
                            newParticipantOfferMessage.sessionDescriptionType,
                            newParticipantOfferMessage.gzippedSessionDescription,
                            newParticipantOfferMessage.gatheringPolicy
                        )
                    }
                }

                NEW_PARTICIPANT_ANSWER_MESSAGE_TYPE -> {
                    val callParticipant = getCallParticipant(bytesContactIdentity)
                    if (callParticipant != null) {
                        val newParticipantAnswerMessage = objectMapper.readValue(
                            serializedMessagePayload,
                            JsonNewParticipantAnswerMessage::class.java
                        )
                        handleNewParticipantAnswerMessage(
                            callParticipant,
                            newParticipantAnswerMessage.sessionDescriptionType,
                            newParticipantAnswerMessage.gzippedSessionDescription
                        )
                    }
                }

                KICK_MESSAGE_TYPE -> {
                    val callParticipant = getCallParticipant(bytesContactIdentity)
                    if (callParticipant != null && callParticipant.role == CALLER) {
                        handleKickedMessage()
                    }
                }

                NEW_ICE_CANDIDATE_MESSAGE_TYPE -> {
                    val jsonNewIceCandidateMessage = objectMapper.readValue(
                        serializedMessagePayload,
                        JsonNewIceCandidateMessage::class.java
                    )
                    handleNewIceCandidateMessage(
                        callIdentifier!!,
                        bytesOwnedIdentity!!,
                        bytesContactIdentity!!,
                        JsonIceCandidate(
                            jsonNewIceCandidateMessage.sdp,
                            jsonNewIceCandidateMessage.sdpMLineIndex,
                            jsonNewIceCandidateMessage.sdpMid
                        )
                    )
                }

                REMOVE_ICE_CANDIDATES_MESSAGE_TYPE -> {
                    val jsonRemoveIceCandidatesMessage = objectMapper.readValue(
                        serializedMessagePayload,
                        JsonRemoveIceCandidatesMessage::class.java
                    )
                    handleRemoveIceCandidatesMessage(
                        callIdentifier!!,
                        bytesOwnedIdentity!!,
                        bytesContactIdentity!!,
                        jsonRemoveIceCandidatesMessage.candidates
                    )
                }
            }
        } catch (e: JsonProcessingException) {
            e.printStackTrace()
        }
    }

    private fun stopThisService() {
        if (callIdentifier != null) {
            executor.execute { uncalledReceivedIceCandidates.remove(callIdentifier) }
        }
        stopForeground(true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            unregisterDeviceOrientationChange()
        }
        Handler(Looper.getMainLooper()).postDelayed({ this.stopSelf() }, 300)
    }

    // region Steps
    private fun initialize() {
        executor.execute {
            if (!initialized) {
                initialized = true
                audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                if (audioManager == null) {
                    failReason = INTERNAL_ERROR
                    setState(FAILED)
                    return@execute
                }
                incomingCallRinger = IncomingCallRinger(this)
                outgoingCallRinger = OutgoingCallRinger(this)
                soundPool = SoundPool.Builder()
                    .setMaxStreams(2)
                    .setAudioAttributes(
                        Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                            .build()
                    )
                    .build().apply {
                        connectSound = load(this@WebrtcCallService, raw.connect, 1)
                        disconnectSound = load(this@WebrtcCallService, raw.disconnect, 1)
                        reconnectingSound = load(this@WebrtcCallService, raw.reconnecting, 1)
                    }

                if (ContextCompat.checkSelfPermission(
                        this,
                        permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    readCallStatePermissionGranted()
                }
                if (VERSION.SDK_INT < VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(
                        this,
                        permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothPermissionGranted()
                }
                audioManager!!.isSpeakerphoneOn = false
                wiredHeadsetConnected = audioManager!!.isWiredHeadsetOn
                updateAvailableAudioOutputsList()
                updateCameraList()
                registerWiredHeadsetReceiver()

                screenWidth = resources.displayMetrics.widthPixels
                screenHeight = resources.displayMetrics.heightPixels

                if (VERSION.SDK_INT < VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    registerDeviceOrientationChange()
                }
            }
        }
    }


    private fun handleUnknownOrInvalidIntent() {
        executor.execute {
            if (_state == INITIAL) {
                // we received an unknown intent and no call has been started
                // --> we can safely stop the service
                stopForeground(true)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    unregisterDeviceOrientationChange()
                }
                stopSelf()
            }
        }
    }

    private fun callerStartCall(
        bytesOwnedIdentity: ByteArray,
        bytesContactIdentities: List<ByteArray?>,
        bytesGroupOwnerAndUidOrIdentifier: ByteArray?,
        groupV2: Boolean
    ) {
        executor.execute {
            if (_state != INITIAL) {
                App.toast(string.toast_message_already_in_a_call, Toast.LENGTH_SHORT)
                return@execute
            }
            val callIdentifier = UUID.randomUUID()
            val contacts: MutableList<Contact> = ArrayList(bytesContactIdentities.size)
            for (bytesContactIdentity in bytesContactIdentities) {
                val contact =
                    AppDatabase.getInstance().contactDao()[bytesOwnedIdentity, bytesContactIdentity]
                if (contact == null) {
                    failReason = CONTACT_NOT_FOUND
                    setState(FAILED)
                    return@execute
                }
                contacts.add(contact)
            }
            setContactsAndRole(bytesOwnedIdentity, contacts, callIdentifier, true)
            this.bytesGroupOwnerAndUidOrIdentifier = bytesGroupOwnerAndUidOrIdentifier
            discussionType =
                if (bytesGroupOwnerAndUidOrIdentifier == null) Discussion.TYPE_CONTACT else if (groupV2) Discussion.TYPE_GROUP_V2 else Discussion.TYPE_GROUP

            // show notification
            showOngoingForeground()
            callerStartCallInternal()
        }
    }

    private fun callerWaitForAudioPermission(
        bytesOwnedIdentity: ByteArray,
        bytesContactIdentities: List<ByteArray?>,
        bytesGroupOwnerAndUidOrIdentifier: ByteArray?,
        groupV2: Boolean
    ) {
        executor.execute {
            if (_state != INITIAL) {
                App.toast(string.toast_message_already_in_a_call, Toast.LENGTH_SHORT)
                return@execute
            }
            val callIdentifier = UUID.randomUUID()
            val contacts: MutableList<Contact> = ArrayList(bytesContactIdentities.size)
            for (bytesContactIdentity in bytesContactIdentities) {
                val contact =
                    AppDatabase.getInstance().contactDao()[bytesOwnedIdentity, bytesContactIdentity]
                if (contact == null) {
                    failReason = CONTACT_NOT_FOUND
                    setState(FAILED)
                    return@execute
                }
                contacts.add(contact)
            }
            setContactsAndRole(bytesOwnedIdentity, contacts, callIdentifier, true)
            this.bytesGroupOwnerAndUidOrIdentifier = bytesGroupOwnerAndUidOrIdentifier
            discussionType =
                if (bytesGroupOwnerAndUidOrIdentifier == null) Discussion.TYPE_CONTACT else if (groupV2) Discussion.TYPE_GROUP_V2 else Discussion.TYPE_GROUP
            setState(WAITING_FOR_AUDIO_PERMISSION)
        }
    }

    fun audioPermissionGranted() {
        executor.execute {
            if (_state != WAITING_FOR_AUDIO_PERMISSION) {
                return@execute
            }
            if (isCaller) {
                // show notification
                showOngoingForeground()
                callerStartCallInternal()
            } else {
                recipientAnswerCallInternal()
            }
        }
    }

    fun bluetoothPermissionGranted() {
        executor.execute {
            if (bluetoothHeadsetManager == null) {
                bluetoothHeadsetManager = BluetoothHeadsetManager(this)
                bluetoothHeadsetManager!!.start()
            }
        }
    }

    fun readCallStatePermissionGranted() {
        executor.execute {
            if (phoneCallStateListener == null) {
                Handler(Looper.getMainLooper()).post {
                    phoneCallStateListener = PhoneCallStateListener(this, executor)
                }
            }
        }
    }

    private fun callerStartCallInternal() {
        // get audio focus
        requestAudioManagerFocus()

        // initialize a peerConnection
        WebrtcPeerConnectionHolder.initializePeerConnectionFactory()

        // check if we have cached some turn credentials:
        val callCredentialsCacheSharedPreference = App.getContext().getSharedPreferences(
            App.getContext().getString(
                string.preference_filename_call_credentials_cache
            ), MODE_PRIVATE
        )
        val credentialTimestamp =
            callCredentialsCacheSharedPreference.getLong(PREF_KEY_TIMESTAMP, 0)
        if (System.currentTimeMillis() < credentialTimestamp + CREDENTIALS_TTL) {
            val username1 = callCredentialsCacheSharedPreference.getString(PREF_KEY_USERNAME1, null)
            val password1 = callCredentialsCacheSharedPreference.getString(PREF_KEY_PASSWORD1, null)
            val username2 = callCredentialsCacheSharedPreference.getString(PREF_KEY_USERNAME2, null)
            val password2 = callCredentialsCacheSharedPreference.getString(PREF_KEY_PASSWORD2, null)
            val turnServers = callCredentialsCacheSharedPreference.getStringSet(
                PREF_KEY_TURN_SERVERS, null
            )
            if (username1 != null && password1 != null && username2 != null && password2 != null && turnServers != null) {
                Logger.d("☎ Reusing cached turn credentials")
                setState(GETTING_TURN_CREDENTIALS)
                callerSetTurnCredentialsAndInitializeCall(
                    username1,
                    password1,
                    username2,
                    password2,
                    ArrayList(turnServers)
                )
                return
            }
        }
        Logger.d("☎ Requesting new turn credentials")
        // request turn credentials
        setState(GETTING_TURN_CREDENTIALS)

        // check if my current owned identity has call permission, if not, check if another non-hidden identity has it
        var bytesOwnedIdentityWithCallPermission = bytesOwnedIdentity
        val currentOwnedIdentity = AppDatabase.getInstance().ownedIdentityDao()[bytesOwnedIdentity]
        if (currentOwnedIdentity == null || !currentOwnedIdentity.getApiKeyPermissions().contains(
                CALL
            )
        ) {
            // if my current identity can't call, check other identities
            for (ownedIdentity in AppDatabase.getInstance().ownedIdentityDao().allNotHidden) {
                if (Arrays.equals(ownedIdentity.bytesOwnedIdentity, bytesOwnedIdentity)) {
                    // skip the current identity
                    continue
                }
                if (ownedIdentity.getApiKeyPermissions().contains(CALL)) {
                    bytesOwnedIdentityWithCallPermission = ownedIdentity.bytesOwnedIdentity
                    break
                }
            }
        }
        AppSingleton.getEngine().getTurnCredentials(
            bytesOwnedIdentityWithCallPermission,
            callIdentifier,
            "caller",
            "recipient"
        )
    }

    fun clearCredentialsCache() {
        executor.execute {
            if (isCaller) {
                Logger.d("☎ Clearing cached turn credentials")
                val callCredentialsCacheSharedPreference = App.getContext().getSharedPreferences(
                    App.getContext().getString(
                        string.preference_filename_call_credentials_cache
                    ), MODE_PRIVATE
                )
                val editor = callCredentialsCacheSharedPreference.edit()
                editor.clear()
                editor.apply()
            }
        }
    }

    private fun callerSetTurnCredentialsAndInitializeCall(
        callerUsername: String,
        callerPassword: String,
        recipientUsername: String,
        recipientPassword: String,
        turnServers: List<String>
    ) {
        executor.execute {
            if (_state != GETTING_TURN_CREDENTIALS) {
                return@execute
            }
            turnUserName = callerUsername
            turnPassword = callerPassword
            this.turnServers = turnServers
            recipientTurnUserName = recipientUsername
            recipientTurnPassword = recipientPassword
            for (callParticipant in callParticipants.values) {
                callParticipant.peerConnectionHolder.setTurnCredentials(
                    callerUsername,
                    callerPassword /*, turnServers*/
                )
                callParticipant.peerConnectionHolder.createPeerConnection()
                if (callParticipant.peerConnectionHolder.peerConnection == null) {
                    peerConnectionHolderFailed(callParticipant, PEER_CONNECTION_CREATION_ERROR)
                }
            }
            setState(INITIALIZING_CALL)
        }
    }

    private fun callerFailedTurnCredentials(rfc: ObvTurnCredentialsFailedReason?) {
        executor.execute {
            when (rfc) {
                BAD_SERVER_SESSION -> failReason = SERVER_AUTHENTICATION_ERROR
                UNABLE_TO_CONTACT_SERVER -> failReason = SERVER_UNREACHABLE
                PERMISSION_DENIED -> {
                    failReason = FailReason.PERMISSION_DENIED
                    App.openAppDialogSubscriptionRequired(bytesOwnedIdentity, CALL)
                }

                CALLS_NOT_SUPPORTED_ON_SERVER -> {
                    failReason = CALL_INITIATION_NOT_SUPPORTED
                    App.openAppDialogCallInitiationNotSupported(bytesOwnedIdentity)
                }

                else -> {}
            }
            setState(FAILED)
        }
    }

    fun sendLocalDescriptionToPeer(
        callParticipant: CallParticipant,
        sdpType: String,
        sdpDescription: String,
        reconnectCounter: Int,
        peerReconnectCounterToOverride: Int
    ) {
        executor.execute {
            if (!callParticipantIndexes.containsKey(BytesKey(callParticipant.bytesContactIdentity))) {
                return@execute
            }
            try {
                if (callParticipant.peerState == PeerState.INITIAL) {
                    Logger.d("☎☎ Sending peer the following sdp [$sdpType]\n$sdpDescription")
                    if (isCaller) {
                        if (sendStartCallMessage(
                                callParticipant,
                                sdpType,
                                sdpDescription,
                                recipientTurnUserName,
                                recipientTurnPassword,
                                turnServers
                            )
                        ) {
                            callParticipant.setPeerState(START_CALL_MESSAGE_SENT)
                        } else {
                            callParticipant.setPeerState(PeerState.FAILED)
                        }
                    } else {
                        if (callParticipant.role == CALLER) {
                            sendAnswerCallMessage(callParticipant, sdpType, sdpDescription)
                            callParticipant.setPeerState(CONNECTING_TO_PEER)
                        } else if (shouldISendTheOfferToCallParticipant(callParticipant)) {
                            sendNewParticipantOfferMessage(callParticipant, sdpType, sdpDescription)
                            callParticipant.setPeerState(START_CALL_MESSAGE_SENT)
                        } else {
                            sendNewParticipantAnswerMessage(
                                callParticipant,
                                sdpType,
                                sdpDescription
                            )
                            callParticipant.setPeerState(CONNECTING_TO_PEER)
                        }
                    }
                } else if (callParticipant.peerState == CONNECTED || callParticipant.peerState == RECONNECTING) {
                    Logger.d("☎☎ Sending peer the following restart sdp [$sdpType]\n$sdpDescription")
                    sendReconnectCallMessage(
                        callParticipant,
                        sdpType,
                        sdpDescription,
                        reconnectCounter,
                        peerReconnectCounterToOverride
                    )
                }
            } catch (e: IOException) {
                peerConnectionHolderFailed(callParticipant, INTERNAL_ERROR)
                e.printStackTrace()
            }
        }
    }

    // Used for debug purpose only
    //    static int countSdpMedia(String description) {
    //        Pattern mediaStart = Pattern.compile("^m=");
    //        BufferedReader br = new BufferedReader(new StringReader(description));
    //        int count = 0;
    //        String line;
    //        try {
    //            while ((line = br.readLine()) != null) {
    //                Matcher m = mediaStart.matcher(line);
    //                if (m.find()) {
    //                    count++;
    //                }
    //            }
    //        } catch (Exception e) {
    //            e.printStackTrace();
    //            return 0;
    //        }
    //        return count;
    //    }
    private fun callerHandleRingingMessage(callParticipant: CallParticipant) {
        executor.execute {
            if (callParticipant.peerState != START_CALL_MESSAGE_SENT) {
                return@execute
            }
            callParticipant.setPeerState(RINGING)
            if (_state == INITIALIZING_CALL || _state == BUSY) {
                outgoingCallRinger!!.ring(RING)
                setState(State.RINGING)
            }
        }
    }

    private fun callerHandleBusyMessage(callParticipant: CallParticipant) {
        executor.execute {
            if (callParticipant.peerState != START_CALL_MESSAGE_SENT) {
                return@execute
            }
            callParticipant.setPeerState(PeerState.BUSY)

            // if all participants are busy, create a busy log entry
            var allBusy = true
            for (callParticipantOther in callParticipants.values) {
                if (callParticipantOther.peerState != PeerState.BUSY) {
                    allBusy = false
                    break
                }
            }
            if (allBusy) {
                createLogEntry(CallLogItem.STATUS_BUSY)
            }
            if (_state == INITIALIZING_CALL) {
                outgoingCallRinger!!.ring(Type.BUSY)
                setState(BUSY)
            }
        }
    }

    private fun callerHandleAnswerCallMessage(
        callParticipant: CallParticipant,
        peerSdpType: String?,
        gzippedPeerSdpDescription: ByteArray
    ) {
        executor.execute {
            if (callParticipant.peerState != START_CALL_MESSAGE_SENT && callParticipant.peerState != RINGING) {
                return@execute
            }
            if (_state == State.RINGING || _state == BUSY) {
                outgoingCallRinger!!.stop()
            }
            val peerSdpDescription: String = try {
                gunzip(gzippedPeerSdpDescription)
            } catch (e: IOException) {
                peerConnectionHolderFailed(callParticipant, INTERNAL_ERROR)
                e.printStackTrace()
                return@execute
            }
            callParticipant.peerConnectionHolder.setPeerSessionDescription(
                peerSdpType,
                peerSdpDescription
            )
            callParticipant.setPeerState(CONNECTING_TO_PEER)
            if (_state == State.RINGING) {
                setState(INITIALIZING_CALL)
            }
        }
    }

    private fun callerHandleRejectCallMessage(callParticipant: CallParticipant) {
        executor.execute {
            if (callParticipant.peerState != START_CALL_MESSAGE_SENT && callParticipant.peerState != RINGING) {
                return@execute
            }
            callParticipant.setPeerState(CALL_REJECTED)

            // if all participants reject the call, create a reject call log entry
            var allRejected = true
            for (callParticipantOther in callParticipants.values) {
                if (callParticipantOther.peerState != CALL_REJECTED) {
                    allRejected = false
                    break
                }
            }
            if (allRejected) {
                createLogEntry(CallLogItem.STATUS_REJECTED)
            }
            updateStateFromPeerStates()
        }
    }

    fun handleHangedUpMessage(callParticipant: CallParticipant) {
        executor.execute {
            callParticipant.setPeerState(HANGED_UP)
            updateStateFromPeerStates()

            // If I am the caller, notify the other call participants that someone hanged up (they should receive a message too, but this ensures everyone is in sync as soon as possible)
            if (isCaller && _state != CALL_ENDED) {
                val message = JsonUpdateParticipantsInnerMessage(callParticipants.values)
                for (callPart in callParticipants.values) {
                    if (callPart != callParticipant) {
                        sendDataChannelMessage(callPart, message)
                    }
                }
            }
        }
    }

    private fun hangUpCall(callIdentifier: UUID) {
        executor.execute {
            if (this.callIdentifier != callIdentifier) {
                return@execute
            }
            hangUpCallInternal()
        }
    }

    fun hangUpCall() {
        executor.execute { hangUpCallInternal() }
    }

    private fun hangUpCallInternal() {
        // notify peer that you hung up (it's not just a connection loss)
        sendHangedUpMessage(callParticipants.values)
        if (soundPool != null && _state != CALL_ENDED) { // do not play if state is already call ended
            soundPool!!.play(disconnectSound, 1f, 1f, 0, 0, 1f)
        }
        createLogEntry(CallLogItem.STATUS_MISSED)
        setState(CALL_ENDED)
        stopThisService()
    }

    private fun recipientReceiveCall(
        bytesOwnedIdentity: ByteArray?,
        bytesContactIdentity: ByteArray?,
        callIdentifier: UUID,
        peerSdpType: String?,
        gzippedPeerSdpDescription: ByteArray,
        turnUsername: String?,
        turnPassword: String?,  /* @Nullable List<String> turnServers,*/
        participantCount: Int,
        bytesGroupOwnerAndUidOrIdentifier: ByteArray?,
        gatheringPolicy: GatheringPolicy
    ) {
        executor.execute {
            if (_state != INITIAL && callIdentifier != this.callIdentifier) {
                sendBusyMessage(
                    bytesOwnedIdentity,
                    bytesContactIdentity,
                    callIdentifier,
                    bytesGroupOwnerAndUidOrIdentifier
                )
                return@execute
            }
            if (callIdentifier == this.callIdentifier && !Arrays.equals(
                    bytesOwnedIdentity,
                    this.bytesOwnedIdentity
                )
            ) {
                // receiving a call from another profile on same device...
                sendBusyMessage(
                    bytesOwnedIdentity,
                    bytesContactIdentity,
                    callIdentifier,
                    bytesGroupOwnerAndUidOrIdentifier
                )
                return@execute
            }
            val alreadyAnsweredOrRejectedOnOtherDevice =
                uncalledAnsweredOrRejectedOnOtherDevice.remove(callIdentifier)
            if (alreadyAnsweredOrRejectedOnOtherDevice != null) {
                App.runThread {
                    val callLogItem = CallLogItem(
                        bytesOwnedIdentity!!,
                        bytesGroupOwnerAndUidOrIdentifier,
                        CallLogItem.TYPE_INCOMING,
                        if (alreadyAnsweredOrRejectedOnOtherDevice) CallLogItem.STATUS_ANSWERED_ON_OTHER_DEVICE else CallLogItem.STATUS_REJECTED_ON_OTHER_DEVICE
                    )
                    callLogItem.id = AppDatabase.getInstance().callLogItemDao().insert(callLogItem)
                    val callLogItemContactJoin = CallLogItemContactJoin(
                        callLogItem.id,
                        bytesOwnedIdentity,
                        bytesContactIdentity!!
                    )
                    AppDatabase.getInstance().callLogItemDao().insert(callLogItemContactJoin)
                    var discussion: Discussion? = null
                    if (bytesGroupOwnerAndUidOrIdentifier != null) {
                        discussion = AppDatabase.getInstance().discussionDao()
                            .getByGroupOwnerAndUidOrIdentifier(
                                bytesOwnedIdentity,
                                bytesGroupOwnerAndUidOrIdentifier
                            )
                    }
                    if (discussion == null) {
                        discussion = AppDatabase.getInstance().discussionDao()
                            .getByContact(bytesOwnedIdentity, bytesContactIdentity)
                    }
                    if (discussion != null) {
                        val callMessage = Message.createPhoneCallMessage(
                            AppDatabase.getInstance(),
                            discussion!!.id,
                            bytesContactIdentity,
                            callLogItem
                        )
                        AppDatabase.getInstance().messageDao().insert(callMessage)
                        if (discussion!!.updateLastMessageTimestamp(callMessage.timestamp)) {
                            AppDatabase.getInstance().discussionDao().updateLastMessageTimestamp(
                                discussion!!.id,
                                discussion!!.lastMessageTimestamp
                            )
                        }
                    }
                }
                return@execute
            }
            val peerSdpDescription: String
            try {
                peerSdpDescription = gunzip(gzippedPeerSdpDescription)
            } catch (e: IOException) {
                failReason = INTERNAL_ERROR
                setState(FAILED)
                e.printStackTrace()
                return@execute
            }
            val contact =
                AppDatabase.getInstance().contactDao()[bytesOwnedIdentity, bytesContactIdentity]
            if (contact == null) {
                failReason = CONTACT_NOT_FOUND
                setState(FAILED)
                return@execute
            }
            setContactsAndRole(bytesOwnedIdentity!!, listOf(contact), callIdentifier, false)
            val callParticipant = getCallParticipant(bytesContactIdentity)
            if (callParticipant == null) {
                failReason = CONTACT_NOT_FOUND
                setState(FAILED)
                return@execute
            }
            this.bytesGroupOwnerAndUidOrIdentifier = bytesGroupOwnerAndUidOrIdentifier
            turnUserName = turnUsername
            this.turnPassword = turnPassword
            incomingParticipantCount = participantCount
            val discussion: Discussion?
            if (bytesGroupOwnerAndUidOrIdentifier == null) {
                discussion = AppDatabase.getInstance().discussionDao()
                    .getByContact(bytesOwnedIdentity, bytesContactIdentity)
                discussionType = Discussion.TYPE_CONTACT
            } else {
                discussion = AppDatabase.getInstance().discussionDao()
                    .getByGroupOwnerAndUidOrIdentifier(
                        bytesOwnedIdentity,
                        bytesGroupOwnerAndUidOrIdentifier
                    )
                discussionType = discussion.discussionType
            }
            var discussionCustomization: DiscussionCustomization? = null
            if (discussion != null) {
                discussionCustomization =
                    AppDatabase.getInstance().discussionCustomizationDao()[discussion.id]
            }
            callParticipant.peerConnectionHolder.setGatheringPolicy(gatheringPolicy)
            callParticipant.peerConnectionHolder.setPeerSessionDescription(
                peerSdpType,
                peerSdpDescription
            )
            callParticipant.peerConnectionHolder.setTurnCredentials(
                turnUsername,
                turnPassword /*, turnServers*/
            )
            showIncomingCallForeground(callParticipant.contact, participantCount)
            sendRingingMessage(callParticipant)
            registerScreenOffReceiver()
            incomingCallRinger!!.ring(callIdentifier, discussionCustomization)
            setState(State.RINGING)
        }
    }

    private fun recipientAnswerCall(callIdentifier: UUID, waitForAudioPermission: Boolean) {
        executor.execute {
            if (_state != State.RINGING || this.callIdentifier != callIdentifier) {
                return@execute
            }

            // stop ringing and listening for power button
            incomingCallRinger!!.stop()
            unregisterScreenOffReceiver()

            // remove notification in case previous starting foreground failed
            try {
                val notificationManager = NotificationManagerCompat.from(this)
                notificationManager.cancel(NOT_FOREGROUND_NOTIFICATION_ID)
            } catch (e: Exception) {
                // do nothing
            }
            if (waitForAudioPermission) {
                setState(WAITING_FOR_AUDIO_PERMISSION)
            } else {
                recipientAnswerCallInternal()
            }
        }
    }

    private fun recipientAnswerCallInternal() {
        showOngoingForeground()

        // get audio focus
        requestAudioManagerFocus()

        // initialize the peer connection factory
        WebrtcPeerConnectionHolder.initializePeerConnectionFactory()
        val callerCallParticipant = callerCallParticipant
        if (callerCallParticipant == null) {
            failReason = CONTACT_NOT_FOUND
            setState(FAILED)
            return
        }

        // turn credentials have already been set in recipientReceiveCall step, so we can create the peer connection
        callerCallParticipant.peerConnectionHolder.createPeerConnection()
        if (callerCallParticipant.peerConnectionHolder.peerConnection == null) {
            peerConnectionHolderFailed(callerCallParticipant, PEER_CONNECTION_CREATION_ERROR)
            return
        }
        setState(INITIALIZING_CALL)
    }

    private fun recipientRejectCall(callIdentifier: UUID) {
        executor.execute {
            if (_state != State.RINGING || this.callIdentifier != callIdentifier) {
                return@execute
            }
            rejectCallInternal(endedFromOtherDevice = false, answered = false)
        }
    }

    fun recipientRejectCall() {
        executor.execute {
            if (_state != State.RINGING) {
                return@execute
            }
            rejectCallInternal(endedFromOtherDevice = false, answered = false)
        }
    }

    ///////////
    // endedFromOtherDevice indicates the call should be ended because it was answered or rejected from another device
    // answered indicates that this other device picked up the call (this is ignored if endedFromOtherDevice is false)
    private fun rejectCallInternal(endedFromOtherDevice: Boolean, answered: Boolean) {
        // stop ringing and listening for power button
        incomingCallRinger!!.stop()
        unregisterScreenOffReceiver()
        val callerCallParticipant = callerCallParticipant
        if (callerCallParticipant == null) {
            failReason = CONTACT_NOT_FOUND
            setState(FAILED)
            return
        }
        if (!endedFromOtherDevice) {
            // notify peer of rejected call
            sendRejectCallMessage(callerCallParticipant)

            // create log entry
            createLogEntry(CallLogItem.STATUS_REJECTED)
        } else {
            if (answered) {
                createLogEntry(CallLogItem.STATUS_ANSWERED_ON_OTHER_DEVICE)
            } else {
                createLogEntry(CallLogItem.STATUS_REJECTED_ON_OTHER_DEVICE)
            }
        }
        callerCallParticipant.setPeerState(CALL_REJECTED)
        setState(CALL_ENDED)
        stopThisService()
    }

    fun peerConnectionHolderFailed(callParticipant: CallParticipant, failReason: FailReason) {
        executor.execute {
            if (callParticipantIndexes[BytesKey(callParticipant.bytesContactIdentity)] == null) {
                return@execute
            }
            if (callParticipants.size == 1) {
                // if there is a single participant in the call, fail the whole call
                this.failReason = failReason
                setState(FAILED)

                // also send a hang up message to notify peer
                hangUpCallInternal()
            } else if (isCaller) {
                val wasConnected =
                    callParticipant.peerState == CONNECTED || callParticipant.peerState == RECONNECTING

                // fail this participant only so it will be removed from the call
                callParticipant.setPeerState(PeerState.FAILED)
                updateStateFromPeerStates()

                // notify other participants in case the participant was already connected
                if (wasConnected) {
                    val message = JsonUpdateParticipantsInnerMessage(callParticipants.values)
                    for (callPart in callParticipants.values) {
                        if (callPart != callParticipant) {
                            sendDataChannelMessage(callPart, message)
                        }
                    }
                }
            } else {
                // we were unable to connect to one "secondary" call participant
                // nothing we can do...
            }
        }
    }

    fun markParticipantAsReconnecting(callParticipant: CallParticipant) {
        executor.execute {
            if (_state == CALL_ENDED || _state == FAILED) {
                return@execute
            }
            callParticipant.setPeerState(RECONNECTING)
            updateStateFromPeerStates()
        }
    }

    fun peerConnectionConnected(callParticipant: CallParticipant) {
        executor.execute {
            val oldState = callParticipant.peerState
            callParticipant.setPeerState(CONNECTED)
            if (callParticipant.timeoutTask != null) {
                callParticipant.timeoutTask!!.cancel()
                callParticipant.timeoutTask = null
            }
            if (isCaller && oldState != CONNECTED && oldState != RECONNECTING) {
                val message = JsonUpdateParticipantsInnerMessage(callParticipants.values)
                for (callPart in callParticipants.values) {
                    if (callPart != callParticipant) {
                        sendDataChannelMessage(callPart, message)
                    }
                }
            }
            // we call updateStateFromPeerStates here so we get a chance to stop playing the reconnecting sound
            updateStateFromPeerStates()
            if (_state == CALL_IN_PROGRESS) {
                return@execute
            }
            if (timeoutTimerTask != null) {
                timeoutTimerTask!!.cancel()
                timeoutTimerTask = null
            }
            acquireWakeLock(WIFI)
            createLogEntry(CallLogItem.STATUS_SUCCESSFUL)
            if (soundPool != null) {
                soundPool!!.play(connectSound, 1f, 1f, 0, 0, 1f)
            }
            if (callDurationTimer != null) {
                callDurationTimer = null
            }
            callDuration.postValue(0)
            callDurationTimer = Timer()
            callDurationTimer!!.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    var duration = callDuration.value
                    if (duration == null) {
                        duration = 0
                    }
                    callDuration.postValue(duration + 1)
                }
            }, 0, 1000)
            setState(CALL_IN_PROGRESS)
        }
    }

    fun callerAddCallParticipants(contactsToAdd: List<Contact>) {
        executor.execute {
            if (!isCaller) {
                return@execute
            }
            val newCallParticipants: MutableList<CallParticipant> = ArrayList()
            for (contact in contactsToAdd) {
                if (!Arrays.equals(contact.bytesOwnedIdentity, bytesOwnedIdentity)) {
                    Logger.w("☎ Trying to add contact to call for a different ownedIdentity")
                    continue
                }
                if (getCallParticipant(contact.bytesContactIdentity) != null) {
                    Logger.w("☎ Trying to add contact to call which is already in the call")
                    continue
                }
                Logger.d("☎ Adding a call participant")
                val callParticipant = this.CallParticipant(contact, RECIPIENT)
                newCallParticipants.add(callParticipant)
                callParticipantIndexes[BytesKey(callParticipant.bytesContactIdentity)] =
                    callParticipantIndex
                callParticipants[callParticipantIndex] = callParticipant
                callParticipantIndex++
                if (_state != INITIAL && _state != WAITING_FOR_AUDIO_PERMISSION && _state != GETTING_TURN_CREDENTIALS) { // only create the peer if the turn credentials were already retrieved
                    callParticipant.peerConnectionHolder.setTurnCredentials(
                        turnUserName,
                        turnPassword /*, turnServers*/
                    )
                    callParticipant.peerConnectionHolder.createPeerConnection()
                    if (callParticipant.peerConnectionHolder.peerConnection == null) {
                        peerConnectionHolderFailed(callParticipant, PEER_CONNECTION_CREATION_ERROR)
                    }
                }
            }
            notifyCallParticipantsChanged()
            updateLogEntry(newCallParticipants)
        }
    }

    fun callerKickParticipant(bytesContactIdentity: ByteArray?) {
        executor.execute {
            if (!isCaller) {
                return@execute
            }
            val callParticipant = getCallParticipant(bytesContactIdentity)
            if (callParticipant != null) {
                internalRemoveCallParticipant(callParticipant)
                sendKickMessage(callParticipant)
                val message = JsonUpdateParticipantsInnerMessage(callParticipants.values)
                for (callPart in callParticipants.values) {
                    sendDataChannelMessage(callPart, message)
                }
            }
        }
    }

    private fun internalRemoveCallParticipant(callParticipant: CallParticipant) {
        callParticipant.peerConnectionHolder.cleanUp()
        val index = callParticipantIndexes.remove(BytesKey(callParticipant.bytesContactIdentity))
        if (index != null) {
            callParticipants.remove(index)
        } else {
            Logger.w("☎ Calling removeCallParticipant for participant not in the call")
        }
        notifyCallParticipantsChanged()
    }

    private fun handleReconnectCallMessage(
        callParticipant: CallParticipant,
        peerSdpType: String?,
        gzippedPeerSdpDescription: ByteArray,
        reconnectCounter: Int,
        peerReconnectCounterToOverride: Int
    ) {
        executor.execute {
            Logger.d("☎ Received reconnect call message")
            val peerSdpDescription: String = try {
                gunzip(gzippedPeerSdpDescription)
            } catch (e: IOException) {
                peerConnectionHolderFailed(callParticipant, INTERNAL_ERROR)
                e.printStackTrace()
                return@execute
            }
            callParticipant.peerConnectionHolder.handleReceivedRestartSdp(
                peerSdpType,
                peerSdpDescription,
                reconnectCounter,
                peerReconnectCounterToOverride
            )
        }
    }

    private fun handleNewParticipantOfferMessage(
        callParticipant: CallParticipant,
        sessionDescriptionType: String,
        gzippedPeerSdpDescription: ByteArray,
        gatheringPolicy: GatheringPolicy
    ) {
        executor.execute {
            if (callParticipant.role != RECIPIENT || shouldISendTheOfferToCallParticipant(
                    callParticipant
                )
            ) {
                return@execute
            }
            val peerSdpDescription: String = try {
                gunzip(gzippedPeerSdpDescription)
            } catch (e: IOException) {
                callParticipant.setPeerState(HANGED_UP)
                e.printStackTrace()
                return@execute
            }
            callParticipant.peerConnectionHolder.setGatheringPolicy(gatheringPolicy)
            callParticipant.peerConnectionHolder.setPeerSessionDescription(
                sessionDescriptionType,
                peerSdpDescription
            )
            callParticipant.peerConnectionHolder.setTurnCredentials(
                turnUserName,
                turnPassword /*, turnServers*/
            )
            callParticipant.peerConnectionHolder.createPeerConnection()
            if (callParticipant.peerConnectionHolder.peerConnection == null) {
                peerConnectionHolderFailed(callParticipant, PEER_CONNECTION_CREATION_ERROR)
            }
        }
    }

    private fun handleNewParticipantAnswerMessage(
        callParticipant: CallParticipant,
        sessionDescriptionType: String,
        gzippedPeerSdpDescription: ByteArray
    ) {
        executor.execute {
            if (callParticipant.role != RECIPIENT || !shouldISendTheOfferToCallParticipant(
                    callParticipant
                )
            ) {
                return@execute
            }
            val peerSdpDescription: String = try {
                gunzip(gzippedPeerSdpDescription)
            } catch (e: IOException) {
                callParticipant.setPeerState(HANGED_UP)
                e.printStackTrace()
                return@execute
            }
            callParticipant.peerConnectionHolder.setPeerSessionDescription(
                sessionDescriptionType,
                peerSdpDescription
            )
            callParticipant.setPeerState(CONNECTING_TO_PEER)
        }
    }

    private fun handleKickedMessage() {
        executor.execute {
            for (callParticipant in callParticipants.values) {
                callParticipant.setPeerState(HANGED_UP)
            }
            failReason = FailReason.KICKED
            setState(FAILED)
        }
    }

    private fun handleNewIceCandidateMessage(
        callIdentifier: UUID,
        bytesOwnedIdentity: ByteArray,
        bytesContactIdentity: ByteArray,
        jsonIceCandidate: JsonIceCandidate
    ) {
        executor.execute {
            Logger.d("☎ received new ICE candidate")
            if (Arrays.equals(
                    bytesOwnedIdentity,
                    this.bytesOwnedIdentity
                ) && callIdentifier == this.callIdentifier
            ) {
                // we are in the right call, handle the message directly (if the participant is in the call)
                val callParticipant = getCallParticipant(bytesContactIdentity)
                if (callParticipant != null) {
                    Logger.d("☎ passing candidate to peerConnectionHolder")
                    callParticipant.peerConnectionHolder.addIceCandidates(listOf(jsonIceCandidate))
                    return@execute
                }
            }


            // this is not the right call, store the candidate on the side
            var callerCandidatesMap = uncalledReceivedIceCandidates[callIdentifier]
            if (callerCandidatesMap == null) {
                callerCandidatesMap = HashMap()
                uncalledReceivedIceCandidates[callIdentifier] = callerCandidatesMap
            }
            var candidates = callerCandidatesMap[BytesKey(bytesContactIdentity)]
            if (candidates == null) {
                candidates = HashSet()
                callerCandidatesMap[BytesKey(bytesContactIdentity)] = candidates
            }
            candidates.add(jsonIceCandidate)
        }
    }

    private fun handleRemoveIceCandidatesMessage(
        callIdentifier: UUID,
        bytesOwnedIdentity: ByteArray,
        bytesContactIdentity: ByteArray,
        jsonIceCandidates: Array<JsonIceCandidate>
    ) {
        executor.execute {
            if (Arrays.equals(
                    bytesOwnedIdentity,
                    this.bytesOwnedIdentity
                ) && callIdentifier == this.callIdentifier
            ) {
                // we are in the right call, handle the message directly
                val callParticipant = getCallParticipant(bytesContactIdentity)
                callParticipant?.peerConnectionHolder?.removeIceCandidates(jsonIceCandidates)
            } else {
                // this is not the right call, remove the candidate from the side
                val callerCandidatesMap = uncalledReceivedIceCandidates[callIdentifier]
                if (callerCandidatesMap != null) {
                    val candidates = callerCandidatesMap[BytesKey(bytesContactIdentity)]
                    if (candidates != null) {
                        for (jsonIceCandidate in jsonIceCandidates) {
                            candidates.remove(jsonIceCandidate)
                        }
                        if (candidates.isEmpty()) {
                            callerCandidatesMap.remove(BytesKey(bytesContactIdentity))
                            if (callerCandidatesMap.isEmpty()) {
                                uncalledReceivedIceCandidates.remove(callIdentifier)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleAnsweredOrRejectedOnOtherDeviceMessage(
        callIdentifier: UUID,
        bytesOwnedIdentity: ByteArray,
        answered: Boolean
    ) {
        executor.execute {
            Logger.d("☎ Call handled on other owned device: " + if (answered) "answered" else "rejected")
            if (Arrays.equals(
                    bytesOwnedIdentity,
                    this.bytesOwnedIdentity
                ) && callIdentifier == this.callIdentifier
            ) {
                // we are in the right call, handle the message directly
                if (_state != State.RINGING) {
                    return@execute
                }
                rejectCallInternal(true, answered)
            } else {
                // this is not the right call, remove the candidate from the side
                uncalledAnsweredOrRejectedOnOtherDevice[callIdentifier] = answered
            }
        }
    }

    private fun handleUpdateCallParticipantsMessage(jsonUpdateParticipantsInnerMessage: JsonUpdateParticipantsInnerMessage) {
        executor.execute {
            val participantsToRemove: MutableSet<BytesKey> = HashSet(callParticipantIndexes.keys)
            val newCallParticipants: MutableList<CallParticipant> = ArrayList()
            for (jsonContactBytesAndName in jsonUpdateParticipantsInnerMessage.callParticipants) {
                if (Arrays.equals(
                        jsonContactBytesAndName.bytesContactIdentity,
                        bytesOwnedIdentity
                    )
                ) {
                    // the received array contains the user himself
                    continue
                }
                val bytesKey = BytesKey(jsonContactBytesAndName.bytesContactIdentity)
                if (participantsToRemove.contains(bytesKey)) {
                    participantsToRemove.remove(bytesKey)
                } else {
                    // call participant not already in the call --> we add him
                    val callParticipant = CallParticipant(
                        bytesOwnedIdentity,
                        jsonContactBytesAndName.bytesContactIdentity,
                        jsonContactBytesAndName.displayName,
                        jsonContactBytesAndName.gatheringPolicy
                    )
                    if (callParticipant.contact == null) {
                        // contact not found --> we use the name pushed by the caller
                        callParticipant.displayName = jsonContactBytesAndName.displayName
                    } else {
                        newCallParticipants.add(callParticipant)
                    }
                    callParticipantIndexes[bytesKey] = callParticipantIndex
                    callParticipants[callParticipantIndex] = callParticipant
                    callParticipantIndex++
                    if (shouldISendTheOfferToCallParticipant(callParticipant)) {
                        Logger.d("☎ I am in charge of sending the offer to a new participant")
                        callParticipant.peerConnectionHolder.setTurnCredentials(
                            turnUserName,
                            turnPassword /*, turnServers*/
                        )
                        callParticipant.peerConnectionHolder.createPeerConnection()
                        if (callParticipant.peerConnectionHolder.peerConnection == null) {
                            peerConnectionHolderFailed(
                                callParticipant,
                                PEER_CONNECTION_CREATION_ERROR
                            )
                        }
                    } else {
                        Logger.d("☎ I am NOT in charge of sending the offer to a new participant")
                        // check if we already received the offer the CallParticipant is supposed to send us
                        val newParticipantOfferMessage =
                            receivedOfferMessages.remove(BytesKey(callParticipant.bytesContactIdentity))
                        if (newParticipantOfferMessage != null) {
                            Logger.d("☎ Reusing previously received participant offer message")
                            handleNewParticipantOfferMessage(
                                callParticipant,
                                newParticipantOfferMessage.sessionDescriptionType,
                                newParticipantOfferMessage.gzippedSessionDescription,
                                newParticipantOfferMessage.gatheringPolicy
                            )
                        }
                    }
                }
            }
            for (bytesKeyToRemove in participantsToRemove) {
                val index = callParticipantIndexes[bytesKeyToRemove] ?: continue
                val callParticipant = callParticipants[index]
                if (callParticipant == null || callParticipant.role == CALLER) {
                    continue
                }
                if (callParticipant.peerState != HANGED_UP) {
                    callParticipant.setPeerState(KICKED)
                }
            }
            updateLogEntry(newCallParticipants)
            notifyCallParticipantsChanged()
        }
    }

    // endregion
    // region Setters and Getters
    private fun setState(state: State) {
        if (this._state == FAILED) {
            // we cannot come back from FAILED state
            return
        }
        this._state = state
        stateLiveData.postValue(state)

        // handle special state change hooks
        if (state == FAILED) {
            // create the log entry --> this will only create one if one was not already created
            createLogEntry(CallLogItem.STATUS_FAILED)
            stopThisService()
        } else if (state == State.RINGING && !isCaller) {
            createRecipientRingingTimeout()
        }
    }

    fun getState(): LiveData<State> {
        return stateLiveData
    }

    private fun setContactsAndRole(
        bytesOwnedIdentity: ByteArray,
        contacts: List<Contact>,
        callIdentifier: UUID,
        iAmTheCaller: Boolean
    ) {
        this.bytesOwnedIdentity = bytesOwnedIdentity
        this.callIdentifier = callIdentifier
        role = if (iAmTheCaller) CALLER else RECIPIENT
        for (contact in contacts) {
            val callParticipant = CallParticipant(contact, if (iAmTheCaller) RECIPIENT else CALLER)
            callParticipantIndexes[BytesKey(contact.bytesContactIdentity)] = callParticipantIndex
            callParticipants[callParticipantIndex] = callParticipant
            callParticipantIndex++
        }
        notifyCallParticipantsChanged()
    }

    fun getCallParticipantsLiveData(): LiveData<List<CallParticipantPojo>> {
        return callParticipantsLiveData
    }

    val isCaller: Boolean
        get() = role == CALLER

    var speakingWhileMuted by mutableStateOf(false)
    fun toggleMuteMicrophone() {
        executor.execute {
            microphoneMuted = !microphoneMuted
            val jsonMutedInnerMessage = JsonMutedInnerMessage(microphoneMuted)
            for (callParticipant in callParticipants.values) {
                callParticipant.peerConnectionHolder.setAudioEnabled(!microphoneMuted)
                sendDataChannelMessage(callParticipant, jsonMutedInnerMessage)
            }
            if (microphoneMuted.not()) {
                speakingWhileMuted = false
            }
            microphoneMutedLiveData.postValue(microphoneMuted)
        }
    }

    fun getMicrophoneMuted(): LiveData<Boolean> {
        return microphoneMutedLiveData
    }

    fun toggleCamera() {
        if (cameraEnabled.not()) {
            try {
                localVideoTrack?.setEnabled(true)
            } catch (ignored: Exception) {
                localVideoTrack = null
            }
            if (videoCapturer == null) {
                createLocalVideo()
            } else {
                videoCapturer?.startCapture(selectedCamera?.captureFormat?.width ?: 1280, selectedCamera?.captureFormat?.height ?: 720, 30)
            }
            cameraEnabled = true
            // if output is PHONE --> toggle speaker on
            if (selectedAudioOutput == PHONE) {
                selectAudioOutput(LOUDSPEAKER)
            }
        } else {
            cameraEnabled = false
            try {
                localVideoTrack?.setEnabled(false)
            } catch (ignored: Exception) {
                localVideoTrack = null
            }
            try {
                videoCapturer?.stopCapture()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            try {
                screenCapturerAndroid?.stopCapture()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        executor.execute {
            val jsonVideoSharingInnerMessage = JsonVideoSharingInnerMessage(cameraEnabled)
            for (callParticipant in callParticipants.values) {
                sendDataChannelMessage(callParticipant, jsonVideoSharingInnerMessage)
            }
        }
    }

    fun toggleScreenShare(intent: Intent? = null) {
        if (screenShareActive.not()) {
            if (screenCapturerAndroid == null) {
                intent?.let {
                    // restart the ongoing foreground service to set the correct foreground service type
                    screenShareActive = true
                    showOngoingForeground()
                    createScreenCapturer(it)
                } ?: {
                    screenShareActive = false
                }
            } else {
                screenShareActive = true
                screenCapturerAndroid?.startCapture(screenWidth, screenHeight, 0)
            }
            try {
                localScreenTrack?.setEnabled(true)
            } catch (ignored: Exception) { }
        } else {
            screenShareActive = false
            try {
                screenCapturerAndroid?.stopCapture()
                localScreenTrack?.setEnabled(false)
            } catch (ignored: InterruptedException) {
            } finally {
                screenCapturerAndroid?.dispose()
                screenCapturerAndroid = null
            }
        }
        executor.execute {
            val jsonScreenSharingInnerMessage = JsonScreenSharingInnerMessage(screenShareActive)
            callParticipants.values.forEach {
                sendDataChannelMessage(it, jsonScreenSharingInnerMessage)
            }
        }
    }

    fun flipCamera() {
        if (availableCameras.size > 1) {
            val currentIndex = availableCameras.indexOfFirst { it.cameraId == selectedCamera?.cameraId }
            val newIndex = (currentIndex + 1) % availableCameras.size
            val cameraAndFormat = availableCameras.get(newIndex)
            selectedCamera = cameraAndFormat
            selectedCameraLiveData.postValue(selectedCamera)
            (videoCapturer as? CameraVideoCapturer)?.let {
                it.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(p0: Boolean) {
                        it.changeCaptureFormat(cameraAndFormat.captureFormat.width, cameraAndFormat.captureFormat.height, 30)
                    }

                    override fun onCameraSwitchError(p0: String?) {}
                }, cameraAndFormat.cameraId)
            }
        }
    }

    private fun createLocalVideo() {
        val surfaceTextureHelper = SurfaceTextureHelper.create(
            "captureThread",
            WebrtcPeerConnectionHolder.eglBase?.eglBaseContext
        )
        videoCapturer = try {
            createVideoCapturer(this)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return
        }
        videoCapturer!!.initialize(
            surfaceTextureHelper,
            this,
            WebrtcPeerConnectionHolder.videoSource?.capturerObserver
        )
        videoCapturer!!.startCapture(selectedCamera?.captureFormat?.width ?: 1280, selectedCamera?.captureFormat?.height ?: 720, 30)
    }

    private var videoCapturer: VideoCapturer? = null

    @Throws(Exception::class)
    private fun createVideoCapturer(context: Context): VideoCapturer {
        val capturer = selectedCamera?.let {
            val cameraEnumerator =
                if (Camera2Enumerator.isSupported(context)) Camera2Enumerator(context) else Camera1Enumerator(true)
            cameraEnumerator.createCapturer(it.cameraId, null)
        }
        if (capturer == null) {
            Logger.e("No selected camera, unable to create video capturer")
            throw Exception()
        }
        return  capturer
    }

    fun setScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    private var screenCapturerAndroid: ScreenCapturerAndroid? = null
    private fun createScreenCapturer(intent: Intent) {
        screenCapturerAndroid = ScreenCapturerAndroid(intent, object : MediaProjection.Callback() {
            override fun onCapturedContentResize(width: Int, height: Int) {
                super.onCapturedContentResize(width, height)
                screenCapturerAndroid?.changeCaptureFormat(width, height, 0);
            }

            override fun onStop() {
                super.onStop()
                if (screenShareActive) {
                    toggleScreenShare()
                }
            }
        })
        val surfaceTextureHelper = SurfaceTextureHelper.create(
            "captureThread",
            WebrtcPeerConnectionHolder.eglBase?.eglBaseContext
        )
        screenCapturerAndroid!!.initialize(
            surfaceTextureHelper,
            this,
            WebrtcPeerConnectionHolder.screenShareVideoSource?.capturerObserver
        )
        screenCapturerAndroid!!.startCapture(screenWidth, screenHeight, 0)
    }

    //

    fun bluetoothDisconnected() {
        executor.execute {
            if (selectedAudioOutput != BLUETOOTH) {
                return@execute
            }
            selectAudioOutput(availableAudioOutputs[0])
        }
    }

    fun selectAudioOutput(audioOutput: AudioOutput) {
        executor.execute {
            if (!availableAudioOutputs.contains(audioOutput)) {
                return@execute
            }
            if (audioOutput == selectedAudioOutput) {
                return@execute
            }
            if (selectedAudioOutput == BLUETOOTH && bluetoothHeadsetManager != null) {
                bluetoothHeadsetManager!!.disconnectAudio()
            }

            if (VERSION.SDK_INT >= VERSION_CODES.M) {
                audioDeviceModule?.setSpeakerMute(audioOutput == MUTED) ?: return@execute
            }

            when (audioOutput) {
                PHONE, HEADSET, MUTED -> if (audioManager!!.isSpeakerphoneOn) {
                    audioManager!!.isSpeakerphoneOn = false
                }

                LOUDSPEAKER -> if (!audioManager!!.isSpeakerphoneOn) {
                    audioManager!!.isSpeakerphoneOn = true
                }

                BLUETOOTH -> if (bluetoothHeadsetManager != null) {
                    if (audioManager!!.isSpeakerphoneOn) {
                        audioManager!!.isSpeakerphoneOn = false
                    }
                    bluetoothHeadsetManager!!.connectAudio()
                } else {
                    return@execute
                }
            }
            selectedAudioOutput = audioOutput
        }
    }

    fun getCallDuration(): LiveData<Int?> {
        return callDuration
    }

    // endregion
    // region Helper methods
    fun shouldISendTheOfferToCallParticipant(callParticipant: CallParticipant): Boolean {
        return BytesKey(bytesOwnedIdentity).compareTo(BytesKey(callParticipant.bytesContactIdentity)) > 0
    }

    fun synchronizeOnExecutor(runnable: Runnable) {
        executor.execute(runnable)
    }

    fun getActiveLocalVideo(): VideoTrack? {
        return if (screenShareActive) localScreenTrack else if (cameraEnabled) localVideoTrack else null
    }

    fun getCallParticipant(bytesContactIdentity: ByteArray?): CallParticipant? {
        val index = callParticipantIndexes[BytesKey(bytesContactIdentity)] ?: return null
        return callParticipants[index]
    }

    private val callerCallParticipant: CallParticipant?
        get() {
            for (callParticipant in callParticipants.values) {
                if (callParticipant.role == CALLER) {
                    return callParticipant
                }
            }
            return null
        }

    private fun notifyCallParticipantsChanged() {
        val pojos: MutableList<CallParticipantPojo> = ArrayList(callParticipants.size)
        for (callParticipant in callParticipants.values) {
            pojos.add(CallParticipantPojo(callParticipant))
        }
        callParticipantsLiveData.postValue(pojos)
    }

    private fun updateStateFromPeerStates() {
        var allPeersAreInFinalState = true
        var allReconnecting = true
        for (callParticipant in callParticipants.values) {
            when (callParticipant.peerState) {
                PeerState.INITIAL, START_CALL_MESSAGE_SENT, RINGING, PeerState.BUSY, CONNECTING_TO_PEER, CONNECTED -> {
                    allReconnecting = false
                    run { allPeersAreInFinalState = false }
                }

                RECONNECTING -> {
                    allPeersAreInFinalState = false
                }

                CALL_REJECTED, HANGED_UP, KICKED, PeerState.FAILED -> {
                    allReconnecting = false
                }
            }
        }
        if (callParticipants.size == 1 && allReconnecting) {
            if (reconnectingStreamId == null) {
                reconnectingStreamId = soundPool!!.play(reconnectingSound, .5f, .5f, 0, -1, 1f)
            }
        } else {
            if (reconnectingStreamId != null) {
                soundPool!!.stop(reconnectingStreamId!!)
                reconnectingStreamId = null
            }
        }
        if (allPeersAreInFinalState) {
            createLogEntry(CallLogItem.STATUS_MISSED) // this only create the log if it was not yet created
            if (soundPool != null && _state != CALL_ENDED) {
                soundPool!!.play(disconnectSound, 1f, 1f, 0, 0, 1f)
            }
            setState(CALL_ENDED)
            stopThisService()
        }
    }

    fun updateAvailableAudioOutputsList() {
        executor.execute {
            val audioOutputs = mutableListOf<AudioOutput>()
            if (wiredHeadsetConnected) {
                audioOutputs.add(HEADSET)
            } else {
                audioOutputs.add(PHONE)
            }
            audioOutputs.add(LOUDSPEAKER)
            if (bluetoothHeadsetManager != null) {
                if (bluetoothHeadsetManager!!.state != HEADSET_UNAVAILABLE) {
                    audioOutputs.add(BLUETOOTH)
                    if (bluetoothAutoConnect) {
                        bluetoothAutoConnect = false
                        selectAudioOutput(BLUETOOTH)
                    }
                }
            }
            if (VERSION.SDK_INT >= VERSION_CODES.M) {
                audioOutputs.add(MUTED)
            }
            if (!audioOutputs.contains(selectedAudioOutput)) {
                selectAudioOutput(audioOutputs[0])
            }
            availableAudioOutputs.clear()
            availableAudioOutputs.addAll(audioOutputs)
        }
    }

    private fun updateCameraList() {
        executor.execute {
            val useCamera2 = Camera2Enumerator.isSupported(this)
            val cameraEnumerator = if (useCamera2) Camera2Enumerator(this) else Camera1Enumerator()

            val targetResolution = SettingsActivity.getVideoSendResolution()

            // For now, we keep the first front and the first back camera
            val cameras = cameraEnumerator.deviceNames.toList()

            val frontCameraId = cameras.firstOrNull { cameraEnumerator.isFrontFacing(it) }
            val backCameraId = cameras.firstOrNull { cameraEnumerator.isBackFacing(it) }

            availableCameras = listOfNotNull(
                frontCameraId?.let { CameraAndFormat(it, true, getFormatForResolution(cameraEnumerator, it, targetResolution)) },
                backCameraId?.let { CameraAndFormat(it, false, getFormatForResolution(cameraEnumerator, it, targetResolution)) },
            )
            availableCamerasLiveData.postValue(availableCameras)
            selectedCamera = availableCameras.firstOrNull()
            selectedCameraLiveData.postValue(selectedCamera)
        }
    }

    private fun getFormatForResolution(cameraEnumerator: CameraEnumerator, cameraId: String, targetResolution: Int) : CameraEnumerationAndroid.CaptureFormat {
        return cameraEnumerator.getSupportedFormats(cameraId).sortedWith(compareByDescending<CameraEnumerationAndroid.CaptureFormat> { it.height }.then(compareByDescending { it.width })).first { it.height <= targetResolution && (it.width <= it.height*16f/9f + 10) }
    }

    private fun sendDataChannelMessage(
        callParticipant: CallParticipant,
        jsonDataChannelInnerMessage: JsonDataChannelInnerMessage
    ) {
        try {
            val jsonDataChannelMessage = JsonDataChannelMessage()
            jsonDataChannelMessage.messageType = jsonDataChannelInnerMessage.messageType
            jsonDataChannelMessage.serializedMessage = objectMapper.writeValueAsString(
                jsonDataChannelInnerMessage
            )
            callParticipant.peerConnectionHolder.sendDataChannelMessage(
                objectMapper.writeValueAsString(
                    jsonDataChannelMessage
                )
            )
        } catch (e: JsonProcessingException) {
            e.printStackTrace()
        }
    }

    private fun createRecipientRingingTimeout() {
        if (timeoutTimerTask != null) {
            timeoutTimerTask!!.cancel()
            timeoutTimerTask = null
        }
        timeoutTimerTask = object : TimerTask() {
            override fun run() {
                executor.execute { hangUpCallInternal() }
            }
        }
        try {
            timeoutTimer.schedule(timeoutTimerTask, RINGING_TIMEOUT_MILLIS)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    private fun createLogEntry(callLogItemStatus: Int) {
        if (callLogItem != null) {
            // a call log entry was already created, don't create a new one
            return
        }
        if (callParticipants.isEmpty()) {
            return
        }
        val callParticipants = callParticipants.values.toTypedArray<CallParticipant>()
        val type = if (isCaller) CallLogItem.TYPE_OUTGOING else CallLogItem.TYPE_INCOMING
        var callLogItem: CallLogItem? = null
        when (callLogItemStatus) {
            CallLogItem.STATUS_SUCCESSFUL, CallLogItem.STATUS_MISSED, CallLogItem.STATUS_BUSY, CallLogItem.STATUS_FAILED, CallLogItem.STATUS_REJECTED, CallLogItem.STATUS_ANSWERED_ON_OTHER_DEVICE, CallLogItem.STATUS_REJECTED_ON_OTHER_DEVICE -> callLogItem =
                CallLogItem(
                    bytesOwnedIdentity!!, bytesGroupOwnerAndUidOrIdentifier, type, callLogItemStatus
                )
        }
        if (callLogItem != null) {
            this.callLogItem = callLogItem
            App.runThread {
                this.callLogItem!!.id =
                    AppDatabase.getInstance().callLogItemDao().insert(this.callLogItem)
                val callLogItemContactJoins =
                    arrayOfNulls<CallLogItemContactJoin>(callParticipants.size)
                for (i in callParticipants.indices) {
                    callLogItemContactJoins[i] = CallLogItemContactJoin(
                        this.callLogItem!!.id,
                        bytesOwnedIdentity!!,
                        callParticipants[i].bytesContactIdentity
                    )
                }
                AppDatabase.getInstance().callLogItemDao().insert(*callLogItemContactJoins)
                if (this.callLogItem!!.callType == CallLogItem.TYPE_INCOMING
                    && (this.callLogItem!!.callStatus == CallLogItem.STATUS_MISSED || this.callLogItem!!.callStatus == CallLogItem.STATUS_FAILED || this.callLogItem!!.callStatus == CallLogItem.STATUS_BUSY)
                ) {
                    for (callParticipant in callParticipants) {
                        if (callParticipant.role == CALLER) {
                            AndroidNotificationManager.displayMissedCallNotification(
                                bytesOwnedIdentity!!,
                                callParticipant.bytesContactIdentity
                            )
                            break
                        }
                    }
                }
                if (this.callLogItem!!.callType == CallLogItem.TYPE_OUTGOING) {
                    if (this.callLogItem!!.bytesGroupOwnerAndUidOrIdentifier != null) {
                        // group discussion
                        val discussion = AppDatabase.getInstance().discussionDao()
                            .getByGroupOwnerAndUidOrIdentifier(
                                bytesOwnedIdentity,
                                this.callLogItem!!.bytesGroupOwnerAndUidOrIdentifier
                            )
                        if (discussion != null) {
                            val callMessage = Message.createPhoneCallMessage(
                                AppDatabase.getInstance(),
                                discussion.id,
                                bytesOwnedIdentity,
                                this.callLogItem
                            )
                            AppDatabase.getInstance().messageDao().insert(callMessage)
                            if (discussion.updateLastMessageTimestamp(callMessage.timestamp)) {
                                AppDatabase.getInstance().discussionDao()
                                    .updateLastMessageTimestamp(
                                        discussion.id,
                                        discussion.lastMessageTimestamp
                                    )
                            }
                        }
                    } else if (callParticipants.size == 1) {
                        // one-to-one discussion
                        val discussion = AppDatabase.getInstance().discussionDao().getByContact(
                            bytesOwnedIdentity,
                            callParticipants[0].bytesContactIdentity
                        )
                        if (discussion != null) {
                            val callMessage = Message.createPhoneCallMessage(
                                AppDatabase.getInstance(),
                                discussion.id,
                                callParticipants[0].bytesContactIdentity,
                                this.callLogItem
                            )
                            AppDatabase.getInstance().messageDao().insert(callMessage)
                            if (discussion.updateLastMessageTimestamp(callMessage.timestamp)) {
                                AppDatabase.getInstance().discussionDao()
                                    .updateLastMessageTimestamp(
                                        discussion.id,
                                        discussion.lastMessageTimestamp
                                    )
                            }
                        }
                    }
                    // for multi-call without a discussion, we do not insert a message in any discussion
                } else {
                    // find the caller, then insert either in a group discussion, or in his one-to-one discussion
                    for (callParticipant in callParticipants) {
                        if (callParticipant.role == CALLER) {
                            var discussion: Discussion? = null
                            if (this.callLogItem!!.bytesGroupOwnerAndUidOrIdentifier != null) {
                                discussion = AppDatabase.getInstance().discussionDao()
                                    .getByGroupOwnerAndUidOrIdentifier(
                                        bytesOwnedIdentity,
                                        this.callLogItem!!.bytesGroupOwnerAndUidOrIdentifier
                                    )
                            }
                            if (discussion == null) {
                                discussion = AppDatabase.getInstance().discussionDao().getByContact(
                                    bytesOwnedIdentity,
                                    callParticipant.bytesContactIdentity
                                )
                            }
                            if (discussion != null) {
                                val callMessage = Message.createPhoneCallMessage(
                                    AppDatabase.getInstance(),
                                    discussion.id,
                                    callParticipant.bytesContactIdentity,
                                    this.callLogItem
                                )
                                AppDatabase.getInstance().messageDao().insert(callMessage)
                                if (discussion.updateLastMessageTimestamp(callMessage.timestamp)) {
                                    AppDatabase.getInstance().discussionDao()
                                        .updateLastMessageTimestamp(
                                            discussion.id,
                                            discussion.lastMessageTimestamp
                                        )
                                }
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    private fun updateLogEntry(newCallParticipants: List<CallParticipant>) {
        if (callLogItem == null || newCallParticipants.isEmpty()) {
            return
        }
        App.runThread {
            val callLogItemContactJoins =
                arrayOfNulls<CallLogItemContactJoin>(newCallParticipants.size)
            for (i in newCallParticipants.indices) {
                callLogItemContactJoins[i] = CallLogItemContactJoin(
                    callLogItem!!.id,
                    bytesOwnedIdentity!!,
                    newCallParticipants[i].bytesContactIdentity
                )
            }
            AppDatabase.getInstance().callLogItemDao().insert(*callLogItemContactJoins)
        }
    }

    private fun requestAudioManagerFocus() {
        audioFocusRequest =
            AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributesCompat.Builder()
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributesCompat.FLAG_AUDIBILITY_ENFORCED)
                        .setUsage(AudioAttributesCompat.USAGE_VOICE_COMMUNICATION)
                        .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange: Int -> Logger.d("☎ Audio focus changed: $focusChange") }
                .build()
        val result = AudioManagerCompat.requestAudioFocus(audioManager!!, audioFocusRequest!!)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Logger.d("☎ Audio focus granted")
        } else {
            Logger.e("☎ Audio focus denied")
        }
        savedAudioManagerMode = audioManager!!.mode
        audioManager!!.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun showOngoingForeground() {
        if (callParticipants.isEmpty()) {
            stopForeground(true)
            return
        }
        val endCallIntent = Intent(this, WebrtcCallService::class.java)
        endCallIntent.setAction(ACTION_HANG_UP)
        endCallIntent.putExtra(CALL_IDENTIFIER_INTENT_EXTRA, Logger.getUuidString(callIdentifier))
        val endCallPendingIntent = PendingIntent.getService(
            this,
            0,
            endCallIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val callActivityIntent = Intent(this, WebrtcCallActivity::class.java)
        val callActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            callActivityIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val initialView = InitialView(App.getContext())
        var notificationName: String? = null
        if (callParticipants.size > 1 && bytesGroupOwnerAndUidOrIdentifier != null) {
            when (discussionType) {
                Discussion.TYPE_GROUP -> {
                    val group = AppDatabase.getInstance()
                        .groupDao()[bytesOwnedIdentity, bytesGroupOwnerAndUidOrIdentifier]
                    group?.getCustomPhotoUrl()?.let {
                        initialView.setPhotoUrl(
                            bytesGroupOwnerAndUidOrIdentifier,
                            it
                        )
                        notificationName = getString(
                            string.text_count_contacts_from_group,
                            callParticipants.size,
                            group.getCustomName()
                        )
                    }
                }

                Discussion.TYPE_GROUP_V2 -> {
                    val group = AppDatabase.getInstance()
                        .group2Dao()[bytesOwnedIdentity, bytesGroupOwnerAndUidOrIdentifier]
                    group?.getCustomPhotoUrl()?.let {
                        initialView.setPhotoUrl(
                            bytesGroupOwnerAndUidOrIdentifier,
                            it
                        )
                        notificationName = getString(
                            string.text_count_contacts_from_group,
                            callParticipants.size,
                            group.getCustomName()
                        ) // this group has members, so no need to check if getCustomName() returns ""
                    }
                }
            }
            if (notificationName == null) {
                initialView.setGroup(bytesGroupOwnerAndUidOrIdentifier)
                notificationName = getString(string.text_count_contacts, callParticipants.size)
            }
        } else {
            val callParticipant = callParticipants.values.iterator().next()
            notificationName = if (callParticipant.contact != null) {
                initialView.setContact(callParticipant.contact)
                callParticipant.contact.getCustomDisplayName()
            } else {
                initialView.setInitial(
                    callParticipant.bytesContactIdentity,
                    StringUtils.getInitial(callParticipant.displayName)
                )
                callParticipant.displayName
            }
        }
        val size = App.getContext().resources.getDimensionPixelSize(dimen.notification_icon_size)
        initialView.setSize(size, size)
        val largeIcon = Bitmap.createBitmap(size, size, ARGB_8888)
        initialView.drawOnCanvas(Canvas(largeIcon))
        if (VERSION.SDK_INT >= VERSION_CODES.S) {
            val caller = Person.Builder()
                .setName(notificationName)
                .setIcon(Icon.createWithBitmap(largeIcon))
                .setImportant(false)
                .build()
            val builder = Notification.Builder(
                this,
                AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID
            )
                .setStyle(CallStyle.forOngoingCall(caller, endCallPendingIntent))
                .setSmallIcon(drawable.ic_phone_animated)
                .setOngoing(true)
                .setGroup("silent")
                .setGroupSummary(false)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                .setCategory(Notification.CATEGORY_CALL)
                .setContentIntent(callActivityPendingIntent)
            try {
                if (VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        SERVICE_ID,
                        builder.build(),
                        if (screenShareActive)
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        else
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(SERVICE_ID, builder.build())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val builder = NotificationCompat.Builder(
                this,
                AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID
            )
            builder.setContentTitle(getString(string.notification_title_webrtc_call))
                .setContentText(notificationName)
                .setSmallIcon(drawable.ic_phone_animated)
                .setSilent(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setLargeIcon(largeIcon)
                .setContentIntent(callActivityPendingIntent)
            builder.addAction(
                drawable.ic_end_call,
                getString(string.notification_action_end_call),
                endCallPendingIntent
            )
            try {
                startForeground(SERVICE_ID, builder.build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showIncomingCallForeground(contact: Contact?, participantCount: Int) {
        val rejectCallIntent = Intent(this, WebrtcCallService::class.java)
        rejectCallIntent.setAction(ACTION_REJECT_CALL)
        rejectCallIntent.putExtra(
            CALL_IDENTIFIER_INTENT_EXTRA,
            Logger.getUuidString(callIdentifier)
        )
        val rejectCallPendingIntent = PendingIntent.getService(
            this,
            0,
            rejectCallIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val answerCallIntent = Intent(this, WebrtcCallActivity::class.java)
        answerCallIntent.setAction(WebrtcCallActivity.ANSWER_CALL_ACTION)
        answerCallIntent.putExtra(
            WebrtcCallActivity.ANSWER_CALL_EXTRA_CALL_IDENTIFIER,
            Logger.getUuidString(callIdentifier)
        )
        answerCallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val answerCallPendingIntent = PendingIntent.getActivity(
            this,
            0,
            answerCallIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenIntent = Intent(this, WebrtcIncomingCallActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val initialView = InitialView(App.getContext())
        val size = App.getContext().resources.getDimensionPixelSize(dimen.notification_icon_size)
        initialView.setSize(size, size)
        initialView.setContact(contact!!)
        val largeIcon = Bitmap.createBitmap(size, size, ARGB_8888)
        initialView.drawOnCanvas(Canvas(largeIcon))
        if (VERSION.SDK_INT >= VERSION_CODES.S) {
            val caller = Person.Builder()
                .setName(contact.getCustomDisplayName())
                .setIcon(Icon.createWithBitmap(largeIcon))
                .setImportant(true)
                .build()
            val callStyle = CallStyle
                .forIncomingCall(caller, rejectCallPendingIntent, answerCallPendingIntent)
                .setIsVideo(false)
            if (participantCount > 1) {
                callStyle.setVerificationText(
                    resources.getQuantityString(
                        plurals.text_and_x_other,
                        participantCount - 1,
                        participantCount - 1
                    )
                )
            }
            val publicBuilder = Notification.Builder(
                this,
                AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID
            )
                .setSmallIcon(drawable.ic_phone_animated)
                .setContentTitle(getString(string.notification_public_title_incoming_webrtc_call))
            val builder = Notification.Builder(
                this,
                AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID
            )
                .setPublicVersion(publicBuilder.build())
                .setSmallIcon(drawable.ic_phone_animated)
                .setStyle(callStyle)
                .addPerson(caller)
                .setContentIntent(fullScreenPendingIntent)
                .setDeleteIntent(rejectCallPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
            try {
                if (VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        SERVICE_ID,
                        builder.build(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(SERVICE_ID, builder.build())
                }
            } catch (e: Exception) {
                e.printStackTrace()

                // failed to start foreground service --> only show a notification and hope for the best!
                // we make it dismissible
                builder.setOngoing(false)
                val notificationManager = NotificationManagerCompat.from(this)
                notificationManager.notify(NOT_FOREGROUND_NOTIFICATION_ID, builder.build())
            }
        } else {
            val publicBuilder = NotificationCompat.Builder(
                this,
                AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID
            )
                .setSmallIcon(drawable.ic_phone_animated)
                .setContentTitle(getString(string.notification_public_title_incoming_webrtc_call))
            val builder = NotificationCompat.Builder(
                this,
                AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID
            )
            builder.setContentTitle(
                getString(
                    string.notification_title_incoming_webrtc_call,
                    contact.getCustomDisplayName()
                )
            )
                .setPublicVersion(publicBuilder.build())
                .setSmallIcon(drawable.ic_phone_animated)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(fullScreenPendingIntent)
                .setDeleteIntent(rejectCallPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
            if (participantCount > 1) {
                builder.setContentText(
                    resources.getQuantityString(
                        plurals.text_and_x_other,
                        participantCount - 1,
                        participantCount - 1
                    )
                )
            }
            builder.setLargeIcon(largeIcon)
            val redReject = SpannableString(getString(string.notification_action_reject))
            redReject.setSpan(
                ForegroundColorSpan(resources.getColor(color.red)),
                0,
                redReject.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.addAction(drawable.ic_end_call, redReject, rejectCallPendingIntent)
            val greenAccept = SpannableString(getString(string.notification_action_accept))
            greenAccept.setSpan(
                ForegroundColorSpan(resources.getColor(color.green)),
                0,
                greenAccept.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.addAction(drawable.ic_answer_call, greenAccept, answerCallPendingIntent)
            try {
                startForeground(SERVICE_ID, builder.build())
            } catch (e: Exception) {
                e.printStackTrace()

                // failed to start foreground service --> only show a notification and hope for the best!
                // we make it dismissible
                builder.setOngoing(false)
                val notificationManager = NotificationManagerCompat.from(this)
                notificationManager.notify(NOT_FOREGROUND_NOTIFICATION_ID, builder.build())
            }
        }
    }

    @Throws(IOException::class)
    private fun gzip(sdp: String): ByteArray {
        val baos = ByteArrayOutputStream()
        val deflater = DeflaterOutputStream(baos, Deflater(5, true))
        deflater.write(sdp.toByteArray(StandardCharsets.UTF_8))
        deflater.close()
        val gzipped = baos.toByteArray()
        baos.close()
        return gzipped
    }

    @Throws(IOException::class)
    private fun gunzip(gzipped: ByteArray): String {
        ByteArrayInputStream(gzipped).use { bais ->
            InflaterInputStream(bais, Inflater(true)).use { inflater ->
                ByteArrayOutputStream().use { baos ->
                    val buffer = ByteArray(8192)
                    var c: Int
                    while (inflater.read(buffer).also { c = it } != -1) {
                        baos.write(buffer, 0, c)
                    }
                    return String(baos.toByteArray(), StandardCharsets.UTF_8)
                }
            }
        }
    }

    // endregion
// region Service lifecycle
    override fun onCreate() {
        super.onCreate()
        webrtcMessageReceivedBroadcastReceiver = WebrtcMessageReceivedBroadcastReceiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            webrtcMessageReceivedBroadcastReceiver!!, IntentFilter(ACTION_MESSAGE)
        )
        engineTurnCredentialsReceiver = EngineTurnCredentialsReceiver()
        AppSingleton.getEngine().addNotificationListener(
            EngineNotifications.TURN_CREDENTIALS_RECEIVED,
            engineTurnCredentialsReceiver
        )
        AppSingleton.getEngine().addNotificationListener(
            EngineNotifications.TURN_CREDENTIALS_FAILED,
            engineTurnCredentialsReceiver
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
        if (callLogItem != null && callLogItem!!.callStatus == CallLogItem.STATUS_SUCCESSFUL && callDuration.value != null) {
            callLogItem!!.duration = callDuration.value!!
            App.runThread { AppDatabase.getInstance().callLogItemDao().update(callLogItem) }
        }
        if (outgoingCallRinger != null) {
            outgoingCallRinger!!.stop()
        }
        if (incomingCallRinger != null) {
            incomingCallRinger!!.stop()
        }
        if (soundPool != null) {
            soundPool!!.release()
        }
        if (videoCapturer != null) {
            try {
                videoCapturer!!.stopCapture()
            } catch (ignored: InterruptedException) {
            }
            videoCapturer!!.dispose()
            videoCapturer = null
        }
        if (screenCapturerAndroid != null) {
            try {
                screenCapturerAndroid!!.stopCapture()
            } catch (ignored: InterruptedException) {
            }
            screenCapturerAndroid!!.dispose()
            screenCapturerAndroid = null
        }
        unregisterScreenOffReceiver()
        unregisterWiredHeadsetReceiver()
        timeoutTimer.cancel()
        releaseWakeLocks(ALL)
        for (callParticipant in callParticipants.values) {
            callParticipant.peerConnectionHolder.cleanUp()
        }
        callParticipantIndexes.clear()
        callParticipants.clear()
        WebrtcPeerConnectionHolder.globalCleanup()
        if (webrtcMessageReceivedBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(
                webrtcMessageReceivedBroadcastReceiver!!
            )
            webrtcMessageReceivedBroadcastReceiver = null
        }
        if (audioManager != null && audioFocusRequest != null) {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager!!, audioFocusRequest!!)
            audioManager!!.isSpeakerphoneOn = false
            audioManager!!.mode = savedAudioManagerMode
        }
        if (engineTurnCredentialsReceiver != null) {
            AppSingleton.getEngine().removeNotificationListener(
                EngineNotifications.TURN_CREDENTIALS_RECEIVED,
                engineTurnCredentialsReceiver
            )
            AppSingleton.getEngine().removeNotificationListener(
                EngineNotifications.TURN_CREDENTIALS_FAILED,
                engineTurnCredentialsReceiver
            )
        }
        if (phoneCallStateListener != null) {
            phoneCallStateListener!!.unregister()
        }
        if (callDurationTimer != null) {
            callDurationTimer!!.cancel()
        }
        if (bluetoothHeadsetManager != null) {
            bluetoothHeadsetManager!!.disconnectAudio()
            bluetoothHeadsetManager!!.stop()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return webrtcCallServiceBinder
    }

    // endregion

    // region Listeners

    inner class WebrtcCallServiceBinder : Binder() {
        val service: WebrtcCallService
            get() = this@WebrtcCallService
    }

    private inner class WebrtcMessageReceivedBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == null || ACTION_MESSAGE != intent.action) {
                return
            }
            handleMessageIntent(intent)
        }
    }

    private inner class ScreenOffReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            incomingCallRinger!!.stop()
        }
    }

    private inner class WiredHeadsetReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == null || AudioManager.ACTION_HEADSET_PLUG != intent.action) {
                return
            }
            wiredHeadsetConnected = 1 == intent.getIntExtra("state", 0)
            updateAvailableAudioOutputsList()
        }
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiver == null) {
            screenOffReceiver = ScreenOffReceiver()
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
    }

    private fun unregisterScreenOffReceiver() {
        if (screenOffReceiver != null) {
            unregisterReceiver(screenOffReceiver)
            screenOffReceiver = null
        }
    }

    private fun registerWiredHeadsetReceiver() {
        registerReceiver(wiredHeadsetReceiver, IntentFilter(AudioManager.ACTION_HEADSET_PLUG))
    }

    private fun unregisterWiredHeadsetReceiver() {
        unregisterReceiver(wiredHeadsetReceiver)
    }

    private inner class EngineTurnCredentialsReceiver : EngineNotificationListener {
        private var registrationNumber: Long? = null
        override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
            when (notificationName) {
                EngineNotifications.TURN_CREDENTIALS_RECEIVED -> {

//                    byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.TURN_CREDENTIALS_RECEIVED_OWNED_IDENTITY_KEY);
                    val callUuid =
                        userInfo[EngineNotifications.TURN_CREDENTIALS_RECEIVED_CALL_UUID_KEY] as UUID?
                    // ignore notifications from another call...
                    if (callIdentifier == callUuid) {
                        val callerUsername =
                            userInfo[EngineNotifications.TURN_CREDENTIALS_RECEIVED_USERNAME_1_KEY] as String?
                        val callerPassword =
                            userInfo[EngineNotifications.TURN_CREDENTIALS_RECEIVED_PASSWORD_1_KEY] as String?
                        val recipientUsername =
                            userInfo[EngineNotifications.TURN_CREDENTIALS_RECEIVED_USERNAME_2_KEY] as String?
                        val recipientPassword =
                            userInfo[EngineNotifications.TURN_CREDENTIALS_RECEIVED_PASSWORD_2_KEY] as String?
                        val turnServers =
                            userInfo[EngineNotifications.TURN_CREDENTIALS_RECEIVED_SERVERS_KEY] as List<String>?
                        if (callerUsername == null || callerPassword == null || recipientUsername == null || recipientPassword == null || turnServers == null) {
                            callerFailedTurnCredentials(UNABLE_TO_CONTACT_SERVER)
                        } else {
                            callerSetTurnCredentialsAndInitializeCall(
                                callerUsername,
                                callerPassword,
                                recipientUsername,
                                recipientPassword,
                                turnServers
                            )
                            Logger.d("☎ Caching received turn credentials for reuse")
                            val callCredentialsCacheSharedPreference =
                                App.getContext().getSharedPreferences(
                                    App.getContext().getString(
                                        string.preference_filename_call_credentials_cache
                                    ), MODE_PRIVATE
                                )
                            val editor = callCredentialsCacheSharedPreference.edit()
                            editor.clear()
                            editor.putLong(PREF_KEY_TIMESTAMP, System.currentTimeMillis())
                            editor.putString(PREF_KEY_USERNAME1, callerUsername)
                            editor.putString(PREF_KEY_PASSWORD1, callerPassword)
                            editor.putString(PREF_KEY_USERNAME2, recipientUsername)
                            editor.putString(PREF_KEY_PASSWORD2, recipientPassword)
                            editor.putStringSet(PREF_KEY_TURN_SERVERS, HashSet(turnServers))
                            editor.apply()
                        }
                    }
                }

                EngineNotifications.TURN_CREDENTIALS_FAILED -> {

//                    byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.TURN_CREDENTIALS_FAILED_OWNED_IDENTITY_KEY);
                    val callUuid =
                        userInfo[EngineNotifications.TURN_CREDENTIALS_FAILED_CALL_UUID_KEY] as UUID?
                    // ignore notifications from another call...
                    if (callIdentifier == callUuid) {
                        val rfc =
                            userInfo[EngineNotifications.TURN_CREDENTIALS_FAILED_REASON_KEY] as ObvTurnCredentialsFailedReason?
                        callerFailedTurnCredentials(rfc)
                    }
                }
            }
        }

        override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
            this.registrationNumber = registrationNumber
        }

        override fun getEngineNotificationListenerRegistrationNumber(): Long {
            return if (registrationNumber == null) 0 else registrationNumber!!
        }

        override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
            return registrationNumber != null
        }
    }

    @SuppressLint("WakelockTimeout")
    fun acquireWakeLock(wakeLock: WakeLock?) {
        var lockProximity = false
        var lockWifi = false
        when (wakeLock) {
            ALL -> {
                lockProximity = true
                lockWifi = true
            }

            WIFI -> lockWifi = true
            PROXIMITY -> lockProximity = true
            else -> {}
        }
        if (lockProximity) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager?
            proximityLock =
                if (powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
                    powerManager.newWakeLock(
                        PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                        "io.olvid:proximity_lock"
                    )?.apply { acquire() }
                } else {
                    null
                }
        }
        if (lockWifi) {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager?
            wifiLock = wifiManager?.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "io.olvid:wifi_lock"
            )?.apply { acquire() }
        }
    }

    fun releaseWakeLocks(wakeLock: WakeLock?) {
        var unlockProximity = false
        var unlockWifi = false
        when (wakeLock) {
            ALL -> {
                unlockProximity = true
                unlockWifi = true
            }

            WIFI -> unlockWifi = true
            PROXIMITY -> unlockProximity = true
            else -> {}
        }
        if (unlockProximity && proximityLock != null) {
            proximityLock!!.release()
            proximityLock = null
        }
        if (unlockWifi && wifiLock != null) {
            wifiLock!!.release()
            wifiLock = null
        }
    }

    // endregion
// region Send messages via Oblivious channel
    @Throws(IOException::class)
    fun sendStartCallMessage(
        callParticipant: CallParticipant,
        sessionDescriptionType: String?,
        sessionDescription: String,
        turnUserName: String?,
        turnPassword: String?,
        turnServers: List<String>?
    ): Boolean {
        var startCallMessage: JsonStartCallMessage? = null
        when (discussionType) {
            Discussion.TYPE_GROUP -> {
                if (AppDatabase.getInstance().contactGroupJoinDao().isGroupMember(
                        bytesOwnedIdentity,
                        callParticipant.bytesContactIdentity,
                        bytesGroupOwnerAndUidOrIdentifier
                    )
                ) {
                    startCallMessage = JsonStartCallMessage(
                        sessionDescriptionType,
                        gzip(sessionDescription),
                        turnUserName,
                        turnPassword,
                        turnServers,
                        callParticipants.size,
                        bytesGroupOwnerAndUidOrIdentifier,
                        false,
                        callParticipant.gatheringPolicy
                    )
                }
            }

            Discussion.TYPE_GROUP_V2 -> {
                if (AppDatabase.getInstance().group2MemberDao().isGroupMember(
                        bytesOwnedIdentity,
                        bytesGroupOwnerAndUidOrIdentifier,
                        callParticipant.bytesContactIdentity
                    )
                ) {
                    startCallMessage = JsonStartCallMessage(
                        sessionDescriptionType,
                        gzip(sessionDescription),
                        turnUserName,
                        turnPassword,
                        turnServers,
                        callParticipants.size,
                        bytesGroupOwnerAndUidOrIdentifier,
                        true,
                        callParticipant.gatheringPolicy
                    )
                }
            }
        }
        if (startCallMessage == null) {
            startCallMessage = JsonStartCallMessage(
                sessionDescriptionType,
                gzip(sessionDescription),
                turnUserName,
                turnPassword,
                turnServers,
                callParticipants.size,
                null,
                false,
                callParticipant.gatheringPolicy
            )
        }
        return postMessage(listOf(callParticipant), startCallMessage)
    }

    fun sendAddIceCandidateMessage(
        callParticipant: CallParticipant,
        jsonIceCandidate: JsonIceCandidate
    ) {
        executor.execute {
            if (!callParticipantIndexes.containsKey(BytesKey(callParticipant.bytesContactIdentity))) {
                return@execute
            }
            try {
                val jsonNewIceCandidateMessage = JsonNewIceCandidateMessage(
                    jsonIceCandidate.sdp,
                    jsonIceCandidate.sdpMLineIndex,
                    jsonIceCandidate.sdpMid
                )
                Logger.d(
                    """☎ sending peer an ice candidate for call ${
                        Logger.getUuidString(
                            callIdentifier
                        )
                    }
${jsonIceCandidate.sdpMLineIndex} -> ${jsonIceCandidate.sdp}"""
                )
                if (callParticipant.contact != null && callParticipant.contact.establishedChannelCount > 0) {
                    postMessage(listOf(callParticipant), jsonNewIceCandidateMessage)
                } else {
                    val caller = callerCallParticipant
                    if (caller != null) {
                        sendDataChannelMessage(
                            caller,
                            JsonRelayInnerMessage(
                                callParticipant.bytesContactIdentity,
                                jsonNewIceCandidateMessage.messageType,
                                objectMapper.writeValueAsString(jsonNewIceCandidateMessage)
                            )
                        )
                    }
                }
            } catch (ignored: IOException) {
                // failed to serialize inner message
            }
        }
    }

    fun sendRemoveIceCandidatesMessage(
        callParticipant: CallParticipant,
        jsonIceCandidates: Array<JsonIceCandidate?>?
    ) {
        executor.execute {
            if (!callParticipantIndexes.containsKey(BytesKey(callParticipant.bytesContactIdentity))) {
                return@execute
            }
            try {
                val jsonRemoveIceCandidatesMessage =
                    JsonRemoveIceCandidatesMessage(jsonIceCandidates)
                if (callParticipant.contact != null && callParticipant.contact.establishedChannelCount > 0) {
                    postMessage(listOf(callParticipant), jsonRemoveIceCandidatesMessage)
                } else {
                    val caller = callerCallParticipant
                    if (caller != null) {
                        sendDataChannelMessage(
                            caller,
                            JsonRelayInnerMessage(
                                callParticipant.bytesContactIdentity,
                                jsonRemoveIceCandidatesMessage.messageType,
                                objectMapper.writeValueAsString(jsonRemoveIceCandidatesMessage)
                            )
                        )
                    }
                }
            } catch (ignored: IOException) {
                // failed to serialize inner message
            }
        }
    }

    @Throws(IOException::class)
    fun sendAnswerCallMessage(
        callParticipant: CallParticipant,
        sessionDescriptionType: String?,
        sessionDescription: String
    ) {
        val answerCallMessage =
            JsonAnswerCallMessage(sessionDescriptionType, gzip(sessionDescription))
        postMessage(listOf(callParticipant), answerCallMessage)
        if (AppDatabase.getInstance().ownedDeviceDao()
                .doesOwnedIdentityHaveAnotherDeviceWithChannel(bytesOwnedIdentity)
        ) {
            postMessage(
                JsonAnsweredOrRejectedOnOtherDeviceMessage(true),
                bytesOwnedIdentity,
                listOf(bytesOwnedIdentity),
                callIdentifier
            )
        }
    }

    private fun sendRingingMessage(callParticipant: CallParticipant) {
        postMessage(listOf(callParticipant), JsonRingingMessage())
    }

    private fun sendRejectCallMessage(callParticipant: CallParticipant) {
        postMessage(listOf(callParticipant), JsonRejectCallMessage())
        if (AppDatabase.getInstance().ownedDeviceDao()
                .doesOwnedIdentityHaveAnotherDeviceWithChannel(bytesOwnedIdentity)
        ) {
            postMessage(
                JsonAnsweredOrRejectedOnOtherDeviceMessage(false),
                bytesOwnedIdentity,
                listOf(bytesOwnedIdentity),
                callIdentifier
            )
        }
    }

    private fun sendBusyMessage(
        bytesOwnedIdentity: ByteArray?,
        bytesContactIdentity: ByteArray?,
        callIdentifier: UUID?,
        bytesGroupOwnerAndUid: ByteArray?
    ) {
        App.runThread {
            val callLogItem = CallLogItem(
                bytesOwnedIdentity!!,
                bytesGroupOwnerAndUid,
                CallLogItem.TYPE_INCOMING,
                CallLogItem.STATUS_BUSY
            )
            callLogItem.id = AppDatabase.getInstance().callLogItemDao().insert(callLogItem)
            val callLogItemContactJoin =
                CallLogItemContactJoin(callLogItem.id, bytesOwnedIdentity, bytesContactIdentity!!)
            AppDatabase.getInstance().callLogItemDao().insert(callLogItemContactJoin)
            AndroidNotificationManager.displayMissedCallNotification(
                bytesOwnedIdentity,
                bytesContactIdentity
            )
            postMessage(
                JsonBusyMessage(),
                bytesOwnedIdentity,
                listOf<ByteArray?>(bytesContactIdentity),
                callIdentifier
            )
            var discussion: Discussion? = null
            if (bytesGroupOwnerAndUid != null) {
                discussion = AppDatabase.getInstance().discussionDao()
                    .getByGroupOwnerAndUidOrIdentifier(bytesOwnedIdentity, bytesGroupOwnerAndUid)
            }
            if (discussion == null) {
                discussion = AppDatabase.getInstance().discussionDao()
                    .getByContact(bytesOwnedIdentity, bytesContactIdentity)
            }
            if (discussion != null) {
                val busyCallMessage = Message.createPhoneCallMessage(
                    AppDatabase.getInstance(),
                    discussion.id,
                    bytesContactIdentity,
                    callLogItem
                )
                AppDatabase.getInstance().messageDao().insert(busyCallMessage)
                if (discussion.updateLastMessageTimestamp(busyCallMessage.timestamp)) {
                    AppDatabase.getInstance().discussionDao()
                        .updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp)
                }
            }
        }
    }

    @Throws(IOException::class)
    fun sendReconnectCallMessage(
        callParticipant: CallParticipant,
        sessionDescriptionType: String?,
        sessionDescription: String,
        reconnectCounter: Int,
        peerReconnectCounterToOverride: Int
    ) {
        val reconnectCallMessage = JsonReconnectCallMessage(
            sessionDescriptionType,
            gzip(sessionDescription),
            reconnectCounter,
            peerReconnectCounterToOverride
        )
        if (callParticipant.contact != null && callParticipant.contact.establishedChannelCount > 0) {
            postMessage(listOf(callParticipant), reconnectCallMessage)
        } else {
            val caller = callerCallParticipant
            if (caller != null) {
                sendDataChannelMessage(
                    caller,
                    JsonRelayInnerMessage(
                        callParticipant.bytesContactIdentity,
                        reconnectCallMessage.messageType,
                        objectMapper.writeValueAsString(reconnectCallMessage)
                    )
                )
            }
        }
    }

    private fun sendHangedUpMessage(callParticipants: Collection<CallParticipant>) {
        val callParticipantsWithContact: MutableList<CallParticipant> =
            ArrayList(callParticipants.size)
        val jsonHangedUpMessage = JsonHangedUpMessage()
        for (callParticipant in callParticipants) {
            sendDataChannelMessage(callParticipant, JsonHangedUpInnerMessage())
            if (callParticipant.contact == null || callParticipant.contact.establishedChannelCount == 0) {
                try {
                    val caller = callerCallParticipant
                    if (caller != null) {
                        sendDataChannelMessage(
                            caller,
                            JsonRelayInnerMessage(
                                callParticipant.bytesContactIdentity,
                                jsonHangedUpMessage.messageType,
                                objectMapper.writeValueAsString(jsonHangedUpMessage)
                            )
                        )
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } else {
                callParticipantsWithContact.add(callParticipant)
            }
        }
        postMessage(callParticipantsWithContact, jsonHangedUpMessage)
    }

    private fun sendKickMessage(callParticipant: CallParticipant) {
        if (!isCaller) {
            return
        }
        postMessage(listOf(callParticipant), JsonKickMessage())
    }

    @Throws(IOException::class)
    fun sendNewParticipantOfferMessage(
        callParticipant: CallParticipant,
        sessionDescriptionType: String?,
        sessionDescription: String
    ) {
        val newParticipantOfferMessage = JsonNewParticipantOfferMessage(
            sessionDescriptionType,
            gzip(sessionDescription),
            callParticipant.gatheringPolicy
        )
        if (callParticipant.contact != null && callParticipant.contact.establishedChannelCount > 0) {
            postMessage(listOf(callParticipant), newParticipantOfferMessage)
        } else {
            val caller = callerCallParticipant
            if (caller != null) {
                sendDataChannelMessage(
                    caller,
                    JsonRelayInnerMessage(
                        callParticipant.bytesContactIdentity,
                        newParticipantOfferMessage.messageType,
                        objectMapper.writeValueAsString(newParticipantOfferMessage)
                    )
                )
            }
        }
    }

    @Throws(IOException::class)
    fun sendNewParticipantAnswerMessage(
        callParticipant: CallParticipant,
        sessionDescriptionType: String?,
        sessionDescription: String
    ) {
        val newParticipantAnswerMessage =
            JsonNewParticipantAnswerMessage(sessionDescriptionType, gzip(sessionDescription))
        if (callParticipant.contact != null && callParticipant.contact.establishedChannelCount > 0) {
            postMessage(listOf(callParticipant), newParticipantAnswerMessage)
        } else {
            val caller = callerCallParticipant
            if (caller != null) {
                sendDataChannelMessage(
                    caller,
                    JsonRelayInnerMessage(
                        callParticipant.bytesContactIdentity,
                        newParticipantAnswerMessage.messageType,
                        objectMapper.writeValueAsString(newParticipantAnswerMessage)
                    )
                )
            }
        }
    }

    private fun postMessage(
        callParticipants: Collection<CallParticipant>,
        protocolMessage: JsonWebrtcProtocolMessage
    ): Boolean {
        val bytesContactIdentities: MutableList<ByteArray?> = ArrayList(callParticipants.size)
        for (callParticipant in callParticipants) {
            if (callParticipant.contact != null && callParticipant.contact.establishedChannelCount > 0) {
                bytesContactIdentities.add(callParticipant.bytesContactIdentity)
            }
        }
        return if (bytesContactIdentities.size > 0) {
            postMessage(protocolMessage, bytesOwnedIdentity, bytesContactIdentities, callIdentifier)
        } else false
    }

    private fun postMessage(
        protocolMessage: JsonWebrtcProtocolMessage,
        bytesOwnedIdentity: ByteArray?,
        bytesContactIdentities: List<ByteArray?>,
        callIdentifier: UUID?
    ): Boolean {
        return try {
            val jsonWebrtcMessage = JsonWebrtcMessage()
            jsonWebrtcMessage.messageType = protocolMessage.messageType
            jsonWebrtcMessage.callIdentifier = callIdentifier
            jsonWebrtcMessage.serializedMessagePayload =
                objectMapper.writeValueAsString(protocolMessage)
            val jsonPayload = JsonPayload()
            jsonPayload.jsonWebrtcMessage = jsonWebrtcMessage

            // only mark START_CALL_MESSAGE_TYPE messages as voip
            val tagAsVoipMessage = protocolMessage.messageType == START_CALL_MESSAGE_TYPE
            val messagePayload = AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload)
            val obvPostMessageOutput = AppSingleton.getEngine().post(
                messagePayload,
                null,
                arrayOfNulls(0),
                bytesContactIdentities,
                bytesOwnedIdentity,
                tagAsVoipMessage,
                tagAsVoipMessage
            )
            // do not use the foreground service for this
            obvPostMessageOutput.isMessagePostedForAtLeastOneContact
        } catch (e: JsonProcessingException) {
            e.printStackTrace()
            failReason = INTERNAL_ERROR
            setState(FAILED)
            false
        }
    }

    // endregion
// region JsonDataChannelMessages
    private inner class DataChannelListener(private val callParticipant: CallParticipant) :
        DataChannelMessageListener {
        override fun onConnect() {
            executor.execute {
                sendDataChannelMessage(callParticipant, JsonMutedInnerMessage(microphoneMuted))
                sendDataChannelMessage(callParticipant, JsonVideoSupportedInnerMessage(true))
                sendDataChannelMessage(
                    callParticipant,
                    JsonVideoSharingInnerMessage(cameraEnabled)
                )
                if (isCaller) {
                    sendDataChannelMessage(
                        callParticipant,
                        JsonUpdateParticipantsInnerMessage(callParticipants.values)
                    )
                }
            }
        }

        override fun onMessage(byteBuffer: ByteBuffer) {
            val bytes = ByteArray(byteBuffer.limit())
            byteBuffer[bytes]
            try {
                val jsonDataChannelMessage =
                    objectMapper.readValue(bytes, JsonDataChannelMessage::class.java)
                when (jsonDataChannelMessage.messageType) {
                    MUTED_DATA_MESSAGE_TYPE -> {
                        val jsonMutedInnerMessage = objectMapper.readValue(
                            jsonDataChannelMessage.serializedMessage,
                            JsonMutedInnerMessage::class.java
                        )
                        executor.execute { callParticipant.setPeerIsMuted(jsonMutedInnerMessage.isMuted) }
                    }

                    VIDEO_SHARING_DATA_MESSAGE_TYPE -> {
                        val jsonVideoSharingInnerMessage = objectMapper.readValue(
                            jsonDataChannelMessage.serializedMessage,
                            JsonVideoSharingInnerMessage::class.java
                        )
                        executor.execute {
                            callParticipant.setPeerVideoSharing(
                                jsonVideoSharingInnerMessage.isVideoSharing
                            )
                        }
                    }

                    SCREEN_SHARING_DATA_MESSAGE_TYPE -> {
                        val jsonScreenSharingInnerMessage = objectMapper.readValue(
                            jsonDataChannelMessage.serializedMessage,
                            JsonScreenSharingInnerMessage::class.java
                        )
                        executor.execute {
                            callParticipant.setPeerScreenSharing(
                                jsonScreenSharingInnerMessage.isScreenSharing
                            )
                        }
                    }

                    VIDEO_SUPPORTED_DATA_MESSAGE_TYPE -> {
                        val jsonVideoSupportedInnerMessage = objectMapper.readValue(
                            jsonDataChannelMessage.serializedMessage,
                            JsonVideoSupportedInnerMessage::class.java
                        )
                        if (jsonVideoSupportedInnerMessage.isVideoSupported && shouldISendTheOfferToCallParticipant(callParticipant)) {
                            executor.execute {
                                Logger.d("☎ received video supported message (${jsonVideoSupportedInnerMessage.isVideoSupported})")
                                callParticipant.setPeerVideoIsSupported(
                                    jsonVideoSupportedInnerMessage.isVideoSupported
                                )
                            }
                        }
                    }

                    UPDATE_PARTICIPANTS_DATA_MESSAGE_TYPE -> {
                        if (callParticipant.role != CALLER) {
                            return
                        }
                        val jsonUpdateParticipantsInnerMessage = objectMapper.readValue(
                            jsonDataChannelMessage.serializedMessage,
                            JsonUpdateParticipantsInnerMessage::class.java
                        )
                        handleUpdateCallParticipantsMessage(jsonUpdateParticipantsInnerMessage)
                    }

                    RELAY_DATA_MESSAGE_TYPE -> {
                        if (!isCaller) {
                            return
                        }
                        val jsonRelayInnerMessage = objectMapper.readValue(
                            jsonDataChannelMessage.serializedMessage,
                            JsonRelayInnerMessage::class.java
                        )
                        val bytesContactIdentity = jsonRelayInnerMessage.to
                        val messageType = jsonRelayInnerMessage.relayedMessageType
                        val serializedMessagePayload =
                            jsonRelayInnerMessage.serializedMessagePayload
                        executor.execute {
                            val callParticipant = getCallParticipant(bytesContactIdentity)
                            if (callParticipant != null) {
                                sendDataChannelMessage(
                                    callParticipant,
                                    JsonRelayedInnerMessage(
                                        this.callParticipant.bytesContactIdentity,
                                        messageType,
                                        serializedMessagePayload
                                    )
                                )
                            }
                        }
                    }

                    RELAYED_DATA_MESSAGE_TYPE -> {
                        if (isCaller || callParticipant.role != CALLER) {
                            return
                        }
                        val jsonRelayedInnerMessage = objectMapper.readValue(
                            jsonDataChannelMessage.serializedMessage,
                            JsonRelayedInnerMessage::class.java
                        )
                        val bytesContactIdentity = jsonRelayedInnerMessage.from
                        val messageType = jsonRelayedInnerMessage.relayedMessageType
                        val serializedMessagePayload =
                            jsonRelayedInnerMessage.serializedMessagePayload
                        executor.execute {
                            handleMessage(
                                bytesContactIdentity,
                                messageType,
                                serializedMessagePayload
                            )
                        }
                    }

                    HANGED_UP_DATA_MESSAGE_TYPE -> {
                        handleHangedUpMessage(callParticipant)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // endregion
    inner class CallParticipant {
        internal val role: Role

        @JvmField
        val bytesContactIdentity: ByteArray

        @JvmField
        val contact: Contact?

        // this gathering policy corresponds to what we have in Db. For received multi-calls, this is what the caller sends us.
        // only used when sending an offer, when receiving the offer, we use the value in the offer message
        @JvmField
        val gatheringPolicy: GatheringPolicy
        var displayName: String? = null
        val peerConnectionHolder: WebrtcPeerConnectionHolder
        private val dataChannelMessageListener: DataChannelMessageListener
        var _peerIsMuted: Boolean
        var _peerVideoSharing: Boolean
        var _peerScreenSharing: Boolean
        var _peerVideoIsSupported: Boolean

        @JvmField
        var peerState: PeerState
        private var markedForRemoval: Boolean
        var timeoutTask: TimerTask?

        constructor(
            bytesOwnedIdentity: ByteArray?,
            bytesContactIdentity: ByteArray,
            displayName: String,
            gatheringPolicy: GatheringPolicy
        ) {
            this.role = RECIPIENT
            this.bytesContactIdentity = bytesContactIdentity
            contact =
                AppDatabase.getInstance().contactDao()[bytesOwnedIdentity, bytesContactIdentity]
            if (contact != null) {
                this.displayName = contact.getCustomDisplayName()
            } else {
                this.displayName = displayName
            }
            this.gatheringPolicy = gatheringPolicy
            peerConnectionHolder = WebrtcPeerConnectionHolder(this@WebrtcCallService, this)
            dataChannelMessageListener = DataChannelListener(this)
            peerConnectionHolder.dataChannelMessageListener = dataChannelMessageListener
            _peerIsMuted = false
            _peerVideoSharing = false
            _peerScreenSharing = false
            _peerVideoIsSupported = false
            peerState = PeerState.INITIAL
            markedForRemoval = false
            timeoutTask = null
            addUncalledReceivedIceCandidates()
        }

        constructor(contact: Contact, contactRole: Role) {
            this.role = contactRole
            bytesContactIdentity = contact.bytesContactIdentity
            this.contact = contact
            gatheringPolicy =
                if (contact.capabilityWebrtcContinuousIce) GATHER_CONTINUOUSLY else GATHER_ONCE
            displayName = contact.getCustomDisplayName()
            peerConnectionHolder = WebrtcPeerConnectionHolder(this@WebrtcCallService, this)
            dataChannelMessageListener = DataChannelListener(this)
            peerConnectionHolder.dataChannelMessageListener = dataChannelMessageListener
            _peerIsMuted = false
            _peerVideoSharing = false
            _peerScreenSharing = false
            _peerVideoIsSupported = false
            peerState = PeerState.INITIAL
            markedForRemoval = false
            timeoutTask = null
            addUncalledReceivedIceCandidates()
        }

        private fun addUncalledReceivedIceCandidates() {
            // handle already received ice candidate
            val map = uncalledReceivedIceCandidates[callIdentifier]
            if (map != null) {
                val candidates = map.remove(BytesKey(bytesContactIdentity))
                if (candidates != null) {
                    peerConnectionHolder.addIceCandidates(candidates)
                }
            }
        }

        fun setPeerState(peerState: PeerState) {
            this.peerState = peerState
            notifyCallParticipantsChanged()
            when (peerState) {
                PeerState.INITIAL, CONNECTED, START_CALL_MESSAGE_SENT, PeerState.BUSY, RINGING, RECONNECTING, CONNECTING_TO_PEER -> {}

                CALL_REJECTED, HANGED_UP, KICKED, PeerState.FAILED -> {
                    createRemovePeerTimeout()
                }
            }
        }

        private fun createRemovePeerTimeout() {
            if (timeoutTask != null) {
                timeoutTask!!.cancel()
                timeoutTask = null
            }
            markedForRemoval = true
            timeoutTask = object : TimerTask() {
                override fun run() {
                    executor.execute { internalRemoveCallParticipant(this@CallParticipant) }
                }
            }
            try {
                timeoutTimer.schedule(timeoutTask, PEER_CALL_ENDED_WAIT_MILLIS)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }

        fun setPeerIsMuted(peerIsMuted: Boolean) {
            this._peerIsMuted = peerIsMuted
            notifyCallParticipantsChanged()
        }

        fun setPeerVideoIsSupported(peerVideoIsSupported: Boolean) {
            this._peerVideoIsSupported = peerVideoIsSupported
            notifyCallParticipantsChanged()
            peerConnectionHolder.enableVideoTrack()
            peerConnectionHolder.enableScreenSharingTrack()
        }

        fun setPeerVideoSharing(peerVideoSharing: Boolean) {
            this._peerVideoSharing = peerVideoSharing
            notifyCallParticipantsChanged()
        }

        fun setPeerScreenSharing(peerScreenSharing: Boolean) {
            this._peerScreenSharing = peerScreenSharing
            notifyCallParticipantsChanged()
        }

        override fun hashCode(): Int {
            return Arrays.hashCode(bytesContactIdentity)
        }

        override fun equals(other: Any?): Boolean {
            return if (other !is CallParticipant) {
                false
            } else bytesContactIdentity.contentEquals(other.bytesContactIdentity)
        }
    }

    fun getAudioLevel(bytesIdentity: ByteArray?): Double? {
        if (bytesIdentity == null) return null
        return if (bytesIdentity.contentEquals(bytesOwnedIdentity))
            WebrtcPeerConnectionHolder.localAudioLevel
        else
            getCallParticipant(bytesIdentity)?.peerConnectionHolder?.peerAudioLevel
    }

    class CallParticipantPojo(callParticipant: CallParticipant) : Comparable<CallParticipantPojo> {
        @JvmField
        val bytesContactIdentity: ByteArray

        @JvmField
        val contact: Contact?

        @JvmField
        val displayName: String?

        @JvmField
        val peerIsMuted: Boolean

        @JvmField
        val peerVideoSharing: Boolean

        @JvmField
        val peerScreenSharing: Boolean

        @JvmField
        val peerVideoIsSupported: Boolean

        @JvmField
        val peerState: PeerState

        init {
            bytesContactIdentity = callParticipant.bytesContactIdentity
            contact = callParticipant.contact
            displayName = callParticipant.displayName
            peerIsMuted = callParticipant._peerIsMuted
            peerVideoSharing = callParticipant._peerVideoSharing
            peerScreenSharing = callParticipant._peerScreenSharing
            peerVideoIsSupported = callParticipant._peerVideoIsSupported
            peerState = callParticipant.peerState
        }

        override fun compareTo(other: CallParticipantPojo): Int {
            val myName = contact?.getCustomDisplayName() ?: displayName ?: ""
            val theirName = other.contact?.getCustomDisplayName() ?: other.displayName ?: ""
            return myName.compareTo(theirName)
        }
    }

    // device orientation change listener for screen capture on API < 34
    val orientationChangeBroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context?.resources?.configuration?.let { configuration ->
                if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT && screenWidth > screenHeight
                    || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && screenWidth < screenHeight) {
                    // swap both values
                    val tmp = screenHeight
                    screenHeight = screenWidth
                    screenWidth = tmp
                }
                if (screenShareActive) {
                    screenCapturerAndroid?.changeCaptureFormat(screenWidth, screenHeight, 0)
                }
            }
        }
    }

    private fun registerDeviceOrientationChange() {
        registerReceiver(orientationChangeBroadcastReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
    }

    private fun unregisterDeviceOrientationChange() {
        try {
            unregisterReceiver(orientationChangeBroadcastReceiver)
        } catch (ignored: Exception) { }
    }

    data class CameraAndFormat(
        val cameraId: String,
        val mirror: Boolean,
        val captureFormat: CameraEnumerationAndroid.CaptureFormat
    )

    companion object {
        const val ACTION_START_CALL = "action_start_call"
        const val ACTION_ANSWER_CALL = "action_answer_call"
        const val ACTION_REJECT_CALL = "action_reject_call"
        const val ACTION_HANG_UP = "action_hang_up"
        const val ACTION_MESSAGE = "action_message"
        const val BYTES_OWNED_IDENTITY_INTENT_EXTRA = "bytes_owned_identity"
        const val BYTES_CONTACT_IDENTITY_INTENT_EXTRA = "bytes_contact_identity"
        const val SINGLE_CONTACT_IDENTITY_BUNDLE_KEY = "0"
        const val CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA = "contact_identities_bundle"
        const val BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA = "bytes_group_owner_and_uid"
        const val GROUP_V2_INTENT_EXTRA = "group_v2"
        const val CALL_IDENTIFIER_INTENT_EXTRA = "call_identifier"
        const val MESSAGE_TYPE_INTENT_EXTRA = "message_type"
        const val SERIALIZED_MESSAGE_PAYLOAD_INTENT_EXTRA = "serialized_message_payload"
        private const val CREDENTIALS_TTL: Long = 43200000
        private const val PREF_KEY_TIMESTAMP = "timestamp"
        private const val PREF_KEY_USERNAME1 = "username1"
        private const val PREF_KEY_PASSWORD1 = "password1"
        private const val PREF_KEY_USERNAME2 = "username2"
        private const val PREF_KEY_PASSWORD2 = "password2"
        private const val PREF_KEY_TURN_SERVERS = "turn_servers"
        const val SERVICE_ID = 9001
        const val NOT_FOREGROUND_NOTIFICATION_ID = 9086
        const val START_CALL_MESSAGE_TYPE = 0
        const val ANSWER_CALL_MESSAGE_TYPE = 1
        const val REJECT_CALL_MESSAGE_TYPE = 2
        const val HANGED_UP_MESSAGE_TYPE = 3
        const val RINGING_MESSAGE_TYPE = 4
        const val BUSY_MESSAGE_TYPE = 5
        const val RECONNECT_CALL_MESSAGE_TYPE = 6
        const val NEW_PARTICIPANT_OFFER_MESSAGE_TYPE = 7
        const val NEW_PARTICIPANT_ANSWER_MESSAGE_TYPE = 8
        const val KICK_MESSAGE_TYPE = 9
        const val NEW_ICE_CANDIDATE_MESSAGE_TYPE = 10
        const val REMOVE_ICE_CANDIDATES_MESSAGE_TYPE = 11
        const val ANSWERED_OR_REJECTED_ON_OTHER_DEVICE_MESSAGE_TYPE = 12
        const val MUTED_DATA_MESSAGE_TYPE = 0
        const val UPDATE_PARTICIPANTS_DATA_MESSAGE_TYPE = 1
        const val RELAY_DATA_MESSAGE_TYPE = 2
        const val RELAYED_DATA_MESSAGE_TYPE = 3
        const val HANGED_UP_DATA_MESSAGE_TYPE = 4
        const val VIDEO_SUPPORTED_DATA_MESSAGE_TYPE = 5
        const val VIDEO_SHARING_DATA_MESSAGE_TYPE = 6
        const val SCREEN_SHARING_DATA_MESSAGE_TYPE = 7
        const val CALL_TIMEOUT_MILLIS: Long = 30000
        const val RINGING_TIMEOUT_MILLIS: Long = 60000
        const val CALL_CONNECTION_TIMEOUT_MILLIS: Long = 15000
        const val PEER_CALL_ENDED_WAIT_MILLIS: Long = 3000

        // HashMap containing ICE candidates received while outside a call: callIdentifier -> (bytesContactIdentity -> candidate)
        // with continuous gathering, we may send/receive candidates before actually sending/receiving the startCall message
        private val uncalledReceivedIceCandidates =
            HashMap<UUID?, HashMap<BytesKey, HashSet<JsonIceCandidate>>>()
        private val uncalledAnsweredOrRejectedOnOtherDevice = HashMap<UUID, Boolean>()
    }
}
