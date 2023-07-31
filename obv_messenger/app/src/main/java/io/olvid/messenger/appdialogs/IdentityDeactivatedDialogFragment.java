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

package io.olvid.messenger.appdialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.ObvDeviceList;
import io.olvid.engine.engine.types.ObvPushNotificationType;
import io.olvid.engine.engine.types.identities.ObvOwnedDevice;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.settings.SettingsActivity;


public class IdentityDeactivatedDialogFragment extends DialogFragment implements EngineNotificationListener {
    private enum DialogConfiguration {
        MULTI_DEVICE,
        NO_DEVICE,
        CHOOSE_A_DEVICE,
        ONLY_EXPIRING_DEVICES,
        UNKNOWN,
    }

    private OwnedIdentity ownedIdentity;
    private ObvDeviceList deviceList;
    private DialogConfiguration dialogConfiguration;
    private Map.Entry<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> selectedDevice;
    private Runnable dismissCallback;
    private Long engineNotificationRegistrationNumber = null;

    public static IdentityDeactivatedDialogFragment newInstance(OwnedIdentity ownedIdentity, ObvDeviceList deviceList, Runnable dismissCallback) {
        IdentityDeactivatedDialogFragment dialogFragment = new IdentityDeactivatedDialogFragment();
        dialogFragment.setOwnedIdentity(ownedIdentity);
        dialogFragment.setDeviceList(deviceList);
        dialogFragment.setDismissCallback(dismissCallback);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED, dialogFragment);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE, dialogFragment);
        return dialogFragment;
    }

    public void setOwnedIdentity(OwnedIdentity ownedIdentity) {
        this.ownedIdentity = ownedIdentity;
    }

    public void setDeviceList(ObvDeviceList deviceList) {
        this.deviceList = deviceList;
        if (deviceList != null && (deviceList.deviceUidsAndServerInfo == null || deviceList.deviceUidsAndServerInfo.size() == 0)) {
            // no active device --> simple message
            dialogConfiguration = DialogConfiguration.NO_DEVICE;
        } else if (deviceList != null) {
            // user has other active devices --> check if he has multi-device licence
            if (deviceList.multiDevice != null && deviceList.multiDevice) {
                // user has a multi-device licence --> display an informative list of devices with a simple message
                dialogConfiguration = DialogConfiguration.MULTI_DEVICE;
            } else {
                boolean hasNonExpiringDevice = false;
                for (ObvOwnedDevice.ServerDeviceInfo serverDeviceInfo : deviceList.deviceUidsAndServerInfo.values()) {
                    if (serverDeviceInfo.expirationTimestamp == null) {
                        hasNonExpiringDevice = true;
                        break;
                    }
                }
                if (hasNonExpiringDevice) {
                    // no multi device device --> choose which to deactivate
                    dialogConfiguration = DialogConfiguration.CHOOSE_A_DEVICE;
                } else {
                    // no multi device device and all devices expire --> simple message
                    dialogConfiguration = DialogConfiguration.ONLY_EXPIRING_DEVICES;
                }
            }
        } else {
            // no info, no devices to show --> server will deactivate one device at random if there is one
            dialogConfiguration = DialogConfiguration.UNKNOWN;
        }
    }

    public void setDismissCallback(Runnable dismissCallback) {
        this.dismissCallback = dismissCallback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_fragment_identity_deactivated, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View dialogView, @Nullable Bundle savedInstanceState) {
        TextView explanationTextView = dialogView.findViewById(R.id.explanation_profile_inactive);
        RecyclerView deviceListRecyclerView = dialogView.findViewById(R.id.device_list_recycler_view);
        TextView activeDevicesLabel = dialogView.findViewById(R.id.label_active_devices);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        Button reactivateButton = dialogView.findViewById(R.id.button_reactivate_identity);

        switch (dialogConfiguration) {
            case MULTI_DEVICE:
                explanationTextView.setText(R.string.dialog_message_identity_deactivated_multi_device);
                reactivateButton.setEnabled(true);
                break;
            case NO_DEVICE:
                explanationTextView.setText(R.string.dialog_message_identity_deactivated_no_device);
                reactivateButton.setEnabled(true);
                break;
            case CHOOSE_A_DEVICE:
                explanationTextView.setText(R.string.dialog_message_identity_deactivated_choose_device);
                reactivateButton.setEnabled(false);
                break;
            case ONLY_EXPIRING_DEVICES:
                explanationTextView.setText(R.string.dialog_message_identity_deactivated_only_expiring);
                reactivateButton.setEnabled(true);
                break;
            case UNKNOWN:
            default:
                explanationTextView.setText(R.string.dialog_message_identity_deactivated);
                reactivateButton.setEnabled(true);
                break;
        }


        if (dialogConfiguration == DialogConfiguration.MULTI_DEVICE || dialogConfiguration == DialogConfiguration.CHOOSE_A_DEVICE) {
            // fill the recyclerview
            deviceListRecyclerView.setVisibility(View.VISIBLE);
            activeDevicesLabel.setVisibility(View.VISIBLE);
            //noinspection Convert2Diamond
            ListAdapter<Map.Entry<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo>, DeviceViewHolder> deviceAdapter = new ListAdapter<Map.Entry<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo>, DeviceViewHolder>(new DiffUtil.ItemCallback<Map.Entry<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo>>() {
                @Override
                public boolean areItemsTheSame(@NonNull Map.Entry<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> oldItem, @NonNull Map.Entry<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> newItem) {
                    return oldItem.getKey().equals(newItem.getKey());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Map.Entry<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> oldItem, @NonNull Map.Entry<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> newItem) {
                    return oldItem.getValue().equals(newItem.getValue());
                }
            }) {
                final List<DeviceViewHolder> holders = new ArrayList<>();
                @NonNull
                @Override
                public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    DeviceViewHolder holder = new DeviceViewHolder(getLayoutInflater().inflate(R.layout.item_view_selectable_owned_device, parent, false));
                    holders.add(holder);
                    return holder;
                }

                @Override
                public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
                    Map.Entry<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> item = getItem(position);
                    holder.bind(item);

                    if (dialogConfiguration == DialogConfiguration.CHOOSE_A_DEVICE) {
                        holder.setSelectable(true);
                        holder.setOnClickListener((DeviceViewHolder currentHolder) -> {
                            if (item == selectedDevice) {
                                currentHolder.setSelected(false);
                                selectedDevice = null;
                                reactivateButton.setEnabled(false);
                            } else {
                                for (DeviceViewHolder deviceViewHolder : holders) {
                                    deviceViewHolder.setSelected(false);
                                }
                                currentHolder.setSelected(true);
                                selectedDevice = item;
                                reactivateButton.setEnabled(true);
                            }
                        });
                    } else {
                        holder.setSelectable(false);
                    }
                }
            };
            deviceListRecyclerView.setAdapter(deviceAdapter);
            deviceListRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            List<Map.Entry<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo>> sortedDevices = new ArrayList<>(deviceList.deviceUidsAndServerInfo.entrySet());
            //noinspection ComparatorCombinators
            Collections.sort(sortedDevices, (o1, o2) -> o1.getKey().compareTo(o2.getKey()));

            deviceAdapter.submitList(sortedDevices);
        } else {
            deviceListRecyclerView.setVisibility(View.GONE);
            activeDevicesLabel.setVisibility(View.GONE);
        }

        cancelButton.setOnClickListener(v -> dismiss());

        reactivateButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(v.getContext(), R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_identity_reactivation_confirmation)
                    .setNegativeButton(R.string.button_label_cancel, null);

            switch (dialogConfiguration) {
                case CHOOSE_A_DEVICE:
                    if (selectedDevice == null) {
                        return;
                    }
                    String displayName = selectedDevice.getValue().displayName;
                    if (displayName == null) {
                        displayName = getString(R.string.text_device_xxxx, Logger.toHexString(Arrays.copyOfRange(selectedDevice.getKey().getBytes(), 0, 2)));
                    }
                    builder.setMessage(getString(R.string.dialog_message_identity_reactivation_confirmation, displayName))
                            .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                                try {
                                    AppSingleton.getEngine().registerToPushNotification(ownedIdentity.bytesOwnedIdentity, ObvPushNotificationType.createAndroid(AppSingleton.retrieveFirebaseToken()), true, selectedDevice.getKey().getBytes());
                                } catch (Exception e) {
                                    App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
                                    return;
                                }
                                reactivateButton.setEnabled(false);
                            });
                    break;
                case MULTI_DEVICE:
                case NO_DEVICE:
                case ONLY_EXPIRING_DEVICES:
                    builder.setMessage(R.string.dialog_message_identity_reactivation_confirmation_multi)
                            .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                                try {
                                    AppSingleton.getEngine().registerToPushNotification(ownedIdentity.bytesOwnedIdentity, ObvPushNotificationType.createAndroid(AppSingleton.retrieveFirebaseToken()), true, null);
                                } catch (Exception e) {
                                    App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
                                    return;
                                }
                                reactivateButton.setEnabled(false);
                            });
                    break;
                case UNKNOWN:
                default:
                    builder.setMessage(R.string.dialog_message_identity_reactivation_confirmation_unknown)
                            .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                                try {
                                    AppSingleton.getEngine().registerToPushNotification(ownedIdentity.bytesOwnedIdentity, ObvPushNotificationType.createAndroid(AppSingleton.retrieveFirebaseToken()), true, null);
                                } catch (Exception e) {
                                    App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
                                    return;
                                }
                                reactivateButton.setEnabled(false);
                            });
                    break;
            }

            builder.create().show();
        });
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED, this);
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE, this);
        if (dismissCallback != null) {
            dismissCallback.run();
        }
    }

    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        engineNotificationRegistrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return engineNotificationRegistrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return engineNotificationRegistrationNumber != null;
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED: {
                Boolean active = (Boolean) userInfo.get(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_ACTIVE_KEY);
                byte[] ownedIdentityBytes = (byte[]) userInfo.get(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                if (active != null && active && Arrays.equals(ownedIdentity.bytesOwnedIdentity, ownedIdentityBytes)) {
                    new Handler(Looper.getMainLooper()).post(this::dismiss);
                }
                break;
            }
            case EngineNotifications.PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE: {
                byte[] ownedIdentityBytes = (byte[]) userInfo.get(EngineNotifications.PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE_BYTES_OWNED_IDENTITY_KEY);
                if (Arrays.equals(ownedIdentity.bytesOwnedIdentity, ownedIdentityBytes)) {
                    App.toast(R.string.toast_message_failed_to_reactivate_device, Toast.LENGTH_LONG);
                    // close the dialog, and trigger a re-open in 1 second (leave enough time for the onDismissListener to be executed
                    new Handler(Looper.getMainLooper()).post(this::dismiss);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> App.openAppDialogIdentityDeactivated(ownedIdentity), 1000);
                }
            }
        }
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        final RadioButton selectionRadio;
        final ImageView deviceIcon;
        final TextView deviceName;
        final TextView deviceStatus;
        final TextView deviceExpiration;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            selectionRadio = itemView.findViewById(R.id.device_selected_radio_button);
            deviceIcon = itemView.findViewById(R.id.device_icon);
            selectionRadio.setClickable(false);
            deviceName = itemView.findViewById(R.id.device_name_text_view);
            deviceStatus = itemView.findViewById(R.id.device_status_text_view);
            deviceExpiration = itemView.findViewById(R.id.device_expiration_text_view);
        }

        public void bind(Map.Entry<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> item) {
            String displayName = item.getValue().displayName;
            if (displayName != null) {
                deviceName.setText(displayName);
            } else {
                deviceName.setText(itemView.getContext().getString(R.string.text_device_xxxx, Logger.toHexString(Arrays.copyOfRange(item.getKey().getBytes(), 0, 2))));
            }

            if (item.getValue().lastRegistrationTimestamp != null) {
                deviceStatus.setVisibility(View.VISIBLE);
                deviceStatus.setText(itemView.getContext().getString(R.string.text_last_online, StringUtils.getLongNiceDateString(itemView.getContext(), item.getValue().lastRegistrationTimestamp)));
            } else {
                deviceStatus.setVisibility(View.GONE);
            }

            if (item.getValue().expirationTimestamp == null) {
                deviceExpiration.setVisibility(View.GONE);
            } else {
                deviceExpiration.setVisibility(View.VISIBLE);
                deviceExpiration.setText(itemView.getContext().getString(R.string.text_deactivates_on, StringUtils.getPreciseAbsoluteDateString(itemView.getContext(), item.getValue().expirationTimestamp, itemView.getContext().getString(R.string.text_date_time_separator))));
                deviceExpiration.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_device_expiration, 0, 0, 0);
            }
        }

        public void setSelectable(boolean selectable) {
            selectionRadio.setVisibility(selectable ? View.VISIBLE : View.GONE);
            deviceIcon.setVisibility(selectable ? View.GONE : View.VISIBLE);
        }


        public void setSelected(boolean selected) {
            selectionRadio.setChecked(selected);
        }

        public void setOnClickListener(DeviceClickedListener deviceClickedListener) {
            if (deviceClickedListener == null) {
                itemView.setOnClickListener(null);
            } else {
                itemView.setOnClickListener(v -> deviceClickedListener.onClick(this));
            }
        }
    }

    interface DeviceClickedListener {
        void onClick(DeviceViewHolder deviceViewHolder);
    }
}
