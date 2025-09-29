/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.billing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.olvid.engine.engine.types.EngineAPI
import io.olvid.messenger.databases.entity.OwnedIdentity

class SubscriptionStatusFragment : Fragment() {
    private lateinit var activity: AppCompatActivity
    val viewModel: SubscriptionPurchaseViewModel by activityViewModels()
    lateinit var bytesOwnedIdentity: ByteArray
    var apiKeyStatus: EngineAPI.ApiKeyStatus? = null
    var apiKeyPermissions: MutableList<EngineAPI.ApiKeyPermission?>? = null
    var apiKeyExpirationTimestamp: Long? = null
    var licenseQuery: Boolean = false
    var showInAppPurchase: Boolean = false
    var anotherIdentityHasCallsPermission: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity() as AppCompatActivity
        val arguments = getArguments()
        if (arguments != null) {
            bytesOwnedIdentity = arguments.getByteArray(BYTES_OWNED_IDENTITY) ?: ByteArray(0)
            apiKeyStatus = OwnedIdentity.deserializeApiKeyStatus(arguments.getInt(API_KEY_STATUS))
            apiKeyExpirationTimestamp = if (arguments.containsKey(API_KEY_EXPIRATION)) {
                arguments.getLong(API_KEY_EXPIRATION)
            } else {
                null
            }
            apiKeyPermissions = OwnedIdentity.deserializeApiKeyPermissions(
                arguments.getLong(
                    API_KEY_PERMISSIONS
                )
            )
            licenseQuery = arguments.getBoolean(LICENSE_QUERY)
            showInAppPurchase = arguments.getBoolean(SHOW_IN_APP_PURCHASE)
            anotherIdentityHasCallsPermission = arguments.getBoolean(
                ANOTHER_IDENTITY_HAS_CALLS_PERMISSION
            )
        }
        viewModel.updateBytesOwnedIdentity(bytesOwnedIdentity)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SubscriptionStatusScreen(
                    viewModel = viewModel,
                    apiKeyStatus = apiKeyStatus,
                    apiKeyExpirationTimestamp = apiKeyExpirationTimestamp,
                    apiKeyPermissions = apiKeyPermissions ?: emptyList(),
                    licenseQuery = licenseQuery,
                    showInAppPurchase = showInAppPurchase,
                    anotherIdentityHasCallsPermission = anotherIdentityHasCallsPermission,
                )
            }
        }
    }

    companion object {
        const val BYTES_OWNED_IDENTITY: String = "bytes_owned_identity"
        const val API_KEY_STATUS: String = "status"
        const val API_KEY_PERMISSIONS: String = "permissions"
        const val API_KEY_EXPIRATION: String = "expiration"
        const val LICENSE_QUERY: String = "licenseQuery"
        const val SHOW_IN_APP_PURCHASE: String = "showInAppPurchase"
        const val ANOTHER_IDENTITY_HAS_CALLS_PERMISSION: String =
            "anotherIdentityHasCallsPermission"

        fun newInstance(
            bytesOwnedIdentity: ByteArray,
            apiKeyStatus: EngineAPI.ApiKeyStatus?,
            apiKeyExpirationTimestamp: Long?,
            apiKeyPermissions: List<EngineAPI.ApiKeyPermission>?,
            licenseQuery: Boolean,
            showInAppPurchase: Boolean,
            anotherIdentityHasCallsPermission: Boolean
        ): SubscriptionStatusFragment {
            val fragment = SubscriptionStatusFragment()
            val args = Bundle()
            args.putByteArray(BYTES_OWNED_IDENTITY, bytesOwnedIdentity)
            args.putInt(API_KEY_STATUS, OwnedIdentity.serializeApiKeyStatus(apiKeyStatus))
            if (apiKeyExpirationTimestamp != null) {
                args.putLong(API_KEY_EXPIRATION, apiKeyExpirationTimestamp)
            }
            args.putLong(
                API_KEY_PERMISSIONS,
                OwnedIdentity.serializeApiKeyPermissions(apiKeyPermissions)
            )
            args.putBoolean(LICENSE_QUERY, licenseQuery)
            args.putBoolean(SHOW_IN_APP_PURCHASE, showInAppPurchase)
            args.putBoolean(
                ANOTHER_IDENTITY_HAS_CALLS_PERMISSION,
                anotherIdentityHasCallsPermission
            )
            fragment.setArguments(args)
            return fragment
        }
    }
}