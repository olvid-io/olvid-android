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

package io.olvid.messenger.discussion

import android.content.DialogInterface
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LocationIntegrationSelectorDialog
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonLocation
import io.olvid.messenger.discussion.location.LocationActivity
import io.olvid.messenger.services.UnifiedForegroundService.LocationSharingSubService
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.BASIC
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.CUSTOM_OSM
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.MAPS
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.NONE
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.OSM

class LocationMessageHandler(
    private val activity: DiscussionActivity,
    private val discussionViewModel: DiscussionViewModel
) {

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun onLocationClick(message: Message) {
        when (SettingsActivity.locationIntegration) {
            OSM, CUSTOM_OSM, MAPS -> {
                openMap(message)
            }

            BASIC -> {
                // if basic integration is configured
                if (message.hasAttachments()) {
                    // if have a preview: show preview
                    App.runThread {
                        val fyleAndStatuses =
                            AppDatabase.getInstance()
                                .fyleMessageJoinWithStatusDao()
                                .getFylesAndStatusForMessageSync(message.id)

                        discussionViewModel.markAsReadOnPause = false
                        if (fyleAndStatuses.size == 1) {
                            App.openDiscussionGalleryActivity(
                                activity,
                                discussionViewModel.discussionId ?: -1,
                                message.id,
                                fyleAndStatuses[0].fyle.id,
                                true,
                                false
                            )
                        } else {
                            // in case we don't have a single attachment, simply open the message gallery... This should never happen :)
                            App.openMessageGalleryActivity(
                                activity,
                                message.id,
                                -1,
                                false
                            )
                        }
                    }
                } else {
                    try {
                        message.jsonLocation?.let {
                            AppSingleton.getJsonObjectMapper()
                                .readValue(it, JsonLocation::class.java)
                        }?.let { jsonLocation ->
                            // else : open in a third party app
                            App.openLocationInMapApplication(
                                activity,
                                jsonLocation.truncatedLatitudeString,
                                jsonLocation.truncatedLongitudeString,
                                message.contentBody
                            ) { discussionViewModel.markAsReadOnPause = false }
                        }
                    } catch (e : Exception) {
                        Logger.x(e)
                    }
                }
            }

            NONE -> {
                // if no integration is configured, offer to choose an integration
                LocationIntegrationSelectorDialog(
                    activity,
                    object : LocationIntegrationSelectorDialog.OnIntegrationSelectedListener {
                        override fun onIntegrationSelected(
                            integration: LocationIntegrationEnum,
                            customOsmServerUrl: String?
                        ) {
                            SettingsActivity.setLocationIntegration(
                                integration.string,
                                customOsmServerUrl
                            )
                            // re-run onClick if something was selected
                            if (integration == OSM || integration == MAPS || integration == BASIC || integration == CUSTOM_OSM) {
                                openMap(message)
                            }
                        }
                    }).show()
            }
        }
    }

    fun openLocationPreviewInGallery(message: Message) {
        App.runThread {
            val fyleAndStatuses = AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                .getFylesAndStatusForMessageSync(message.id)
            discussionViewModel.markAsReadOnPause = false
            if (fyleAndStatuses.size == 1) {
                App.openDiscussionGalleryActivity(
                    activity,
                    discussionViewModel.discussionId ?: -1,
                    message.id,
                    fyleAndStatuses[0].fyle.id,
                    true,
                    false
                )
            } else {
                // in case we don't have a single attachment, simply open the message gallery... This should never happen :)
                App.openMessageGalleryActivity(activity, message.id, -1, false)
            }
        }
    }

    fun openMap(message: Message? = null) {
        // if a map integration is configured: open fullscreen map (behaviour will change depending on message.locationType)
        LocationActivity.start(
            activity,
            message,
            discussionViewModel.discussionId,
            null,
            SettingsActivity.locationIntegration
        )
    }

    fun stopSharingLocation() {
        SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
            .setTitle(R.string.title_stop_sharing_location)
            .setMessage(R.string.label_stop_sharing_location)
            .setPositiveButton(R.string.button_label_stop) { _: DialogInterface?, _: Int ->
                LocationSharingSubService.stopSharingInDiscussion(
                    discussionViewModel.discussionId ?: -1, false
                )
            }
            .setNegativeButton(R.string.button_label_cancel, null)
            .show()
    }

    fun changeIntegration() {
        LocationIntegrationSelectorDialog(
            activity,
            object : LocationIntegrationSelectorDialog.OnIntegrationSelectedListener {
                override fun onIntegrationSelected(
                    integration: LocationIntegrationEnum,
                    customOsmServerUrl: String?
                ) {
                    SettingsActivity.setLocationIntegration(
                        integration.string,
                        customOsmServerUrl
                    )
                }
            }).show()
    }
}