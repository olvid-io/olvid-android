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

package io.olvid.messenger.customClasses;


import android.content.Context;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.olvid.messenger.R;
import io.olvid.messenger.databases.tasks.backup.SettingsPojo_0;
import io.olvid.messenger.settings.SettingsActivity;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigurationSettingsPojo {
    public Boolean beta;

    public Integer hn; // hide_notification_contents;
    public Integer ns; // allow_notification_suggestions;
    public Integer xr; // expose_recent_discussions;
    public Integer ik; // incognito_keyboard;
    public Integer sc; // prevent_screen_capture;
    public Integer hp; // hidden_profile_policy;
    public Integer hpg; // hidden_profile_background_grace;
    public Integer dp; // disable_push_notifications;
    public Integer pw; // permanent_websocket;

    public Integer iv; // internal_viewer;

    public Integer aj; // auto_join_groups;
    public Integer tl; // show_trust_level;

    public Integer lb; // lock_biometry;
    public Integer ld; // lock_delay_s;
    public Integer ln; // lock_notification;
    public Integer lw; // lock_wipe_on_fail;

    public Long ad; // auto_download_size;
    public Integer rr; // send_read_receipt;
    public Integer ao; // auto_open_limited_visibility;
    public Integer rw; // retain_wiped_outbound;

    public Integer rj; // remove_jpeg_metadata;

    public Integer wk; // wc_keep_after_close;
    public Integer we; // wc_error_notification;
    public Integer wi; // wc_inactivity_indicator;
    public Integer wr; // wc_require_unlock;

    public Integer fs; // permanent_foreground_service;
    public Integer av; // share_app_version;
    public Integer cc; // notify_certificate_change;
    public Integer bc; // block_unknown_certificate;
    public Integer ss; // sending_foreground_service;
    public Integer ci; // connectivity_indicator;

    @JsonIgnore
    @NonNull
    public SettingsPojo_0 toBackupPojo() {
        SettingsPojo_0 pojo = new SettingsPojo_0();
        if (beta != null) { pojo.beta = beta; }

        if (hn != null) { pojo.hide_notification_contents = hn != 0; }
        if (ns != null) { pojo.allow_notification_suggestions = ns != 0; }
        if (xr != null) { pojo.expose_recent_discussions = xr != 0; }
        if (ik != null) { pojo.incognito_keyboard = ik != 0; }
        if (sc != null) { pojo.prevent_screen_capture = sc != 0; }
        if (hp != null) { pojo.hidden_profile_policy = hp; }
        if (hpg != null) { pojo.hidden_profile_background_grace = hpg; }
        if (dp != null) { pojo.disable_push_notifications = dp != 0; }
        if (pw != null) { pojo.permanent_websocket = pw != 0; }

        if (iv != null) { pojo.internal_viewer = iv != 0; }

        if (aj != null) {
            switch (aj) {
                case 0:
                    pojo.auto_join_groups = "nobody";
                    break;
                case 2:
                    pojo.auto_join_groups = "everyone";
                    break;
                case 1:
                default:
                    pojo.auto_join_groups = "contacts";
                    break;
            }
        }
        if (tl != null) { pojo.show_trust_level = tl != 0; }

        if (lb != null) { pojo.lock_biometry = lb != 0; }
        if (ld != null) { pojo.lock_delay_s = ld; }
        if (ln != null) { pojo.lock_notification = ln != 0; }
        if (lw != null) { pojo.lock_wipe_on_fail = lw != 0; }

        if (ad != null) { pojo.auto_download_size = ad; }
        if (rr != null) { pojo.send_read_receipt = rr != 0; }
        if (ao != null) { pojo.auto_open_limited_visibility = ao != 0; }
        if (rw != null) { pojo.retain_wiped_outbound = rw != 0; }
        
        if (rj != null) { pojo.remove_jpeg_metadata = rj != 0; }
        
        if (wk != null) { pojo.wc_keep_after_close = wk != 0; }
        if (we != null) { pojo.wc_error_notification = we != 0; }
        if (wi != null) { pojo.wc_inactivity_indicator = wi != 0; }
        if (wr != null) { pojo.wc_require_unlock = wr != 0; }

        if (fs != null) { pojo.permanent_foreground_service = fs != 0; }
        if (av != null) { pojo.share_app_version = av != 0; }
        if (cc != null) { pojo.notify_certificate_change = cc != 0; }
        if (bc != null) {
            switch (bc) {
                case 0:
                    pojo.block_unknown_certificate = "never";
                    break;
                case 2:
                    pojo.block_unknown_certificate = "always";
                    break;
                case 1:
                default:
                    pojo.block_unknown_certificate = "issuer";
                    break;
            }
        }
        if (ss != null) { pojo.sending_foreground_service = ss != 0; }
        if (ci != null) { 
            switch (ci) {
                case 1:
                    pojo.connectivity_indicator = "dot";
                    break;
                case 2:
                    pojo.connectivity_indicator = "line";
                    break;
                case 3:
                    pojo.connectivity_indicator = "full";
                    break;
                case 0:
                default:
                    pojo.connectivity_indicator = "none";
                    break;
            }
        }

        return pojo;
    }

    @NonNull
    public CharSequence prettyPrint(Context c) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (beta != null) { sb.append(highlight(c, R.string.text_setting_beta)).append(bool(c, beta)).append("\n"); }

        if (hn != null) { sb.append(highlight(c, R.string.text_setting_hide_notification_contents)).append(bool(c, hn != 0)).append("\n"); }
        if (ns != null) { sb.append(highlight(c, R.string.text_setting_allow_notification_suggestions)).append(bool(c, ns != 0)).append("\n"); }
        if (xr != null) { sb.append(highlight(c, R.string.text_setting_expose_recent_discussions)).append(bool(c, xr != 0)).append("\n"); }
        if (ik != null) { sb.append(highlight(c, R.string.text_setting_incognito_keyboard)).append(bool(c, ik != 0)).append("\n"); }
        if (sc != null) { sb.append(highlight(c, R.string.text_setting_prevent_screen_capture)).append(bool(c, sc != 0)).append("\n"); }
        if (hp != null) {
            sb.append(highlight(c, R.string.text_setting_hidden_profile_policy));
            switch (hp) {
                case SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND:
                    int delay = hpg != null ? hpg : 0;
                    sb.append(c.getString(R.string.text_setting_background, delay));
                    break;
                case SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_SCREEN_LOCK:
                    sb.append(c.getString(R.string.text_setting_screen_lock));
                    break;
                case SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_MANUAL_SWITCHING:
                default:
                    sb.append(c.getString(R.string.text_setting_manual));
                    break;
            }
            sb.append("\n");
        }
        if (dp != null) { sb.append(highlight(c, R.string.text_setting_disable_push_notifications)).append(bool(c, dp != 0)).append("\n"); }
        if (pw != null) { sb.append(highlight(c, R.string.text_setting_permanent_websocket)).append(bool(c, pw != 0)).append("\n"); }

        if (iv != null) { sb.append(highlight(c, R.string.text_setting_internal_viewer)).append(bool(c, iv != 0)).append("\n"); }

        if (aj != null) {
            sb.append(highlight(c, R.string.text_setting_auto_join_groups));
            switch (aj) {
                case 0:
                    sb.append(c.getString(R.string.text_setting_nobody));
                    break;
                case 2:
                    sb.append(c.getString(R.string.text_setting_everyone));
                    break;
                case 1:
                default:
                    sb.append(c.getString(R.string.text_setting_contacts));
                    break;
            }
            sb.append("\n");
        }
        if (tl != null) { sb.append(highlight(c, R.string.text_setting_show_trust_level)).append(bool(c, tl != 0)).append("\n"); }

        if (lb != null) { sb.append(highlight(c, R.string.text_setting_lock_biometry)).append(bool(c, lb != 0)).append("\n"); }
        if (ld != null) {
            sb.append(highlight(c, R.string.text_setting_lock_delay));
            if (ld == -1) {
                sb.append(c.getString(R.string.text_setting_lock_manually));
            } else {
                sb.append(c.getString(R.string.text_setting_xx_seconds, ld));
            }
            sb.append("\n");
        }
        if (ln != null) { sb.append(highlight(c, R.string.text_setting_lock_notification)).append(bool(c, ln != 0)).append("\n"); }
        if (lw != null) { sb.append(highlight(c, R.string.text_setting_lock_wipe_on_fail)).append(bool(c, lw != 0)).append("\n"); }

        if (ad != null) {
            sb.append(highlight(c, R.string.text_setting_auto_download_size));
            if (ad == -1) {
                sb.append(c.getString(R.string.text_setting_always));
            } else if (ad == 0) {
                sb.append(c.getString(R.string.text_setting_never));
            } else {
                sb.append(c.getString(R.string.text_setting_smaller_mb, ad/1000000));
            }
            sb.append("\n");
        }

        if (rr != null) { sb.append(highlight(c, R.string.text_setting_send_read_receipt)).append(bool(c, rr != 0)).append("\n"); }
        if (ao != null) { sb.append(highlight(c, R.string.text_setting_auto_open_limited_visibility)).append(bool(c, ao != 0)).append("\n"); }
        if (rw != null) { sb.append(highlight(c, R.string.text_setting_retain_wiped_outbound)).append(bool(c, rw != 0)).append("\n"); }

        if (rj != null) { sb.append(highlight(c, R.string.text_setting_remove_jpeg_metadata)).append(bool(c, rj != 0)).append("\n"); }

        if (wk != null) { sb.append(highlight(c, R.string.text_setting_wc_keep_after_close)).append(bool(c, wk != 0)).append("\n"); }
        if (we != null) { sb.append(highlight(c, R.string.text_setting_wc_error_notification)).append(bool(c, we != 0)).append("\n"); }
        if (wi != null) { sb.append(highlight(c, R.string.text_setting_wc_inactivity_indicator)).append(bool(c, wi != 0)).append("\n"); }
        if (wr != null) { sb.append(highlight(c, R.string.text_setting_wc_require_unlock)).append(bool(c, wr != 0)).append("\n"); }

        if (fs != null) { sb.append(highlight(c, R.string.text_setting_permanent_foreground_service)).append(bool(c, fs != 0)).append("\n"); }
        if (av != null) { sb.append(highlight(c, R.string.text_setting_share_app_version)).append(bool(c, av != 0)).append("\n"); }
        if (cc != null) { sb.append(highlight(c, R.string.text_setting_notify_certificate_change)).append(bool(c, cc != 0)).append("\n"); }
        if (bc != null) {
            sb.append(highlight(c, R.string.text_setting_block_unknown_certificate));
            switch (bc) {
                case 0:
                    sb.append(c.getString(R.string.text_setting_never));
                    break;
                case 2:
                    sb.append(c.getString(R.string.text_setting_always));
                    break;
                case 1:
                default:
                    sb.append(c.getString(R.string.text_setting_issuer_changed));
                    break;
            }
            sb.append("\n");
        }
        if (ss != null) { sb.append(highlight(c, R.string.text_setting_sending_foreground_service)).append(bool(c, ss != 0)).append("\n"); }
        if (ci != null) {
            sb.append(highlight(c, R.string.text_setting_connectivity_indicator));
            switch (ci) {
                case 1:
                    sb.append(c.getString(R.string.text_setting_dot));
                    break;
                case 2:
                    sb.append(c.getString(R.string.text_setting_line));
                    break;
                case 3:
                    sb.append(c.getString(R.string.text_setting_full));
                    break;
                case 0:
                default:
                    sb.append(c.getString(R.string.text_setting_none));
                    break;
            }
            sb.append("\n");
        }
        return sb;
    }

    private static CharSequence highlight(Context context, @StringRes int stringId) {
        SpannableString spannableString = new SpannableString(context.getString(stringId));
        spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.olvid_gradient_light)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannableString;
    }

    private static CharSequence bool(Context context, boolean b) {
        if (b) {
            return context.getString(R.string.text_setting_yes);
        } else {
            return context.getString(R.string.text_setting_no);
        }
    }
}
