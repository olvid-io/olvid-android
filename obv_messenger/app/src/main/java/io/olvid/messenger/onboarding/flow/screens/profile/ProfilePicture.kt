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

package io.olvid.messenger.onboarding.flow.screens.profile

import android.Manifest.permission
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.onboarding.flow.OnboardingFlowViewModel
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import io.olvid.messenger.owneddetails.SelectDetailsPhotoActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun NavGraphBuilder.profilePicture(context : Context, onboardingFlowViewModel: OnboardingFlowViewModel) {
    composable(
        OnboardingRoutes.PROFILE_PICTURE,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        val activity = LocalActivity.current
        fun finishAndOpenDiscussionsTab() {
            activity?.finish()
            App.showMainActivityTab(context, MainActivity.DISCUSSIONS_TAB)
        }
        BackHandler {
            finishAndOpenDiscussionsTab()
        }
        val photoDir =
            File(context.cacheDir, App.CAMERA_PICTURE_FOLDER)
        val photoFile = File(
            photoDir,
            SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(
                Date()
            ) + ".jpg"
        )
        //noinspection ResultOfMethodCallIgnored
        photoDir.mkdirs()
        val photoUri = FileProvider.getUriForFile(
            context,
            BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
            photoFile
        )

        val selectDetailsPhoto =
            rememberLauncherForActivityResult(StartActivityForResult()) { result ->
                if (result.resultCode == AppCompatActivity.RESULT_OK) {
                    onboardingFlowViewModel.absolutePhotoUrl =
                        result.data?.getStringExtra(SelectDetailsPhotoActivity.CROPPED_JPEG_RETURN_INTENT_EXTRA)
                }
            }
        val getContent =
            rememberLauncherForActivityResult(GetContent()) { uri ->
                if (StringUtils.validateUri(uri)) {
                    selectDetailsPhoto.launch(
                        Intent(
                            null,
                            uri,
                            context,
                            SelectDetailsPhotoActivity::class.java
                        )
                    )
                }
            }

        val cameraLauncher =
            rememberLauncherForActivityResult(
                TakePicture()
            ) {
                onboardingFlowViewModel.capturedImageUri = photoUri
                selectDetailsPhoto.launch(
                    Intent(
                        null,
                        onboardingFlowViewModel.capturedImageUri,
                        context,
                        SelectDetailsPhotoActivity::class.java
                    )
                )
            }

        val permissionLauncher = rememberLauncherForActivityResult(
            RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                cameraLauncher.launch(photoUri)
            } else {
                App.toast(
                    string.toast_message_camera_permission_denied,
                    Toast.LENGTH_SHORT
                )
            }
        }
        val ownedIdentity = AppSingleton.getCurrentIdentityLiveData().value

        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(
                    id = string.onboarding_picture_title,
                    ownedIdentity?.getCustomDisplayName()
                        ?: ""),
                subtitle = stringResource(id = string.onboarding_picture_subtitle),
            ),
            onClose = { finishAndOpenDiscussionsTab() }
        ) {
            Box(modifier = Modifier
                .padding(16.dp)
                .requiredSize(160.dp),
            ) {
                InitialView(
                    modifier = Modifier.fillMaxSize(),
                    initialViewSetup = { initialView ->
                        ownedIdentity?.let { initialView.setOwnedIdentity(it) }
                        onboardingFlowViewModel.absolutePhotoUrl?.let {
                            initialView.setAbsolutePhotoUrl(
                                ownedIdentity?.bytesOwnedIdentity,
                                it
                            )
                        }
                    }
                )
                if (onboardingFlowViewModel.absolutePhotoUrl != null) {
                    Image(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .requiredSize(32.dp)
                            .clickable { onboardingFlowViewModel.absolutePhotoUrl = null },
                        painter = painterResource(id = drawable.ic_remove),
                        contentDescription = stringResource(id = string.content_description_remove_picture)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row (
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f, true).fillMaxHeight(),
                    elevation = null,
                    onClick = {
                        val permissionCheckResult =
                            ContextCompat.checkSelfPermission(
                                context,
                                permission.CAMERA
                            )
                        if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(photoUri)
                        } else {
                            permissionLauncher.launch(permission.CAMERA)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorResource(id = color.blueOrWhite))
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(id = drawable.ic_attach_camera),
                        contentDescription = ""
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = string.menu_action_take_photo))
                }

                Spacer(modifier = Modifier.width(16.dp))

                OutlinedButton(
                    modifier = Modifier.weight(1f, true).fillMaxHeight(),
                    elevation = null,
                    onClick = {
                        getContent.launch("image/*")
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorResource(id = color.blueOrWhite))
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(id = drawable.ic_attach_image),
                        contentDescription = ""
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = string.menu_action_choose_picture))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row (
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    enabled = onboardingFlowViewModel.absolutePhotoUrl == null,
                    onClick = ::finishAndOpenDiscussionsTab,
                    colors = ButtonDefaults.textButtonColors(contentColor = colorResource(id = color.olvid_gradient_contrasted), disabledContentColor = Color.Transparent)
                ) {
                    Text(text = stringResource(id = R.string.button_label_maybe_later))
                }

                Button(
                    enabled = onboardingFlowViewModel.absolutePhotoUrl != null,
                    elevation = null,
                    colors = ButtonDefaults.buttonColors(
                        contentColor = colorResource(R.color.alwaysWhite),
                        backgroundColor = colorResource(R.color.olvid_gradient_light),
                        disabledBackgroundColor = colorResource(R.color.olvid_gradient_light).copy(alpha = .5f)
                    ),
                    onClick = {
                        try {
                            AppSingleton.getEngine().updateOwnedIdentityPhoto(
                                ownedIdentity?.bytesOwnedIdentity,
                                onboardingFlowViewModel.absolutePhotoUrl ?: ""
                            )
                            AppSingleton.getEngine()
                                .publishLatestIdentityDetails(ownedIdentity?.bytesOwnedIdentity)
                        } catch (_: Exception) {
                        } finally {
                            finishAndOpenDiscussionsTab()
                        }
                    }
                ) {
                    Text(text = stringResource(id = string.button_label_done))
                }
            }
        }
    }
}