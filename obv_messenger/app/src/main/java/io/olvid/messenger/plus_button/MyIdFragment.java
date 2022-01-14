/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.messenger.plus_button;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import java.util.regex.Matcher;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.identities.ObvUrlIdentity;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.ObvLinkActivity;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.entity.OwnedIdentity;

public class MyIdFragment extends Fragment implements View.OnClickListener {
    private AppCompatActivity activity;
    private PlusButtonViewModel viewModel;
    private InitialView identityInitialView;
    private TextView identityTextView;
    private ImageView qrCodeImageView;

    private Button scanButton;
    private Button keycloakSearchButton;

    private ObvUrlIdentity urlIdentity;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plus_button_my_id, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                if (activity.getWindow().getStatusBarColor() == 0xff000000) {
                    ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.almostWhite)).start();
                } else {
                    activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
                }
            } else {
                ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.olvid_gradient_dark)).start();
            }
        }

        viewModel = new ViewModelProvider(activity).get(PlusButtonViewModel.class);

        // set screen brightness to the max
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        if (params != null && activity.getWindow() != null) {
            params.screenBrightness = 1f;
            activity.getWindow().setAttributes(params);
        }

        view.findViewById(R.id.back_button).setOnClickListener(this);
        identityInitialView = view.findViewById(R.id.myid_initial_view);
        identityTextView = view.findViewById(R.id.myid_name_text_view);
        CardView myIdCardView = view.findViewById(R.id.my_id_card_view);
        qrCodeImageView = view.findViewById(R.id.qr_code_image_view);
        Button shareButton = view.findViewById(R.id.share_button);
        scanButton = view.findViewById(R.id.scan_button);
        ImageView moreButton = view.findViewById(R.id.more_button);
        keycloakSearchButton = view.findViewById(R.id.button_keycloak_search);

        myIdCardView.setOnClickListener(this);
        shareButton.setOnClickListener(this);
        scanButton.setOnClickListener(this);
        moreButton.setOnClickListener(this);
        keycloakSearchButton.setOnClickListener(this);

        displayIdentity(viewModel.getCurrentIdentity());
    }

    private void displayIdentity(@Nullable OwnedIdentity identity) {
        if (identity == null) {
            identityInitialView.setInitial(new byte[0], "");
            identityTextView.setText(null);
            qrCodeImageView.setImageResource(R.drawable.ic_broken_image);
            scanButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_camera, 0, R.drawable.empty, 0);
            keycloakSearchButton.setVisibility(View.GONE);
        } else {
            if (identity.keycloakManaged) {
                scanButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_camera, 0, 0, 0);
                keycloakSearchButton.setVisibility(View.VISIBLE);
                identityInitialView.setKeycloakCertified(true);
            } else {
                scanButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_camera, 0, R.drawable.empty, 0);
                keycloakSearchButton.setVisibility(View.GONE);
                identityInitialView.setKeycloakCertified(false);
            }
            identityInitialView.setInactive(!identity.active);
            if (identity.photoUrl != null) {
                identityInitialView.setPhotoUrl(identity.bytesOwnedIdentity, identity.photoUrl);
            } else {
                identityInitialView.setInitial(identity.bytesOwnedIdentity, App.getInitial(identity.displayName));
            }
            identityTextView.setText(identity.displayName);

            JsonIdentityDetails identityDetails = identity.getIdentityDetails();
            if (identityDetails != null) {
                urlIdentity = new ObvUrlIdentity(identity.bytesOwnedIdentity, identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, false));
            } else {
                urlIdentity = new ObvUrlIdentity(identity.bytesOwnedIdentity, identity.displayName);
            }
            App.setQrCodeImage(qrCodeImageView, urlIdentity.getUrlRepresentation());
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.back_button) {
            activity.onBackPressed();
        } else if (id == R.id.my_id_card_view) {
            if (urlIdentity != null) {
                viewModel.setFullScreenQrCodeUrl(urlIdentity.getUrlRepresentation());
                try {
                    Navigation.findNavController(v).navigate(MyIdFragmentDirections.actionOpenFullScreenQrCode());
                } catch (Exception e) {
                    // do nothing
                }
            }
        } else if (id == R.id.scan_button) {
            try {
                Navigation.findNavController(v).navigate(MyIdFragmentDirections.actionScan());
            } catch (Exception e) {
                // do nothing
            }
        } else if (id == R.id.share_button) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            OwnedIdentity ownedIdentity = viewModel.getCurrentIdentity();
            if (ownedIdentity == null) {
                return;
            }
            final ObvUrlIdentity urlIdentity;
            JsonIdentityDetails identityDetails = ownedIdentity.getIdentityDetails();
            if (identityDetails != null) {
                urlIdentity = new ObvUrlIdentity(ownedIdentity.bytesOwnedIdentity, identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, false));
            } else {
                urlIdentity = new ObvUrlIdentity(ownedIdentity.bytesOwnedIdentity, ownedIdentity.displayName);
            }
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.message_user_invitation_subject, ownedIdentity.displayName));
            intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.message_user_invitation, ownedIdentity.displayName, urlIdentity.getUrlRepresentation()));
            startActivity(Intent.createChooser(intent, getString(R.string.title_invite_chooser)));
            activity.finish();
        } else if (id == R.id.more_button) {
            PopupMenu popup = new PopupMenu(activity, v, Gravity.END);
            popup.inflate(R.menu.popup_more_add_contact);
            popup.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.popup_action_import_from_clipboard) {
                    ClipboardManager clipboard = (ClipboardManager) App.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        ClipData clipData = clipboard.getPrimaryClip();
                        if ((clipData != null) && (clipData.getItemCount() > 0)) {
                            CharSequence textChars = clipData.getItemAt(0).getText();
                            if (textChars != null) {
                                Matcher matcher = ObvLinkActivity.ANY_PATTERN.matcher(textChars);
                                if (matcher.find()) {
                                    String text = textChars.toString();
                                    if (ObvLinkActivity.INVITATION_PATTERN.matcher(text).find()) {
                                        viewModel.setScannedUri(text);
                                        viewModel.setDeepLinked(true);
                                        Navigation.findNavController(v).navigate(R.id.invitation_scanned);
                                        return true;
                                    } else if (ObvLinkActivity.CONFIGURATION_PATTERN.matcher(text).find()) {
                                        viewModel.setScannedUri(text);
                                        viewModel.setDeepLinked(true);
                                        Navigation.findNavController(v).navigate(R.id.configuration_scanned);
                                        return true;
                                    } else if (ObvLinkActivity.WEB_CLIENT_PATTERN.matcher(text).find()) {
                                        viewModel.setScannedUri(text);
                                        viewModel.setDeepLinked(true);
                                        Navigation.findNavController(v).navigate(R.id.webclient_scanned);
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                    App.toast(R.string.toast_message_invalid_clipboard_data, Toast.LENGTH_SHORT);
                    return true;
                }
                return false;
            });
            popup.show();
        } else if (id == R.id.button_keycloak_search) {
            try {
                Navigation.findNavController(v).navigate(MyIdFragmentDirections.actionKeycloakSearch());
            } catch (Exception e) {
                // do nothing
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // restore initial brightness
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        if (params != null && activity.getWindow() != null) {
            params.screenBrightness = -1.0f;
            activity.getWindow().setAttributes(params);
        }
    }
}
