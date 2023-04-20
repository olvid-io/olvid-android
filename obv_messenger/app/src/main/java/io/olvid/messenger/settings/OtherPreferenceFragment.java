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

package io.olvid.messenger.settings;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.main.Utils;
import io.olvid.messenger.services.UnifiedForegroundService;

public class OtherPreferenceFragment extends PreferenceFragmentCompat {
    private FragmentActivity activity;
    private ActivityResultLauncher<String> exportAppDbLauncher;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_preferences_other, rootKey);
        activity = requireActivity();

        PreferenceScreen screen = getPreferenceScreen();

        Preference resetDialogsPreference = screen.findPreference(SettingsActivity.PREF_KEY_RESET_DIALOGS);
        if (resetDialogsPreference != null) {
            resetDialogsPreference.setOnPreferenceClickListener((Preference preference) -> {
                SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
                if (sharedPreferences != null) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_GOOGLE_APIS);
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_BACKGROUND_RESTRICTED);
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_BATTERY_OPTIMIZATION);
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_ALARM_SCHEDULING);
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_ALLOW_NOTIFICATIONS);
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_OPEN_EXTERNAL_APP);
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_FORWARD_MESSAGE_EXPLANATION);
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_OPEN_EXTERNAL_APP_LOCATION);
                    editor.remove(SettingsActivity.PREF_KEY_FIRST_CALL_AUDIO_PERMISSION_REQUESTED);
                    editor.remove(SettingsActivity.PREF_KEY_FIRST_CALL_BLUETOOTH_PERMISSION_REQUESTED);
                    editor.remove(SettingsActivity.PREF_KEY_LAST_BACKUP_REMINDER_TIMESTAMP);
                    editor.apply();
                    App.toast(R.string.toast_message_dialogs_restored, Toast.LENGTH_SHORT);
                    Utils.dialogsLoaded = false;
                }
                return false;
            });
        }

        SwitchPreference shareAppVersionPreference = screen.findPreference(SettingsActivity.PREF_KEY_SHARE_APP_VERSION);
        if (shareAppVersionPreference != null) {
            shareAppVersionPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean checked = (Boolean) newValue;
                if (checked) {
                    AppSingleton.getEngine().connectWebsocket("android", Integer.toString(android.os.Build.VERSION.SDK_INT), BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME);
                } else {
                    AppSingleton.getEngine().connectWebsocket(null, null, 0, null);
                }
                return true;
            });
        }

        if (!BuildConfig.ENABLE_SSL_HANDSHAKE_VERIFICATION) {
            Preference notifyCertificateChangePreference = screen.findPreference(SettingsActivity.PREF_KEY_NOTIFY_CERTIFICATE_CHANGE);
            if (notifyCertificateChangePreference != null) {
                screen.removePreference(notifyCertificateChangePreference);
            }
            Preference blockUntrustedCertificatePreference = screen.findPreference(SettingsActivity.PREF_KEY_BLOCK_UNTRUSTED_CERTIFICATE);
            if (blockUntrustedCertificatePreference != null) {
                screen.removePreference(blockUntrustedCertificatePreference);
            }
            Preference noNotifyCertificateChangeForPreviewsPreference = screen.findPreference(SettingsActivity.PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS);
            if (noNotifyCertificateChangeForPreviewsPreference != null) {
                screen.removePreference(noNotifyCertificateChangeForPreviewsPreference);
            }
        } else if (!SettingsActivity.getBetaFeaturesEnabled()) {
            Preference noNotifyCertificateChangeForPreviewsPreference = screen.findPreference(SettingsActivity.PREF_KEY_NO_NOTIFY_CERTIFICATE_CHANGE_FOR_PREVIEWS);
            if (noNotifyCertificateChangeForPreviewsPreference != null) {
                screen.removePreference(noNotifyCertificateChangeForPreviewsPreference);
            }
        }

        SwitchPreference permanentForegroundPreference = screen.findPreference(SettingsActivity.PREF_KEY_PERMANENT_FOREGROUND_SERVICE);
        if (permanentForegroundPreference != null) {
            permanentForegroundPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                App.runThread(() -> {
                    try {
                        // wait 1 second for the setting to actually be updated
                        Thread.sleep(1_000);
                    } catch (InterruptedException e) {
                        // do nothing
                    }
                    activity.startService(new Intent(activity, UnifiedForegroundService.class));
                });
                return true;
            });
        }

        SwitchPreference sendingForegroundServicePreference = screen.findPreference(SettingsActivity.PREF_KEY_SENDING_FOREGROUND_SERVICE);
        if (sendingForegroundServicePreference != null) {
            sendingForegroundServicePreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                App.runThread(() -> {
                    try {
                        // wait 1 second for the setting to actually be updated
                        Thread.sleep(1_000);
                    } catch (InterruptedException e) {
                        // do nothing
                    }
                    activity.startService(new Intent(activity, UnifiedForegroundService.class));
                });
                return true;
            });
        }

        SwitchPreference debugLogLevelSwitchPreference = screen.findPreference(SettingsActivity.PREF_KEY_DEBUG_LOG_LEVEL);
        if (debugLogLevelSwitchPreference != null) {
            debugLogLevelSwitchPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean checked = (Boolean) newValue;
                Logger.setOutputLogLevel(checked ? Logger.DEBUG : BuildConfig.LOG_LEVEL);
                return true;
            });
        }

        DropDownPreference scaledTurnPreference = screen.findPreference(SettingsActivity.PREF_KEY_SCALED_TURN_REGION);
        if (scaledTurnPreference != null) {
            if (SettingsActivity.getBetaFeaturesEnabled()) {
                scaledTurnPreference.setVisible(true);
            }
        }

        SwitchPreference useLegacyZxingScannerPreference = screen.findPreference(SettingsActivity.PREF_KEY_USE_LEGACY_ZXING_SCANNER);
        if (useLegacyZxingScannerPreference != null && !BuildConfig.USE_GOOGLE_LIBS) {
            screen.removePreference(useLegacyZxingScannerPreference);
        }

        Preference exportAppDbPreference = screen.findPreference(SettingsActivity.PREF_KEY_EXPORT_APP_DATABASES);
        if (exportAppDbPreference != null) {
            if (SettingsActivity.getBetaFeaturesEnabled()) {
                if (exportAppDbLauncher == null) {
                    exportAppDbLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), this::onExportAppDbFileSelected);
                }

                exportAppDbPreference.setVisible(true);
                exportAppDbPreference.setOnPreferenceClickListener((Preference preference) -> {
                    AlertDialog dialog = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_export_app_databases)
                            .setMessage(R.string.dialog_message_export_app_databases)
                            .setNegativeButton(R.string.button_label_cancel, null)
                            .setPositiveButton(R.string.button_label_export, (DialogInterface dialogInterface, int which) -> {
                                App.prepareForStartActivityForResult(this);
                                exportAppDbLauncher.launch("olvid_databases.zip");
                            })
                            .create();
                    dialog.show();
                    return true;
                });
            }
        }

        Preference storageExplorerPreference = screen.findPreference(SettingsActivity.PREF_KEY_STORAGE_EXPLORER);
        if (storageExplorerPreference != null) {
            if (SettingsActivity.getBetaFeaturesEnabled() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                storageExplorerPreference.setVisible(true);
                storageExplorerPreference.setOnPreferenceClickListener((Preference preference) -> {
                    startActivity(new Intent(activity, StorageExplorer.class));
                    return true;
                });
            }
        }
    }


    private void onExportAppDbFileSelected(Uri uri) {
        if (!StringUtils.validateUri(uri)) {
            return;
        }

        try (OutputStream os = activity.getContentResolver().openOutputStream(uri)) {
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(os)) {
                {
                    ZipEntry engineDb = new ZipEntry(Constants.ENGINE_DB_FILENAME);
                    zipOutputStream.putNextEntry(engineDb);
                    try (FileInputStream fis = new FileInputStream(App.absolutePathFromRelative(Constants.ENGINE_DB_FILENAME))) {
                        byte[] buffer = new byte[262_144];
                        int c;
                        while ((c = fis.read(buffer)) != -1) {
                            zipOutputStream.write(buffer, 0, c);
                        }
                    }
                }

                {
                    ZipEntry appDb = new ZipEntry(AppDatabase.DB_FILE_NAME);
                    zipOutputStream.putNextEntry(appDb);
                    try (FileInputStream fis = new FileInputStream(App.absolutePathFromRelative(AppDatabase.DB_FILE_NAME))) {
                        byte[] buffer = new byte[262_144];
                        int c;
                        while ((c = fis.read(buffer)) != -1) {
                            zipOutputStream.write(buffer, 0, c);
                        }
                    }
                }

                {
                    ZipEntry appWal = new ZipEntry(AppDatabase.DB_FILE_NAME + "-wal");
                    zipOutputStream.putNextEntry(appWal);
                    try (FileInputStream fis = new FileInputStream(App.absolutePathFromRelative(AppDatabase.DB_FILE_NAME + "-wal"))) {
                        byte[] buffer = new byte[262_144];
                        int c;
                        while ((c = fis.read(buffer)) != -1) {
                            zipOutputStream.write(buffer, 0, c);
                        }
                    }
                }

                App.toast(R.string.toast_message_success_exporting_db, Toast.LENGTH_SHORT);
            }
        } catch (IOException e) {
            e.printStackTrace();
            App.toast(R.string.toast_message_error_exporting_db, Toast.LENGTH_SHORT);
        }
    }
}
