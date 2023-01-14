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

package io.olvid.messenger.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.ActionShortcutConfiguration;

public class ActionShortcutWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        for (int appWidgetId : appWidgetIds) {
            Logger.i("\uD83D\uDC7E onUpdate " + appWidgetId);

            configureWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        for (int appWidgetId : appWidgetIds) {
            Logger.i("\uD83D\uDC7E onDelete " + appWidgetId);

            App.runThread(() -> AppDatabase.getInstance().actionShortcutConfigurationDao().delete(appWidgetId));
        }
    }

    public static int getIconResource(@NonNull String iconString) {
        switch (iconString) {
            case ActionShortcutConfiguration.JsonConfiguration.ICON_THUMB:
                return R.drawable.widget_thumb;
            case ActionShortcutConfiguration.JsonConfiguration.ICON_HEXES:
                return R.drawable.widget_hexes;
            case ActionShortcutConfiguration.JsonConfiguration.ICON_STAR:
                return R.drawable.widget_star;
            case ActionShortcutConfiguration.JsonConfiguration.ICON_QUESTION:
                return R.drawable.widget_question;
            case ActionShortcutConfiguration.JsonConfiguration.ICON_HEART:
                return R.drawable.widget_heart;
            case ActionShortcutConfiguration.JsonConfiguration.ICON_HAND:
                return R.drawable.widget_hand;
            case ActionShortcutConfiguration.JsonConfiguration.ICON_GRASS:
                return R.drawable.widget_grass;
            case ActionShortcutConfiguration.JsonConfiguration.ICON_BURN:
                return R.drawable.widget_burn;
            case ActionShortcutConfiguration.JsonConfiguration.ICON_ERROR:
                return R.drawable.widget_error;
            case ActionShortcutConfiguration.JsonConfiguration.ICON_WARN:
                return R.drawable.widget_warning;
            case ActionShortcutConfiguration.JsonConfiguration.ICON_OK:
                return R.drawable.widget_ok;
            case ActionShortcutConfiguration.JsonConfiguration.ICON_SEND:
            default:
                return R.drawable.widget_send;
        }
    }

    static void configureWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        App.runThread(() -> {
            ActionShortcutConfiguration actionShortcutConfiguration = AppDatabase.getInstance().actionShortcutConfigurationDao().getByAppWidgetId(appWidgetId);
            if (actionShortcutConfiguration != null) {
                ActionShortcutConfiguration.JsonConfiguration widgetConfiguration = actionShortcutConfiguration.getJsonConfiguration();
                if (widgetConfiguration != null) {

                    Intent onClickIntent = new Intent(context, ActionShortcutSendMessageActivity.class);
                    onClickIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    onClickIntent.putExtra(ActionShortcutSendMessageActivity.APP_WIDGET_ID_INTENT_EXTRA, appWidgetId);
                    final PendingIntent onClickPendingIntent;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        onClickPendingIntent = PendingIntent.getActivity(context, appWidgetId, onClickIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    } else {
                        onClickPendingIntent = PendingIntent.getActivity(context, appWidgetId, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    }


                    RemoteViews widgetViews = new RemoteViews(context.getPackageName(), R.layout.widget_action_shortcut);
                    widgetViews.setOnClickPendingIntent(R.id.widget_root, onClickPendingIntent);
                    widgetViews.setTextViewText(R.id.widget_label, widgetConfiguration.widgetLabel);
                    if (widgetConfiguration.widgetShowBadge) {
                        widgetViews.setViewVisibility(R.id.widget_branding, View.VISIBLE);
                    } else {
                        widgetViews.setViewVisibility(R.id.widget_branding, View.GONE);
                    }

                    Bundle appWidgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId);
                    int sizeDp = Math.max(appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT), appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
                    if (sizeDp == 0) {
                        sizeDp = 64;
                    }

                    int sizePixels = (int) (context.getResources().getDisplayMetrics().density * sizeDp);

                    Bitmap bitmap = Bitmap.createBitmap(sizePixels, sizePixels, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    Drawable drawable = ResourcesCompat.getDrawable(context.getResources(), getIconResource(widgetConfiguration.widgetIcon), null);
                    if (drawable != null) {
                        if (widgetConfiguration.widgetIconTint != null) {
                            drawable.setColorFilter(new PorterDuffColorFilter(widgetConfiguration.widgetIconTint, PorterDuff.Mode.SRC_IN));
                        }
                        drawable.setBounds(0, 0, sizePixels, sizePixels);
                        drawable.draw(canvas);
                    }
                    widgetViews.setImageViewBitmap(R.id.widget_icon, bitmap);

                    appWidgetManager.updateAppWidget(appWidgetId, widgetViews);
                    return;
                }
            }

            RemoteViews widgetViews = new RemoteViews(context.getPackageName(), R.layout.widget_action_shortcut_invalid);
            appWidgetManager.updateAppWidget(appWidgetId, widgetViews);
        });
    }
}
