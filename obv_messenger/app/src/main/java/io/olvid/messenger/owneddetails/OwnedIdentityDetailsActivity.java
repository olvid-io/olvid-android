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

package io.olvid.messenger.owneddetails;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.ObvDeviceManagementRequest;
import io.olvid.engine.engine.types.SimpleEngineNotificationListener;
import io.olvid.engine.engine.types.identities.ObvUrlIdentity;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.Markdown;
import io.olvid.messenger.customClasses.MuteNotificationDialog;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.OwnedDevice;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.DeleteOwnedIdentityAndEverythingRelatedToItTask;
import io.olvid.messenger.databases.tasks.OwnedDevicesSynchronisationWithEngineTask;
import io.olvid.messenger.fragments.FullScreenImageFragment;
import io.olvid.messenger.fragments.SubscriptionStatusFragment;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.onboarding.flow.OnboardingFlowActivity;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.plus_button.PlusButtonActivity;
import io.olvid.messenger.settings.SettingsActivity;


public class OwnedIdentityDetailsActivity extends LockableActivity implements View.OnClickListener {
    public static final String FULL_SCREEN_IMAGE_FRAGMENT_TAG = "full_screen_image";

    private InitialView myIdInitialView;
    private TextView myIdNameTextView;
    private CardView latestDetailsCardView;
    private LinearLayout latestDetailsTextViews;
    private InitialView latestDetailsInitialView;
    private LinearLayout publishedDetailsTextViews;
    private InitialView publishedDetailsInitialView;
    private CardView inactiveCardView;
    private boolean keycloakManaged = false;

    private EngineNotificationListener deviceChangedEngineListener = null;

    private JsonIdentityDetailsWithVersionAndPhoto latestDetails;
    private IdentityObserver identityObserver;
    private int primary700;

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        OwnedIdentityDetailsActivityViewModel viewModel = new ViewModelProvider(this).get(OwnedIdentityDetailsActivityViewModel.class);

        setContentView(R.layout.activity_owned_identity_details);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        myIdInitialView = findViewById(R.id.myid_initial_view);
        myIdInitialView.setOnClickListener(this);
        myIdNameTextView = findViewById(R.id.myid_name_text_view);

        inactiveCardView = findViewById(R.id.identity_inactive_card_view);
        Button reactivateIdButton = findViewById(R.id.button_reactivate_identity);
        reactivateIdButton.setOnClickListener(this);

        latestDetailsCardView = findViewById(R.id.latest_details_cardview);
        latestDetailsTextViews = findViewById(R.id.latest_details_textviews);
        latestDetailsInitialView = findViewById(R.id.latest_details_initial_view);
        latestDetailsInitialView.setOnClickListener(this);
        publishedDetailsTextViews = findViewById(R.id.published_details_textviews);
        publishedDetailsInitialView = findViewById(R.id.published_details_initial_view);
        publishedDetailsInitialView.setOnClickListener(this);

        Button publishButton = findViewById(R.id.button_publish);
        publishButton.setOnClickListener(this);
        Button discardButton = findViewById(R.id.button_discard);
        discardButton.setOnClickListener(this);
        Button addContactButton = findViewById(R.id.add_contact_button);
        addContactButton.setOnClickListener(this);

        DiffUtil.ItemCallback<OwnedDevice> diffUtilCallback = new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull OwnedDevice oldItem, @NonNull OwnedDevice newItem) {
                return Arrays.equals(oldItem.bytesDeviceUid, newItem.bytesDeviceUid);
            }

            @Override
            public boolean areContentsTheSame(@NonNull OwnedDevice oldItem, @NonNull OwnedDevice newItem) {
                return Objects.equals(oldItem.displayName, newItem.displayName)
                        && (oldItem.channelConfirmed == newItem.channelConfirmed)
                        && (oldItem.hasPreKey == newItem.hasPreKey)
                        && (oldItem.currentDevice == newItem.currentDevice)
                        && (oldItem.trusted == newItem.trusted)
                        && Objects.equals(oldItem.lastRegistrationTimestamp, newItem.lastRegistrationTimestamp)
                        && Objects.equals(oldItem.expirationTimestamp, newItem.expirationTimestamp);
            }
        };
        ListAdapter<OwnedDevice, OwnedDeviceViewHolder> deviceListAdapter = new ListAdapter<>(diffUtilCallback) {
            @NonNull
            @Override
            public OwnedDeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new OwnedDeviceViewHolder(getLayoutInflater().inflate(R.layout.item_view_owned_device, parent, false), viewModel); }
            @Override
            public void onBindViewHolder(@NonNull OwnedDeviceViewHolder holder, int position) { holder.bind(getItem(position), !Objects.equals(viewModel.ownedIdentityActive.getValue(), false)); }
            @Override
            public void onViewRecycled(@NonNull OwnedDeviceViewHolder holder) { holder.unbind(); }
        };

        RecyclerView deviceListRecyclerView = findViewById(R.id.device_list_recycler_view);
        deviceListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        deviceListRecyclerView.setAdapter(deviceListAdapter);
        viewModel.ownedDevicesLiveData.observe(this, deviceListAdapter::submitList);
        viewModel.ownedIdentityActive.observe(this, active -> deviceListAdapter.notifyDataSetChanged());

        Button addDeviceButton = findViewById(R.id.add_device_button);
        addDeviceButton.setOnClickListener(this);

        View loadingSpinner = findViewById(R.id.loading_spinner);
        viewModel.showRefreshSpinner.observe(this, refreshing -> loadingSpinner.setVisibility(refreshing ? View.VISIBLE : View.GONE));

        primary700 = ContextCompat.getColor(this, R.color.primary700);

        identityObserver = new IdentityObserver();
        AppSingleton.getCurrentIdentityLiveData().observe(this, identityObserver);

        deviceChangedEngineListener = new SimpleEngineNotificationListener(EngineNotifications.OWNED_IDENTITY_DEVICE_LIST_CHANGED) {
            @Override
            public void callback(HashMap<String, Object> userInfo) {
                viewModel.hideRefreshSpinner();
            }
        };
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.OWNED_IDENTITY_DEVICE_LIST_CHANGED, deviceChangedEngineListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceChangedEngineListener != null) {
            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.OWNED_IDENTITY_DEVICE_LIST_CHANGED, deviceChangedEngineListener);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_owned_identity_details, menu);
        if (keycloakManaged) {
            getMenuInflater().inflate(R.menu.menu_owned_identity_details_keycloak, menu);
        }
        if (identityObserver.ownedIdentity != null) {
            if (identityObserver.ownedIdentity.shouldMuteNotifications()) {
                getMenuInflater().inflate(R.menu.menu_owned_identity_details_muted, menu);
            }
            if (identityObserver.ownedIdentity.isHidden()) {
                getMenuInflater().inflate(R.menu.menu_owned_identity_details_hidden, menu);
                MenuItem neutralNotificationItem = menu.findItem(R.id.action_neutral_notification);
                neutralNotificationItem.setChecked(identityObserver.ownedIdentity.prefShowNeutralNotificationWhenHidden);
            }
        }
        // make the delete profile item red
        MenuItem deleteItem = menu.findItem(R.id.action_delete_owned_identity);
        if (deleteItem != null) {
            SpannableString spannableString = new SpannableString(deleteItem.getTitle());
            spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            deleteItem.setTitle(spannableString);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_unmute) {
            if (identityObserver.ownedIdentity != null && identityObserver.ownedIdentity.shouldMuteNotifications()) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_unmute_notifications)
                        .setPositiveButton(R.string.button_label_unmute_notifications, (dialog, which) -> App.runThread(() -> {
                            identityObserver.ownedIdentity.prefMuteNotifications = false;
                            AppDatabase.getInstance().ownedIdentityDao().updateMuteNotifications(identityObserver.ownedIdentity.bytesOwnedIdentity, identityObserver.ownedIdentity.prefMuteNotifications, null, identityObserver.ownedIdentity.prefMuteNotificationsExceptMentioned);
                        }))
                        .setNegativeButton(R.string.button_label_cancel, null);

                if (identityObserver.ownedIdentity.prefMuteNotificationsTimestamp == null) {
                    builder.setMessage(R.string.dialog_message_unmute_notifications);
                } else {
                    builder.setMessage(getString(R.string.dialog_message_unmute_notifications_muted_until, StringUtils.getLongNiceDateString(this, identityObserver.ownedIdentity.prefMuteNotificationsTimestamp)));
                }
                builder.create().show();
            }
            return true;
        } else if (itemId == R.id.action_mute) {
            MuteNotificationDialog muteNotificationDialog = new MuteNotificationDialog(this, (Long muteExpirationTimestamp, boolean muteWholeProfile, boolean exceptMentioned) -> App.runThread(() -> {
                if (identityObserver.ownedIdentity != null) {
                    identityObserver.ownedIdentity.prefMuteNotifications = true;
                    identityObserver.ownedIdentity.prefMuteNotificationsTimestamp = muteExpirationTimestamp;
                    identityObserver.ownedIdentity.prefMuteNotificationsExceptMentioned = exceptMentioned;
                    AppDatabase.getInstance().ownedIdentityDao().updateMuteNotifications(identityObserver.ownedIdentity.bytesOwnedIdentity, identityObserver.ownedIdentity.prefMuteNotifications, identityObserver.ownedIdentity.prefMuteNotificationsTimestamp, identityObserver.ownedIdentity.prefMuteNotificationsExceptMentioned);
                }
            }), MuteNotificationDialog.MuteType.PROFILE, identityObserver.ownedIdentity == null || identityObserver.ownedIdentity.prefMuteNotificationsExceptMentioned);

            muteNotificationDialog.show();
            return true;
        } else if (itemId == R.id.action_neutral_notification) {
            if (identityObserver.ownedIdentity != null) {
                if (identityObserver.ownedIdentity.prefShowNeutralNotificationWhenHidden) {
                    App.runThread(() -> AppDatabase.getInstance().ownedIdentityDao().updateShowNeutralNotificationWhenHidden(identityObserver.ownedIdentity.bytesOwnedIdentity, false));
                } else {
                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_neutral_notification_when_hidden)
                            .setMessage(R.string.dialog_message_neutral_notification_when_hidden)
                            .setPositiveButton(R.string.button_label_activate, (dialog, which) -> App.runThread(() -> AppDatabase.getInstance().ownedIdentityDao().updateShowNeutralNotificationWhenHidden(identityObserver.ownedIdentity.bytesOwnedIdentity, true)))
                            .setNegativeButton(R.string.button_label_cancel, null);
                    builder.create().show();
                }
            }
            return true;
        } else if (itemId == R.id.action_rename) {
            if (identityObserver.ownedIdentity != null) {
                EditOwnedIdentityDetailsDialogFragment dialogFragment = EditOwnedIdentityDetailsDialogFragment.newInstance(
                        this,
                        identityObserver.ownedIdentity.bytesOwnedIdentity,
                        latestDetails,
                        identityObserver.ownedIdentity.customDisplayName,
                        identityObserver.ownedIdentity.unlockPassword != null,
                        identityObserver.ownedIdentity.keycloakManaged,
                        identityObserver.ownedIdentity.active,
                        () -> identityObserver.reload());
                dialogFragment.show(getSupportFragmentManager(), "dialog");
            }
            return true;
        } else if (itemId == R.id.action_refresh_subscription_status) {
            if (identityObserver.ownedIdentity == null) {
                return true;
            }
            AppSingleton.getEngine().recreateServerSession(identityObserver.ownedIdentity.bytesOwnedIdentity);
            return true;
        } else if (itemId == R.id.action_unbind_from_keycloak) {
            if (identityObserver.ownedIdentity != null) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_unbind_from_keycloak)
                        .setMessage(R.string.dialog_message_unbind_from_keycloak)
                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> {
                            KeycloakManager.getInstance().unregisterKeycloakManagedIdentity(identityObserver.ownedIdentity.bytesOwnedIdentity);
                            AppSingleton.getEngine().unbindOwnedIdentityFromKeycloak(identityObserver.ownedIdentity.bytesOwnedIdentity);
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            }
            return true;
        } else if (itemId == R.id.action_delete_owned_identity) {
            App.runThread(() -> {
                OwnedIdentity ownedIdentity = identityObserver.ownedIdentity;
                if (ownedIdentity == null) {
                    return;
                }

                // first check if this is the last profile (or last unhidden profile)
                int otherNotHiddenOwnedIdentityCount = AppDatabase.getInstance().ownedIdentityDao().countNotHidden() - (ownedIdentity.isHidden() ? 0 : 1);
                // also check if there are other devices
                boolean hasOtherDevices = AppDatabase.getInstance().ownedDeviceDao().getAllSync(ownedIdentity.bytesOwnedIdentity).size() > 1;

                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_delete_profile)
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setPositiveButton(R.string.button_label_next, (DialogInterface dialogInterface, int which) -> showDeleteProfileDialog(ownedIdentity, otherNotHiddenOwnedIdentityCount < 1, hasOtherDevices));

                if (hasOtherDevices) {
                    builder.setMessage((otherNotHiddenOwnedIdentityCount >= 1) ? R.string.dialog_message_delete_profile_multi : R.string.dialog_message_delete_last_profile_multi);
                } else {
                    builder.setMessage((otherNotHiddenOwnedIdentityCount >= 1) ? R.string.dialog_message_delete_profile : R.string.dialog_message_delete_last_profile);
                }
                runOnUiThread(() -> builder.create().show());
            });
            return true;
        } else if (itemId == R.id.action_debug_information) {
            OwnedIdentity ownedIdentity = AppSingleton.getCurrentIdentityLiveData().getValue();
            if (ownedIdentity != null) {
                StringBuilder sb = new StringBuilder();
                try {
                    Identity ownIdentity = Identity.of(ownedIdentity.bytesOwnedIdentity);
                    sb.append(getString(R.string.debug_label_server)).append(" ");
                    sb.append(ownIdentity.getServer()).append("\n\n");
                } catch (DecodingException ignored) {}
                sb.append(getString(R.string.debug_label_identity_link)).append("\n");
                sb.append(new ObvUrlIdentity(ownedIdentity.bytesOwnedIdentity, ownedIdentity.displayName).getUrlRepresentation()).append("\n\n");
                sb.append(getString(R.string.debug_label_capabilities)).append("\n");
                sb.append(getString(R.string.bullet)).append(" ").append(getString(R.string.debug_label_capability_continuous_gathering, ownedIdentity.capabilityWebrtcContinuousIce)).append("\n");
                sb.append(getString(R.string.bullet)).append(" ").append(getString(R.string.debug_label_capability_one_to_one_contacts, ownedIdentity.capabilityOneToOneContacts)).append("\n");
                sb.append(getString(R.string.bullet)).append(" ").append(getString(R.string.debug_label_capability_groups_v2, ownedIdentity.capabilityGroupsV2)).append("\n");

                TextView textView = new TextView(this);
                int sixteenDp = (int) (16 * getResources().getDisplayMetrics().density);
                textView.setPadding(sixteenDp, sixteenDp, sixteenDp, sixteenDp);
                textView.setTextIsSelectable(true);
                textView.setAutoLinkMask(Linkify.ALL);
                textView.setMovementMethod(LinkMovementMethod.getInstance());
                textView.setText(sb);

                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.menu_action_debug_information)
                        .setView(textView)
                        .setPositiveButton(R.string.button_label_ok, null);
                builder.create().show();
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private boolean deleteProfileEverywhere = true;

    private void showDeleteProfileDialog(OwnedIdentity ownedIdentity, boolean deleteAllHiddenOwnedIdentities, boolean hasOtherDevices) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_view_delete_profile, null);
        Button cancelButton = dialogView.findViewById(R.id.cancel_button);
        Button deleteButton = dialogView.findViewById(R.id.delete_button);
        deleteButton.setEnabled(false);
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch deleteEverywhereSwitch = dialogView.findViewById(R.id.delete_profile_everywhere_switch);
        deleteEverywhereSwitch.setOnCheckedChangeListener((CompoundButton compoundButton, boolean checked) -> {
            deleteProfileEverywhere = checked;
            deleteButton.setText(checked ? R.string.button_label_delete_everywhere : R.string.button_label_delete);
        });
        EditText typeDeleteEditText = dialogView.findViewById(R.id.type_delete_edit_text);
        typeDeleteEditText.addTextChangedListener(new TextChangeListener() {
            final String target = getString(R.string.text_delete_capitalized);

            @Override
            public void afterTextChanged(Editable s) {
                deleteButton.setEnabled(s != null && target.equals(s.toString().toUpperCase()));
            }
        });
        TextView explanationTextView = dialogView.findViewById(R.id.delete_dialog_confirmation_explanation);
        explanationTextView.setMovementMethod(ScrollingMovementMethod.getInstance());
        if (ownedIdentity.active) {
            if (hasOtherDevices) {
                explanationTextView.setText(R.string.explanation_delete_owned_identity_multi);
                deleteEverywhereSwitch.setChecked(false);
                deleteProfileEverywhere = false;
            } else {
                explanationTextView.setText(R.string.explanation_delete_owned_identity);
                deleteEverywhereSwitch.setChecked(true);
                deleteProfileEverywhere = true;
            }
            deleteEverywhereSwitch.setEnabled(true);
        } else {
            explanationTextView.setText(R.string.explanation_delete_inactive_owned_identity);
            deleteEverywhereSwitch.setChecked(false);
            deleteEverywhereSwitch.setEnabled(false);
            deleteProfileEverywhere = false;
        }

        AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_delete_profile)
                .setView(dialogView);
        final Dialog dialog = builder.create();
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        deleteButton.setOnClickListener(v -> App.runThread(() -> {
            List<byte[]> bytesOwnedIdentities = new ArrayList<>();
            bytesOwnedIdentities.add(ownedIdentity.bytesOwnedIdentity);
            if (deleteAllHiddenOwnedIdentities) {
                List<OwnedIdentity> hiddenOwnedIdentities = AppDatabase.getInstance().ownedIdentityDao().getAllHidden();
                for (OwnedIdentity hiddenOwnedIdentity : hiddenOwnedIdentities) {
                    bytesOwnedIdentities.add(hiddenOwnedIdentity.bytesOwnedIdentity);
                }
            }

            try {
                for (byte[] bytesOwnedIdentity : bytesOwnedIdentities) {
                    AppSingleton.getEngine().deleteOwnedIdentityAndNotifyContacts(bytesOwnedIdentity, deleteProfileEverywhere);
                }
                runOnUiThread(dialog::dismiss);
                for (byte[] bytesOwnedIdentity : bytesOwnedIdentities) {
                    App.runThread(new DeleteOwnedIdentityAndEverythingRelatedToItTask(bytesOwnedIdentity));
                }
                finish();
                App.toast(R.string.toast_message_profile_deleted, Toast.LENGTH_SHORT);
            } catch (Exception e) {
                App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT);
            }
        }));

        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        }
        dialog.show();
    }

    public void displayDetails(OwnedIdentity ownedIdentity) {
        if (ownedIdentity == null) {
            finish();
            return;
        }

        keycloakManaged = ownedIdentity.keycloakManaged;
        invalidateOptionsMenu();

        myIdInitialView.setOwnedIdentity(ownedIdentity);
        myIdNameTextView.setText(ownedIdentity.getCustomDisplayName());

        if (ownedIdentity.active) {
            inactiveCardView.setVisibility(View.GONE);
        } else {
            inactiveCardView.setVisibility(View.VISIBLE);
        }

        SubscriptionStatusFragment subscriptionStatusFragment = SubscriptionStatusFragment.newInstance(ownedIdentity.bytesOwnedIdentity, ownedIdentity.getApiKeyStatus(), ownedIdentity.apiKeyExpirationTimestamp, ownedIdentity.getApiKeyPermissions(), false, !keycloakManaged, AppSingleton.getOtherProfileHasCallsPermission());
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.subscription_status_placeholder, subscriptionStatusFragment);
        transaction.commit();

        try {
            JsonIdentityDetailsWithVersionAndPhoto[] jsons = AppSingleton.getEngine().getOwnedIdentityPublishedAndLatestDetails(ownedIdentity.bytesOwnedIdentity);
            if (jsons == null || jsons.length == 0) {
                return;
            }
            publishedDetailsTextViews.removeAllViews();
            JsonIdentityDetails publishedDetails = jsons[0].getIdentityDetails();
            String publishedFirstLine = publishedDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
            String publishedSecondLine = publishedDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
            if (jsons[0].getPhotoUrl() != null) {
                publishedDetailsInitialView.setPhotoUrl(ownedIdentity.bytesOwnedIdentity, jsons[0].getPhotoUrl());
            } else {
                publishedDetailsInitialView.setInitial(ownedIdentity.bytesOwnedIdentity, StringUtils.getInitial(publishedFirstLine));
            }
            {
                TextView tv = makeTextView();
                tv.setText(publishedFirstLine);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                publishedDetailsTextViews.addView(tv);
            }
            if (publishedSecondLine != null && publishedSecondLine.length() > 0) {
                TextView tv = makeTextView();
                tv.setText(publishedSecondLine);
                publishedDetailsTextViews.addView(tv);
            }
            if (publishedDetails.getCustomFields() != null) {
                List<String> keys = new ArrayList<>(publishedDetails.getCustomFields().size());
                keys.addAll(publishedDetails.getCustomFields().keySet());
                Collections.sort(keys);
                for (String key: keys) {
                    TextView tv = makeTextView();
                    String value = publishedDetails.getCustomFields().get(key);
                    SpannableString spannableString = new SpannableString(getString(R.string.format_identity_details_custom_field, key, value));
                    spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tv.setText(spannableString);
                    publishedDetailsTextViews.addView(tv);
                }
            }

            // update OwnedIdentity photoUrl if not in sync
            if ((ownedIdentity.photoUrl == null && jsons[0].getPhotoUrl() != null) ||
                    (ownedIdentity.photoUrl != null && !ownedIdentity.photoUrl.equals(jsons[0].getPhotoUrl()))) {
                App.runThread(() -> {
                    ownedIdentity.photoUrl = jsons[0].getPhotoUrl();
                    AppDatabase.getInstance().ownedIdentityDao().updateIdentityDetailsAndPhoto(ownedIdentity.bytesOwnedIdentity, ownedIdentity.identityDetails, ownedIdentity.displayName, ownedIdentity.photoUrl);
                });
            }

            if (jsons.length == 2) {
                latestDetails = jsons[1];
                latestDetailsCardView.setVisibility(View.VISIBLE);
                latestDetailsTextViews.removeAllViews();
                JsonIdentityDetails latestDetails = jsons[1].getIdentityDetails();
                String latestFirstLine  = latestDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                String latestSecondLine = latestDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                if (jsons[1].getPhotoUrl() != null) {
                    latestDetailsInitialView.setPhotoUrl(ownedIdentity.bytesOwnedIdentity, jsons[1].getPhotoUrl());
                } else {
                    latestDetailsInitialView.setInitial(ownedIdentity.bytesOwnedIdentity, StringUtils.getInitial(latestFirstLine));
                }
                {
                    TextView tv = makeTextView();
                    tv.setText(latestFirstLine);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    if (!latestFirstLine.equals(publishedFirstLine)) {
                        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                    }
                    latestDetailsTextViews.addView(tv);
                }
                if (latestSecondLine != null && latestSecondLine.length() > 0) {
                    TextView tv = makeTextView();
                    tv.setText(latestSecondLine);
                    if (!latestSecondLine.equals(publishedSecondLine)) {
                        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                    }
                    latestDetailsTextViews.addView(tv);
                }
                if (latestDetails.getCustomFields() != null) {
                    List<String> keys = new ArrayList<>(latestDetails.getCustomFields().size());
                    keys.addAll(latestDetails.getCustomFields().keySet());
                    Collections.sort(keys);
                    for (String key: keys) {
                        TextView tv = makeTextView();
                        String value = latestDetails.getCustomFields().get(key);
                        if (value == null) {
                            continue;
                        }
                        SpannableString spannableString = new SpannableString(getString(R.string.format_identity_details_custom_field, key, value));
                        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        tv.setText(spannableString);
                        if (!(publishedDetails.getCustomFields() != null && value.equals(publishedDetails.getCustomFields().get(key)))) {
                            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                        }
                        latestDetailsTextViews.addView(tv);
                    }
                }

                if (ownedIdentity.unpublishedDetails == OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW) {
                    ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_EXIST;
                    AppDatabase.getInstance().ownedIdentityDao().updateUnpublishedDetails(ownedIdentity.bytesOwnedIdentity, ownedIdentity.unpublishedDetails);
                }
            } else {
                latestDetails = jsons[0];
                latestDetailsCardView.setVisibility(View.GONE);

                if (ownedIdentity.unpublishedDetails != OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW) {
                    ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW;
                    AppDatabase.getInstance().ownedIdentityDao().updateUnpublishedDetails(ownedIdentity.bytesOwnedIdentity, ownedIdentity.unpublishedDetails);
                }
            }
        } catch (Exception e) {
            // nothing to do
        }
    }

    private TextView makeTextView() {
        TextView tv = new AppCompatTextView(this);
        tv.setTextColor(primary700);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setMaxLines(4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, getResources().getDimensionPixelSize(R.dimen.identity_details_margin), 0, 0);
        tv.setLayoutParams(params);
        return tv;
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            Fragment fullScreenImageFragment = getSupportFragmentManager().findFragmentByTag(FULL_SCREEN_IMAGE_FRAGMENT_TAG);
            if (fullScreenImageFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(0, R.anim.fade_out)
                        .remove(fullScreenImageFragment)
                        .commit();
            }
        }
        return super.dispatchTouchEvent(event);
    }


    @Override
    public void onBackPressed() {
        Fragment fullScreenImageFragment = getSupportFragmentManager().findFragmentByTag(FULL_SCREEN_IMAGE_FRAGMENT_TAG);
        if (fullScreenImageFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(0, R.anim.fade_out)
                    .remove(fullScreenImageFragment)
                    .commit();
        } else {
            if (isTaskRoot()) {
                App.showMainActivityTab(this, MainActivity.DISCUSSIONS_TAB);
            }
            finish();
        }
    }

    @Override
    public void onClick(View view) {
        byte[] bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity();
        if (bytesOwnedIdentity == null) {
            return;
        }

        int id = view.getId();
        if (id == R.id.add_contact_button) {
            startActivity(new Intent(this, PlusButtonActivity.class));
        } else if (id == R.id.button_publish) {
            AppSingleton.getEngine().publishLatestIdentityDetails(bytesOwnedIdentity);
        } else if (id == R.id.button_discard) {
            AppSingleton.getEngine().discardLatestIdentityDetails(bytesOwnedIdentity);
            identityObserver.reload();
        } else if (id == R.id.button_reactivate_identity) {
            OwnedIdentity ownedIdentity = AppSingleton.getCurrentIdentityLiveData().getValue();
            if (ownedIdentity != null) {
                App.openAppDialogIdentityDeactivated(ownedIdentity);
            }
        } else if (id == R.id.add_device_button) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());
            if (!prefs.getBoolean(SettingsActivity.USER_DIALOG_HIDE_ADD_DEVICE_EXPLANATION, false)) {
                View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_message_and_checkbox, null);
                TextView message = dialogView.findViewById(R.id.dialog_message);
                message.setText(Markdown.formatMarkdown(getString(R.string.dialog_message_add_device_explanation)));
                CheckBox checkBox = dialogView.findViewById(R.id.checkbox);
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(SettingsActivity.USER_DIALOG_HIDE_ADD_DEVICE_EXPLANATION, isChecked);
                    editor.apply();
                });

                AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog);
                builder.setTitle(R.string.dialog_title_add_device_explanation)
                        .setView(dialogView)
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setPositiveButton(R.string.button_label_proceed, (DialogInterface dialog, int which) -> {
                            Intent intent = new Intent(this, OnboardingFlowActivity.class);
                            intent.putExtra(OnboardingFlowActivity.TRANSFER_SOURCE_INTENT_EXTRA, true);
                            startActivity(intent);
                        });
                builder.create().show();
            } else {
                Intent intent = new Intent(this, OnboardingFlowActivity.class);
                intent.putExtra(OnboardingFlowActivity.TRANSFER_SOURCE_INTENT_EXTRA, true);
                startActivity(intent);
            }
        } else if (view instanceof InitialView) {
            String photoUrl = ((InitialView) view).getPhotoUrl();
            if (photoUrl != null) {
                FullScreenImageFragment fullScreenImageFragment = FullScreenImageFragment.newInstance(photoUrl);
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, 0)
                        .replace(R.id.overlay, fullScreenImageFragment, FULL_SCREEN_IMAGE_FRAGMENT_TAG)
                        .commit();
            }
        }
    }

    class IdentityObserver implements Observer<OwnedIdentity> {
        @Nullable private OwnedIdentity ownedIdentity = null;

        @Override
        public void onChanged(OwnedIdentity ownedIdentity) {
            if (this.ownedIdentity != null && (ownedIdentity == null || !Arrays.equals(ownedIdentity.bytesOwnedIdentity, this.ownedIdentity.bytesOwnedIdentity))) {
                // the owned identity change --> leave the activity
                finish();
            } else {
                this.ownedIdentity = ownedIdentity;
                reload();
            }
        }

        public void reload() {
            latestDetails = null;
            displayDetails(ownedIdentity);
        }
    }

    static class OwnedDeviceViewHolder extends RecyclerView.ViewHolder {
        final OwnedIdentityDetailsActivityViewModel viewModel;
        final TextView deviceNameTextView;
        final TextView deviceStatusTextView;
        final TextView expirationTextView;
        final ViewGroup channelCreationGroup;
        final ImageView channelCreationDotsImageView;
        final ImageView dots;
        final ImageView untrusted;
        OwnedDevice ownedDevice;
        public OwnedDeviceViewHolder(@NonNull View itemView, @NonNull OwnedIdentityDetailsActivityViewModel viewModel) {
            super(itemView);
            this.viewModel = viewModel;
            deviceNameTextView = itemView.findViewById(R.id.device_name_text_view);
            deviceStatusTextView = itemView.findViewById(R.id.device_status_text_view);
            expirationTextView = itemView.findViewById(R.id.device_expiration_text_view);
            channelCreationGroup = itemView.findViewById(R.id.establishing_channel_group);
            channelCreationDotsImageView = itemView.findViewById(R.id.establishing_channel_image_view);
            dots = itemView.findViewById(R.id.button_dots);
            dots.setOnClickListener(this::onClick);
            untrusted = itemView.findViewById(R.id.untrusted);
        }

        public void bind(@NonNull OwnedDevice ownedDevice, boolean currentDeviceIsActive) {
            this.ownedDevice = ownedDevice;
            deviceNameTextView.setText(ownedDevice.getDisplayNameOrDeviceHexName(deviceNameTextView.getContext()));

            if (ownedDevice.currentDevice) {
                deviceStatusTextView.setVisibility(View.VISIBLE);
                deviceStatusTextView.setText(R.string.text_this_device);
            } else if (ownedDevice.lastRegistrationTimestamp != null) {
                deviceStatusTextView.setVisibility(View.VISIBLE);
                deviceStatusTextView.setText(deviceStatusTextView.getContext().getString(R.string.text_last_online, StringUtils.getLongNiceDateString(deviceStatusTextView.getContext(), ownedDevice.lastRegistrationTimestamp)));
            } else {
                deviceStatusTextView.setVisibility(View.GONE);
            }

            if (ownedDevice.currentDevice && !currentDeviceIsActive) {
                expirationTextView.setVisibility(View.VISIBLE);
                expirationTextView.setText(R.string.text_device_is_inactive);
                expirationTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_device_inactive, 0, 0, 0);
            } else if (ownedDevice.expirationTimestamp == null) {
                expirationTextView.setVisibility(View.GONE);
            } else {
                expirationTextView.setVisibility(View.VISIBLE);
                expirationTextView.setText(deviceStatusTextView.getContext().getString(R.string.text_deactivates_on, StringUtils.getPreciseAbsoluteDateString(deviceStatusTextView.getContext(), ownedDevice.expirationTimestamp, expirationTextView.getContext().getString(R.string.text_date_time_separator))));
                expirationTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_device_expiration, 0, 0, 0);
            }

            if (ownedDevice.trusted || ownedDevice.currentDevice) {
                untrusted.setVisibility(View.GONE);
            } else {
                untrusted.setVisibility(View.VISIBLE);
            }

            if (!currentDeviceIsActive || ownedDevice.channelConfirmed || ownedDevice.currentDevice) {
                channelCreationGroup.setVisibility(View.GONE);
                channelCreationDotsImageView.setImageDrawable(null);
            } else {
                channelCreationGroup.setVisibility(View.VISIBLE);
                final AnimatedVectorDrawableCompat animated = AnimatedVectorDrawableCompat.create(channelCreationDotsImageView.getContext(), R.drawable.dots);
                if (animated != null) {
                    animated.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
                        @Override
                        public void onAnimationEnd(Drawable drawable) {
                            new Handler(Looper.getMainLooper()).post(animated::start);
                        }
                    });
                    animated.start();
                }
                channelCreationDotsImageView.setImageDrawable(animated);
            }

            if (currentDeviceIsActive) {
                dots.setVisibility(View.VISIBLE);
            } else {
                dots.setVisibility(View.GONE);
            }
        }

        public void unbind() {
            ownedDevice = null;
        }

        private void onClick(View view) {
            if (ownedDevice != null) {
                int order = 0;
                PopupMenu popupMenu = new PopupMenu(view.getContext(), view);

                if (!ownedDevice.currentDevice && !ownedDevice.trusted) {
                    popupMenu.getMenu().add(Menu.NONE, 6, order++, R.string.menu_action_trust_device);
                }

                if (ownedDevice.currentDevice) {
                    popupMenu.getMenu().add(Menu.NONE, 3, order++, R.string.menu_action_rename_device);
                } else {
                    popupMenu.getMenu().add(Menu.NONE, 4, order++, R.string.menu_action_rename_device);
                }

                if (ownedDevice.expirationTimestamp != null) {
                    popupMenu.getMenu().add(Menu.NONE, 5, order++, R.string.menu_action_remove_expiration);
                }


                if (ownedDevice.currentDevice) {
                    popupMenu.getMenu().add(Menu.NONE, 0, order++, R.string.menu_action_refresh_device_list);
                }

                if (!ownedDevice.currentDevice) {
                    popupMenu.getMenu().add(Menu.NONE, 1, order++, R.string.menu_action_recreate_channel);
                    SpannableString removeDeviceSpannableString = new SpannableString(view.getContext().getString(R.string.menu_action_remove_device));
                    removeDeviceSpannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(view.getContext(), R.color.red)), 0, removeDeviceSpannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    popupMenu.getMenu().add(Menu.NONE, 2, order++, removeDeviceSpannableString);
                }
                popupMenu.setOnMenuItemClickListener(this::onMenuItemClick);
                popupMenu.show();
            }
        }

        private boolean onMenuItemClick(MenuItem popupItem) {
            if (ownedDevice == null) {
                return false;
            }


            switch (popupItem.getItemId()) {
                case 0: { // refresh
                    if (ownedDevice.currentDevice) {
                        App.runThread(() -> {
                            new OwnedDevicesSynchronisationWithEngineTask(ownedDevice.bytesOwnedIdentity).run();
                            AppSingleton.getEngine().refreshOwnedDeviceList(ownedDevice.bytesOwnedIdentity);
                        });
                    }
                    break;
                }
                case 1: { // recreate channel
                    if (!ownedDevice.currentDevice) {
                        AppSingleton.getEngine().recreateOwnedDeviceChannel(ownedDevice.bytesOwnedIdentity, ownedDevice.bytesDeviceUid);
                    }
                    break;
                }
                case 2: { // delete
                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(itemView.getContext(), R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_remove_device)
                            .setMessage(R.string.dialog_message_remove_device)
                            .setPositiveButton(R.string.button_label_remove, ((dialog, which) -> {
                                try {
                                    viewModel.showRefreshSpinner();
                                    AppSingleton.getEngine().processDeviceManagementRequest(ownedDevice.bytesOwnedIdentity, ObvDeviceManagementRequest.createDeactivateDeviceRequest(ownedDevice.bytesDeviceUid));
                                } catch (Exception ignored) {}
                            }))
                            .setNegativeButton(R.string.button_label_cancel, null);
                    builder.create().show();
                    break;
                }
                case 3:
                case 4: { // rename device
                    View dialogView = LayoutInflater.from(itemView.getContext()).inflate(R.layout.dialog_view_message_and_input, null);
                    TextView messageTextView = dialogView.findViewById(R.id.dialog_message);
                    if (popupItem.getItemId() == 3) {
                        // current device
                        messageTextView.setText(R.string.dialog_message_rename_current_device);
                    } else {
                        // other device
                        messageTextView.setText(R.string.dialog_message_rename_other_device);
                    }
                    TextInputLayout textInputLayout = dialogView.findViewById(R.id.dialog_text_layout);
                    textInputLayout.setHint(R.string.hint_device_name);

                    TextInputEditText deviceNameEditText = dialogView.findViewById(R.id.dialog_edittext);
                    deviceNameEditText.setText(ownedDevice.displayName);
                    deviceNameEditText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);

                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(itemView.getContext(), R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_rename_device)
                            .setView(dialogView)
                            .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                                CharSequence nickname = deviceNameEditText.getText();
                                if (nickname != null) {
                                    try {
                                        viewModel.showRefreshSpinner();
                                        AppSingleton.getEngine().processDeviceManagementRequest(ownedDevice.bytesOwnedIdentity, ObvDeviceManagementRequest.createSetNicknameRequest(ownedDevice.bytesDeviceUid, nickname.toString()));
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            })
                            .setNegativeButton(R.string.button_label_cancel, null);
                    if (popupItem.getItemId() == 3) {
                        builder.setNeutralButton(R.string.button_label_default, null);
                    }

                    AlertDialog dialog = builder.create();
                    dialog.setOnShowListener(dialogInterface -> {
                        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        if (ok != null) {
                            deviceNameEditText.addTextChangedListener(new TextChangeListener() {
                                @Override
                                public void afterTextChanged(Editable s) {
                                    ok.setEnabled(s != null && s.length() != 0);
                                }
                            });
                        }
                        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                        if (neutral != null) {
                            neutral.setOnClickListener(v -> deviceNameEditText.setText(AppSingleton.DEFAULT_DEVICE_DISPLAY_NAME));
                        }
                    });
                    dialog.show();
                    break;
                }
                case 5: {
                    // set as unexpiring
                    if (ownedDevice.expirationTimestamp != null) {
                        App.runThread(() -> {
                            OwnedIdentity ownedIdentity = AppDatabase.getInstance().ownedIdentityDao().get(ownedDevice.bytesOwnedIdentity);
                            if (!ownedIdentity.hasMultiDeviceApiKeyPermission()) {
                                List<OwnedDevice> ownedDevices = AppDatabase.getInstance().ownedDeviceDao().getAllSync(ownedIdentity.bytesOwnedIdentity);
                                OwnedDevice currentlyNotExpiringDevice = null;
                                for (OwnedDevice ownedDevice : ownedDevices) {
                                    if (ownedDevice.expirationTimestamp == null) {
                                        currentlyNotExpiringDevice = ownedDevice;
                                        break;
                                    }
                                }

                                if (currentlyNotExpiringDevice != null) {
                                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(itemView.getContext(), R.style.CustomAlertDialog)
                                            .setTitle(R.string.dialog_title_set_unexpiring_device)
                                            .setMessage(itemView.getContext().getString(R.string.dialog_message_set_unexpiring_device, ownedDevice.getDisplayNameOrDeviceHexName(itemView.getContext()), currentlyNotExpiringDevice.getDisplayNameOrDeviceHexName(itemView.getContext())))
                                            .setPositiveButton(R.string.button_label_proceed, ((dialog, which) -> {
                                                try {
                                                    viewModel.showRefreshSpinner();
                                                    AppSingleton.getEngine().processDeviceManagementRequest(ownedDevice.bytesOwnedIdentity, ObvDeviceManagementRequest.createSetUnexpiringDeviceRequest(ownedDevice.bytesDeviceUid));
                                                } catch (Exception ignored) {}
                                            }))
                                            .setNegativeButton(R.string.button_label_cancel, null);
                                    new Handler(Looper.getMainLooper()).post(() -> builder.create().show());
                                    return;
                                }
                            }

                            try {
                                // if we reach this point, the user either has the multi-device permission, or has an expiration for all of his devices --> no need to show a confirmation dialog
                                viewModel.showRefreshSpinner();
                                AppSingleton.getEngine().processDeviceManagementRequest(ownedDevice.bytesOwnedIdentity, ObvDeviceManagementRequest.createSetUnexpiringDeviceRequest(ownedDevice.bytesDeviceUid));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    }
                    break;
                }
                case 6: {
                    // trust
                    App.runThread(() -> {
                        AndroidNotificationManager.clearDeviceTrustNotification(ownedDevice.bytesDeviceUid);
                        AppDatabase.getInstance().ownedDeviceDao().updateTrusted(ownedDevice.bytesOwnedIdentity, ownedDevice.bytesDeviceUid, true);
                    });
                    break;
                }

            }
            return true;
        }
    }

    public static class OwnedIdentityDetailsActivityViewModel extends ViewModel {
        private final MutableLiveData<Boolean> showRefreshSpinner = new MutableLiveData<>(false);
        final MutableLiveData<Boolean> ownedIdentityActive = new MutableLiveData<>(null);
        final LiveData<List<OwnedDevice>> ownedDevicesLiveData = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), ownedIdentity -> {
            if (ownedIdentity == null) {
                if (!Objects.equals(ownedIdentityActive.getValue(), true)) {
                    ownedIdentityActive.postValue(true);
                }
                return new MutableLiveData<>(Collections.emptyList());
            }
            if (!Objects.equals(ownedIdentityActive.getValue(), ownedIdentity.active)) {
                ownedIdentityActive.postValue(ownedIdentity.active);
            }
            return AppDatabase.getInstance().ownedDeviceDao().getAllSorted(ownedIdentity.bytesOwnedIdentity);
        });

        void showRefreshSpinner() {
            this.showRefreshSpinner.postValue(true);
        }

        void hideRefreshSpinner() {
            this.showRefreshSpinner.postValue(false);
        }
    }
}
