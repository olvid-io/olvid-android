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

package io.olvid.messenger.settings;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.RecyclerViewDividerDecoration;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.ActionShortcutConfiguration;
import io.olvid.messenger.widget.ActionShortcutConfigurationActivity;
import io.olvid.messenger.widget.ActionShortcutWidgetProvider;

public class WidgetListFragment extends Fragment {
    private FragmentActivity activity;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_widget_settings, container, false);

        EmptyRecyclerView recyclerView = rootView.findViewById(R.id.widget_list_recycler_view);
        View emptyView = rootView.findViewById(R.id.widget_list_empty_view);
        recyclerView.setEmptyView(emptyView);
        recyclerView.setHideIfEmpty(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new RecyclerViewDividerDecoration(activity, 60, 8));

        WidgetListAdapter adapter = new WidgetListAdapter();
        recyclerView.setAdapter(adapter);

        AppDatabase.getInstance().actionShortcutConfigurationDao().getAll().observe(this, adapter);

        return rootView;
    }


    private class WidgetListAdapter extends RecyclerView.Adapter<WidgetViewHolder> implements Observer<List<ActionShortcutConfiguration>> {
        private List<ActionShortcutConfiguration> widgetList;

        @Override
        public void onBindViewHolder(@NonNull WidgetViewHolder holder, int position) {
            if (widgetList == null || position < 0 || position >= widgetList.size()) {
                return;
            }
            ActionShortcutConfiguration actionShortcutConfiguration = widgetList.get(position);
            ActionShortcutConfiguration.JsonConfiguration configuration = actionShortcutConfiguration.getJsonConfiguration();
            holder.appWidgetId = actionShortcutConfiguration.appWidgetId;
            if (configuration == null) {
                holder.widgetIconImageView.setImageDrawable(null);
                SpannableString spannableString = new SpannableString(getString(R.string.label_unable_to_read_widget_configuration));
                spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.widgetLabelTextView.setText(spannableString);
            } else {
                Drawable drawable = ResourcesCompat.getDrawable(getResources(), ActionShortcutWidgetProvider.getIconResource(configuration.widgetIcon), null);
                if (drawable != null) {
                    drawable.mutate();
                    if (configuration.widgetIconTint != null) {
                        drawable.setColorFilter(new PorterDuffColorFilter(configuration.widgetIconTint, PorterDuff.Mode.SRC_IN));
                    }
                    holder.widgetIconImageView.setImageDrawable(drawable);
                } else {
                    holder.widgetIconImageView.setImageDrawable(null);
                }

                if (configuration.widgetLabel != null && configuration.widgetLabel.length() > 0) {
                    holder.widgetLabelTextView.setText(configuration.widgetLabel);
                } else {
                    SpannableString spannableString = new SpannableString(getString(R.string.label_no_label));
                    spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.widgetLabelTextView.setText(spannableString);
                }
            }
        }

        @Override
        public void onViewRecycled(@NonNull WidgetViewHolder holder) {
            super.onViewRecycled(holder);
            holder.appWidgetId = null;
        }

        @NonNull
        @Override
        public WidgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new WidgetViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.item_view_widget, parent, false));
        }

        @Override
        public int getItemCount() {
            if (widgetList == null) {
                return 0;
            }
            return widgetList.size();
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onChanged(List<ActionShortcutConfiguration> actionShortcutConfigurations) {
            widgetList = actionShortcutConfigurations;
            notifyDataSetChanged();
        }
    }

    private class WidgetViewHolder extends RecyclerView.ViewHolder {
        Integer appWidgetId;
        final ImageView widgetIconImageView;
        final TextView widgetLabelTextView;
        final ImageView deleteWidgetImageView;

        public WidgetViewHolder(@NonNull View itemView) {
            super(itemView);

            widgetIconImageView = itemView.findViewById(R.id.widget_icon);
            widgetLabelTextView = itemView.findViewById(R.id.widget_label);
            deleteWidgetImageView = itemView.findViewById(R.id.delete_widget);
            itemView.setOnClickListener(v -> {
                if (appWidgetId != null) {
                    Intent configurationIntent = new Intent(getContext(), ActionShortcutConfigurationActivity.class);
                    configurationIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    startActivity(configurationIntent);
                }
            });

            deleteWidgetImageView.setOnClickListener(v -> {
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_delete_widget_configuration)
                        .setMessage(R.string.dialog_message_delete_widget_configuration)
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setPositiveButton(R.string.button_label_ok, (dialog, which) -> App.runThread(() -> {
                            if (appWidgetId != null) {
                                AppDatabase.getInstance().actionShortcutConfigurationDao().delete(appWidgetId);

                                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(activity);
                                RemoteViews widgetViews = new RemoteViews(activity.getPackageName(), R.layout.widget_action_shortcut_invalid);
                                appWidgetManager.updateAppWidget(appWidgetId, widgetViews);
                            }
                        }));
                builder.create().show();
            });
        }
    }
}
