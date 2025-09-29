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

package io.olvid.messenger.customClasses

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.core.content.ContextCompat
import io.olvid.messenger.R
import io.olvid.messenger.settings.SettingsActivity


private fun AnnotatedString.Builder.appendFluid(a: AnnotatedString): AnnotatedString.Builder {
    append(a)
    return this
}
private fun AnnotatedString.Builder.appendFluid(a: String): AnnotatedString.Builder {
    append(a)
    return this
}

fun ConfigurationSettingsPojo.prettyPrint(c: Context): AnnotatedString {
    val sb = AnnotatedString.Builder()
    if (beta != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_beta)).appendFluid(bool(c, beta)).appendFluid("\n")
    }

    if (hn != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_hide_notification_contents))
            .appendFluid(bool(c, hn != 0)).appendFluid("\n")
    }
    if (ns != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_allow_notification_suggestions))
            .appendFluid(bool(c, ns != 0)).appendFluid("\n")
    }
    if (xr != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_expose_recent_discussions))
            .appendFluid(bool(c, xr != 0)).appendFluid("\n")
    }
    if (ik != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_incognito_keyboard)).appendFluid(bool(c, ik != 0))
            .appendFluid("\n")
    }
    if (sc != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_prevent_screen_capture))
            .appendFluid(bool(c, sc != 0)).appendFluid("\n")
    }
    if (hp != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_hidden_profile_policy))
        when (hp) {
            SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND -> {
                val delay = if (hpg != null) hpg else 0
                sb.appendFluid(c.getString(R.string.text_setting_background, delay))
            }

            SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_SCREEN_LOCK -> sb.appendFluid(c.getString(R.string.text_setting_screen_lock))
            SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_MANUAL_SWITCHING -> sb.appendFluid(c.getString(R.string.text_setting_manual))
            else -> sb.appendFluid(c.getString(R.string.text_setting_manual))
        }
        sb.appendFluid("\n")
    }
    if (dp != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_disable_push_notifications))
            .appendFluid(bool(c, dp != 0)).appendFluid("\n")
    }
    if (pw != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_permanent_websocket)).appendFluid(bool(c, pw != 0))
            .appendFluid("\n")
    }

    if (iv != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_internal_viewer)).appendFluid(bool(c, iv != 0))
            .appendFluid("\n")
    }

    if (aj != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_auto_join_groups))
        when (aj) {
            0 -> sb.appendFluid(c.getString(R.string.text_group_remote_delete_setting_nobody))
            2 -> sb.appendFluid(c.getString(R.string.text_group_remote_delete_setting_everyone))
            1 -> sb.appendFluid(c.getString(R.string.text_setting_contacts))
            else -> sb.appendFluid(c.getString(R.string.text_setting_contacts))
        }
        sb.appendFluid("\n")
    }
    if (tl != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_show_trust_level)).appendFluid(bool(c, tl != 0))
            .appendFluid("\n")
    }

    if (lb != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_lock_biometry)).appendFluid(bool(c, lb != 0))
            .appendFluid("\n")
    }
    if (ld != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_lock_delay))
        if (ld == -1) {
            sb.appendFluid(c.getString(R.string.text_setting_lock_manually))
        } else {
            sb.appendFluid(c.getString(R.string.text_setting_xx_seconds, ld))
        }
        sb.appendFluid("\n")
    }
    if (ln != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_lock_notification)).appendFluid(bool(c, ln != 0))
            .appendFluid("\n")
    }
    if (lw != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_lock_wipe_on_fail)).appendFluid(bool(c, lw != 0))
            .appendFluid("\n")
    }

    if (ad != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_auto_download_size))
        if (ad == -1L) {
            sb.appendFluid(c.getString(R.string.text_setting_always))
        } else if (ad == 0L) {
            sb.appendFluid(c.getString(R.string.text_setting_never))
        } else {
            sb.appendFluid(c.getString(R.string.text_setting_smaller_mb, ad / 1000000))
        }
        sb.appendFluid("\n")
    }
    if (aa != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_auto_download_archived))
            .appendFluid(bool(c, aa != 0)).appendFluid("\n")
    }
    if (un != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_unarchive_on_notification))
            .appendFluid(bool(c, un != 0)).appendFluid("\n")
    }

    if (lpi != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_link_preview_inbound))
            .appendFluid(bool(c, lpi != 0)).appendFluid("\n")
    }
    if (lpo != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_link_preview_outbound))
            .appendFluid(bool(c, lpo != 0)).appendFluid("\n")
    }

    if (rr != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_send_read_receipt)).appendFluid(bool(c, rr != 0))
            .appendFluid("\n")
    }
    if (ao != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_auto_open_limited_visibility))
            .appendFluid(bool(c, ao != 0)).appendFluid("\n")
    }
    if (rw != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_retain_wiped_outbound))
            .appendFluid(bool(c, rw != 0)).appendFluid("\n")
    }

    if (co != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_map_integration)).appendFluid(co).appendFluid("\n")
    } else if (mi != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_map_integration))
        when (mi) {
            1 -> sb.appendFluid(c.getString(R.string.text_setting_google_maps))
            2 -> sb.appendFluid(c.getString(R.string.text_setting_osm))
            0 -> sb.appendFluid(c.getString(R.string.text_setting_none))
            else -> sb.appendFluid(c.getString(R.string.text_setting_none))
        }
        sb.appendFluid("\n")
    }
    if (ca != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_address_lookup_server)).appendFluid(ca).appendFluid("\n")
    } else if (da != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_disable_address_lookup))
            .appendFluid(bool(c, da != 0)).appendFluid("\n")
    }


    if (rj != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_remove_jpeg_metadata))
            .appendFluid(bool(c, rj != 0)).appendFluid("\n")
    }

    if (wk != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_wc_keep_after_close)).appendFluid(bool(c, wk != 0))
            .appendFluid("\n")
    }
    if (we != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_wc_error_notification))
            .appendFluid(bool(c, we != 0)).appendFluid("\n")
    }
    if (wi != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_wc_inactivity_indicator))
            .appendFluid(bool(c, wi != 0)).appendFluid("\n")
    }
    if (wr != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_wc_require_unlock)).appendFluid(bool(c, wr != 0))
            .appendFluid("\n")
    }

    if (fs != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_permanent_foreground_service))
            .appendFluid(bool(c, fs != 0)).appendFluid("\n")
    }
    if (av != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_share_app_version)).appendFluid(bool(c, av != 0))
            .appendFluid("\n")
    }
    if (cc != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_notify_certificate_change))
            .appendFluid(bool(c, cc != 0)).appendFluid("\n")
    }
    if (bc != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_block_unknown_certificate))
        when (bc) {
            0 -> sb.appendFluid(c.getString(R.string.text_setting_never))
            2 -> sb.appendFluid(c.getString(R.string.text_setting_always))
            1 -> sb.appendFluid(c.getString(R.string.text_setting_issuer_changed))
            else -> sb.appendFluid(c.getString(R.string.text_setting_issuer_changed))
        }
        sb.appendFluid("\n")
    }
    if (clp != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_no_notify_certificate_change_for_previews))
            .appendFluid(bool(c, clp != 0)).appendFluid("\n")
    }
    if (ss != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_sending_foreground_service))
            .appendFluid(bool(c, ss != 0)).appendFluid("\n")
    }
    if (ci != null) {
        sb.appendFluid(highlight(c, R.string.text_setting_connectivity_indicator))
        when (ci) {
            1 -> sb.appendFluid(c.getString(R.string.text_setting_dot))
            2 -> sb.appendFluid(c.getString(R.string.text_setting_line))
            3 -> sb.appendFluid(c.getString(R.string.text_setting_full))
            0 -> sb.appendFluid(c.getString(R.string.text_setting_none))
            else -> sb.appendFluid(c.getString(R.string.text_setting_none))
        }
        sb.appendFluid("\n")
    }
    return sb.toAnnotatedString().let {
        if (it.isNotEmpty())
            it.subSequence(0, it.length - 1)
        else
            it
    }
}

private fun highlight(context: Context, @StringRes stringId: Int): AnnotatedString {
    val string: String = context.getString(stringId)

    return AnnotatedString(
        text = string,
        spanStyles = listOf(
            AnnotatedString.Range(
                item = SpanStyle(color = Color(ContextCompat.getColor(context, R.color.olvid_gradient_light))),
                start = 0,
                end = string.length
            )
        ),
    )
}

private fun bool(context: Context, b: Boolean): String {
    return context.getString(
        if (b)
            R.string.text_setting_yes
        else
            R.string.text_setting_no
    )
}
