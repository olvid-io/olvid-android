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

package io.olvid.messenger.openid;


import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import net.openid.appauth.browser.BrowserDescriptor;
import net.openid.appauth.browser.BrowserSelector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.Markdown;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.settings.SettingsActivity;

public class KeycloakBrowserChooserDialog {

    public static void openBrowserChoiceDialog(@NonNull View v) {
        openBrowserChoiceDialog(v.getContext());
    }

    public static void openBrowserChoiceDialog(@NonNull Context context) {
        try {
            String currentBrowser = SettingsActivity.getPreferredKeycloakBrowser();

            HashMap<String, String> knownBrowsersMap = buildKnownBrowsersMap();
            List<Browser> browsers = new ArrayList<>();
            Set<String> insertedPackageNames = new HashSet<>();
            for (BrowserDescriptor browserDescriptor : BrowserSelector.getAllBrowsers(context)) {
                if (insertedPackageNames.contains(browserDescriptor.packageName)) {
                    continue;
                }

                String commonName = knownBrowsersMap.get(browserDescriptor.packageName);
                Browser browser = new Browser(commonName, browserDescriptor.packageName, false, browserDescriptor.packageName.equals(currentBrowser));
                if (browsers.isEmpty()) {
                    browsers.add(new Browser(context.getString(R.string.text_default_browser), browser.commonName != null ? browser.commonName : browser.packageName, true, currentBrowser == null));
                }
                browsers.add(browser);
                insertedPackageNames.add(browserDescriptor.packageName);
            }

            if (browsers.isEmpty()) {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_no_browser_found)
                        .setMessage(Markdown.formatMarkdown(context.getString(R.string.dialog_message_no_browser_found), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE))
                        .setNegativeButton(R.string.button_label_ok, null);
                builder.create().show();
            } else {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_choose_browser_for_authentication)
                        .setAdapter(new BrowserAdapter(context, browsers), (dialog, which) -> {
                            Browser browser = browsers.get(which);
                            if (browser.defaultBrowser) {
                                SettingsActivity.setPreferredKeycloakBrowser(null);
                            } else {
                                SettingsActivity.setPreferredKeycloakBrowser(browser.packageName);
                            }
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            }
        } catch (Exception e) {
            App.toast(R.string.toast_message_error_querying_for_available_browsers, Toast.LENGTH_SHORT);
        }
    }

    private static class BrowserAdapter extends ArrayAdapter<BrowserDescriptor> {
        @NonNull
        final List<Browser> browsers;
        final LayoutInflater layoutInflater;

        public BrowserAdapter(@NonNull Context context, @NonNull List<Browser> browsers) {
            super(context, 0);
            this.browsers = browsers;
            this.layoutInflater = LayoutInflater.from(context);
        }

        @SuppressLint("InflateParams")
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.item_view_browser, null);
            }
            TextView commonNameTextView = convertView.findViewById(R.id.browser_common_name_text_view);
            TextView packageNameTextView = convertView.findViewById(R.id.browser_package_name_text_view);
            ImageView currentBrowserImageView = convertView.findViewById(R.id.browser_is_current_image_view);

            Browser browser = browsers.get(position);
            if (browser.commonName != null) {
                commonNameTextView.setText(browser.commonName);
                packageNameTextView.setText(browser.packageName);
                packageNameTextView.setVisibility(View.VISIBLE);
            } else {
                commonNameTextView.setText(browser.packageName);
                packageNameTextView.setVisibility(View.GONE);
            }
            currentBrowserImageView.setVisibility(browser.currentBrowser ? View.VISIBLE : View.GONE);
            return convertView;
        }

        @Override
        public int getCount() {
            return browsers.size();
        }
    }

    private static HashMap<String, String> buildKnownBrowsersMap() {
        HashMap<String, String> map = new HashMap<>();
        map.put("org.mozilla.firefox", "Firefox");
        map.put("com.brave.browser", "Brave");
        map.put("com.opera.mini.native", "Opera Mini");
        map.put("com.android.chrome", "Chrome");
        map.put("org.torproject.torbrowser", "Tor Browser");
        map.put("com.microsoft.emmx", "Microsoft Edge");
        map.put("com.duckduckgo.mobile.android", "DuckDuckGo Browser");
        map.put("com.opera.browser", "Opera");
        map.put("com.sec.android.app.sbrowser", "Samsung Internet");
        map.put("org.mozilla.focus", "Firefox Focus");
        map.put("com.vivaldi.browser", "Vivaldi");
        map.put("com.kiwibrowser.browser", "Kiwi Browser");
        map.put("com.UCMobile.intl", "UC Browser");
        map.put("org.chromium.webview_shell", "Chromium WebView Shell");
        map.put("org.lineageos.jelly", "LineageOS Jelly Browser");
        map.put("org.mozilla.fennec_fdroid", "Fennec F-Droid");
        map.put("com.stoutner.privacybrowser.standard", "Privacy Browser");
        return map;
    }

    private static class Browser {
        @Nullable
        final String commonName;
        @NonNull
        final String packageName;
        final boolean defaultBrowser;
        final boolean currentBrowser;

        public Browser(@Nullable String commonName, @NonNull String packageName, boolean defaultBrowser, boolean currentBrowser) {
            this.commonName = commonName;
            this.packageName = packageName;
            this.defaultBrowser = defaultBrowser;
            this.currentBrowser = currentBrowser;
        }
    }
}
