package io.olvid.messenger.plus_button.configuration

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.engine.engine.types.EngineAPI
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.RegisterApiKeyResult
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ConfigurationPojo
import io.olvid.messenger.databases.entity.OwnedIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class LicenseActivationViewModel : ViewModel() {
    var errorText by mutableStateOf<String?>(null)
        private set
    private var configurationApiKeyUuid by mutableStateOf<UUID?>(null)
    var newApiKeyStatus by mutableStateOf<EngineAPI.ApiKeyStatus?>(null)
        private set
    var newApiKeyPermissions by mutableStateOf<List<EngineAPI.ApiKeyPermission>?>(null)
        private set
    var newApiKeyExpirationTimestamp by mutableStateOf<Long?>(null)
        private set
    var activating by mutableStateOf(false)
        private set
    var queryFailed by mutableStateOf(false)
        private set
    private var callbackCalled by mutableStateOf(false)

    private var ownedIdentity: OwnedIdentity? = null
    private var listenersAttached = false

    private val listener = object : EngineNotificationListener {
        var engineNumber: Long = -1
        override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
            val bytesOwnedIdentity =
                userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
            val apiKey =
                userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY] as UUID?
            if (ownedIdentity?.bytesOwnedIdentity.contentEquals(bytesOwnedIdentity) && configurationApiKeyUuid == apiKey) {
                callbackCalled = true
                when (notificationName) {
                    EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS -> {
                        newApiKeyStatus =
                            userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY] as? EngineAPI.ApiKeyStatus
                        newApiKeyPermissions =
                            userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_PERMISSIONS_KEY] as? List<EngineAPI.ApiKeyPermission>
                        newApiKeyExpirationTimestamp =
                            userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY] as? Long
                    }

                    EngineNotifications.API_KEY_STATUS_QUERY_FAILED -> {
                        queryFailed = true
                    }
                }
            }
        }

        override fun setEngineNotificationListenerRegistrationNumber(number: Long) {
            engineNumber = number
        }
        override fun getEngineNotificationListenerRegistrationNumber(): Long {
            return engineNumber
        }
        override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
            return engineNumber != -1L
        }
    }

    fun init(
        context: Context,
        ownedIdentity: OwnedIdentity,
        configurationPojo: ConfigurationPojo,
        onCancel: () -> Unit
    ) {
        this.ownedIdentity = ownedIdentity

        if (ownedIdentity.keycloakManaged) {
            errorText =
                context.getString(R.string.text_explanation_keycloak_license_activation_impossible)
            return
        }

        if (!ownedIdentity.active) {
            errorText =
                context.getString(R.string.text_explanation_inactive_identity_activation_link)
            return
        }

        if (configurationPojo.server == null || configurationPojo.apikey == null) {
            errorText = context.getString(R.string.text_explanation_malformed_activation_link)
            return
        }

        try {
            configurationApiKeyUuid = UUID.fromString(configurationPojo.apikey)
        } catch (_: Exception) {
            errorText = context.getString(R.string.text_explanation_malformed_activation_link)
            return
        }

        val ownServer = AppSingleton.getEngine().getServerOfIdentity(ownedIdentity.bytesOwnedIdentity)
        if (ownServer == null) {
            onCancel()
            return
        }

        if (configurationPojo.server != ownServer) {
            errorText = "${context.getString(R.string.label_license_for_another_server)}\n" +
                    "${context.getString(R.string.label_license_server)} ${configurationPojo.server}\n" +
                    "${context.getString(R.string.label_your_server)} $ownServer"
            return
        }

        AppSingleton.getEngine()
            .queryApiKeyStatus(ownedIdentity.bytesOwnedIdentity, configurationApiKeyUuid)

        viewModelScope.launch {
            delay(5000)
            if (!callbackCalled) {
                queryFailed = true
            }
        }

        if (!listenersAttached) {
            AppSingleton.getEngine()
                .addNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS, listener)
            AppSingleton.getEngine()
                .addNotificationListener(EngineNotifications.API_KEY_STATUS_QUERY_FAILED, listener)
            listenersAttached = true
        }
    }

    fun activateLicense(onSuccess: () -> Unit) {
        activating = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                AppSingleton.getEngine().registerOwnedIdentityApiKeyOnServer(
                    ownedIdentity?.bytesOwnedIdentity, configurationApiKeyUuid
                )
            }
            when (result) {
                RegisterApiKeyResult.SUCCESS -> onSuccess()
                RegisterApiKeyResult.INVALID_KEY -> App.toast(
                    R.string.toast_message_license_rejected_by_server,
                    Toast.LENGTH_LONG
                )

                else -> App.toast(
                    R.string.toast_message_error_retry,
                    Toast.LENGTH_LONG
                )
            }
            activating = false
        }
    }

    override fun onCleared() {
        if (listenersAttached) {
            AppSingleton.getEngine()
                .removeNotificationListener(
                    EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS,
                    listener
                )
            AppSingleton.getEngine()
                .removeNotificationListener(
                    EngineNotifications.API_KEY_STATUS_QUERY_FAILED,
                    listener
                )
            listenersAttached = false
        }
        super.onCleared()
    }
}
