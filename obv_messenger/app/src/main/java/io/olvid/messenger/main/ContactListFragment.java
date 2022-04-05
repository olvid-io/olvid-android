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
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LoadAwareAdapter;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.PromptToDeleteContactTask;
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.settings.SettingsActivity;

public class ContactListFragment extends Fragment implements PopupMenu.OnMenuItemClickListener, SwipeRefreshLayout.OnRefreshListener, EngineNotificationListener {
    private FragmentActivity activity;
    private Long engineNotificationListenerRegistrationNumber;
    protected SwipeRefreshLayout swipeRefreshLayout;

    protected ContactListViewModel contactListViewModel = null;
    protected EmptyRecyclerView recyclerView;
    protected TextView emptyViewTextView;

    protected ContactListAdapter contactListAdapter;
    protected HeaderAndSeparatorDecoration headerAndSeparatorDecoration;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = requireActivity();
        contactListViewModel = new ViewModelProvider(this).get(ContactListViewModel.class);

        LiveData<List<Contact>> unfilteredContacts = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().contactDao().getAllOneToOneForOwnedIdentity(ownedIdentity.bytesOwnedIdentity);
        });

        unfilteredContacts.observe(activity, (List<Contact> contacts) -> contactListViewModel.setUnfilteredContacts(contacts));

        LiveData<List<Contact>> unfilteredNotOneToOneContacts = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().contactDao().getAllNotOneToOneForOwnedIdentity(ownedIdentity.bytesOwnedIdentity);
        });

        unfilteredNotOneToOneContacts.observe(activity, (List<Contact> contacts) -> contactListViewModel.setUnfilteredNotOneToOneContacts(contacts));

        engineNotificationListenerRegistrationNumber = null;
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.SERVER_POLLED, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.SERVER_POLLED, this);
    }


    Boolean wasFiltering = null;

    private void setEmptyTextViewText() {
        boolean filtering = false;
        if (contactListViewModel != null) {
            String filter = contactListViewModel.getFilter();
            filtering = filter != null && filter.trim().length() > 0;
        }
        if (wasFiltering != null && wasFiltering == filtering) {
            return;
        }

        wasFiltering = filtering;
        if (filtering) {
            emptyViewTextView.setText(R.string.explanation_no_contact_match_filter);
        } else {
            emptyViewTextView.setText(R.string.explanation_empty_contact_list);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main_contact_list, container, false);

        recyclerView = rootView.findViewById(R.id.filtered_contact_list_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        contactListAdapter = new ContactListAdapter(activity);
        contactListViewModel.getFilteredContacts().observe(getViewLifecycleOwner(), contactListAdapter);
        recyclerView.setAdapter(contactListAdapter);
        View recyclerEmptyView = rootView.findViewById(R.id.filtered_contact_list_empty_view);
        recyclerView.setEmptyView(recyclerEmptyView);
        emptyViewTextView = rootView.findViewById(R.id.widget_list_empty_view_text_view);

        View loadingSpinner = rootView.findViewById(R.id.loading_spinner);
        recyclerView.setLoadingSpinner(loadingSpinner);

        recyclerView.addItemDecoration(new HeaderAndSeparatorDecoration());

        recyclerView.addOnScrollStateChangedListener(state -> {
            if (state == RecyclerView.SCROLL_STATE_DRAGGING) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(recyclerView.getWindowToken(), 0);
                }
            }
        });

        setEmptyTextViewText();

        swipeRefreshLayout = rootView.findViewById(R.id.discussion_list_swipe_refresh_layout);
        swipeRefreshLayout.setColorSchemeResources(R.color.primary700);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(getResources().getColor(R.color.dialogBackground));
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

    public void contactClicked(View view, ContactListViewModel.ContactOrKeycloakDetails contactOrKeycloakDetails) {
        if (contactOrKeycloakDetails != null) {
            switch (contactOrKeycloakDetails.contactType) {
                case CONTACT:
                    if (contactOrKeycloakDetails.contact != null) {
                        App.openContactDetailsActivity(view.getContext(), contactOrKeycloakDetails.contact.bytesOwnedIdentity, contactOrKeycloakDetails.contact.bytesContactIdentity);
                    }
                    break;
                case KEYCLOAK:
                    if (contactOrKeycloakDetails.keycloakUserDetails != null && contactOrKeycloakDetails.keycloakUserDetails.getIdentity() != null) {
                        OwnedIdentity ownedIdentity = AppSingleton.getCurrentIdentityLiveData().getValue();
                        if (ownedIdentity == null) {
                            break;
                        }

                        JsonIdentityDetails identityDetails = contactOrKeycloakDetails.keycloakUserDetails.getIdentityDetails(null);
                        String name = identityDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());


                        AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog);
                        builder.setTitle(R.string.dialog_title_add_keycloak_user)
                                .setMessage(getString(R.string.dialog_message_add_keycloak_user, name))
                                .setNegativeButton(R.string.button_label_cancel, null)
                                .setPositiveButton(R.string.button_label_add_contact, (DialogInterface dialog, int which) -> KeycloakManager.getInstance().addContact(ownedIdentity.bytesOwnedIdentity, contactOrKeycloakDetails.keycloakUserDetails.getId(), contactOrKeycloakDetails.keycloakUserDetails.getIdentity(), new KeycloakManager.KeycloakCallback<Void>() {
                                    @Override
                                    public void success(Void result) {
                                        App.toast(getString(R.string.toast_message_contact_added, name), Toast.LENGTH_SHORT, Gravity.BOTTOM);
                                    }

                                    @Override
                                    public void failed(int rfc) {
                                        App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
                                    }
                                }));
                        builder.create().show();
                    }
                    break;
                case KEYCLOAK_SEARCHING:
                case KEYCLOAK_MORE_RESULTS:
                    // nothing to do in this case
                    break;
            }
        }
    }

    private Contact longClickedContact;

    public void contactLongClicked(View view, ContactListViewModel.ContactOrKeycloakDetails contactOrKeycloakDetails) {
        if (contactOrKeycloakDetails != null && contactOrKeycloakDetails.contactType == ContactListViewModel.ContactType.CONTACT && contactOrKeycloakDetails.contact != null) {
            this.longClickedContact = contactOrKeycloakDetails.contact;
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.inflate(R.menu.popup_contact);
            popup.setOnMenuItemClickListener(this);

            MenuItem deleteItem = popup.getMenu().findItem(R.id.popup_action_delete_contact);
            if (deleteItem != null) {
                SpannableString spannableString = new SpannableString(deleteItem.getTitle());
                spannableString.setSpan(new ForegroundColorSpan(ContextCompat.getColor(activity, R.color.red)), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                deleteItem.setTitle(spannableString);
            }

            popup.show();
        }
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
            EditNameAndPhotoDialogFragment editNameAndPhotoDialogFragment = EditNameAndPhotoDialogFragment.newInstance(activity, longClickedContact);
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

    public void bindToSearchView(SearchView searchView) {
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    if (contactListViewModel != null) {
                        contactListViewModel.setFilter(newText);
                        setEmptyTextViewText();
                    }
                    return true;
                }
            });

            searchView.setOnCloseListener(() -> {
                if (contactListViewModel != null) {
                    contactListViewModel.setFilter(null);
                    setEmptyTextViewText();
                }
                return false;
            });

            if (contactListViewModel != null) {
                contactListViewModel.setFilter(null);
                setEmptyTextViewText();
            }
        }
    }


    class ContactListAdapter extends LoadAwareAdapter<ContactListAdapter.ContactViewHolder> implements Observer<List<ContactListViewModel.ContactOrKeycloakDetails>> {
        private List<ContactListViewModel.ContactOrKeycloakDetails> filteredContacts = null;
        private final LayoutInflater inflater;
        private final BackgroundColorSpan[] highlightedSpans;

        public static final int TYPE_CONTACT = 0;
        public static final int TYPE_KEYCLOAK = 1;
        public static final int TYPE_KEYCLOAK_SEARCHING = 2;
        public static final int TYPE_MORE_KEYCLOAK = 3;

        private static final int DISPLAY_NAME_OR_PHOTO_CHANGE_MASK = 1;
        private static final int ESTABLISHED_CHANNEL_CHANGE_MASK = 2;
        private static final int ACTIVE_CHANGE_MASK = 4;
        private static final int NEW_PUBLISHED_DETAILS_CHANGE_MASK = 8;

        ContactListAdapter(FragmentActivity activity) {
            this.inflater = LayoutInflater.from(activity);
            highlightedSpans = new BackgroundColorSpan[10];
            for (int i=0; i<highlightedSpans.length; i++) {
                highlightedSpans[i] = new BackgroundColorSpan(ContextCompat.getColor(App.getContext(), R.color.accentOverlay));
            }
        }

        @Override
        public boolean isLoadingDone() {
            return filteredContacts != null;
        }

        @Override
        public void onChanged(@Nullable final List<ContactListViewModel.ContactOrKeycloakDetails> contacts) {
            if ((contacts != null) && (filteredContacts != null)) {
                DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    @NonNull final List<ContactListViewModel.ContactOrKeycloakDetails> oldList = filteredContacts;
                    @NonNull final List<ContactListViewModel.ContactOrKeycloakDetails> newList = contacts;

                    @Override
                    public int getOldListSize() {
                        return oldList.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return newList.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldPos, int newPos) {
                        if (oldPos < 0) {
                            return newPos < 0;
                        } else {
                            if (newPos < 0) {
                                return false;
                            }
                            ContactListViewModel.ContactOrKeycloakDetails oldItem = oldList.get(oldPos);
                            ContactListViewModel.ContactOrKeycloakDetails newItem = newList.get(newPos);
                            if (oldItem.contactType != newItem.contactType) {
                                return false;
                            }
                            switch (oldItem.contactType) {
                                case CONTACT:
                                    //noinspection ConstantConditions
                                    return Arrays.equals(oldItem.contact.bytesContactIdentity, newItem.contact.bytesContactIdentity);
                                case KEYCLOAK:
                                    //noinspection ConstantConditions
                                    return Arrays.equals(oldItem.keycloakUserDetails.getIdentity(), newItem.keycloakUserDetails.getIdentity());
                                case KEYCLOAK_SEARCHING:
                                case KEYCLOAK_MORE_RESULTS:
                                    return true;
                            }
                            return false;
                        }
                    }

                    @Override
                    public boolean areContentsTheSame(int oldPos, int newPos) {
                        switch (oldList.get(oldPos).contactType) {
                            case CONTACT:
                            case KEYCLOAK:
                                // we let getChangePayload do the job
                                return false;
                            case KEYCLOAK_SEARCHING:
                            case KEYCLOAK_MORE_RESULTS:
                                return true;
                        }
                        return false;
                    }

                    @Override
                    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                        if (oldItemPosition < 0 && newItemPosition < 0) {
                            return -1;
                        }
                        ContactListViewModel.ContactOrKeycloakDetails oldItem = oldList.get(oldItemPosition);
                        ContactListViewModel.ContactOrKeycloakDetails newItem = newList.get(newItemPosition);

                        if (oldItem.contactType == ContactListViewModel.ContactType.CONTACT) {
                            if (oldItem.contact == null || newItem.contact == null) {
                                return -1;
                            }
                            
                            int changesMask = 0;
                            if (!oldItem.contact.getCustomDisplayName().equals(newItem.contact.getCustomDisplayName())
                                    || !Objects.equals(oldItem.contact.getCustomPhotoUrl(), newItem.contact.getCustomPhotoUrl())
                                    || (oldItem.contact.keycloakManaged != newItem.contact.keycloakManaged)
                                    || (oldItem.contact.oneToOne != newItem.contact.oneToOne)
                                    || !(Objects.equals(oldItem.contact.identityDetails, newItem.contact.identityDetails))) {
                                changesMask |= DISPLAY_NAME_OR_PHOTO_CHANGE_MASK;
                            }
                            if ((oldItem.contact.establishedChannelCount > 0 && newItem.contact.establishedChannelCount == 0) ||
                                    (newItem.contact.establishedChannelCount > 0 && oldItem.contact.establishedChannelCount == 0)) {
                                changesMask |= ESTABLISHED_CHANNEL_CHANGE_MASK;
                            }
                            if (oldItem.contact.active ^ newItem.contact.active) {
                                changesMask |= ACTIVE_CHANGE_MASK;
                            }
                            if (oldItem.contact.newPublishedDetails != newItem.contact.newPublishedDetails) {
                                changesMask |= NEW_PUBLISHED_DETAILS_CHANGE_MASK;
                            }
                            return changesMask;
                        } else if (oldItem.contactType == ContactListViewModel.ContactType.KEYCLOAK) {
                            if (oldItem.keycloakUserDetails == null || newItem.keycloakUserDetails == null) {
                                return -1;
                            }

                            if (!(Objects.equals(oldItem.keycloakUserDetails.getIdentityDetails(null), newItem.keycloakUserDetails.getIdentityDetails(null)))) {
                                return DISPLAY_NAME_OR_PHOTO_CHANGE_MASK;
                            }
                            return 0;
                        }
                        return -1;
                    }
                });
                this.filteredContacts = contacts;
                result.dispatchUpdatesTo(this);
            } else {
                this.filteredContacts = contacts;
                notifyDataSetChanged();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (filteredContacts == null) {
                return TYPE_CONTACT;
            }
            switch(filteredContacts.get(position).contactType) {
                case KEYCLOAK:
                    return TYPE_KEYCLOAK;
                case KEYCLOAK_SEARCHING:
                    return TYPE_KEYCLOAK_SEARCHING;
                case KEYCLOAK_MORE_RESULTS:
                    return TYPE_MORE_KEYCLOAK;
                case CONTACT:
                default:
                    return TYPE_CONTACT;
            }
        }

        @NonNull
        @Override
        public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            switch (viewType) {
                case TYPE_KEYCLOAK:
                    return new ContactViewHolder(inflater.inflate(R.layout.item_view_keycloak_user, parent, false), TYPE_KEYCLOAK);
                case TYPE_KEYCLOAK_SEARCHING:
                    return new ContactViewHolder(inflater.inflate(R.layout.item_view_keycloak_searching, parent, false), TYPE_KEYCLOAK_SEARCHING);
                case TYPE_MORE_KEYCLOAK:
                    return new ContactViewHolder(inflater.inflate(R.layout.item_view_keycloak_missing_count, parent, false), TYPE_MORE_KEYCLOAK);
                case TYPE_CONTACT:
                default:
                    return new ContactViewHolder(inflater.inflate(R.layout.item_view_contact, parent, false), TYPE_CONTACT);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
            // never called since partial binding is implemented
        }

        @Override
        public void onViewAttachedToWindow(@NonNull ContactViewHolder holder) {
            super.onViewAttachedToWindow(holder);
            if (holder.shouldAnimateChannelImageView) {
                Drawable drawable = holder.establishingChannelImageView.getDrawable();
                if (drawable instanceof Animatable) {
                    ((Animatable) drawable).start();
                }
            }
        }

        private void matchAndHighlight(String contactName, List<Pattern> patterns, TextView textView) {
            int i = 0;
            String unAccented = StringUtils.unAccent(contactName);
            Spannable highlightedContactName = new SpannableString(contactName);
            for (Pattern pattern : patterns) {
                if (i == highlightedSpans.length) {
                    break;
                }
                Matcher matcher = pattern.matcher(unAccented);
                if (matcher.find()) {
                    highlightedContactName.setSpan(highlightedSpans[i], matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i++;
                }
            }
            textView.setText(highlightedContactName);
        }

        @Override
        public void onBindViewHolder(@NonNull final ContactViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (filteredContacts != null) {
                int changesMask = 0;
                if (payloads.size() == 0) {
                    changesMask = -1;
                } else {
                    for (Object payload : payloads) {
                        if (payload instanceof Integer) {
                            changesMask |= (int) payload;
                        }
                    }
                }

                ContactListViewModel.ContactOrKeycloakDetails contactOrKeycloakDetails = filteredContacts.get(position);
                switch (contactOrKeycloakDetails.contactType) {
                    case CONTACT: {
                        Contact contact = contactOrKeycloakDetails.contact;
                        if (contact == null) {
                            return;
                        }

                        List<Pattern> patterns = contactListViewModel.getFilterPatterns();
                        String filter = contactListViewModel.getFilter();
                        if (patterns != null && patterns.size() > 0) {
                            if (!filter.equals(holder.currentFilter) || ((changesMask & DISPLAY_NAME_OR_PHOTO_CHANGE_MASK) != 0) || ((changesMask & ACTIVE_CHANGE_MASK) != 0)) {
                                holder.currentFilter = filter;
                                JsonIdentityDetails identityDetails = contact.getIdentityDetails();
                                if (identityDetails != null) {
                                    if (contact.customDisplayName != null) {
                                        matchAndHighlight(contact.customDisplayName, patterns, holder.contactNameTextView);
                                        holder.contactNameTextView.setMaxLines(1);
                                        holder.contactNameSecondLineTextView.setVisibility(View.VISIBLE);
                                        matchAndHighlight(identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName()), patterns, holder.contactNameSecondLineTextView);
                                    } else {
                                        matchAndHighlight(identityDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()), patterns, holder.contactNameTextView);
                                        String posComp = identityDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                                        if (posComp != null) {
                                            holder.contactNameTextView.setMaxLines(1);
                                            holder.contactNameSecondLineTextView.setVisibility(View.VISIBLE);
                                            matchAndHighlight(posComp, patterns, holder.contactNameSecondLineTextView);
                                        } else {
                                            holder.contactNameTextView.setMaxLines(2);
                                            holder.contactNameSecondLineTextView.setVisibility(View.GONE);
                                        }
                                    }
                                } else {
                                    holder.contactNameTextView.setMaxLines(2);
                                    holder.contactNameSecondLineTextView.setVisibility(View.GONE);
                                    matchAndHighlight(contact.getCustomDisplayName(), patterns, holder.contactNameTextView);
                                }
                            }
                        } else {
                            if ((changesMask & DISPLAY_NAME_OR_PHOTO_CHANGE_MASK) != 0 || (holder.currentFilter != null && holder.currentFilter.trim().length() > 0)) {
                                holder.currentFilter = filter;
                                JsonIdentityDetails identityDetails = contact.getIdentityDetails();
                                if (identityDetails != null) {
                                    if (contact.customDisplayName != null) {
                                        holder.contactNameTextView.setText(contact.customDisplayName);
                                        holder.contactNameTextView.setMaxLines(1);
                                        holder.contactNameSecondLineTextView.setVisibility(View.VISIBLE);
                                        holder.contactNameSecondLineTextView.setText(identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName()));
                                    } else {
                                        holder.contactNameTextView.setText(identityDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));
                                        String posComp = identityDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                                        if (posComp != null) {
                                            holder.contactNameTextView.setMaxLines(1);
                                            holder.contactNameSecondLineTextView.setVisibility(View.VISIBLE);
                                            holder.contactNameSecondLineTextView.setText(posComp);
                                        } else {
                                            holder.contactNameTextView.setMaxLines(2);
                                            holder.contactNameSecondLineTextView.setVisibility(View.GONE);
                                        }
                                    }
                                } else {
                                    holder.contactNameTextView.setMaxLines(2);
                                    holder.contactNameSecondLineTextView.setVisibility(View.GONE);
                                    holder.contactNameTextView.setText(contact.getCustomDisplayName());
                                }
                            }
                        }
                        if ((changesMask & DISPLAY_NAME_OR_PHOTO_CHANGE_MASK) != 0 || (changesMask & ACTIVE_CHANGE_MASK) != 0) {
                            holder.initialView.setContact(contact);
                        }

                        if ((changesMask & ESTABLISHED_CHANNEL_CHANGE_MASK) != 0 || (changesMask & ACTIVE_CHANGE_MASK) != 0) {
                            if (contact.establishedChannelCount == 0 && contact.active) {
                                holder.shouldAnimateChannelImageView = true;
                                holder.establishingChannelGroup.setVisibility(View.VISIBLE);
                                final AnimatedVectorDrawableCompat animated = AnimatedVectorDrawableCompat.create(App.getContext(), R.drawable.dots);
                                if (animated != null) {
                                    animated.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
                                        @Override
                                        public void onAnimationEnd(Drawable drawable) {
                                            new Handler(Looper.getMainLooper()).post(animated::start);
                                        }
                                    });
                                    animated.start();
                                }
                                holder.establishingChannelImageView.setImageDrawable(animated);
                            } else {
                                holder.shouldAnimateChannelImageView = false;
                                holder.establishingChannelImageView.setImageDrawable(null);
                                holder.establishingChannelGroup.setVisibility(View.GONE);
                            }
                        }

                        if ((changesMask & NEW_PUBLISHED_DETAILS_CHANGE_MASK) != 0) {
                            switch (contact.newPublishedDetails) {
                                case Contact.PUBLISHED_DETAILS_NOTHING_NEW:
                                    holder.newPublishedDetailsGroup.setVisibility(View.GONE);
                                    break;
                                case Contact.PUBLISHED_DETAILS_NEW_SEEN:
                                    holder.newPublishedDetailsGroup.setVisibility(View.VISIBLE);
                                    holder.newUnseenPublishedDetailsDot.setVisibility(View.GONE);
                                    break;
                                case Contact.PUBLISHED_DETAILS_NEW_UNSEEN:
                                    holder.newPublishedDetailsGroup.setVisibility(View.VISIBLE);
                                    holder.newUnseenPublishedDetailsDot.setVisibility(View.VISIBLE);
                                    break;
                            }
                        }
                        break;
                    }
                    case KEYCLOAK: {
                        JsonKeycloakUserDetails keycloakUserDetails = contactOrKeycloakDetails.keycloakUserDetails;
                        if (keycloakUserDetails == null) {
                            return;
                        }

                        List<Pattern> patterns = contactListViewModel.getFilterPatterns();
                        String filter = contactListViewModel.getFilter();
                        if (!filter.equals(holder.currentFilter) || ((changesMask & DISPLAY_NAME_OR_PHOTO_CHANGE_MASK) != 0)) {
                            holder.currentFilter = filter;

                            JsonIdentityDetails identityDetails = keycloakUserDetails.getIdentityDetails(null);
                            String name = identityDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());

                            holder.initialView.setInitial(keycloakUserDetails.getIdentity(), StringUtils.getInitial(name));
                            holder.initialView.setKeycloakCertified(true);
                            matchAndHighlight(name, patterns, holder.contactNameTextView);

                            String posComp = identityDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                            if (posComp != null) {
                                holder.contactNameTextView.setMaxLines(1);
                                holder.contactNameSecondLineTextView.setVisibility(View.VISIBLE);
                                matchAndHighlight(posComp, patterns, holder.contactNameSecondLineTextView);
                            } else {
                                holder.contactNameTextView.setMaxLines(2);
                                holder.contactNameSecondLineTextView.setVisibility(View.GONE);
                            }
                        }
                        break;
                    }
                    case KEYCLOAK_SEARCHING:
                    case KEYCLOAK_MORE_RESULTS:
                        // nothing to bind here :)
                        break;
                }
            }
        }

        @Override
        public int getItemCount() {
            if (filteredContacts != null) {
                return filteredContacts.size();
            }
            return 0;
        }

        class ContactViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
            final InitialView initialView;
            final TextView contactNameTextView;
            final TextView contactNameSecondLineTextView;
            final ImageView establishingChannelImageView;
            final ViewGroup establishingChannelGroup;
            final ViewGroup newPublishedDetailsGroup;
            final ImageView newUnseenPublishedDetailsDot;


            boolean shouldAnimateChannelImageView;
            String currentFilter;


            ContactViewHolder(View itemView, int viewType) {
                super(itemView);
                switch (viewType) {
                    case TYPE_KEYCLOAK_SEARCHING:
                    case TYPE_MORE_KEYCLOAK: {
                        initialView = null;
                        contactNameTextView = null;
                        contactNameSecondLineTextView = null;
                        establishingChannelGroup = null;
                        establishingChannelImageView = null;
                        newPublishedDetailsGroup = null;
                        newUnseenPublishedDetailsDot = null;
                        break;
                    }
                    case TYPE_KEYCLOAK: {
                        itemView.setOnClickListener(this);
                        initialView = itemView.findViewById(R.id.initial_view);
                        contactNameTextView = itemView.findViewById(R.id.keycloak_user_name);
                        contactNameSecondLineTextView = itemView.findViewById(R.id.keycloak_user_position);

                        establishingChannelGroup = null;
                        establishingChannelImageView = null;
                        newPublishedDetailsGroup = null;
                        newUnseenPublishedDetailsDot = null;
                        break;
                    }
                    case TYPE_CONTACT:
                    default: {
                        itemView.setOnClickListener(this);
                        itemView.setOnLongClickListener(this);
                        initialView = itemView.findViewById(R.id.initial_view);
                        contactNameTextView = itemView.findViewById(R.id.contact_name_text_view);
                        contactNameSecondLineTextView = itemView.findViewById(R.id.contact_name_second_line_text_view);
                        establishingChannelGroup = itemView.findViewById(R.id.establishing_channel_group);
                        establishingChannelImageView = itemView.findViewById(R.id.establishing_channel_image_view);
                        newPublishedDetailsGroup = itemView.findViewById(R.id.new_published_details_group);
                        newUnseenPublishedDetailsDot = itemView.findViewById(R.id.new_unseen_published_details_dot);
                        break;
                    }
                }

                shouldAnimateChannelImageView = false;
                currentFilter = null;
            }

            @Override
            public void onClick(View view) {
                int position = this.getLayoutPosition();
                contactClicked(view, filteredContacts.get(position));
            }

            @Override
            public boolean onLongClick(View view) {
                int position = this.getLayoutPosition();
                contactLongClicked(view, filteredContacts.get(position));
                return true;
            }
        }
    }

    private class HeaderAndSeparatorDecoration extends RecyclerView.ItemDecoration {
        private final int headerHeight;
        private final int separatorHeight;
        private final int leftMargin;
        private final int rightMargin;
        private final int backgroundColor;
        private final int foregroundColor;

        private final Rect itemRect;
        private Bitmap notOneToOneHeaderBitmap;
        private Bitmap keycloakHeaderBitmap;

        HeaderAndSeparatorDecoration() {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            headerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, metrics);
            leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 68, metrics);
            rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, metrics);
            separatorHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, metrics);
            itemRect = new Rect();
            backgroundColor = ContextCompat.getColor(activity, R.color.almostWhite);
            foregroundColor = ContextCompat.getColor(activity, R.color.lightGrey);
            notOneToOneHeaderBitmap = null;
        }

        @Override
        public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                int position = parent.getChildAdapterPosition(child);
                if (position == RecyclerView.NO_POSITION) {
                    continue;
                }
                ContactListViewModel.ContactOrKeycloakDetails contactOrKeycloakDetails = contactListAdapter.filteredContacts.get(position);
                ContactListViewModel.ContactOrKeycloakDetails previousContactOrKeycloakDetails = position == 0 ? null : contactListAdapter.filteredContacts.get(position - 1);

                switch (contactOrKeycloakDetails.contactType) {
                    case CONTACT: {
                        if (contactOrKeycloakDetails.contact == null) {
                            continue;
                        }
                        if ((previousContactOrKeycloakDetails == null && !contactOrKeycloakDetails.contact.oneToOne)
                                || (previousContactOrKeycloakDetails != null && previousContactOrKeycloakDetails.contact != null && previousContactOrKeycloakDetails.contact.oneToOne && !contactOrKeycloakDetails.contact.oneToOne)) {
                            if (notOneToOneHeaderBitmap == null) {
                                View headerView = LayoutInflater.from(activity).inflate(R.layout.view_contact_list_additional_results_header, parent, false);
                                TextView textView = headerView.findViewById(R.id.header_text);
                                ImageView imageView = headerView.findViewById(R.id.header_image);
                                textView.setText(R.string.label_users_below_not_in_contacts);
                                imageView.setImageResource(R.drawable.ic_not_one_to_one);

                                headerView.measure(View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(headerHeight, View.MeasureSpec.EXACTLY));
                                headerView.layout(0, 0, headerView.getMeasuredWidth(), headerHeight);
                                Bitmap headerBitmap = Bitmap.createBitmap(headerView.getMeasuredWidth(), headerHeight, Bitmap.Config.ARGB_8888);
                                Canvas bitmapCanvas = new Canvas(headerBitmap);
                                headerView.draw(bitmapCanvas);
                                notOneToOneHeaderBitmap = headerBitmap;
                            }
                            canvas.save();
                            parent.getDecoratedBoundsWithMargins(child, itemRect);
//                            itemRect.top += child.getTranslationY();
//                            itemRect.bottom += child.getTranslationY();
                            Paint paint = new Paint();
                            paint.setAlpha((int) (child.getAlpha() * 255));
                            canvas.drawBitmap(notOneToOneHeaderBitmap, itemRect.left, itemRect.top, paint);
                            canvas.restore();
                            continue;
                        }
                        break;
                    }
                    case KEYCLOAK: {
                        if (previousContactOrKeycloakDetails == null || previousContactOrKeycloakDetails.contactType != ContactListViewModel.ContactType.KEYCLOAK) {
                            if (keycloakHeaderBitmap == null) {
                                View headerView = LayoutInflater.from(activity).inflate(R.layout.view_contact_list_additional_results_header, parent, false);
                                TextView textView = headerView.findViewById(R.id.header_text);
                                ImageView imageView = headerView.findViewById(R.id.header_image);
                                textView.setText(R.string.label_users_below_from_keycloak);
                                imageView.setImageResource(R.drawable.ic_keycloak_directory);

                                headerView.measure(View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(headerHeight, View.MeasureSpec.EXACTLY));
                                headerView.layout(0, 0, headerView.getMeasuredWidth(), headerHeight);
                                Bitmap headerBitmap = Bitmap.createBitmap(headerView.getMeasuredWidth(), headerHeight, Bitmap.Config.ARGB_8888);
                                Canvas bitmapCanvas = new Canvas(headerBitmap);
                                headerView.draw(bitmapCanvas);
                                keycloakHeaderBitmap = headerBitmap;
                            }
                            canvas.save();
                            parent.getDecoratedBoundsWithMargins(child, itemRect);
//                            itemRect.top += child.getTranslationY();
//                            itemRect.bottom += child.getTranslationY();
                            Paint paint = new Paint();
                            paint.setAlpha((int) (child.getAlpha() * 255));
                            canvas.drawBitmap(keycloakHeaderBitmap, itemRect.left, itemRect.top, paint);
                            canvas.restore();
                            continue;
                        }
                        break;
                    }
                    case KEYCLOAK_SEARCHING:
                        continue;
                    case KEYCLOAK_MORE_RESULTS:
                        break;
                }

                if (position != 0) {
                    parent.getDecoratedBoundsWithMargins(child, itemRect);
                    itemRect.top += child.getTranslationY();
                    itemRect.bottom += child.getTranslationY();
                    int alpha = (int) (child.getAlpha() * 255);
                    int alphaBack = (backgroundColor & 0x00ffffff) | (alpha << 24);
                    int alphaFore = (foregroundColor & 0x00ffffff) | (alpha << 24);

                    canvas.save();
                    canvas.clipRect(itemRect.left, itemRect.top, itemRect.right, itemRect.top + separatorHeight);
                    canvas.drawColor(alphaBack);
                    canvas.clipRect(itemRect.left + leftMargin, itemRect.top, itemRect.right - rightMargin, itemRect.top + separatorHeight);
                    canvas.drawColor(alphaFore);
                    canvas.restore();
                }
            }
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            ContactListViewModel.ContactOrKeycloakDetails contactOrKeycloakDetails = contactListAdapter.filteredContacts.get(position);
            ContactListViewModel.ContactOrKeycloakDetails previousContactOrKeycloakDetails = position == 0 ? null : contactListAdapter.filteredContacts.get(position - 1);

            switch (contactOrKeycloakDetails.contactType) {
                case CONTACT: {
                    if (contactOrKeycloakDetails.contact == null) {
                        return;
                    }
                    if ((previousContactOrKeycloakDetails == null && !contactOrKeycloakDetails.contact.oneToOne)
                            || (previousContactOrKeycloakDetails != null && previousContactOrKeycloakDetails.contact != null && previousContactOrKeycloakDetails.contact.oneToOne && !contactOrKeycloakDetails.contact.oneToOne)) {
                        outRect.top += headerHeight;
                        return;
                    }
                    break;
                }
                case KEYCLOAK: {
                    if (previousContactOrKeycloakDetails == null || previousContactOrKeycloakDetails.contactType != ContactListViewModel.ContactType.KEYCLOAK) {
                        outRect.top += headerHeight;
                        return;
                    }
                    break;
                }
                case KEYCLOAK_SEARCHING:
                    return;
                case KEYCLOAK_MORE_RESULTS:
                    break;
            }

            if (position != 0) {
                outRect.top += separatorHeight;
            }
        }
    }
}

