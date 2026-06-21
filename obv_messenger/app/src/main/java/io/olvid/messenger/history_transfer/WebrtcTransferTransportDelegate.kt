/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.history_transfer

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor
import io.olvid.engine.engine.types.EngineAPI
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.SimpleEngineNotificationListener
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.jsons.JsonPayload
import io.olvid.messenger.databases.entity.jsons.JsonWebrtcHistoryTransferMessage
import io.olvid.messenger.databases.entity.jsons.JsonWebrtcHistoryTransferMessage.JsonWebrtcHistoryTransferIceCandidate
import io.olvid.messenger.databases.entity.jsons.JsonWebrtcHistoryTransferMessage.JsonWebrtcHistoryTransferSdp
import io.olvid.messenger.history_transfer.types.AttachmentProgressListener
import io.olvid.messenger.history_transfer.types.TransferAbort
import io.olvid.messenger.history_transfer.types.TransferListener
import io.olvid.messenger.history_transfer.types.TransferMessageType
import io.olvid.messenger.history_transfer.types.TransferRole
import io.olvid.messenger.history_transfer.types.TransferTransportDelegate
import io.olvid.messenger.history_transfer.types.TransferTransportLayerState
import io.olvid.messenger.settings.SettingsActivity
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.nio.ByteBuffer
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@OptIn(ExperimentalAtomicApi::class)
class WebRTCTransferTransportDelegate(
    role: TransferRole,
    transferListener: TransferListener,
    objectMapper: ObjectMapper,
    val transferId: String,
    val bytesOwnedIdentity: ByteArray,
    val bytesOtherDeviceUid: ByteArray,
    val context: Context,
) : TransferTransportDelegate(role = role, transferListener = transferListener, objectMapper = objectMapper) {
    private val webRtcExecutor = NoExceptionSingleThreadExecutor("webrtc-history-transfer-executor")
    private val timer = Timer("webrtc-history-transfer-timer")
    private var dataChannelState: TransferTransportLayerState = TransferTransportLayerState.NOT_STARTED
        set(value) {
            if (field != value) {
                field = value
                transferListener.onTransportLayerStateChange(value)
            }
        }
    var sendAttachmentExecutor: ExecutorService = Executors.newFixedThreadPool(2)

    private var aborted = TransferAbort.NONE

    var peerConnectionFactory: PeerConnectionFactory?
    val peerConnectionObserver: PeerConnection.Observer
    val sessionDescriptionObserver: SdpObserver
    private val pendingPeerIceCandidates = mutableListOf<IceCandidate>()
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private val batchingIceCandidates = mutableListOf<IceCandidate>()

    var peerConnection: PeerConnection? = null
    var dataChannel: DataChannel? = null
    val dataChannelObserver: DataChannel.Observer = object : DataChannel.Observer {
        // map from a message id to a pair of:
        // - number of chunks received and
        // - "sparse" ByteArray reconstructed from already received chunks
        val partiallyReceivedMessages = mutableMapOf<Int, Pair<Int, ByteArray>>()
        val partiallyReceivedSha256 = mutableMapOf<BytesKey, PartialSha256>()


        // we cannot rely on this method to be notified of actual buffer fullness. It may be called late, or not get called at all
        override fun onBufferedAmountChange(previousAmount: Long) { }

        override fun onStateChange() {
            if (dataChannel?.state() == DataChannel.State.OPEN) {
                dataChannelState = TransferTransportLayerState.READY
            } else if (dataChannel?.state() == DataChannel.State.CLOSED || dataChannel?.state() == DataChannel.State.CLOSING) {
                dataChannelState = TransferTransportLayerState.CLOSED
            }
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            if (aborted != TransferAbort.NONE) {
                return
            }
            runCatching {
                TransferMessageType.of(buffer.data.get())?.let { messageType ->
                    val byteBuffer = ByteArray(4)
                    when (messageType) {
                        TransferMessageType.ACK -> {
                            // skip 16 useless bytes at the beginning (the ACKs have the same structure as other messages)
                            buffer.data.get(byteBuffer)
                            buffer.data.get(byteBuffer)
                            buffer.data.get(byteBuffer)
                            buffer.data.get(byteBuffer)

                            buffer.data.get(byteBuffer)
                            val id = byteBuffer.toInt32()
                            buffer.data.get(byteBuffer)
                            val chunk = byteBuffer.toInt32()
                            webRtcExecutor.execute {
                                if (!unackedSentMessages.remove((id.toLong() shl 32) + chunk)) {
                                    Logger.w("Received a ack for a data channel message ID ($id-$chunk) we never sent???")
                                }
                            }
                        }

                        else -> {
                            buffer.data.get(byteBuffer)
                            val id = byteBuffer.toInt32()
                            buffer.data.get(byteBuffer)
                            val chunk = byteBuffer.toInt32()
                            buffer.data.get(byteBuffer)
                            val totalChunks = byteBuffer.toInt32()
                            buffer.data.get(byteBuffer)
                            val blockSize = byteBuffer.toInt32()

                            sendAck(id, chunk)

                            val payload = ByteArray(if (chunk == totalChunks - 1) buffer.data.remaining() else blockSize)
                            buffer.data.get(payload)
                            webRtcExecutor.execute {
                                if (totalChunks == 1) {
                                    onMessageUnchunked(messageType = messageType, payload = payload)
                                } else {
                                    val partial = partiallyReceivedMessages[id] ?: Pair(0, ByteArray(blockSize * totalChunks))

                                    payload.copyInto(partial.second, chunk * blockSize)
                                    val updated = Pair(
                                        partial.first + 1,
                                        if (chunk == totalChunks - 1) {
                                            partial.second.sliceArray(0..<(chunk * blockSize) + payload.size)
                                        } else {
                                            partial.second
                                        }
                                    )
                                    if (updated.first == totalChunks) {
                                        partiallyReceivedMessages.remove(id)
                                        onMessageUnchunked(messageType = messageType, payload = updated.second)
                                    } else {
                                        partiallyReceivedMessages[id] = updated
                                    }
                                }
                            }
                        }
                    }
                }
            }.onFailure(action = Logger::x)
        }

        // should always be called from webRtcExecutor thread
        private fun onMessageUnchunked(messageType: TransferMessageType, payload: ByteArray) {
            if (messageType == TransferMessageType.SRC_SHA256) {
                try {
                    val sha256 = payload.sliceArray(0..<32)
                    val sha256Key = BytesKey(sha256)
                    val offset = payload.sliceArray(32..<40).toInt64()

                    val partial = partiallyReceivedSha256[sha256Key] ?: run {
                        val triple = transferListener.onNewAttachment(sha256 = sha256) ?: return
                        return@run PartialSha256(
                            outputStream = triple.first,
                            totalSize = triple.second,
                            attachmentProgressListener = triple.third
                        )
                    }

                    if (offset != partial.currentOffset) {
                        partial.pendingChunks[offset] = payload.sliceArray(40..<payload.size)
                    } else {
                        // we are in the correct order --> append to the output
                        partial.outputStream.write(payload, 40, payload.size - 40)
                        partial.attachmentProgressListener?.bytesTransferred(payload.size.toLong() - 40)
                        partial.currentOffset += payload.size - 40

                        // check if we have any pending chunk ready to be appended
                        while (partial.pendingChunks.contains(partial.currentOffset)) {
                            val chunk = partial.pendingChunks[partial.currentOffset]!!
                            partial.outputStream.write(chunk)
                            partial.pendingChunks.remove(partial.currentOffset)
                            partial.attachmentProgressListener?.bytesTransferred(chunk.size.toLong())
                            partial.currentOffset += chunk.size
                        }
                    }

                    if (partial.currentOffset == partial.totalSize) {
                        // we received and appended the whole file
                        partiallyReceivedSha256.remove(sha256Key)
                        transferListener.onAttachmentComplete(sha256 = sha256)
                    } else {
                        partiallyReceivedSha256[sha256Key] = partial
                    }
                } catch (e: Exception) {
                    Logger.x(e)
                }
            } else {
                transferListener.onJsonMessage(messageType, payload)
            }
        }
    }

    var nextDataChannelMessageId: AtomicInteger = AtomicInteger(0)
    val unackedSentMessages = mutableSetOf<Long>()


    init {
        val initBuilder = PeerConnectionFactory.InitializationOptions.builder(context)
        PeerConnectionFactory.initialize(initBuilder.createInitializationOptions())

//        org.webrtc.Logging.enableLogToDebugOutput(org.webrtc.Logging.Severity.LS_INFO);

        val builder = PeerConnectionFactory.builder()
            .setOptions(
                PeerConnectionFactory
                    .Options().apply {
                        this.networkIgnoreMask = PeerConnectionFactory.Options.ADAPTER_TYPE_LOOPBACK
                    })
        // check if a proxy is required for WebRTC
        try {
            val proxies =
                ProxySelector.getDefault().select(URI.create("https://turn.olvid.io/"))
            for (proxy in proxies) {
                val type = proxy.type()
                if (type == Proxy.Type.HTTP) {
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
        // set a user agent (for the proxy)
        System.getProperty("http.agent")?.let {
            builder.setUserAgent(it)
        }
        peerConnectionFactory = builder.createPeerConnectionFactory()
        peerConnectionObserver = PeerConnectionObserver()
        sessionDescriptionObserver = SessionDescriptionObserver()

        webRtcExecutor.execute {
            var bytesOwnedIdentityWithCallPermission = bytesOwnedIdentity
            val currentOwnedIdentity =
                AppDatabase.getInstance().ownedIdentityDao()[bytesOwnedIdentity]
            val currentIdentityServer =
                AppSingleton.getEngine().getServerOfIdentity(bytesOwnedIdentity)
            if (currentOwnedIdentity == null
                || !currentOwnedIdentity.getApiKeyPermissions()
                    .contains(EngineAPI.ApiKeyPermission.CALL)
            ) {
                // if my current identity can't call, check other identities
                for (ownedIdentity in AppDatabase.getInstance().ownedIdentityDao().allNotHidden) {
                    if (ownedIdentity.bytesOwnedIdentity.contentEquals(bytesOwnedIdentity)) {
                        // skip the current identity
                        continue
                    }
                    if (ownedIdentity.active
                        && ownedIdentity.getApiKeyPermissions()
                            .contains(EngineAPI.ApiKeyPermission.CALL)
                        && AppSingleton.getEngine()
                            .getServerOfIdentity(ownedIdentity.bytesOwnedIdentity) == currentIdentityServer
                    ) {
                        bytesOwnedIdentityWithCallPermission = ownedIdentity.bytesOwnedIdentity
                        break
                    }
                }
            }

            val iceServers = mutableListOf<PeerConnection.IceServer>()
            getTurnCredentialsFromEngine(bytesOwnedIdentityWithCallPermission)?.let { credentials ->
                iceServers.add(
                    PeerConnection.IceServer.builder(credentials.third)
                        .setUsername(credentials.first)
                        .setPassword(credentials.second)
                        .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                        .createIceServer()
                )
            }

            val configuration = PeerConnection.RTCConfiguration(iceServers)
            configuration.iceTransportsType = PeerConnection.IceTransportsType.ALL
            configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            configuration.continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

            peerConnection =
                peerConnectionFactory?.createPeerConnection(configuration, peerConnectionObserver)

            // we wait for an accept message from the TARGET before sending anything
        }
    }


    fun transferAcceptedByTheTargetDevice() {
        // we are the source, create the data channel
        if (role == TransferRole.SOURCE) {
            webRtcExecutor.execute {
                val init = DataChannel.Init()
                init.ordered = true
                init.negotiated = false
                init.id = 1
                dataChannel = peerConnection?.createDataChannel("data-channel-1", init)
                dataChannel?.registerObserver(dataChannelObserver)
                peerConnection?.createOffer(sessionDescriptionObserver, MediaConstraints())

                dataChannelState = TransferTransportLayerState.INITIALIZING
            }
        }
    }

    fun acceptTheTransferAsTargetDevice() {
        // we are the target, switch to the INITIALIZING state
        if (role == TransferRole.DESTINATION) {
            webRtcExecutor.execute {
                dataChannelState = TransferTransportLayerState.INITIALIZING
            }
        }
    }

    override fun abort(userInitiated: Boolean) {
        aborted = if (userInitiated) TransferAbort.USER_ABORT else TransferAbort.DISCONNECT
        sendAttachmentExecutor.shutdownNow()
        cleanup()
    }

    override fun isAborted(): Boolean {
        return aborted != TransferAbort.NONE
    }

    override fun cleanup() {
        webRtcExecutor.execute {
            Logger.d("Unacked message count: ${unackedSentMessages.size}")

            dataChannel?.let {
                dataChannel = null
                it.dispose()
            }
            peerConnection?.let {
                peerConnection = null
                it.dispose()
            }
            peerConnectionFactory?.let {
                peerConnectionFactory = null
                it.dispose()
            }
        }
    }

    fun handleJsonHistoryTransferMessage(jsonWebrtcHistoryTransferMessage: JsonWebrtcHistoryTransferMessage) {
        if (aborted != TransferAbort.NONE) {
            return
        }
        webRtcExecutor.execute {
            jsonWebrtcHistoryTransferMessage.iceCandidates?.forEach {
                Logger.d("\uD83E\uDDF6 ice candidate received: ${it.sdp}")
                val iceCandidate = IceCandidate(it.mid ?: "", it.sdpMLineIndex ?: 0, it.sdp ?: "")
                if (peerConnection?.signalingState() == PeerConnection.SignalingState.STABLE) {
                    peerConnection?.addIceCandidate(iceCandidate)
                } else {
                    pendingPeerIceCandidates.add(iceCandidate)
                }
            }

            jsonWebrtcHistoryTransferMessage.sdp?.let {
                Logger.d("\uD83E\uDDF6 sdp received: ${it.type} - ${it.sdp}")

                if (role == TransferRole.SOURCE && it.type != "answer"
                    || role == TransferRole.DESTINATION && it.type != "offer"
                ) {
                    Logger.e("\uD83E\uDDF6 received the wrong type of SDP, ignoring it. I am the ${role.name}")
                    return@execute
                }

                peerConnection?.setRemoteDescription(
                    sessionDescriptionObserver,
                    SessionDescription(
                        when (it.type) {
                            "offer" -> SessionDescription.Type.OFFER
                            else -> SessionDescription.Type.ANSWER
                        },
                        it.sdp
                    )
                )

                if (role == TransferRole.DESTINATION) {
                    peerConnection?.createAnswer(sessionDescriptionObserver, MediaConstraints())
                } else {
                    dataChannelState = TransferTransportLayerState.CONNECTING
                }
            }
        }
    }

    // should only be called on the executor
    private fun sendIceCandidate(iceCandidate: IceCandidate) {
        if (aborted != TransferAbort.NONE) {
            return
        }
        if (batchingIceCandidates.isEmpty()) {
            // if no candidate was currently being batched, start a new batch and thus a new sendIceCandidatesBatch task, with a delay of 200ms
            timer.schedule(object: TimerTask() {
                override fun run() {
                    webRtcExecutor.execute(::sendIceCandidatesBatch)
                }
            }, 200)
        }
        batchingIceCandidates.add(iceCandidate)
    }

    // should only be called on the executor
    private fun sendIceCandidatesBatch() {
        if (aborted != TransferAbort.NONE) {
            return
        }
        JsonWebrtcHistoryTransferMessage().apply {
            this.transferId = this@WebRTCTransferTransportDelegate.transferId
            this.iceCandidates = batchingIceCandidates.map { iceCandidate ->
                JsonWebrtcHistoryTransferIceCandidate().apply {
                    this.mid = iceCandidate.sdpMid
                    this.sdp = iceCandidate.sdp
                    this.sdpMLineIndex = iceCandidate.sdpMLineIndex
                }
            }
            sendSignalingMessage(this)
        }
        batchingIceCandidates.clear()
    }

    private fun sendSdp(type: String, sdp: String) {
        if (aborted != TransferAbort.NONE) {
            return
        }
        JsonWebrtcHistoryTransferMessage().apply {
            this.transferId = this@WebRTCTransferTransportDelegate.transferId
            this.sdp = JsonWebrtcHistoryTransferSdp().apply {
                this.type = type
                this.sdp = sdp
            }
            sendSignalingMessage(this)
        }
    }

    private fun sendSignalingMessage(jsonWebrtcHistoryTransferMessage: JsonWebrtcHistoryTransferMessage) {
        val jsonPayload = JsonPayload()
        jsonPayload.jsonWebrtcHistoryTransferMessage = jsonWebrtcHistoryTransferMessage

        val messagePayload = objectMapper.writeValueAsBytes(jsonPayload)

        AppSingleton.getEngine().postToSpecificDevices(
            messagePayload,
            listOf(bytesOwnedIdentity),
            listOf(bytesOtherDeviceUid),
            bytesOwnedIdentity,
            false,
            false
        )
    }

    private fun sendAck(id: Int, chunk: Int) {
        val chunkBytes = ByteArray(1) { TransferMessageType.ACK.value } +
                id.to4ByteArray() +
                0.to4ByteArray() +
                1.to4ByteArray() +
                8.to4ByteArray() +
                id.to4ByteArray() +
                chunk.to4ByteArray()

        sendOnDataChannel(chunkBytes)
    }

    override fun sendJsonMessage(
        messageType: TransferMessageType,
        serializedMessage: ByteArray
    ) {
        Logger.d("\uD83E\uDDF6 sendJsonMessage $messageType")
        sendBytes(messageType, serializedMessage)
    }

    private fun sendBytes(messageType: TransferMessageType, messagePayload: ByteArray) {
        val id = nextDataChannelMessageId.getAndAdd(1)
        val blockSize = MAX_DATA_CHANNEL_MESSAGE_SIZE - DATA_CHANNEL_MESSAGE_HEADER_SIZE
        val totalChunks = (messagePayload.size - 1) / blockSize + 1

        var offset = 0
        var chunk = 0
        while (offset < messagePayload.size && aborted == TransferAbort.NONE) {
            unackedSentMessages.add((id.toLong() shl 32) + chunk)
            val chunkBytes = ByteArray(1) { messageType.value } +
                    id.to4ByteArray() +
                    chunk.to4ByteArray() +
                    totalChunks.to4ByteArray() +
                    blockSize.to4ByteArray() +
                    messagePayload.sliceArray(offset..<(offset + blockSize).coerceAtMost(messagePayload.size))

            sendOnDataChannel(chunkBytes)

            offset += blockSize
            chunk++
        }
    }

    override fun sendAttachment(
        sha256: ByteArray,
        size: Long,
        inputStream: InputStream,
        attachmentProgressListener: AttachmentProgressListener?
    ) {
        if (aborted != TransferAbort.NONE) {
            return
        }
        Logger.d("\uD83E\uDDF6 sendAttachment ${Logger.toHexString(sha256.copyOfRange(0, 4))}")
        val buffer = ByteArray(ATTACHMENT_BLOCK_SIZE)

        var offset = 0L
        var finished = false
        while (!finished && aborted == TransferAbort.NONE) {
            var bufferFullness = 0
            var c: Int
            do {
                c = inputStream.read(buffer, bufferFullness, ATTACHMENT_BLOCK_SIZE - bufferFullness)
                if (c == -1) {
                    finished = true
                    break
                }
                bufferFullness += c
            } while (aborted == TransferAbort.NONE && bufferFullness != ATTACHMENT_BLOCK_SIZE)

            if (bufferFullness == 0 || aborted != TransferAbort.NONE) {
                break
            }

            val chunkBytes = sha256 +
                    offset.to8ByteArray() +
                    buffer.sliceArray(0..<bufferFullness)

            sendBytes(TransferMessageType.SRC_SHA256, chunkBytes)

            attachmentProgressListener?.bytesTransferred(bufferFullness.toLong())
            offset += bufferFullness
        }
    }

    override fun queueSendAttachmentTask(sendAttachmentRunnable: Runnable) {
        sendAttachmentExecutor.execute(sendAttachmentRunnable)
    }

    override fun getMessageBatchSize(): Int {
        return 10
    }

    private fun sendOnDataChannel(payload: ByteArray) {
        // we wait before sending more data if our buffer is already encumbered
        while (aborted == TransferAbort.NONE && (dataChannel?.bufferedAmount() ?: 0) > 50 * MAX_DATA_CHANNEL_MESSAGE_SIZE) {
            try {
                Thread.sleep(30)
            } catch (_: InterruptedException) {}
        }
        if (aborted != TransferAbort.NONE) {
            return
        }

        dataChannel?.send(
            DataChannel.Buffer(
                ByteBuffer.wrap(payload),
                true
            )
        )
    }


    inner class PeerConnectionObserver : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
            Logger.d("\uD83E\uDDF6 signaling changed: ${newState?.name}")
            if (newState == PeerConnection.SignalingState.STABLE) {
                webRtcExecutor.execute {
                    // process peer ice candidates
                    pendingPeerIceCandidates.forEach { candidate ->
                        peerConnection?.addIceCandidate(candidate)
                    }
                    pendingPeerIceCandidates.clear()

                    // send pending ICE candidates
                    pendingIceCandidates.forEach { candidate ->
                        sendIceCandidate(candidate)
                    }
                    pendingIceCandidates.clear()
                }
            } else if (newState == PeerConnection.SignalingState.CLOSED) {
                cleanup()
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            Logger.d("\uD83E\uDDF6 ice connection changed: ${newState?.name}")
            when (newState) {
                PeerConnection.IceConnectionState.NEW,
                PeerConnection.IceConnectionState.CHECKING,
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED,
                null -> Unit

                PeerConnection.IceConnectionState.FAILED,
                PeerConnection.IceConnectionState.DISCONNECTED,
                PeerConnection.IceConnectionState.CLOSED -> {
                    // we set aborted to DISCONNECT to unblock the webRtcExecutor
                    aborted = TransferAbort.DISCONNECT
                    webRtcExecutor.execute {
                        dataChannelState = TransferTransportLayerState.CLOSED
                    }
                }
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                Logger.d("\uD83E\uDDF6 new ice candidate: ${candidate.sdp}")
                webRtcExecutor.execute {
                    if (peerConnection?.signalingState() == PeerConnection.SignalingState.STABLE) {
                        // send it to the peer if in STABLE state
                        sendIceCandidate(candidate)
                    } else {
                        pendingIceCandidates.add(candidate)
                    }
                }
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate?>?) {}

        override fun onAddStream(stream: MediaStream?) {}

        override fun onRemoveStream(stream: MediaStream?) {}

        override fun onDataChannel(dataChannel: DataChannel?) {
            Logger.d("\uD83E\uDDF6 data channel received, adding observer")
            dataChannel?.let {
                this@WebRTCTransferTransportDelegate.dataChannel = dataChannel
                dataChannel.registerObserver(dataChannelObserver)
            }
        }

        override fun onRenegotiationNeeded() {
            Logger.d("\uD83E\uDDF6 renegotiation needed")
        }
    }


    inner class SessionDescriptionObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            sdp?.let {
                Logger.d("\uD83E\uDDF6 sdp created: ${sdp.type} - ${sdp.description}")
                webRtcExecutor.execute {
                    peerConnection?.setLocalDescription(sessionDescriptionObserver, sdp)

                    // send it to the peer
                    sendSdp(sdp.type.canonicalForm(), sdp.description)
                }
            }
        }

        override fun onSetSuccess() {
            Logger.d("\uD83E\uDDF6 sdp set")
        }

        override fun onCreateFailure(error: String?) {
            Logger.e("\uD83E\uDDF6 sdp create FAILED")
        }

        override fun onSetFailure(error: String?) {
            Logger.e("\uD83E\uDDF6 sdp set FAILED")
        }
    }

    companion object {
        const val ATTACHMENT_BLOCK_SIZE = 300_000
        const val MAX_DATA_CHANNEL_MESSAGE_SIZE = 64*1024// 65536
        const val DATA_CHANNEL_MESSAGE_HEADER_SIZE = 17

        private fun getTurnCredentialsFromEngine(bytesOwnedIdentity: ByteArray): Triple<String, String, List<String>>? {
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            val lock = Object()
            var out: Triple<String, String, List<String>>? = null

            val successListener = object : SimpleEngineNotificationListener(EngineNotifications.TURN_CREDENTIALS_RECEIVED) {
                override fun callback(userInfo: HashMap<String?, in Any>?) {
                    Logger.d("\uD83E\uDDF6 WebRTCTransferTransportDelegate: turn credentials request to engine success")
                    userInfo?.let {
                        @Suppress("UNCHECKED_CAST")
                        var turnServers = it[EngineNotifications.TURN_CREDENTIALS_RECEIVED_SERVERS_KEY] as List<String>
                        if (SettingsActivity.useAltTurnServers) {
                            @Suppress("UNCHECKED_CAST")
                            val altTurnServers = it[EngineNotifications.TURN_CREDENTIALS_RECEIVED_ALT_SERVERS_KEY] as List<String>?
                            if (altTurnServers != null) {
                                turnServers = altTurnServers
                            }
                        }

                        out = Triple(it[EngineNotifications.TURN_CREDENTIALS_RECEIVED_USERNAME_1_KEY] as String,
                            it[EngineNotifications.TURN_CREDENTIALS_RECEIVED_PASSWORD_1_KEY] as String,
                            turnServers)
                    }
                    synchronized(lock) {
                        lock.notify()
                    }
                }
            }

            val failListener = object : SimpleEngineNotificationListener(EngineNotifications.TURN_CREDENTIALS_FAILED) {
                override fun callback(userInfo: HashMap<String?, in Any>?) {
                    Logger.d("\uD83E\uDDF6 WebRTCTransferTransportDelegate: turn credentials request to engine failed")
                    synchronized(lock) {
                        lock.notify()
                    }
                }
            }

            AppSingleton.getEngine().addNotificationListener(EngineNotifications.TURN_CREDENTIALS_RECEIVED, successListener)
            AppSingleton.getEngine().addNotificationListener(EngineNotifications.TURN_CREDENTIALS_FAILED, failListener)


            AppSingleton.getEngine().getTurnCredentials(
                bytesOwnedIdentity,
                UUID(0, 0),
                "caller",
                "recipient"
            )


            synchronized(lock) {
                lock.wait(10000)
            }

            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.TURN_CREDENTIALS_RECEIVED, successListener)
            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.TURN_CREDENTIALS_FAILED, failListener)

            return out
        }
    }
}



private fun Int.to4ByteArray(): ByteArray {
    return ByteArray(4) { i ->
        (this shr (24 - 8 * i)).toByte()
    }
}

private fun Long.to8ByteArray(): ByteArray {
    return ByteArray(8) { i ->
        (this shr (56 - 8 * i)).toByte()
    }
}


private fun ByteArray.toInt32(): Int {
    return((this[0].toInt() and 0xff) shl 24) +
            ((this[1].toInt() and 0xff) shl 16) +
            ((this[2].toInt() and 0xff) shl 8) +
            (this[3].toInt() and 0xff)
}

private fun ByteArray.toInt64(): Long {
    return ((this[0].toLong() and 0xff) shl 56) +
            ((this[1].toLong() and 0xff) shl 48) +
            ((this[2].toLong() and 0xff) shl 40) +
            ((this[3].toLong() and 0xff) shl 32) +
            ((this[4].toLong() and 0xff) shl 24) +
            ((this[5].toLong() and 0xff) shl 16) +
            ((this[6].toLong() and 0xff) shl 8) +
            (this[7].toLong() and 0xff)
}


private class PartialSha256(
    val outputStream: OutputStream,
    val totalSize: Long,
    val attachmentProgressListener: AttachmentProgressListener?,
    var currentOffset: Long = 0,
    val pendingChunks: MutableMap<Long, ByteArray> = mutableMapOf()
)