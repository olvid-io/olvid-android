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
package io.olvid.messenger.openid

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.text.Spannable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.settings.SettingsActivity.Companion.preferredKeycloakBrowser
import net.openid.appauth.browser.BrowserDescriptor
import net.openid.appauth.browser.BrowserSelector


object KeycloakBrowserChooserDialog {
    @JvmStatic
    fun openBrowserChoiceDialog(context: Context) {
        try {
            val currentBrowser = preferredKeycloakBrowser

            val knownBrowsersMap = buildKnownBrowsersMap()
            val browsers: MutableList<Browser> = ArrayList()
            val insertedPackageNames: MutableSet<String?> = HashSet()
            for (browserDescriptor in BrowserSelector.getAllBrowsers(context)) {
                if (insertedPackageNames.contains(browserDescriptor.packageName)) {
                    continue
                }

                val commonName = knownBrowsersMap.get(browserDescriptor.packageName)
                val browser = Browser(
                    commonName,
                    browserDescriptor.packageName,
                    false,
                    browserDescriptor.packageName == currentBrowser
                )
                if (browsers.isEmpty()) {
                    browsers.add(
                        Browser(
                            context.getString(R.string.text_default_browser),
                            browser.commonName ?: browser.packageName,
                            true,
                            currentBrowser == null
                        )
                    )
                }
                browsers.add(browser)
                insertedPackageNames.add(browserDescriptor.packageName)
            }

            if (browsers.isEmpty()) {
                val builder = SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_no_browser_found)
                    .setMessage(
                        context.getString(R.string.dialog_message_no_browser_found)
                            .formatMarkdown(Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    )
                    .setNegativeButton(R.string.button_label_ok, null)
                builder.create().show()
            } else {
                val builder = SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_choose_browser_for_authentication)
                    .setAdapter(
                        BrowserAdapter(context, browsers)
                    ) { dialog: DialogInterface?, which: Int ->
                        val browser = browsers[which]
                        preferredKeycloakBrowser = if (browser.defaultBrowser) {
                            null
                        } else {
                            browser.packageName
                        }
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                builder.create().show()
            }
        } catch (_: Exception) {
            App.toast(
                R.string.toast_message_error_querying_for_available_browsers,
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun buildKnownBrowsersMap(): HashMap<String?, String?> {
        val map = HashMap<String?, String?>()
        map.put("org.mozilla.firefox", "Firefox")
        map.put("com.brave.browser", "Brave")
        map.put("com.opera.mini.native", "Opera Mini")
        map.put("com.android.chrome", "Chrome")
        map.put("org.torproject.torbrowser", "Tor Browser")
        map.put("com.microsoft.emmx", "Microsoft Edge")
        map.put("com.duckduckgo.mobile.android", "DuckDuckGo Browser")
        map.put("com.opera.browser", "Opera")
        map.put("com.sec.android.app.sbrowser", "Samsung Internet")
        map.put("org.mozilla.focus", "Firefox Focus")
        map.put("com.vivaldi.browser", "Vivaldi")
        map.put("com.kiwibrowser.browser", "Kiwi Browser")
        map.put("com.UCMobile.intl", "UC Browser")
        map.put("org.chromium.webview_shell", "Chromium WebView Shell")
        map.put("org.lineageos.jelly", "LineageOS Jelly Browser")
        map.put("org.mozilla.fennec_fdroid", "Fennec F-Droid")
        map.put("com.stoutner.privacybrowser.standard", "Privacy Browser")
        map.put("com.mi.globalbrowser", "Mi Browser")
        map.put("org.adblockplus.browser", "Adblock Browser")
        return map
    }

    private class BrowserAdapter(context: Context, val browsers: MutableList<Browser>) :
        ArrayAdapter<BrowserDescriptor?>(context, 0) {
        val layoutInflater: LayoutInflater = LayoutInflater.from(context)

        @SuppressLint("InflateParams")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.item_view_browser, null)
            }
            val commonNameTextView =
                convertView!!.findViewById<TextView>(R.id.browser_common_name_text_view)
            val packageNameTextView =
                convertView.findViewById<TextView>(R.id.browser_package_name_text_view)
            val currentBrowserImageView =
                convertView.findViewById<ImageView>(R.id.browser_is_current_image_view)

            val browser = browsers[position]
            if (browser.commonName != null) {
                commonNameTextView.text = browser.commonName
                packageNameTextView.text = browser.packageName
                packageNameTextView.visibility = View.VISIBLE
            } else {
                commonNameTextView.text = browser.packageName
                packageNameTextView.visibility = View.GONE
            }
            currentBrowserImageView.visibility = if (browser.currentBrowser) View.VISIBLE else View.GONE
            return convertView
        }

        override fun getCount(): Int {
            return browsers.size
        }
    }

    private class Browser(
        val commonName: String?,
        val packageName: String,
        val defaultBrowser: Boolean,
        val currentBrowser: Boolean
    )
}