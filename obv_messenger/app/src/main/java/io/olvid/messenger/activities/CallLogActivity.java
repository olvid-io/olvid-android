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

package io.olvid.messenger.activities;


import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.RecyclerViewDividerDecoration;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.CallLogItemDao;
import io.olvid.messenger.databases.entity.CallLogItem;
import io.olvid.messenger.databases.entity.CallLogItemContactJoin;
import io.olvid.messenger.fragments.dialog.CallContactDialogFragment;
import io.olvid.messenger.fragments.dialog.MultiCallStartDialogFragment;

public class CallLogActivity extends LockableActivity implements PopupMenu.OnMenuItemClickListener, View.OnClickListener {
    private CallLogAdapter adapter;
    private LiveData<List<CallLogItemDao.CallLogItemAndContacts>> callLogLiveData;
    private FloatingActionButton fab;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_log);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        fab = findViewById(R.id.fab);
        fab.setOnClickListener(this);

        EmptyRecyclerView recyclerView = findViewById(R.id.call_log_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        View recyclerEmptyView = findViewById(R.id.call_log_empty_view);

        adapter = new CallLogAdapter(this);

        recyclerView.setHideIfEmpty(false);
        recyclerView.setEmptyView(recyclerEmptyView);
        recyclerView.setAdapter(adapter);

        callLogLiveData = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), ownedIdentity -> {
            if (ownedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().callLogItemDao().getWithContactForOwnedIdentity(ownedIdentity.bytesOwnedIdentity);
        });
        callLogLiveData.observe(this, adapter);

        AndroidNotificationManager.clearAllMissedCallNotifications();

        recyclerView.addItemDecoration(new RecyclerViewDividerDecoration(this, 68, 12));
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_call_log, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_clear_log) {
            byte[] bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity();
            if (bytesOwnedIdentity != null) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_clear_call_log)
                        .setMessage(R.string.dialog_message_clear_call_log)
                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> App.runThread(() -> AppDatabase.getInstance().callLogItemDao().deleteAll(bytesOwnedIdentity)))
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void callClicked(CallLogItemDao.CallLogItemAndContacts callLogItem, View view) {
        if (callLogItem != null) {
            if (callLogItem.contacts.size() == 1) {
                App.startWebrtcCall(this, callLogItem.oneContact.bytesOwnedIdentity, callLogItem.oneContact.bytesContactIdentity);
            } else {
                List<byte[]> bytesContactIdentities = new ArrayList<>(callLogItem.contacts.size());
                for (CallLogItemContactJoin callLogItemContactJoin : callLogItem.contacts) {
                    bytesContactIdentities.add(callLogItemContactJoin.bytesContactIdentity);
                }
                MultiCallStartDialogFragment multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(callLogItem.callLogItem.bytesOwnedIdentity, callLogItem.callLogItem.bytesGroupOwnerAndUid, bytesContactIdentities);
                multiCallStartDialogFragment.show(getSupportFragmentManager(), "dialog");
            }
        }
    }

    CallLogItem longClickedCallLogItem;

    private void logLongClicked(CallLogItemDao.CallLogItemAndContacts callLogItem, View view) {
        longClickedCallLogItem = callLogItem.callLogItem;
        PopupMenu popup = new PopupMenu(this, view);
        popup.inflate(R.menu.popup_call_log_entry);
        popup.setOnMenuItemClickListener(this);
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.popup_action_delete_log) {
            if (longClickedCallLogItem != null) {
                final CallLogItem callLogItem = longClickedCallLogItem;
                longClickedCallLogItem = null;
                App.runThread(() -> AppDatabase.getInstance().callLogItemDao().delete(callLogItem));
            }
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.fab) {
            CallContactDialogFragment callContactDialogFragment = CallContactDialogFragment.newInstance();
            callContactDialogFragment.show(getSupportFragmentManager(), "dialog");
        }
    }

    class CallLogAdapter extends RecyclerView.Adapter<CallLogAdapter.CallLogItemViewHolder> implements Observer<List<CallLogItemDao.CallLogItemAndContacts>> {
        private List<CallLogItemDao.CallLogItemAndContacts> callLogItems;
        private final LayoutInflater inflater;

        public CallLogAdapter(AppCompatActivity activity) {
            inflater = activity.getLayoutInflater();
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public CallLogItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.item_view_call_log_item, parent, false);
            return new CallLogItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CallLogItemViewHolder holder, int position) {
            if (callLogItems == null) {
                return;
            }

            CallLogItemDao.CallLogItemAndContacts callLogItemAndContacts = callLogItems.get(position);
            if (callLogItemAndContacts.contacts.size() == 1) {
                holder.initialView.setKeycloakCertified(callLogItemAndContacts.oneContact.keycloakManaged);
                holder.initialView.setInactive(!callLogItemAndContacts.oneContact.active);
                if (callLogItemAndContacts.oneContact.getCustomPhotoUrl() != null) {
                    holder.initialView.setPhotoUrl(callLogItemAndContacts.oneContact.bytesContactIdentity, callLogItemAndContacts.oneContact.getCustomPhotoUrl());
                } else {
                    holder.initialView.setInitial(callLogItemAndContacts.oneContact.bytesContactIdentity, App.getInitial(callLogItemAndContacts.oneContact.getCustomDisplayName()));
                }
                holder.contactNameTextView.setMaxLines(1);
                holder.contactNameTextView.setText(callLogItemAndContacts.oneContact.getCustomDisplayName());
            } else {
                if (callLogItemAndContacts.group == null) {
                    holder.initialView.setInitial(new byte[0], Integer.toString(callLogItemAndContacts.contacts.size()));
                } else {
                    if (callLogItemAndContacts.group.getCustomPhotoUrl() != null) {
                        holder.initialView.setPhotoUrl(callLogItemAndContacts.group.bytesGroupOwnerAndUid, callLogItemAndContacts.group.getCustomPhotoUrl());
                    } else {
                        holder.initialView.setGroup(callLogItemAndContacts.group.bytesGroupOwnerAndUid);
                    }
                }
                holder.contactNameTextView.setMaxLines(Integer.MAX_VALUE);
                String separator = getString(R.string.text_contact_names_separator);
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (CallLogItemContactJoin callLogItemContactJoin: callLogItemAndContacts.contacts) {
                    String contactDisplayName = AppSingleton.getContactCustomDisplayName(callLogItemContactJoin.bytesContactIdentity);
                    if (contactDisplayName != null) {
                        if (!first) {
                            sb.append(separator);
                        }
                        first = false;
                        sb.append(contactDisplayName);
                    }
                }
                holder.contactNameTextView.setText(sb.toString());
            }

            if (callLogItemAndContacts.callLogItem.callType == CallLogItem.TYPE_INCOMING) {
                switch (callLogItemAndContacts.callLogItem.callStatus) {
                    case CallLogItem.STATUS_SUCCESSFUL:
                        holder.callTypeImageView.setImageResource(R.drawable.ic_call_incoming);
                        break;
                    case CallLogItem.STATUS_BUSY:
                        holder.callTypeImageView.setImageResource(R.drawable.ic_phone_busy_in);
                        break;
                    case CallLogItem.STATUS_MISSED:
                    case CallLogItem.STATUS_FAILED:
                        holder.callTypeImageView.setImageResource(R.drawable.ic_call_missed);
                        break;
                }
            } else {
                switch (callLogItemAndContacts.callLogItem.callStatus) {
                    case CallLogItem.STATUS_SUCCESSFUL:
                        holder.callTypeImageView.setImageResource(R.drawable.ic_call_outgoing);
                        break;
                    case CallLogItem.STATUS_BUSY:
                        holder.callTypeImageView.setImageResource(R.drawable.ic_phone_busy_out);
                        break;
                    case CallLogItem.STATUS_MISSED:
                    case CallLogItem.STATUS_FAILED:
                        holder.callTypeImageView.setImageResource(R.drawable.ic_call_failed);
                        break;
                }
            }

            if (callLogItemAndContacts.callLogItem.callStatus == CallLogItem.STATUS_SUCCESSFUL && callLogItemAndContacts.callLogItem.duration > 0) {
                holder.callTimestampTextView.setText(CallLogActivity.this.getString(R.string.text_call_timestamp_and_duration, App.getLongNiceDateString(CallLogActivity.this, callLogItemAndContacts.callLogItem.timestamp), callLogItemAndContacts.callLogItem.duration / 60, callLogItemAndContacts.callLogItem.duration % 60));
            } else {
                holder.callTimestampTextView.setText(App.getLongNiceDateString(CallLogActivity.this, callLogItemAndContacts.callLogItem.timestamp));
            }
        }

        @Override
        public int getItemCount() {
            if (callLogItems != null) {
                return callLogItems.size();
            }
            return 0;
        }

        @Override
        public long getItemId(int position) {
            if (callLogItems != null) {
                return callLogItems.get(position).callLogItem.id;
            }
            return -1;
        }

        @Override
        public void onChanged(List<CallLogItemDao.CallLogItemAndContacts> callLogItemList) {
            callLogItems = callLogItemList;
            notifyDataSetChanged();
        }

        class CallLogItemViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
            final View rootView;
            final InitialView initialView;
            final TextView contactNameTextView;
            final ImageView callTypeImageView;
            final TextView callTimestampTextView;
            final ImageView callImageView;

            CallLogItemViewHolder(View rootView) {
                super(rootView);
                this.rootView = rootView;
                rootView.setOnLongClickListener(this);
                initialView = rootView.findViewById(R.id.initial_view);
                contactNameTextView = rootView.findViewById(R.id.contact_name_text_view);
                callTypeImageView = rootView.findViewById(R.id.call_type_image_view);
                callTimestampTextView = rootView.findViewById(R.id.call_timestamp_text_view);
                callImageView = rootView.findViewById(R.id.call_image_view);
                callImageView.setOnClickListener(this);
            }

            @Override
            public void onClick(View view) {
                if (view.getId() == R.id.call_image_view) {
                    CallLogActivity.this.callClicked(callLogItems.get(this.getLayoutPosition()), view);
                }
            }

            @Override
            public boolean onLongClick(View view) {
                CallLogActivity.this.logLongClicked(callLogItems.get(this.getLayoutPosition()), view);
                return true;
            }
        }
    }
}
