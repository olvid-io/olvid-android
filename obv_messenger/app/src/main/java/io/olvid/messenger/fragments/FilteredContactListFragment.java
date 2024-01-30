/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.messenger.fragments;

import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LoadAwareAdapter;
import io.olvid.messenger.customClasses.ItemDecorationSimpleDivider;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.viewModels.FilteredContactListViewModel;

public class FilteredContactListFragment extends Fragment implements TextWatcher {
    private EditText contactFilterEditText;
    protected FilteredContactListViewModel filteredContactListViewModel = null;
    private FilteredContactListOnClickDelegate onClickDelegate;
    private LiveData<List<Contact>> unfilteredContacts = null;
    protected EmptyRecyclerView recyclerView;
    protected TextView emptyViewTextView;

    private Observer<List<Contact>> selectedContactsObserver;
    private Observer<List<FilteredContactListViewModel.SelectableContact>> filteredContactsObserver;
    private List<Contact> initiallySelectedContacts;


    private boolean removeBottomPadding = false;
    private boolean selectable = false;
    private boolean disableEmptyView = false;
    private boolean disableAnimations = false;
    private String emptyViewText = null;

    protected FilteredContactListAdapter filteredContactListAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        filteredContactListViewModel = new ViewModelProvider(this).get(FilteredContactListViewModel.class);

        if (unfilteredContacts != null) {
            observeUnfiltered();
        }
        if (this.filteredContactsObserver != null) {
            filteredContactListViewModel.getFilteredContacts().observe(this, this.filteredContactsObserver);
        }
        if (this.selectedContactsObserver != null) {
            filteredContactListViewModel.getSelectedContacts().observe(this, this.selectedContactsObserver);
        }
        if (this.initiallySelectedContacts != null) {
            filteredContactListViewModel.setSelectedContacts(initiallySelectedContacts);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_filtered_contact_list, container, false);

        recyclerView = rootView.findViewById(R.id.filtered_contact_list_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        if (disableAnimations) {
            recyclerView.setItemAnimator(null);
        }

        filteredContactListAdapter = new FilteredContactListAdapter();
        filteredContactListViewModel.getFilteredContacts().observe(getViewLifecycleOwner(), filteredContactListAdapter);
        recyclerView.setAdapter(filteredContactListAdapter);
        if (!disableEmptyView) {
            View recyclerEmptyView = rootView.findViewById(R.id.filtered_contact_list_empty_view);
            recyclerView.setEmptyView(recyclerEmptyView);
        }
        emptyViewTextView = rootView.findViewById(R.id.widget_list_empty_view_text_view);
        if (emptyViewText != null) {
            emptyViewTextView.setText(emptyViewText);
        }

        View loadingSpinner = rootView.findViewById(R.id.loading_spinner);
        recyclerView.setLoadingSpinner(loadingSpinner);

        if (removeBottomPadding) {
            recyclerView.setPadding(0,0,0,0);
        }

        recyclerView.addItemDecoration(new ItemDecorationSimpleDivider(rootView.getContext(), selectable ? 100 : 68, 12));

        return rootView;
    }


    public void setUnfilteredContacts(LiveData<List<Contact>> unfilteredContacts) {
        if (this.unfilteredContacts != null) {
            this.unfilteredContacts.removeObservers(this);
        }
        this.unfilteredContacts = unfilteredContacts;
        if (filteredContactListViewModel != null) {
            observeUnfiltered();
        }
    }

    private void observeUnfiltered() {
        this.unfilteredContacts.observe(this, (List<Contact> contacts) -> filteredContactListViewModel.setUnfilteredContacts(contacts));
    }

    public void setContactFilterEditText(EditText contactFilterEditText) {
        if (this.contactFilterEditText != null) {
            this.contactFilterEditText.removeTextChangedListener(this);
        }
        if (SettingsActivity.useKeyboardIncognitoMode()) {
            contactFilterEditText.setImeOptions(contactFilterEditText.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }
        this.contactFilterEditText = contactFilterEditText;
        this.contactFilterEditText.addTextChangedListener(this);
    }

    public void setFilter(String filter) {
        if (filteredContactListViewModel != null) {
            filteredContactListViewModel.setFilter(filter);
        }
    }

    public void setFilteredContactObserver(@Nullable Observer<List<FilteredContactListViewModel.SelectableContact>> observer) {
        if (filteredContactListViewModel != null) {
            if (this.filteredContactsObserver != null) {
                filteredContactListViewModel.getFilteredContacts().removeObserver(this.filteredContactsObserver);
            }
            if (observer != null) {
                filteredContactListViewModel.getFilteredContacts().observe(this, observer);
            }
        }
        this.filteredContactsObserver = observer;
    }

    public void setOnClickDelegate(FilteredContactListOnClickDelegate onClickDelegate) {
        this.onClickDelegate = onClickDelegate;
    }

    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
        if (this.filteredContactListAdapter != null && this.filteredContactListAdapter.filteredContacts != null) {
            this.filteredContactListAdapter.notifyItemRangeChanged(0, this.filteredContactListAdapter.filteredContacts.size(), 0);
        }
    }

    public void setInitiallySelectedContacts(List<Contact> initiallySelectedContacts) {
        this.initiallySelectedContacts = initiallySelectedContacts;
        if (filteredContactListViewModel != null) {
            this.filteredContactListViewModel.setSelectedContacts(initiallySelectedContacts);
        }
    }

    public void setSelectedContactsObserver(Observer<List<Contact>> observer) {
        if (filteredContactListViewModel != null) {
            if (this.selectedContactsObserver != null) {
                filteredContactListViewModel.getSelectedContacts().removeObserver(this.selectedContactsObserver);
            }
            filteredContactListViewModel.getSelectedContacts().observe(this, observer);
        }
        this.selectedContactsObserver = observer;
    }

    public void setEmptyViewText(@Nullable String text) {
        if (emptyViewTextView != null) {
            emptyViewTextView.setText(text);
        } else {
            emptyViewText = text;
        }
    }

    public void removeBottomPadding() {
        removeBottomPadding = true;
    }

    public void disableEmptyView() {
        this.disableEmptyView = true;
    }

    public void disableAnimations() {
        this.disableAnimations = true;
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

    @Override
    public void afterTextChanged(Editable editable) {
        if (filteredContactListViewModel != null) {
            filteredContactListViewModel.setFilter(editable.toString());
        }
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }


    class FilteredContactListAdapter extends LoadAwareAdapter<FilteredContactListAdapter.ContactViewHolder> implements Observer<List<FilteredContactListViewModel.SelectableContact>> {
        private List<FilteredContactListViewModel.SelectableContact> filteredContacts = null;
        private final LayoutInflater inflater;
        private final BackgroundColorSpan[] highlightedSpans;

        private static final int DISPLAY_NAME_OR_PHOTO_CHANGE_MASK = 1;
        private static final int ESTABLISHED_CHANNEL_CHANGE_MASK = 2;
        private static final int SELECTED_CHANGE_MASK = 4;
        private static final int ACTIVE_CHANGE_MASK = 8;
        private static final int NEW_PUBLISHED_DETAILS_CHANGE_MASK = 16;

        FilteredContactListAdapter() {
            this.inflater = LayoutInflater.from(FilteredContactListFragment.this.getContext());
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
        public void onChanged(@Nullable final List<FilteredContactListViewModel.SelectableContact> contacts) {
            if ((contacts != null) && (filteredContacts != null)) {
                DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    @NonNull final List<FilteredContactListViewModel.SelectableContact> oldList = filteredContacts;
                    @NonNull final List<FilteredContactListViewModel.SelectableContact> newList = contacts;

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
                            return Arrays.equals(oldList.get(oldPos).contact.bytesContactIdentity, newList.get(newPos).contact.bytesContactIdentity);
                        }
                    }

                    @Override
                    public boolean areContentsTheSame(int oldPos, int newPos) {
                        return false;
                    }

                    @Override
                    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                        if (oldItemPosition < 0 && newItemPosition < 0) {
                            return -1;
                        }
                        FilteredContactListViewModel.SelectableContact oldItem = oldList.get(oldItemPosition);
                        FilteredContactListViewModel.SelectableContact newItem = newList.get(newItemPosition);

                        int changesMask = 0;
                        if (!oldItem.contact.getCustomDisplayName().equals(newItem.contact.getCustomDisplayName())
                                || !Objects.equals(oldItem.contact.getCustomPhotoUrl(), newItem.contact.getCustomPhotoUrl())
                                || (oldItem.contact.keycloakManaged != newItem.contact.keycloakManaged)
                                || !(Objects.equals(oldItem.contact.identityDetails, newItem.contact.identityDetails))) {
                            changesMask |= DISPLAY_NAME_OR_PHOTO_CHANGE_MASK;
                        }
                        if (oldItem.contact.shouldShowChannelCreationSpinner() != newItem.contact.shouldShowChannelCreationSpinner()) {
                            changesMask |= ESTABLISHED_CHANNEL_CHANGE_MASK;
                        }
                        if (oldItem.contact.active ^ newItem.contact.active) {
                            changesMask |= ACTIVE_CHANGE_MASK;
                        }
                        if (oldItem.selected ^ newItem.selected) {
                            changesMask |= SELECTED_CHANGE_MASK;
                        }
                        if (oldItem.contact.newPublishedDetails != newItem.contact.newPublishedDetails) {
                            changesMask |= NEW_PUBLISHED_DETAILS_CHANGE_MASK;
                        }
                        return changesMask;
                    }
                });
                this.filteredContacts = contacts;
                result.dispatchUpdatesTo(this);
            } else {
                this.filteredContacts = contacts;
                notifyDataSetChanged();
            }
        }

        @NonNull
        @Override
        public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ContactViewHolder(inflater.inflate(R.layout.item_view_contact_selectable, parent, false));
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
                    highlightedContactName.setSpan(highlightedSpans[i], StringUtils.unaccentedOffsetToActualOffset(contactName, matcher.start()), StringUtils.unaccentedOffsetToActualOffset(contactName, matcher.end()), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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

                FilteredContactListViewModel.SelectableContact selectableContact = filteredContacts.get(position);

                List<Pattern> patterns = FilteredContactListFragment.this.filteredContactListViewModel.getFilterPatterns();
                String filter = FilteredContactListFragment.this.filteredContactListViewModel.getFilter();
                if (patterns != null && patterns.size() > 0) {
                    if (!filter.equals(holder.currentFilter) || ((changesMask & DISPLAY_NAME_OR_PHOTO_CHANGE_MASK) != 0) || ((changesMask & ACTIVE_CHANGE_MASK) != 0)) {
                        holder.currentFilter = filter;
                        JsonIdentityDetails identityDetails = selectableContact.contact.getIdentityDetails();
                        if (identityDetails != null) {
                            if (selectableContact.contact.customDisplayName != null) {
                                matchAndHighlight(selectableContact.contact.customDisplayName, patterns, holder.contactNameTextView);
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
                            matchAndHighlight(selectableContact.contact.getCustomDisplayName(), patterns, holder.contactNameTextView);
                        }
                    }
                } else {
                    if ((changesMask & DISPLAY_NAME_OR_PHOTO_CHANGE_MASK) != 0 || (holder.currentFilter != null && holder.currentFilter.trim().length() > 0)) {
                        holder.currentFilter = filter;
                        JsonIdentityDetails identityDetails = selectableContact.contact.getIdentityDetails();
                        if (identityDetails != null) {
                            if (selectableContact.contact.customDisplayName != null) {
                                holder.contactNameTextView.setText(selectableContact.contact.customDisplayName);
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
                            holder.contactNameTextView.setText(selectableContact.contact.getCustomDisplayName());
                        }
                    }
                }
                if ((changesMask & DISPLAY_NAME_OR_PHOTO_CHANGE_MASK) != 0 || (changesMask & ACTIVE_CHANGE_MASK) != 0) {
                    holder.initialView.setContact(selectableContact.contact);
                }

                if ((changesMask & ESTABLISHED_CHANNEL_CHANGE_MASK) != 0 || (changesMask & ACTIVE_CHANGE_MASK) != 0) {
                    if (selectableContact.contact.shouldShowChannelCreationSpinner() && selectableContact.contact.active) {
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
                    if (selectable) {
                        holder.newPublishedDetailsGroup.setVisibility(View.GONE);
                    } else {
                        switch (selectableContact.contact.newPublishedDetails) {
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
                }

                if (selectable) {
                    holder.contactSelectionCheckbox.setVisibility(View.VISIBLE);
                    if ((changesMask & SELECTED_CHANGE_MASK) != 0) {
                        holder.contactSelectionCheckbox.setChecked(selectableContact.selected);
                    }
                } else {
                    holder.contactSelectionCheckbox.setVisibility(View.GONE);
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
            final CheckBox contactSelectionCheckbox;
            String currentFilter;


            ContactViewHolder(View itemView) {
                super(itemView);
                itemView.setOnClickListener(this);
                itemView.setOnLongClickListener(this);
                contactNameTextView = itemView.findViewById(R.id.contact_name_text_view);
                contactNameSecondLineTextView = itemView.findViewById(R.id.contact_name_second_line_text_view);
                initialView = itemView.findViewById(R.id.initial_view);
                establishingChannelGroup = itemView.findViewById(R.id.establishing_channel_group);
                establishingChannelImageView = itemView.findViewById(R.id.establishing_channel_image_view);
                newPublishedDetailsGroup = itemView.findViewById(R.id.new_published_details_group);
                newUnseenPublishedDetailsDot = itemView.findViewById(R.id.new_unseen_published_details_dot);

                shouldAnimateChannelImageView = false;
                contactSelectionCheckbox = itemView.findViewById(R.id.contact_selection_checkbox);
                currentFilter = null;
            }

            @Override
            public void onClick(View view) {
                int position = this.getLayoutPosition();
                if (selectable) {
                    filteredContactListViewModel.selectContact(filteredContacts.get(position).contact);
                } else {
                    if (FilteredContactListFragment.this.onClickDelegate != null) {
                        FilteredContactListFragment.this.onClickDelegate.contactClicked(view, filteredContacts.get(position).contact);
                    }
                }
            }

            @Override
            public boolean onLongClick(View view) {
                if (selectable) {
                    return false;
                }
                int position = this.getLayoutPosition();
                if (FilteredContactListFragment.this.onClickDelegate != null) {
                    FilteredContactListFragment.this.onClickDelegate.contactLongClicked(view, filteredContacts.get(position).contact);
                    return true;
                }
                return false;
            }
        }
    }

    public interface FilteredContactListOnClickDelegate {
        void contactClicked(View view, Contact contact);
        void contactLongClicked(View view, Contact contact);
    }
}
