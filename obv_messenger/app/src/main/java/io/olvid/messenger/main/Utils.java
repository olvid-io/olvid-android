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

package io.olvid.messenger.main;


import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;

public class Utils {
    public static boolean dialogsLoaded = false;
    private static boolean dialogShowing = false;
    private static final Deque<String> dialogsToShow = new ArrayDeque<>();

    private static final String USER_DIALOG_GOOGLE_APIS = "google_apis";
    private static final String USER_DIALOG_BACKGROUND_RESTRICTED = "background_restricted";
    private static final String USER_DIALOG_BATTERY_OPTIMIZATION = "battery_optimization";
    private static final String USER_DIALOG_ALARM_SCHEDULING = "alarm_scheduling";

    static void showDialogs(FragmentActivity activity) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());

        if (!dialogsLoaded) {
            dialogsLoaded = true;
            if (ConnectionResult.SUCCESS != GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
                    && !SettingsActivity.usePermanentWebSocket()) {
                boolean hideDialog = prefs.getBoolean(SettingsActivity.USER_DIALOG_HIDE_GOOGLE_APIS, false);
                if (!hideDialog) {
                    dialogsToShow.offerLast(USER_DIALOG_GOOGLE_APIS);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ActivityManager activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager != null && activityManager.isBackgroundRestricted()) {
                    boolean hideDialog = prefs.getBoolean(SettingsActivity.USER_DIALOG_HIDE_BACKGROUND_RESTRICTED, false);
                    if (!hideDialog) {
                        dialogsToShow.offerLast(USER_DIALOG_BACKGROUND_RESTRICTED);
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(activity.getPackageName())) {
                    boolean hideDialog = prefs.getBoolean(SettingsActivity.USER_DIALOG_HIDE_BATTERY_OPTIMIZATION, false);
                    if (!hideDialog) {
                        dialogsToShow.offerLast(USER_DIALOG_BATTERY_OPTIMIZATION);
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager alarmManager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    boolean hideDialog = prefs.getBoolean(SettingsActivity.USER_DIALOG_HIDE_ALARM_SCHEDULING, false);
                    if (!hideDialog) {
                        dialogsToShow.offerLast(USER_DIALOG_ALARM_SCHEDULING);
                    }
                }
            }
        }

        if (dialogShowing) {
            return;
        }

        String dialogToShow = dialogsToShow.pollFirst();
        if (dialogToShow == null) {
            return;
        }

        dialogShowing = true;
        switch (dialogToShow) {
            case USER_DIALOG_GOOGLE_APIS: {
                View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_view_message_and_checkbox, null);
                TextView message = dialogView.findViewById(R.id.dialog_message);
                message.setText(R.string.dialog_message_google_apis_missing);
                CheckBox checkBox = dialogView.findViewById(R.id.checkbox);
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(SettingsActivity.USER_DIALOG_HIDE_GOOGLE_APIS, isChecked);
                    editor.apply();
                });

                AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog);
                builder.setTitle(R.string.dialog_title_google_apis_missing)
                        .setView(dialogView)
                        .setPositiveButton(R.string.button_label_ok, null)
                        .setNeutralButton(R.string.button_label_enable_permanent_websocket, (DialogInterface dialogInterface, int which) -> {
                            SettingsActivity.setUsePermanentWebSocket(true);
                            activity.startService(new Intent(activity, UnifiedForegroundService.class));
                        })
                        .setOnDismissListener((DialogInterface dialog) -> {
                            dialogShowing = false;
                            showDialogs(activity);
                        });
                builder.create().show();
                break;
            }
            case USER_DIALOG_BACKGROUND_RESTRICTED: {
                View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_view_message_and_checkbox, null);
                TextView message = dialogView.findViewById(R.id.dialog_message);
                message.setText(R.string.dialog_message_background_restricted);
                CheckBox checkBox = dialogView.findViewById(R.id.checkbox);
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(SettingsActivity.USER_DIALOG_HIDE_BACKGROUND_RESTRICTED, isChecked);
                    editor.apply();
                });

                AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog);
                builder.setTitle(R.string.dialog_title_background_restricted)
                        .setView(dialogView)
                        .setPositiveButton(R.string.button_label_ok, null)
                        .setNeutralButton(R.string.button_label_app_settings, null)
                        .setOnDismissListener((DialogInterface dialog) -> {
                            dialogShowing = false;
                            showDialogs(activity);
                        });

                AlertDialog dialog = builder.create();
                dialog.setOnShowListener(alertDialog -> {
                    Button button = ((AlertDialog) alertDialog).getButton(DialogInterface.BUTTON_NEUTRAL);
                    if (button != null) {
                        button.setOnClickListener(v -> {
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                            intent.setData(uri);
                            activity.startActivity(intent);
                        });
                    }
                });
                dialog.show();
                break;
            }
            case USER_DIALOG_BATTERY_OPTIMIZATION: {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_view_message_and_checkbox, null);
                    TextView message = dialogView.findViewById(R.id.dialog_message);
                    message.setText(R.string.dialog_message_battery_optimization);
                    CheckBox checkBox = dialogView.findViewById(R.id.checkbox);
                    checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean(SettingsActivity.USER_DIALOG_HIDE_BATTERY_OPTIMIZATION, isChecked);
                        editor.apply();
                    });

                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog);
                    builder.setTitle(R.string.dialog_title_battery_optimization)
                            .setView(dialogView)
                            .setPositiveButton(R.string.button_label_ok, null)
                            .setNeutralButton(R.string.button_label_battery_optimization_settings, null)
                            .setOnDismissListener((DialogInterface dialog) -> {
                                dialogShowing = false;
                                showDialogs(activity);
                            });

                    AlertDialog dialog = builder.create();
                    dialog.setOnShowListener(alertDialog -> {
                        Button button = ((AlertDialog) alertDialog).getButton(DialogInterface.BUTTON_NEUTRAL);
                        if (button != null) {
                            button.setOnClickListener(v -> {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(intent);
                            });
                        }
                    });
                    dialog.show();
                }
                break;
            }
            case USER_DIALOG_ALARM_SCHEDULING: {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_view_message_and_checkbox, null);
                    TextView message = dialogView.findViewById(R.id.dialog_message);
                    message.setText(R.string.dialog_message_alarm_scheduling_forbidden);
                    CheckBox checkBox = dialogView.findViewById(R.id.checkbox);
                    checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean(SettingsActivity.USER_DIALOG_HIDE_ALARM_SCHEDULING, isChecked);
                        editor.apply();
                    });

                    AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog);
                    builder.setTitle(R.string.dialog_title_alarm_scheduling_forbidden)
                            .setView(dialogView)
                            .setPositiveButton(R.string.button_label_ok, null)
                            .setNeutralButton(R.string.button_label_app_settings, null)
                            .setOnDismissListener((DialogInterface dialog) -> {
                                dialogShowing = false;
                                showDialogs(activity);
                            });

                    AlertDialog dialog = builder.create();
                    dialog.setOnShowListener(alertDialog -> {
                        Button button = ((AlertDialog) alertDialog).getButton(DialogInterface.BUTTON_NEUTRAL);
                        if (button != null) {
                            button.setOnClickListener(v -> {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                                intent.setData(uri);
                                activity.startActivity(intent);
                            });
                        }
                    });
                    dialog.show();

                }
                break;
            }
        }
    }



    static void verifyPurchases(byte[] bytesOwnedIdentity, Context context) {
        try {
            final BillingClient billingClient = BillingClient.newBuilder(context)
                    .enablePendingPurchases()
                    .setListener((billingResult, list) -> Logger.e("Purchase updated " + list))
                    .build();

            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(@NonNull BillingResult setupBillingResult) {
                    billingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, (BillingResult queryBillingResult, List<Purchase> list) -> {
                        if (list.size() > 0) {
                            for (Purchase purchase : list) {
                                if (!purchase.isAcknowledged()) {
                                    Logger.d("Found not acknowledged purchase --> sending it to server");
                                    AppSingleton.getEngine().verifyReceipt(bytesOwnedIdentity, purchase.getPurchaseToken());
                                    final EngineNotificationListener notificationListener = new EngineNotificationListener() {
                                        private Long regNumber;

                                        @Override
                                        public void callback(String notificationName, HashMap<String, Object> userInfo) {
                                            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, this);
                                            AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                                    .setPurchaseToken(purchase.getPurchaseToken())
                                                    .build();

                                            billingClient.acknowledgePurchase(acknowledgePurchaseParams, purchaseBillingResult -> {
                                                if (purchaseBillingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                                                    Logger.e("Error acknowledging store purchase " + purchaseBillingResult.getDebugMessage());
                                                }
                                            });
                                        }

                                        @Override
                                        public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
                                            regNumber = registrationNumber;
                                        }

                                        @Override
                                        public long getEngineNotificationListenerRegistrationNumber() {
                                            return regNumber;
                                        }

                                        @Override
                                        public boolean hasEngineNotificationListenerRegistrationNumber() {
                                            return regNumber != null;
                                        }
                                    };
                                    AppSingleton.getEngine().addNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, notificationListener);
                                    // auto remove the listener after 20s
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> AppSingleton.getEngine().removeNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS, notificationListener), 20_000);
                                }
                            }
                        } else {
                            billingClient.endConnection();
                        }
                    });
                }

                @Override
                public void onBillingServiceDisconnected() {
                    // do nothing, we'll query again at next startup
                }
            });

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    billingClient.endConnection();
                } catch (Exception e) {
                    // nothing
                }
            }, 30_000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // region Websocket latency ping
    static Timer pingTimer = null;
    static boolean doPing = false;

    public static void startPinging() {
        doPing = true;
        if (pingTimer == null) {
            pingTimer = new Timer("MainActivity-websocketLatencyPingTimer");
            pingTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (doPing) {
                        byte[] bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity();
                        if (bytesOwnedIdentity != null) {
                            AppSingleton.getEngine().pingWebsocket(bytesOwnedIdentity);
                        }
                    } else {
                        pingTimer.cancel();
                        pingTimer = null;
                    }
                }
            }, 0, 10_000);
        } else {
            // even if a ping is already schedule, immediately ping
            byte[] bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity();
            if (bytesOwnedIdentity != null) {
                AppSingleton.getEngine().pingWebsocket(bytesOwnedIdentity);
            }
        }
    }

    public static void stopPinging() {
        doPing = false;
    }

    // endregion
}
