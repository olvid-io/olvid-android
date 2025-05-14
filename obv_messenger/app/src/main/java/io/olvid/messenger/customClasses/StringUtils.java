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

package io.olvid.messenger.customClasses;

import android.content.ContentResolver;
import android.content.Context;
import android.icu.lang.UCharacter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;

public class StringUtils {

    public static final Pattern unAccentPattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    public static final Pattern positionAndCompanyPattern = Pattern.compile("^([^(]+) \\(.+\\)$");

    public static String getNiceDurationString(@NonNull Context context, long duration) {
        if (duration < 60) {
            return context.getResources().getQuantityString(R.plurals.duration_seconds, (int) duration, duration);
        } else if (duration < 3600) {
            return context.getResources().getQuantityString(R.plurals.duration_minutes, (int) (duration/60), duration/60);
        } else if (duration < 86400) {
            return context.getResources().getQuantityString(R.plurals.duration_hours, (int) (duration/3600), duration/3600);
        } else if (duration < 31536000) {
            return context.getResources().getQuantityString(R.plurals.duration_days, (int) (duration/86400), duration/86400);
        } else {
            return context.getResources().getQuantityString(R.plurals.duration_years, (int) (duration/31536000), duration/31536000);
        }
    }

    public static CharSequence getNiceDateString(@NonNull Context context, long timestamp) {
        long now = System.currentTimeMillis();
        if (DateUtils.isToday(timestamp)) {
            // same day
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        } else if (timestamp < now
                && (timestamp + 86_400_000*6 > now || DateUtils.isToday(timestamp + 86_400_000*6))) {
            // same week
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_WEEKDAY) + context.getString(R.string.text_date_time_separator) + DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        } else {
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH) + context.getString(R.string.text_date_time_separator) + DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        }
    }

    public static CharSequence getLongNiceDateString(@NonNull Context context, long timestamp) {
        long now = System.currentTimeMillis();
        if (DateUtils.isToday(timestamp)) {
            // same day
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        } else if (timestamp < now
                && (timestamp + 86_400_000*6 > now || DateUtils.isToday(timestamp + 86_400_000*6))) {
            // same week
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_WEEKDAY) + context.getString(R.string.text_date_time_separator) + DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        } else {
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_MONTH) + context.getString(R.string.text_date_time_separator) + DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        }
    }

    public static CharSequence getDayOfDateString(@NonNull Context context, long timestamp) {
        long now = System.currentTimeMillis();
        if (DateUtils.isToday(timestamp)) {
            // same day
            return context.getString(R.string.text_today);
        } else if ((timestamp < now && timestamp + 86_400_000 > now) ||
                DateUtils.isToday(timestamp + 86_400_000)) {
            // yesterday
            return context.getString(R.string.text_yesterday);
        } else if ((timestamp < now && timestamp + 86_400_000*6 > now) ||
                DateUtils.isToday(timestamp + 86_400_000*6)) {
            // same week
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_WEEKDAY);
        } else {
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY);
        }
    }

    public static String getDateString(@NonNull Context context, long timestamp) {
        long now = System.currentTimeMillis();
        if (DateUtils.isToday(timestamp)) {
            // same day
            return context.getString(R.string.text_today);
        } else if ((timestamp < now && timestamp + 86_400_000 > now) ||
                DateUtils.isToday(timestamp + 86_400_000)) {
            // yesterday
            return context.getString(R.string.text_yesterday);
        } else {
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE);
        }
    }

    public static CharSequence getPreciseAbsoluteDateString(@NonNull Context context, long timestamp) {
        return getPreciseAbsoluteDateString(context, timestamp, "\n");
    }

    private static final HashMap<String, SimpleDateFormat> bestTimeFormatterCache = new HashMap<>();

    public static CharSequence getPreciseAbsoluteDateString(@NonNull Context context, long timestamp, String separator) {
        Locale locale = context.getResources().getConfiguration().locale;
        SimpleDateFormat formatter = bestTimeFormatterCache.get(locale.toString());
        if (formatter == null) {
            String patternDay = DateFormat.getBestDateTimePattern(locale, "yyyy MMM dd");
            String patternTime = DateFormat.getBestDateTimePattern(locale, "jj mm ss");
            String pattern = patternDay + separator + patternTime;
            formatter = new SimpleDateFormat(pattern, locale);
            bestTimeFormatterCache.put(locale.toString(), formatter);
        }
        return formatter.format(new Date(timestamp));
    }



    public static String getInitial(String name) {
        if ((name == null) || (name.isEmpty())) {
            return "";
        }
        BreakIterator breakIterator = BreakIterator.getCharacterInstance();
        breakIterator.setText(name);
        int offset;
        int glue;
        int modifier;
        do {
            offset = breakIterator.next();
            glue = name.charAt(offset-1);
            modifier = (offset < name.length())?name.codePointAt(offset):0;
        } while ((glue == 0x200d) || ((0x1f3fb <= modifier) && (modifier <= 0x1f3ff)));
        return name.substring(0, offset).toUpperCase(Locale.getDefault());
    }

    public static String removeCompanyFromDisplayName(@NonNull String displayName) {
        Matcher matcher = positionAndCompanyPattern.matcher(displayName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return displayName;
    }

    public static boolean isShortEmojiString(String text, int maxLength) {
        // Alternate method based on EmojiCompat library --> we use the legacy method for now, it still works well :)
//        CharSequence emojiSequence = EmojiCompat.get().process(text, 0, text.length(), maxLength);
//        if (emojiSequence instanceof Spanned) {
//            Spanned spannable = (Spanned) emojiSequence;
//            int regionEnd;
//            for (int regionStart = 0; regionStart < spannable.length(); regionStart = regionEnd) {
//                regionEnd = spannable.nextSpanTransition(regionStart, spannable.length(), EmojiSpan.class);
//
//                EmojiSpan[] spans = spannable.getSpans(regionStart, regionEnd, EmojiSpan.class);
//                if (spans.length == 0) {
//                    return false;
//                }
//            }
//            return true;
//        }
//        return false;

        if (text == null || text.isEmpty()) {
            return false;
        }

        BreakIterator breakIterator = BreakIterator.getCharacterInstance();
        breakIterator.setText(text);

        int computedEmojiLength = 0;
        int offset;
        int glue;
        int codePoint = text.codePointAt(0);

        do {
            do {
                if (!isEmojiCodepoint(codePoint)) {
                    return false;
                }
                offset = breakIterator.next();
                glue = text.charAt(offset - 1);
                codePoint = (offset < text.length()) ? text.codePointAt(offset) : 0;
            } while ((glue == 0x200d) || ((0x1f3fb <= codePoint) && (codePoint <= 0x1f3ff)));
            computedEmojiLength++;
            if (offset >= text.length()) {
                break;
            }
        } while (computedEmojiLength <= maxLength);

        return computedEmojiLength <= maxLength;
    }

    static boolean isEmojiCodepoint(int codePoint) {
        if (codePoint >= 0x1f000 && codePoint <= 0x1faff) {
            return true;
        }
        if (codePoint >= 0xe0020 && codePoint <= 0xe007f) {
            return true;
        }
        if (codePoint >= 0xfe00 && codePoint <= 0xfe0f) {
            return true;
        }
        if (codePoint >= 0x2194 && codePoint <= 0x2b55) {
            return true;
        }
        if (codePoint >= 0x20d0 && codePoint <= 0x20ff) {
            return true;
        }
        return codePoint == 0x200d;
    }

    public static String unAccent(CharSequence source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return UCharacter.toLowerCase(unAccentPattern.matcher(Normalizer.normalize(source, Normalizer.Form.NFD)).replaceAll(""));
        } else {
            return unAccentPattern.matcher(Normalizer.normalize(source, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.getDefault());
        }
    }

    public static boolean isASubstringOfB(@NonNull String a, @NonNull String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        int aIndex = 0;
        int bIndex = 0;
        while (aIndex < aBytes.length && bIndex < bBytes.length) {
            if (aBytes[aIndex] == bBytes[bIndex]) {
                aIndex++;
            }
            bIndex++;
        }

        return aIndex == aBytes.length;
    }

    @NonNull
    public static String joinContactDisplayNames(String[] groupMembersNames, int limit) {
        if (groupMembersNames == null || groupMembersNames.length == 0) {
            return "";
        } else if (groupMembersNames.length == 1) {
            return groupMembersNames[0];
        } else {
            String joiner = App.getContext().getString(R.string.text_contact_names_separator);
            String lastJoiner = App.getContext().getString(R.string.text_contact_names_last_separator);
            StringBuilder sb = new StringBuilder(groupMembersNames[0]);
            int length;
            int others;
            if (limit > 0 && groupMembersNames.length - 1 > limit) { // we consider the last " & 2 others" as a name counting towards the limit, so others should never be 1
                length = limit;
                others = groupMembersNames.length - limit;
            } else {
                length = groupMembersNames.length - 1;
                others = 0;
            }
            for (int i = 1; i < length; i++) {
                sb.append(joiner);
                sb.append(groupMembersNames[i]);
            }
            if (others > 0) {
                sb.append(App.getContext().getResources().getQuantityString(R.plurals.text_contact_names_last_separator_x_other, others, others));
            } else {
                sb.append(lastJoiner);
                sb.append(groupMembersNames[groupMembersNames.length - 1]);
            }
            return sb.toString();
        }
    }

    @NonNull
    public static String joinContactDisplayNames(String[] groupMembersNames) {
        return  joinContactDisplayNames(groupMembersNames, 0);
    }



    // used to validate an externally received Uri and make sure it does not point to an internal file
    public static boolean validateUri(Uri uri) {
        String DATA_DIR = Environment.getDataDirectory().toString();
        boolean valid = false;
        if (uri != null && uri.getPath() != null) {
            try {
                if (Objects.equals(ContentResolver.SCHEME_FILE, uri.getScheme())) {
                    String filePath = new File(uri.getPath()).getCanonicalPath();
                    valid = !filePath.startsWith(DATA_DIR);
                } else {
                    valid = true;
                }
            } catch (Exception ignored) { }
        }
        if (!valid) {
            Logger.w("Filtered out potentially harmful Uri: " + uri);
        }
        return valid;
    }
}
