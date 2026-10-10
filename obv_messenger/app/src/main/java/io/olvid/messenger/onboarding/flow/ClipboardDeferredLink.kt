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

package io.olvid.messenger.onboarding.flow

import android.app.Activity
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.core.content.edit
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.activities.ObvLinkActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.days

/**
 * Carries an Olvid link (invitation, mutual scan, configuration…) across a Play Store install.
 *
 * The landing pages copy the tapped link to the clipboard when the user heads to the Play Store,
 * and the Play install referrer only carries the flag `olvid_link=1`. On its first launch the app
 * reads that flag and, while the onboarding has window focus (the only moment Android lets a
 * foreground app read the clipboard), looks for an Olvid link in the clipboard exactly once.
 */
object ClipboardDeferredLink {
    private const val REFERRER_FLAG_PARAM = "olvid_link"
    private const val PREF_KEY_CLIPBOARD_LINK_EXPECTED = "pref_key_clipboard_link_expected"

    /** Below this system TextClassifier confidence the clipboard is not even read (API 31+). */
    private const val URL_CONFIDENCE_THRESHOLD = 0.5f

    /**
     * An invitation found after an install is worth keeping longer than one tapped mid-onboarding:
     * the user may only finish creating their profile days later and still expects it.
     */
    val DEFERRED_INVITATION_VALIDITY = 7.days

    private val prefs: SharedPreferences
        get() = App.getContext().getSharedPreferences(
            App.getContext().getString(R.string.preference_filename_app),
            Context.MODE_PRIVATE
        )

    private val _expected by lazy {
        MutableStateFlow(prefs.getBoolean(PREF_KEY_CLIPBOARD_LINK_EXPECTED, false))
    }

    /** True while an install flagged as coming from an Olvid link has not had its clipboard read yet. */
    val expected: StateFlow<Boolean>
        get() = _expected.asStateFlow()

    /** Returns true if [referrer] (a Play install referrer query string) carries the Olvid flag. */
    fun referrerCarriesFlag(referrer: String): Boolean =
        parseQuery(referrer)[REFERRER_FLAG_PARAM] == "1"

    /** Records that the clipboard should be inspected if [referrer] is flagged; returns whether it was. */
    fun handleInstallReferrer(referrer: String): Boolean {
        if (!referrerCarriesFlag(referrer)) {
            return false
        }
        prefs.edit { putBoolean(PREF_KEY_CLIPBOARD_LINK_EXPECTED, true) }
        _expected.value = true
        return true
    }

    /** Returns the first Olvid link found in [text], or null. */
    fun extractOlvidLink(text: CharSequence?): String? {
        val matcher = ObvLinkActivity.ANY_PATTERN.matcher(text ?: return null)
        return if (matcher.find()) matcher.group(0) else null
    }

    /**
     * Reads the clipboard once and returns the Olvid link it holds, if any. Must be called from an
     * [activity] that currently has window focus, otherwise Android hides the clipboard content.
     * The expectation is consumed whatever the outcome, so the system "pasted from…" notice is
     * shown at most once per install.
     */
    fun consumeFromClipboard(activity: Activity): String? {
        prefs.edit { putBoolean(PREF_KEY_CLIPBOARD_LINK_EXPECTED, false) }
        _expected.value = false

        val clipboard = activity.getSystemService(ClipboardManager::class.java) ?: return null
        val description = clipboard.primaryClipDescription ?: return null
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
            && !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        ) {
            return null
        }
        // the description is readable without touching the content: skip clips the system already
        // classified as clearly not a URL, so unrelated clipboards are never read
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && description.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE
            && description.getConfidenceScore(TextClassifier.TYPE_URL) < URL_CONFIDENCE_THRESHOLD
        ) {
            return null
        }
        val text = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(activity) }
            .getOrNull()
        return extractOlvidLink(text)
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&')
            .mapNotNull { pair ->
                val separator = pair.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                pair.substring(0, separator) to pair.substring(separator + 1)
            }
            .toMap()
}
