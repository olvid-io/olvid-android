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

package io.olvid.messenger.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.engine.types.EngineDbQueryStatisticsEntry;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.DatabaseKey;
import io.olvid.messenger.customClasses.Markdown;
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
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_OPEN_EXTERNAL_APP);
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_FORWARD_MESSAGE_EXPLANATION);
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_OPEN_EXTERNAL_APP_LOCATION);
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_ADD_DEVICE_EXPLANATION);
                    editor.remove(SettingsActivity.USER_DIALOG_HIDE_UNARCHIVE_SETTINGS);
                    editor.remove(SettingsActivity.PREF_KEY_FIRST_CALL_AUDIO_PERMISSION_REQUESTED);
                    editor.remove(SettingsActivity.PREF_KEY_LAST_TROUBLESHOOTING_TIP_TIMESTAMP);
                    editor.remove(SettingsActivity.PREF_KEY_LAST_EXPIRING_DEVICE_TIP_TIMESTAMP);
                    editor.remove(SettingsActivity.PREF_KEY_LAST_OFFLINE_DEVICE_TIP_TIMESTAMP);
                    if (SettingsActivity.getLastReadReceiptTipTimestamp() != -1) {
                        editor.remove(SettingsActivity.PREF_KEY_LAST_READ_RECEIPT_TIP_TIMESTAMP);
                    }
                    try {
                        if (SettingsActivity.getBackupsV2Status() == SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_CONFIGURED
                                && AppSingleton.getEngine().getDeviceBackupSeed() == null) {
                            SettingsActivity.setBackupsV2Status(SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_NOT_CONFIGURED);
                        }
                    } catch (Exception ignored) { }
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
                    AppSingleton.getEngine().connectWebsocket(false, "android", Integer.toString(android.os.Build.VERSION.SDK_INT), BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME);
                } else {
                    AppSingleton.getEngine().connectWebsocket(false, null, null, 0, null);
                }
                return true;
            });
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

        SwitchPreference useLegacyZxingScannerPreference = screen.findPreference(SettingsActivity.PREF_KEY_USE_LEGACY_ZXING_SCANNER);
        if (useLegacyZxingScannerPreference != null && !BuildConfig.USE_GOOGLE_LIBS) {
            screen.removePreference(useLegacyZxingScannerPreference);
        }

        Preference engineDbTimings = screen.findPreference(SettingsActivity.PREF_KEY_SHOW_ENGINE_DATABASE_STATISTICS);
        if (engineDbTimings != null) {
            if (SettingsActivity.getBetaFeaturesEnabled()) {
                engineDbTimings.setVisible(true);
                engineDbTimings.setOnPreferenceClickListener((Preference preference) -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("query,count,time_µs\n");
                    ArrayList<Map.Entry<String, EngineDbQueryStatisticsEntry>> entries = new ArrayList<>(AppSingleton.getEngine().getEngineDbQueryStatistics().entrySet());
                    entries.sort(Comparator.comparingLong(e -> -e.getValue().totalTimeMicro));
                    for (Map.Entry<String, EngineDbQueryStatisticsEntry> entry : entries) {
                        sb.append(String.format(Locale.ENGLISH, "%s,%d,%d\n", entry.getKey(), entry.getValue().count, entry.getValue().totalTimeMicro));
                    }
                    String stats = sb.toString();
                    AlertDialog dialog = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_engine_db_statistics)
                            .setMessage(stats)
                            .setNeutralButton(R.string.button_label_copy, (di, which) -> {
                                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText("db_stats", stats);
                                clipboard.setPrimaryClip(clip);
                                App.toast(R.string.toast_message_clipboard_copied, Toast.LENGTH_SHORT);
                            })
                            .setPositiveButton(R.string.button_label_ok, null)
                            .create();
                    dialog.show();
                    return true;
                });
            }
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
                            .setMessage(Markdown.formatMarkdown(getString(R.string.dialog_message_export_app_databases), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE))
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
                File plaintextDbFile = new File(App.absolutePathFromRelative("plaintext.db"));
                if (plaintextDbFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    plaintextDbFile.delete();
                }

                {
                    String engineDbKey = DatabaseKey.get(DatabaseKey.ENGINE_DATABASE_SECRET);
                    if (engineDbKey != null) {
                        try (Session session = Session.getUpgradeTablesSession(App.absolutePathFromRelative(Constants.ENGINE_DB_FILENAME), engineDbKey)) {
                            try (Statement statement = session.createStatement()) {
                                statement.execute("ATTACH DATABASE '" + plaintextDbFile.getPath() + "' AS plaintext KEY ''");
                                statement.execute("SELECT sqlcipher_export('plaintext');");
                                statement.execute("DETACH DATABASE plaintext;");
                            }
                        } catch (Exception e) {
                            Logger.e("Unable to decrypt engine database", e);
                        }
                    }
                    ZipEntry engineDb = new ZipEntry(Constants.ENGINE_DB_FILENAME);
                    zipOutputStream.putNextEntry(engineDb);
                    try (FileInputStream fis = new FileInputStream(plaintextDbFile.getPath())) {
                        byte[] buffer = new byte[262_144];
                        int c;
                        while ((c = fis.read(buffer)) != -1) {
                            zipOutputStream.write(buffer, 0, c);
                        }
                    }
                    //noinspection ResultOfMethodCallIgnored
                    plaintextDbFile.delete();
                }

                {
                    String appDbKey = DatabaseKey.get(DatabaseKey.APP_DATABASE_SECRET);
                    if (appDbKey != null) {
                        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(App.absolutePathFromRelative(AppDatabase.DB_FILE_NAME), appDbKey, null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY, null)) {
                            db.rawExecSQL("ATTACH DATABASE '" + plaintextDbFile.getPath() + "' AS plaintext KEY '';");
                            db.rawExecSQL("SELECT sqlcipher_export('plaintext');");
                            db.rawExecSQL("DETACH DATABASE plaintext;");
                        } catch (Exception ex) {
                            Logger.e("Unable to decrypt app database", ex);
                        }
                    }
                    ZipEntry appDb = new ZipEntry(AppDatabase.DB_FILE_NAME);
                    zipOutputStream.putNextEntry(appDb);
                    try (FileInputStream fis = new FileInputStream(plaintextDbFile.getPath())) {
                        byte[] buffer = new byte[262_144];
                        int c;
                        while ((c = fis.read(buffer)) != -1) {
                            zipOutputStream.write(buffer, 0, c);
                        }
                    }
                    //noinspection ResultOfMethodCallIgnored
                    plaintextDbFile.delete();
                }

                App.toast(R.string.toast_message_success_exporting_db, Toast.LENGTH_SHORT);
            }
        } catch (IOException e) {
            e.printStackTrace();
            App.toast(R.string.toast_message_error_exporting_db, Toast.LENGTH_SHORT);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(ContextCompat.getColor(view.getContext(), R.color.almostWhite));
    }

    @Override
    public void onResume() {
        super.onResume();
        activity.setTitle(R.string.pref_category_other_title);
    }
}
