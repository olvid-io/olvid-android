package io.olvid.messenger.discussion.location

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentContainerView
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.HandlerExecutor
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonLocation
import io.olvid.messenger.databases.tasks.PostOsmLocationMessageInDiscussionTask
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.OlvidOutlinedActionButton
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.lock_screen.LockableActivity
import io.olvid.messenger.services.GpsDebugLogger
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

enum class MapActivityType {
    MESSAGE, DISCUSSION, OWNED_IDENTITY, SEND_LOCATION, SEND_LOCATION_BASIC
}

class LocationActivity : LockableActivity() {

    companion object {
        const val INTEGRATION_KEY = "integration"
        const val TYPE_KEY = "type"
        const val MESSAGE_ID_KEY = "olvid_message_id"
        const val MESSAGE_SENDER_IDENTIFIER_KEY = "message_sender_identifier"
        const val MESSAGE_LOCATION_TYPE_KEY = "message_location_type"
        const val MESSAGE_JSON_LOCATION_KEY = "message_json_location"
        const val MESSAGE_CONTENT_BODY_KEY = "message_content_body"
        const val DISCUSSION_ID_KEY = "discussion_id"
        const val OWNED_IDENTITY_KEY = "owned_identity"

        const val ADDRESS_NOT_FOUND = "_NotFound"
        const val ADDRESS_DISABLED = "_Disabled"
        const val ADDRESS_ZOOM_IN = "_ZoomIn"

        @JvmStatic
        fun start(
            context: Context,
            message: Message?,
            discussionId: Long?,
            ownedIdentity: ByteArray?,
            integration: SettingsActivity.LocationIntegrationEnum
        ) {
            val intent = Intent(context, LocationActivity::class.java).apply {
                if (ownedIdentity != null) {
                    putExtra(TYPE_KEY, MapActivityType.OWNED_IDENTITY.ordinal)
                    putExtra(OWNED_IDENTITY_KEY, ownedIdentity)
                } else if (message != null && discussionId != null) {
                    putExtra(TYPE_KEY, MapActivityType.MESSAGE.ordinal)
                    putExtra(MESSAGE_ID_KEY, message.id)
                    putExtra(MESSAGE_SENDER_IDENTIFIER_KEY, message.senderIdentifier)
                    putExtra(MESSAGE_LOCATION_TYPE_KEY, message.locationType)
                    putExtra(MESSAGE_JSON_LOCATION_KEY, message.jsonLocation)
                    putExtra(MESSAGE_CONTENT_BODY_KEY, message.contentBody)
                    putExtra(DISCUSSION_ID_KEY, discussionId)
                } else if (discussionId != null) {
                    putExtra(TYPE_KEY, MapActivityType.DISCUSSION.ordinal)
                    putExtra(DISCUSSION_ID_KEY, discussionId)
                } else {
                    return
                }
                putExtra(INTEGRATION_KEY, integration.ordinal)
            }
            context.startActivity(intent)
        }

        @JvmStatic
        fun startSendLocation(
            context: Context,
            discussionId: Long,
            integration: SettingsActivity.LocationIntegrationEnum
        ) {
            val intent = Intent(context, LocationActivity::class.java).apply {
                putExtra(TYPE_KEY, MapActivityType.SEND_LOCATION.ordinal)
                putExtra(DISCUSSION_ID_KEY, discussionId)
                putExtra(INTEGRATION_KEY, integration.ordinal)
            }
            context.startActivity(intent)
        }

        @JvmStatic
        fun startSendLocationBasic(
            context: Context,
            discussionId: Long,
            integration: SettingsActivity.LocationIntegrationEnum,
            continuousSharing: Boolean = false
        ) {
            val intent = Intent(context, LocationActivity::class.java).apply {
                putExtra(TYPE_KEY, MapActivityType.SEND_LOCATION_BASIC.ordinal)
                putExtra(DISCUSSION_ID_KEY, discussionId)
                putExtra(INTEGRATION_KEY, integration.ordinal)
                putExtra(CONTINUOUS_SHARING_KEY, continuousSharing)
            }
            context.startActivity(intent)
            (context as Activity).overridePendingTransition(0, 0)
        }

        private const val CONTINUOUS_SHARING_KEY = "continuous_sharing"
    }

    private var integration: SettingsActivity.LocationIntegrationEnum =
        SettingsActivity.LocationIntegrationEnum.NONE
    private var type: MapActivityType = MapActivityType.MESSAGE
    private var messageId: Long? = null
    private var messageSenderIdentifier: ByteArray? = null
    private var messageLocationType: Int = 0
    private var messageJsonLocation: JsonLocation? = null
    private var messageContentBody: String? = null
    private var discussionId: Long? = null
    private var bytesOwnedIdentity: ByteArray? = null
    private var continuousLocationSharing: Boolean = false
    private val currentLocationState = mutableStateOf<Location?>(null)

    private val peliasServer by lazy {
        SettingsActivity.locationCustomAddressServer
            ?: AppSingleton.getEngine().getAddressServerUrl(AppSingleton.getBytesCurrentIdentity())
            ?: "https://pelias.olvid.io"
    }

    private var locationManager: LocationManager? = null
    private val passiveLocationListenerCompat = LocationListenerCompat { location ->
        GpsDebugLogger.logGpsEvent("Next location is from passive provider")
        onLocationUpdate(location)
    }
    private val locationListenerCompat = LocationListenerCompat { location -> onLocationUpdate(location) }
    private val fakeLocationListenerForGps = LocationListenerCompat { _ -> }

    private var mapView: MapViewAbstractFragment? = null
    private var centerOnMarkersOnNextLocationMessagesUpdate = false
    private val currentlyShownMessagesIdList = mutableListOf<Long>()
    private val markersRedrawTriggerer = mutableIntStateOf(0)
    private lateinit var locationPermissionHelper: LocationPermissionHelper

    // For SEND_LOCATION mode
    private val handler = Handler(Looper.getMainLooper())
    private var addressRequestTask: RequestAndUpdateAddressFieldTask? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                androidx.compose.ui.graphics.Color.Transparent.toArgb(),
                androidx.compose.ui.graphics.Color.Transparent.toArgb()
            ),
            navigationBarStyle = SystemBarStyle.light(
                androidx.compose.ui.graphics.Color.Transparent.toArgb(),
                ContextCompat.getColor(this, R.color.blackOverlay)
            )
        )
        super.onCreate(savedInstanceState)

        WindowInsetsControllerCompat(window, window.decorView).run {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        locationPermissionHelper = LocationPermissionHelper(this, this) {
            checkPermissionsAndEnableLocation()
            if (type == MapActivityType.SEND_LOCATION || type == MapActivityType.SEND_LOCATION_BASIC) {
                mapView?.centerOnCurrentLocation(true)
            }
        }

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        intent?.let {
            integration = SettingsActivity.LocationIntegrationEnum.entries.getOrNull(
                it.getIntExtra(
                    INTEGRATION_KEY,
                    -1
                )
            ) ?: SettingsActivity.LocationIntegrationEnum.NONE

            val typeOrdinal = it.getIntExtra(TYPE_KEY, MapActivityType.MESSAGE.ordinal)
            type = MapActivityType.entries.getOrElse(typeOrdinal) { MapActivityType.MESSAGE }

            when (type) {
                MapActivityType.MESSAGE -> {
                    messageId = it.getLongExtra(MESSAGE_ID_KEY, -1).takeIf { id -> id != -1L }
                    messageSenderIdentifier = it.getByteArrayExtra(MESSAGE_SENDER_IDENTIFIER_KEY)
                    messageLocationType = it.getIntExtra(MESSAGE_LOCATION_TYPE_KEY, 0)
                    val serializedJsonLocation = it.getStringExtra(MESSAGE_JSON_LOCATION_KEY)
                    try {
                        messageJsonLocation = AppSingleton.getJsonObjectMapper()
                            .readValue(serializedJsonLocation, JsonLocation::class.java)
                    } catch (_: Exception) {
                    }
                    messageContentBody = it.getStringExtra(MESSAGE_CONTENT_BODY_KEY)
                    discussionId = it.getLongExtra(DISCUSSION_ID_KEY, -1).takeIf { id -> id != -1L }
                }

                MapActivityType.DISCUSSION -> {
                    discussionId = it.getLongExtra(DISCUSSION_ID_KEY, -1).takeIf { id -> id != -1L }
                }

                MapActivityType.OWNED_IDENTITY -> {
                    bytesOwnedIdentity = it.getByteArrayExtra(OWNED_IDENTITY_KEY)
                }

                MapActivityType.SEND_LOCATION, MapActivityType.SEND_LOCATION_BASIC -> {
                    discussionId = it.getLongExtra(DISCUSSION_ID_KEY, -1).takeIf { id -> id != -1L }
                    continuousLocationSharing = it.getBooleanExtra(CONTINUOUS_SHARING_KEY, false)
                    if (discussionId == null) {
                        finish()
                        return
                    }
                }
            }
        } ?: finish()

        setContent {
            FullscreenMapContent()
        }
    }

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FullscreenMapContent() {
        val currentType = type
        val currentBytesOwnedIdentity = bytesOwnedIdentity
        val currentDiscussionId = discussionId
        val sharingMessages = remember {
            if (currentType == MapActivityType.OWNED_IDENTITY && currentBytesOwnedIdentity != null) {
                AppDatabase.getInstance().messageDao()
                    .getCurrentlySharingLocationMessagesForOwnedIdentityLiveData(
                        currentBytesOwnedIdentity
                    )
            } else if ((currentType == MapActivityType.DISCUSSION || currentType == MapActivityType.MESSAGE) && currentDiscussionId != null) {
                AppDatabase.getInstance().messageDao()
                    .getCurrentlySharingLocationMessagesInDiscussionLiveData(currentDiscussionId)
            } else {
                null
            }
        }?.observeAsState(emptyList())

        var showBottomSheet by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()
        var layersButtonVisible by remember { mutableStateOf(false) }

        // For SEND_LOCATION mode
        var address by remember { mutableStateOf<String?>(null) }
        var isFetchingAddress by remember { mutableStateOf(false) }
        var isMapCenteredOnGps by remember { mutableStateOf(false) }
        var isMapLoading by remember { mutableStateOf(true) }
        var showCenterPointer by remember { mutableStateOf(false) }
        var isMoving by remember { mutableStateOf(false) }
        var showLayersMenu by rememberSaveable { mutableStateOf(false) }
        var showSendLocationBasicDialog by rememberSaveable { mutableStateOf(false) }
        val currentLocation by currentLocationState

        var failedStyleUrl: String? by remember { mutableStateOf(null) }
        var forceMapReload by remember { mutableIntStateOf(0) }

        if (currentType != MapActivityType.SEND_LOCATION_BASIC) {
            Scaffold(
                modifier = Modifier.fillMaxSize()
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorResource(R.color.blackDarkOverlay))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Map Fragment Hosting
                        AndroidView(
                            factory = { context ->
                                FragmentContainerView(context).apply {
                                    id = R.id.fullscreen_map_fragment_container
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { _ ->
                                // read the forceMapRealod state so we can trigger a fragment recreation
                                Logger.d("MapLibre forcereload calls: $forceMapReload")

                                val fragment =
                                    MapFragmentProvider.getMapFragmentForProvider(integration)
                                if (fragment != null) {
                                    mapView = fragment
                                    supportFragmentManager.beginTransaction()
                                        .replace(
                                            R.id.fullscreen_map_fragment_container,
                                            fragment,
                                            "map_fragment"
                                        )
                                        .commit()
                                    fragment.setFailedStyleUrlCallback { url ->
                                        failedStyleUrl = url
                                    }
                                    fragment.setOnMapReadyCallback {
                                        isMapLoading = false
                                        onMapReadyCallback()
                                        if (type == MapActivityType.SEND_LOCATION) {
                                            showCenterPointer = true
                                            if (LocationUtils.isLocationPermissionGranted(this@LocationActivity)) {
                                                fragment.centerOnCurrentLocation(false)
                                            } else {
                                                locationPermissionHelper.requestLocationPermission()
                                            }

                                            fragment.currentlyCenteredOnGpsPosition.observe(this@LocationActivity) { centered ->
                                                isMapCenteredOnGps = centered == true
                                            }

                                            fragment.getCurrentCameraCenterLiveData()
                                                .observe(this@LocationActivity) { center ->
                                                    if (center == null) {
                                                        isMoving = true
                                                        handler.removeCallbacksAndMessages(null)
                                                        addressRequestTask = null
                                                        isFetchingAddress = false
                                                        address = null
                                                    } else {
                                                        isMoving = false
                                                        if (SettingsActivity.locationDisableAddressLookup) {
                                                            address = ADDRESS_DISABLED
                                                        } else {
                                                            if (fragment.cameraZoom < RequestAndUpdateAddressFieldTask.MIN_ZOOM_FOR_REQUESTS) {
                                                                address = ADDRESS_ZOOM_IN
                                                            } else {
                                                                isFetchingAddress = true
                                                                val task =
                                                                    RequestAndUpdateAddressFieldTask(
                                                                        this@LocationActivity,
                                                                        peliasServer,
                                                                        center
                                                                    ) { t, newAddress ->
                                                                        if (addressRequestTask == t) {
                                                                            address = newAddress ?: ADDRESS_NOT_FOUND
                                                                            isFetchingAddress = false
                                                                        }
                                                                    }
                                                                addressRequestTask = task
                                                                handler.postDelayed({
                                                                    if (addressRequestTask == task) {
                                                                        App.runThread(task)
                                                                    }
                                                                }, 100)
                                                            }
                                                        }
                                                    }
                                                }
                                        } else {
                                            checkPermissionsAndEnableLocation()
                                        }
                                    }
                                    fragment.setRedrawMarkersCallback { redrawMarkersCallback() }
                                    fragment.setLayersButtonVisibilitySetter { visible ->
                                        layersButtonVisible = visible == true
                                    }
                                }
                            }
                        )

                        // Layers Button
                        if (layersButtonVisible) {
                            Box(
                                modifier = Modifier
                                    .safeDrawingPadding()
                                    .align(Alignment.TopEnd)
                            ) {
                                IconButton(
                                    modifier = Modifier
                                        .padding(all = 8.dp)
                                        .shadow(elevation = 8.dp, shape = CircleShape)
                                        .size(32.dp),
                                    onClick = { showLayersMenu = !showLayersMenu },
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.iconButtonColors().copy(
                                        containerColor = colorResource(R.color.almostWhite),
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Icon(
                                        modifier = Modifier.size(24.dp),
                                        painter = painterResource(R.drawable.ic_layers),
                                        contentDescription = null
                                    )
                                }
                                OlvidDropdownMenu(
                                    expanded = showLayersMenu,
                                    onDismissRequest = { showLayersMenu = false }
                                ) {
                                    val currentLayerId = mapView?.getCurrentMapLayerId()
                                    mapView?.getMapLayers()?.forEach { (id, name) ->
                                        OlvidDropdownMenuItem(
                                            text = name,
                                            onClick = {
                                                mapView?.setMapLayer(id)
                                                showLayersMenu = false
                                            },
                                            trailingIcon = if (id == currentLayerId) {
                                                {
                                                    Icon(
                                                        modifier = Modifier.size(20.dp),
                                                        painter = painterResource(R.drawable.ic_check),
                                                        contentDescription = null,
                                                        tint = colorResource(R.color.darkGrey)
                                                    )
                                                }
                                            } else {
                                                {
                                                    Spacer(Modifier.width(20.dp))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            // Top Controls (Back Button)
                            IconButton(
                                onClick = { finish() },
                                modifier = Modifier
                                    .safeDrawingPadding()
                                    .padding(8.dp)
                                    .shadow(elevation = 8.dp, shape = CircleShape)
                                    .size(48.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.iconButtonColors().copy(
                                    containerColor = colorResource(R.color.almostWhite),
                                    contentColor = colorResource(R.color.almostBlack)
                                )
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_back_white),
                                    contentDescription = stringResource(R.string.button_label_go_back)
                                )
                            }
                            if (type == MapActivityType.SEND_LOCATION) {
                                // Bottom FABs for SEND_LOCATION
                                CurrentLocationFab(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .cutoutHorizontalPadding()
                                        .systemBarsHorizontalPadding()
                                        .padding(horizontal = 8.dp, vertical = 16.dp)
                                        .shadow(elevation = 8.dp, shape = CircleShape)
                                        .size(48.dp),
                                    onClick = {
                                        if (locationPermissionHelper.checkPermissionsAndEnableLocation()) {
                                            mapView?.centerOnCurrentLocation(true)
                                        }
                                    },
                                    isCentered = isMapCenteredOnGps
                                )

                                OlvidActionButton(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(vertical = 20.dp)
                                        .height(40.dp),
                                    onClick = {
                                        if (currentDiscussionId != null) {
                                            continuousLocationSharing = true
                                            showSendLocationBasicDialog = true
                                        }
                                    },
                                    elevation = 8.dp,
                                    large = true,
                                    text = stringResource(R.string.button_label_live_share),
                                    icon = R.drawable.ic_location_sharing,
                                )
                            }
                        }

                        if (type != MapActivityType.SEND_LOCATION) {
                            // Marker View Mode Bottom FABs
                            Column(
                                modifier = Modifier
                                    .safeDrawingPadding()
                                    .padding(8.dp)
                                    .align(Alignment.BottomEnd),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                IconButton(
                                    modifier = Modifier
                                        .shadow(elevation = 8.dp, shape = CircleShape)
                                        .size(48.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.iconButtonColors().copy(
                                        containerColor = colorResource(R.color.almostWhite),
                                        contentColor = colorResource(R.color.almostBlack)
                                    ),
                                    onClick = { mapView?.centerOnMarkers(true, true) }
                                ) {
                                    Icon(
                                        modifier = Modifier.size(24.dp),
                                        painter = painterResource(R.drawable.ic_location_center_on_markers),
                                        contentDescription = null
                                    )
                                }

                                IconButton(
                                    modifier = Modifier
                                        .shadow(elevation = 8.dp, shape = CircleShape)
                                        .size(48.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.iconButtonColors().copy(
                                        containerColor = colorResource(R.color.almostWhite),
                                        contentColor = colorResource(R.color.almostBlack)
                                    ),
                                    onClick = {
                                        if (sharingMessages != null && sharingMessages.value.isNotEmpty()) {
                                            showBottomSheet = true
                                        } else if (messageJsonLocation != null) {
                                            App.openLocationInMapApplication(
                                                this@LocationActivity,
                                                messageJsonLocation!!.truncatedLatitudeString,
                                                messageJsonLocation!!.truncatedLongitudeString,
                                                messageContentBody,
                                                null
                                            )
                                        }
                                    }
                                ) {
                                    val iconRes =
                                        if (sharingMessages != null && sharingMessages.value.isNotEmpty()) {
                                            R.drawable.ic_location_person_pin
                                        } else {
                                            R.drawable.ic_open_location_in_third_party_app_48
                                        }
                                    Icon(
                                        modifier = Modifier.size(24.dp),
                                        painter = painterResource(iconRes),
                                        contentDescription = null
                                    )
                                }
                            }

                            if (showBottomSheet) {
                                ModalBottomSheet(
                                    onDismissRequest = { showBottomSheet = false },
                                    sheetState = sheetState,
                                    containerColor = colorResource(R.color.almostWhite),
                                ) {
                                    SharingLocationList(
                                        messages = sharingMessages?.value ?: emptyList(),
                                        onItemClick = { message ->
                                            mapView?.centerOnMarker(message.id, true)
                                            showBottomSheet = false
                                        },
                                        onExternalClick = { message ->
                                            message.getJsonLocation()?.let { loc ->
                                                App.openLocationInMapApplication(
                                                    this@LocationActivity,
                                                    loc.truncatedLatitudeString,
                                                    loc.truncatedLongitudeString,
                                                    message.contentBody,
                                                    null
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        // Loading Spinner
                        androidx.compose.animation.AnimatedVisibility(
                            modifier = Modifier
                                .align(Alignment.Center),
                            visible = isMapLoading && failedStyleUrl == null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = colorResource(R.color.newDialogBackground),
                                    contentColor = colorResource(R.color.almostBlack),
                                ),
                                border = BorderStroke(1.dp, colorResource(R.color.newDialogBorder)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            ) {
                                OlvidCircularProgress(
                                    modifier = Modifier.padding(8.dp),
                                    size = 48.dp
                                )
                            }
                        }

                        // Center Pointer
                        if (showCenterPointer) {
                            CenterPointer(
                                isMoving = isMoving,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        val coroutineScope = rememberCoroutineScope()

                        // style load error message
                        androidx.compose.animation.AnimatedVisibility(
                            modifier = Modifier
                                .align(Alignment.Center),
                            visible = failedStyleUrl != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Card(
                                modifier = Modifier.padding(32.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = colorResource(R.color.newDialogBackground),
                                    contentColor = colorResource(R.color.almostBlack),
                                ),
                                border = BorderStroke(1.dp, colorResource(R.color.newDialogBorder)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = stringResource(R.string.label_error_loading_openstreetmap_style_file),
                                        style = OlvidTypography.body1,
                                        textAlign = TextAlign.Center,
                                    )
                                    val context = LocalContext.current
                                    val url by remember { mutableStateOf(failedStyleUrl) }

                                    url?.takeIf { it.isNotEmpty() }?.let { url ->
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(),
                                                onClick = {
                                                    runCatching {
                                                        App.openLink(context, url.toUri())
                                                    }
                                                }
                                            ),
                                            text = url,
                                            style = OlvidTypography.body2.copy(
                                                color = colorResource(R.color.olvid_gradient_light)
                                            ),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    OlvidOutlinedActionButton(
                                        text = stringResource(R.string.button_label_retry)
                                    ) {
                                        coroutineScope.launch {
                                            failedStyleUrl = null
                                            runCatching {
                                                delay(1000)
                                            }
                                            forceMapReload++
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (type == MapActivityType.SEND_LOCATION) {
                        // Bottom Sheet for SEND_LOCATION
                        SendLocationBottomSheet(
                            address = address,
                            isFetchingAddress = isFetchingAddress,
                            onSendClick = {
                                if (isMapLoading) return@SendLocationBottomSheet
                                mapView?.let { map ->
                                    isMapLoading = true
                                    showCenterPointer = false
                                    map.setEnableCurrentLocation(false)

                                    map.launchMapSnapshot { bitmap ->
                                        val canvas = Canvas(bitmap)
                                        val density = resources.displayMetrics.density
                                        val markerSize = (48 * density).toInt()
                                        val markerDrawable = ResourcesCompat.getDrawable(
                                            resources,
                                            R.drawable.ic_location_red,
                                            null
                                        )

                                        markerDrawable?.let {
                                            it.setBounds(
                                                bitmap.width / 2 - markerSize / 2,
                                                (bitmap.height / 2 - markerSize) + (6 * density).toInt(),
                                                bitmap.width / 2 + markerSize / 2,
                                                (bitmap.height / 2) + (6 * density).toInt()
                                            )
                                            it.draw(canvas)
                                        }

                                        val location =
                                            map.getCurrentCameraCenterLiveData().value?.toLocation()
                                        if (location != null && discussionId != null) {
                                            GpsDebugLogger.logGpsEvent("Sending one-shot location")
                                            if (isMapCenteredOnGps) {
                                                location.accuracy = map.latestLocationAccuracy
                                                location.altitude = map.latestLocationAltitude
                                            }
                                            App.runThread(
                                                PostOsmLocationMessageInDiscussionTask(
                                                    location,
                                                    discussionId!!,
                                                    bitmap,
                                                    address.takeIf {
                                                        it != ADDRESS_NOT_FOUND && it != ADDRESS_ZOOM_IN && it != ADDRESS_DISABLED
                                                    }
                                                )
                                            )
                                        }

                                        finish()
                                    }
                                }
                            }
                        )
                    }
                }
            }
            // Logic side-effects for marker updates
            LaunchedEffect(sharingMessages?.value, markersRedrawTriggerer.intValue) {
                sharingMessages?.value?.let { messages ->
                    updateMarkers(messages)
                }
            }
        }

        if ((currentType == MapActivityType.SEND_LOCATION_BASIC || showSendLocationBasicDialog) && currentDiscussionId != null) {
            SendLocationBasicDialog(
                discussionId = currentDiscussionId,
                currentLocation = currentLocation,
                continuousLocationSharing = continuousLocationSharing,
                onRequestBackgroundPermission = { locationPermissionHelper.requestBackgroundLocationPermission() },
                onDismissRequest = {
                    @Suppress("AssignedValueIsNeverRead")
                    showSendLocationBasicDialog = false
                    if (currentType == MapActivityType.SEND_LOCATION_BASIC) {
                        finish()
                    }
                },
                onFinish = { finish() }
            )
        }
    }

    private fun onMapReadyCallback() {
        if (type == MapActivityType.MESSAGE && messageLocationType != Message.LOCATION_TYPE_SHARE) {
            messageJsonLocation?.let { loc ->
                if (messageLocationType == Message.LOCATION_TYPE_SEND) {
                    mapView?.addMarker(
                        messageId!!,
                        getPinMarkerIcon(),
                        LatLngWrapper(loc),
                        loc.precision
                    )
                } else if (messageLocationType == Message.LOCATION_TYPE_SHARE_FINISHED) {
                    mapView?.addMarker(
                        messageId!!,
                        getInitialViewMarkerIcon(messageSenderIdentifier),
                        LatLngWrapper(loc),
                        loc.precision
                    )
                }
            }
            mapView?.centerOnMarkers(false, false)
        } else if (type != MapActivityType.SEND_LOCATION) {
            centerOnMarkersOnNextLocationMessagesUpdate = true
            currentlyShownMessagesIdList.clear()
            markersRedrawTriggerer.intValue++
        }
    }

    private fun redrawMarkersCallback() {
        if (type == MapActivityType.MESSAGE && messageLocationType != Message.LOCATION_TYPE_SHARE) {
            messageJsonLocation?.let { loc ->
                if (messageLocationType == Message.LOCATION_TYPE_SEND) {
                    mapView?.addMarker(
                        messageId!!,
                        getPinMarkerIcon(),
                        LatLngWrapper(loc),
                        loc.precision
                    )
                } else if (messageLocationType == Message.LOCATION_TYPE_SHARE_FINISHED) {
                    mapView?.addMarker(
                        messageId!!,
                        getInitialViewMarkerIcon(messageSenderIdentifier),
                        LatLngWrapper(loc),
                        loc.precision
                    )
                }
            }
        } else if (type != MapActivityType.SEND_LOCATION) {
            currentlyShownMessagesIdList.clear()
            markersRedrawTriggerer.intValue++
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkPermissionsAndEnableLocation() {
        if (!locationPermissionHelper.checkPermissionsAndEnableLocation()) {
            return
        }

        mapView?.setEnableCurrentLocation(true)
        registerToLocationUpdate()
    }

    private fun updateMarkers(messages: List<Message>) {
        val mapView = this.mapView ?: return
        if (type == MapActivityType.SEND_LOCATION) return

        val noMoreSharingMessagesId = currentlyShownMessagesIdList.toHashSet()

        for (message in messages) {
            val location = message.getJsonLocation() ?: continue
            if (currentlyShownMessagesIdList.contains(message.id)) {
                mapView.updateMarker(message.id, LatLngWrapper(location), location.precision)
            } else {
                mapView.addMarker(
                    message.id,
                    getInitialViewMarkerIcon(message.senderIdentifier),
                    LatLngWrapper(location),
                    location.precision
                )
                currentlyShownMessagesIdList.add(message.id)
            }
            noMoreSharingMessagesId.remove(message.id)
        }

        for (id in noMoreSharingMessagesId) {
            mapView.removeMarker(id)
            currentlyShownMessagesIdList.remove(id)
        }

        if (centerOnMarkersOnNextLocationMessagesUpdate) {
            mapView.centerOnMarkers(false, false)
            centerOnMarkersOnNextLocationMessagesUpdate = false
        }
    }

    private fun onLocationUpdate(location: Location) {
        GpsDebugLogger.logReceivedLocation(location)

        currentLocationState.value = location
        mapView?.onLocationUpdate(location)
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        registerToLocationUpdate()
    }

    @SuppressLint("MissingPermission")
    override fun onPause() {
        super.onPause()
        locationManager?.let {
            if (LocationUtils.isLocationPermissionGranted(this)) {
                GpsDebugLogger.logGpsEvent("Unregistering all foreground location update listeners (map closed)")
                LocationManagerCompat.removeUpdates(it, passiveLocationListenerCompat)
                LocationManagerCompat.removeUpdates(it, locationListenerCompat)
                LocationManagerCompat.removeUpdates(it, fakeLocationListenerForGps)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerToLocationUpdate() {
        if (locationManager != null
            && LocationUtils.isLocationPermissionGranted(this)
            && LocationUtils.isLocationEnabled()
        ) {
            GpsDebugLogger.logGpsEvent("Registering to foreground location updates (map opened)")

            val locationRequest = LocationRequestCompat.Builder(1000)
                .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                .build()

            val executor: Executor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mainExecutor
            } else {
                HandlerExecutor(Looper.getMainLooper())
            }

            val providers = locationManager!!.getProviders(true)
            GpsDebugLogger.logGpsEvent("Available providers: ${providers.joinToString(", ")}")

            if (providers.contains(LocationManager.PASSIVE_PROVIDER)) {
                GpsDebugLogger.logGpsEvent("Request updates from ${LocationManager.PASSIVE_PROVIDER}")
                LocationManagerCompat.requestLocationUpdates(
                    locationManager!!,
                    LocationManager.PASSIVE_PROVIDER,
                    LocationRequestCompat.Builder(io.olvid.messenger.services.UnifiedForegroundService.LocationSharingSubService.PASSIVE_PROVIDER_UPDATE_INTERVAL_MILLIS)
                        .build(),
                    executor,
                    passiveLocationListenerCompat
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && providers.contains(LocationManager.FUSED_PROVIDER)) {
                GpsDebugLogger.logGpsEvent("Request updates from ${LocationManager.FUSED_PROVIDER}")
                LocationManagerCompat.requestLocationUpdates(
                    locationManager!!,
                    LocationManager.FUSED_PROVIDER,
                    locationRequest,
                    executor,
                    locationListenerCompat
                )
                if (providers.contains(LocationManager.GPS_PROVIDER)) {
                    LocationManagerCompat.requestLocationUpdates(
                        locationManager!!,
                        LocationManager.GPS_PROVIDER,
                        locationRequest,
                        executor,
                        fakeLocationListenerForGps
                    )
                }
            } else if (providers.contains(LocationManager.GPS_PROVIDER)
                || providers.contains(LocationManager.NETWORK_PROVIDER)
            ) {
                if (providers.contains(LocationManager.GPS_PROVIDER)) {
                    GpsDebugLogger.logGpsEvent("Request updates from ${LocationManager.GPS_PROVIDER}")
                    LocationManagerCompat.requestLocationUpdates(
                        locationManager!!,
                        LocationManager.GPS_PROVIDER,
                        locationRequest,
                        executor,
                        locationListenerCompat
                    )
                }
                if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                    GpsDebugLogger.logGpsEvent("Request updates from ${LocationManager.NETWORK_PROVIDER}")
                    LocationManagerCompat.requestLocationUpdates(
                        locationManager!!,
                        LocationManager.NETWORK_PROVIDER,
                        locationRequest,
                        executor,
                        locationListenerCompat
                    )
                }
            }
        }
    }

    private fun getPinMarkerIcon(): Bitmap? {
        val locationMarkerDrawable =
            ResourcesCompat.getDrawable(resources, R.drawable.ic_location_red, null)
        if (locationMarkerDrawable != null) {
            val canvas = Canvas()
            val bitmap = createBitmap(
                locationMarkerDrawable.intrinsicWidth,
                11 * locationMarkerDrawable.intrinsicHeight / 6
            )
            canvas.setBitmap(bitmap)
            locationMarkerDrawable.setBounds(
                0,
                0,
                locationMarkerDrawable.intrinsicWidth,
                locationMarkerDrawable.intrinsicHeight
            )
            locationMarkerDrawable.draw(canvas)
            return bitmap
        }
        return null
    }

    private fun getInitialViewMarkerIcon(bytesIdentity: ByteArray?): Bitmap {
        val initialView = InitialView(this)
        bytesIdentity?.let { initialView.setFromCache(it) } ?: initialView.setUnknown()

        val initialViewSize = (64 * resources.displayMetrics.density).toInt()
        val shadowHeight = initialViewSize / 3
        val markerIcon = createBitmap(initialViewSize, 2 * initialViewSize + shadowHeight)
        val markerIconCanvas = Canvas(markerIcon)

        val shadowBitmap = BitmapFactory.decodeResource(resources, R.mipmap.location_pin_shadow)
        val resizeShadowMatrix = Matrix()
        resizeShadowMatrix.postScale(
            initialViewSize.toFloat() / shadowBitmap.width,
            shadowHeight.toFloat() / shadowBitmap.height
        )
        val resizedBitmap = Bitmap.createBitmap(
            shadowBitmap,
            0,
            0,
            shadowBitmap.width,
            shadowBitmap.height,
            resizeShadowMatrix,
            false
        )
        shadowBitmap.recycle()

        val alphaWhitePaint = Paint()
        alphaWhitePaint.alpha = 128
        markerIconCanvas.drawBitmap(resizedBitmap, 0f, initialViewSize.toFloat(), alphaWhitePaint)

        val blackPaint = Paint()
        blackPaint.color = Color.BLACK
        markerIconCanvas.drawCircle(
            initialViewSize.toFloat() / 2,
            initialViewSize + shadowHeight.toFloat() / 2,
            2 * resources.displayMetrics.density,
            blackPaint
        )

        initialView.setSize(initialViewSize, initialViewSize)
        initialView.drawOnCanvas(markerIconCanvas)

        return markerIcon
    }
}
