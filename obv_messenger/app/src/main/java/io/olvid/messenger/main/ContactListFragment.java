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

package io.olvid.messenger.main;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.fragment.app.FragmentActivity;

import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import androidx.lifecycle.Transformations;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.fragments.FilteredContactListFragment;
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.PromptToDeleteContactTask;

public class ContactListFragment extends FilteredContactListFragment implements PopupMenu.OnMenuItemClickListener, FilteredContactListFragment.FilteredContactListOnClickDelegate, SwipeRefreshLayout.OnRefreshListener, EngineNotificationListener {
    private FragmentActivity parentActivity;
    private TextView emptyViewTextView;
    private Long engineNotificationListenerRegistrationNumber;
    private View rootView;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof FragmentActivity) {
            parentActivity = (FragmentActivity) context;
        } else {
            parentActivity = null;
        }
   }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setOnClickDelegate(this);

        engineNotificationListenerRegistrationNumber = null;
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.SERVER_POLLED, this);

        Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().contactDao().getAllForOwnedIdentity(ownedIdentity.bytesOwnedIdentity);
        }).observe(this, (List<Contact> contacts) -> {
            filteredContactListViewModel.setUnfilteredContacts(contacts);
            if (contacts != null) {
                setEmptyTextViewText(contacts.size() == 0);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.SERVER_POLLED, this);
    }

    private void setEmptyTextViewText(boolean empty) {
        if (empty) {
            emptyViewTextView.setText(R.string.explanation_empty_contact_list);
        } else {
            emptyViewTextView.setText(R.string.explanation_no_contact_match_filter);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = super.onCreateView(inflater, container, savedInstanceState);
        recyclerView.setPadding(0,0,0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 155, getResources().getDisplayMetrics()));
        if (rootView != null) {
            emptyViewTextView = rootView.findViewById(R.id.widget_list_empty_view_text_view);
            if (filteredContactListViewModel.getUnfilteredContacts() != null) {
                setEmptyTextViewText(filteredContactListViewModel.getUnfilteredContacts().size() == 0);
            }
        }
        swipeRefreshLayout.setEnabled(true);
        swipeRefreshLayout.setOnRefreshListener(this);

        return rootView;
    }

    @Override
    public void onRefresh() {
        if (AppSingleton.getBytesCurrentIdentity() != null) {
            AppSingleton.getEngine().downloadMessages(AppSingleton.getBytesCurrentIdentity());
            App.runThread(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                        App.toast(R.string.toast_message_polling_failed, Toast.LENGTH_SHORT);
                    }
                });
            });
        } else {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        if (EngineNotifications.SERVER_POLLED.equals(notificationName)) {
            byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.SERVER_POLLED_BYTES_OWNED_IDENTITY_KEY);
            final Boolean success = (Boolean) userInfo.get(EngineNotifications.SERVER_POLLED_SUCCESS_KEY);
            if (success != null
                    && Arrays.equals(bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        if (!success) {
                            App.toast(R.string.toast_message_polling_failed, Toast.LENGTH_SHORT);
                        }
                    });
                }
            }
        }
    }

    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        engineNotificationListenerRegistrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return engineNotificationListenerRegistrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return engineNotificationListenerRegistrationNumber != null;
    }

    @Override
    public void contactClicked(View view, Contact contact) {
        if (contact != null) {
            App.openContactDetailsActivity(view.getContext(), contact.bytesOwnedIdentity, contact.bytesContactIdentity);
        }
    }


    private Contact longClickedContact;

    @Override
    public void contactLongClicked(View view, Contact contact) {
        this.longClickedContact = contact;
        PopupMenu popup = new PopupMenu(view.getContext(), view);
        popup.inflate(R.menu.popup_contact);
        popup.setOnMenuItemClickListener(this);
        popup.show();
    }



    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.popup_action_delete_contact) {
            if (getContext() != null) {
                App.runThread(new PromptToDeleteContactTask(getContext(), longClickedContact.bytesOwnedIdentity, longClickedContact.bytesContactIdentity, null));
            }
            return true;
        } else if (itemId == R.id.popup_action_rename_contact) {
            EditNameAndPhotoDialogFragment editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(parentActivity, longClickedContact);
            editNameAndPhotoDialogFragment.show(getChildFragmentManager(), "dialog");
            return true;
        } else if (itemId == R.id.popup_action_call_contact) {
            if (getContext() != null) {
                App.startWebrtcCall(getContext(), longClickedContact.bytesOwnedIdentity, longClickedContact.bytesContactIdentity);
            }
            return true;
        }
        return false;
    }

    // endregion
}

