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

package io.olvid.messenger.plus_button;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.RecyclerViewDividerDecoration;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.fragments.FilteredContactListFragment;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;

public class KeycloakSearchFragment extends Fragment implements View.OnClickListener {
    private AppCompatActivity activity;
    private PlusButtonViewModel viewModel;

    private SearchResultAdapter searchResultAdapter;
    private EditText keycloakSearchEditText;
    private TextView explanationTextView;
    private View spinner;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plus_button_keycloak_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                if (activity.getWindow().getStatusBarColor() == 0xff000000) {
                    ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.almostWhite)).start();
                } else {
                    activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
                }
            } else {
                ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.olvid_gradient_dark)).start();
            }
        }

        viewModel = new ViewModelProvider(activity).get(PlusButtonViewModel.class);

        keycloakSearchEditText = view.findViewById(R.id.keycloak_search_edit_text);
        EmptyRecyclerView searchResultRecyclerView = view.findViewById(R.id.keycloak_search_recycler_view);
        CardView explanationCard = view.findViewById(R.id.keycloak_search_explanation);
        explanationTextView = view.findViewById(R.id.keycloak_search_explanation_text);
        spinner = view.findViewById(R.id.keycloak_search_spinner);

        view.findViewById(R.id.search_button).setOnClickListener(this);
        view.findViewById(R.id.back_button).setOnClickListener(this);

        OwnedIdentity ownedIdentity = viewModel.getCurrentIdentity();
        if (ownedIdentity == null) {
            searchResultAdapter = new SearchResultAdapter(null, this::onUserClick);
        } else {
            searchResultAdapter = new SearchResultAdapter(ownedIdentity.bytesOwnedIdentity, this::onUserClick);
        }
        viewModel.getKeycloakSearchResult().observe(getViewLifecycleOwner(), searchResultAdapter);
        viewModel.getKeycloakSearchMissingResults().observe(getViewLifecycleOwner(), searchResultAdapter::onMissingResultsChanged);

        searchResultRecyclerView.setAdapter(searchResultAdapter);
        searchResultRecyclerView.setEmptyView(explanationCard);
        searchResultRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        searchResultRecyclerView.addItemDecoration(new RecyclerViewDividerDecoration(activity, 60, 12));

        keycloakSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            startSearch();
            return true;
        });
        keycloakSearchEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setKeycloakSearchString(s.toString());
            }
        });
        keycloakSearchEditText.setText(viewModel.getKeycloakSearchString());

        viewModel.getKeycloakSearchStatus().observe(getViewLifecycleOwner(), status -> {
            switch (status) {
                case NONE: {
                    explanationTextView.setGravity(Gravity.START);
                    explanationTextView.setText(R.string.explanation_keycloak_search_not_started);
                    spinner.setVisibility(View.GONE);
                    keycloakSearchEditText.requestFocus();
                    break;
                }
                case SEARCHING: {
                    explanationTextView.setGravity(Gravity.CENTER_HORIZONTAL);
                    explanationTextView.setText(R.string.explanation_keycloak_searching);
                    spinner.setVisibility(View.VISIBLE);
                    break;
                }
                case DONE: {
                    explanationTextView.setGravity(Gravity.CENTER_HORIZONTAL);
                    explanationTextView.setText(R.string.explanation_empty_keycloak_search);
                    spinner.setVisibility(View.GONE);
                    break;
                }
            }
        });
        startSearch();
    }

    private void startSearch() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(keycloakSearchEditText.getWindowToken(), 0);
        }

        OwnedIdentity ownedIdentity = viewModel.getCurrentIdentity();
        if (ownedIdentity != null) {
            viewModel.setKeycloakSearchStatus(PlusButtonViewModel.SEARCH_STATUS.SEARCHING);
            viewModel.setKeycloakSearchResult(null);


            searchResultAdapter.setFilter(viewModel.getKeycloakSearchString());

            KeycloakManager.getInstance().search(ownedIdentity.bytesOwnedIdentity, viewModel.getKeycloakSearchString(), new KeycloakManager.KeycloakCallback<Pair<List<JsonKeycloakUserDetails>, Integer>>() {
                @Override
                public void success(Pair<List<JsonKeycloakUserDetails>, Integer> searchResult) {
                    viewModel.setKeycloakSearchStatus(PlusButtonViewModel.SEARCH_STATUS.DONE);
                    viewModel.setKeycloakSearchResult(searchResult.first);
                    if (searchResult.second == null) {
                        viewModel.setKeycloakSearchMissingResults(0);
                    } else {
                        if (searchResult.first == null) {
                            viewModel.setKeycloakSearchMissingResults(searchResult.second);
                        } else {
                            viewModel.setKeycloakSearchMissingResults(searchResult.second - searchResult.first.size());
                        }
                    }
                }

                @Override
                public void failed(int rfc) {
                    viewModel.setKeycloakSearchStatus(PlusButtonViewModel.SEARCH_STATUS.DONE);
                    App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
                }
            });
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.back_button) {
            activity.onBackPressed();
        } else if (id == R.id.search_button) {
            startSearch();
        }
    }

    public void onUserClick(final JsonKeycloakUserDetails userDetails) {
        OwnedIdentity ownedIdentity = viewModel.getCurrentIdentity();
        if (ownedIdentity == null) {
            return;
        }

        String name = "";
        if (userDetails.getFirstName() != null) {
            name += userDetails.getFirstName();
        }
        if (userDetails.getLastName() != null) {
            if (name.length() > 0) {
                name += " ";
            }
            name += userDetails.getLastName();
        }

        String finalName = name;

        AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog);
        builder.setTitle(R.string.dialog_title_add_keycloak_user)
                .setMessage(getString(R.string.dialog_message_add_keycloak_user, finalName))
                .setNegativeButton(R.string.button_label_cancel, null)
                .setPositiveButton(R.string.button_label_add_contact, (DialogInterface dialog, int which) -> KeycloakManager.getInstance().addContact(ownedIdentity.bytesOwnedIdentity, userDetails.getId(), userDetails.getIdentity(), new KeycloakManager.KeycloakCallback<Void>() {
                    @Override
                    public void success(Void result) {
                        App.toast(getString(R.string.toast_message_contact_added, finalName), Toast.LENGTH_SHORT, Gravity.BOTTOM);
                    }

                    @Override
                    public void failed(int rfc) {
                        App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT);
                    }
                }));
        builder.create().show();
    }


    static class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder> implements Observer<List<JsonKeycloakUserDetails>> {
        private List<JsonKeycloakUserDetails> searchResults;
        private int missingResults;
        @Nullable private final byte[] bytesOwnedIdentity;
        @NonNull private final ClickListener clickListener;
        private final BackgroundColorSpan[] highlightedSpans;
        private final List<Pattern> patterns;

        public static final int VIEW_TYPE_NORMAL = 0;
        public static final int VIEW_TYPE_MISSING_RESULTS = 1;

        public SearchResultAdapter(@Nullable byte[] bytesOwnedIdentity, @NonNull ClickListener clickListener) {
            this.bytesOwnedIdentity = bytesOwnedIdentity;
            this.clickListener = clickListener;
            this.patterns = new ArrayList<>();
            highlightedSpans = new BackgroundColorSpan[10];
            for (int i=0; i<highlightedSpans.length; i++) {
                highlightedSpans[i] = new BackgroundColorSpan(ContextCompat.getColor(App.getContext(), R.color.accentOverlay));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if ((searchResults == null && position == 0) || (searchResults != null && searchResults.size() == position)) {
                return VIEW_TYPE_MISSING_RESULTS;
            } else {
                return VIEW_TYPE_NORMAL;
            }
        }

        @NonNull
        @Override
        public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_MISSING_RESULTS) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_view_keycloak_missing_count, parent, false);
                return new SearchResultViewHolder(view, null);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_view_keycloak_user, parent, false);
                return new SearchResultViewHolder(view, clickListener);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
            if (searchResults != null && position < searchResults.size()) {
                JsonKeycloakUserDetails userDetails = searchResults.get(position);

                holder.setUserDetails(userDetails);

                String name = "";
                if (userDetails.getFirstName() != null) {
                    name += userDetails.getFirstName();
                }
                if (userDetails.getLastName() != null) {
                    if (name.length() > 0) {
                        name += " ";
                    }
                    name += userDetails.getLastName();
                }
                matchAndHighlight(name, holder.keycloakUserNameTextView);
                holder.initialView.setInitial(userDetails.getIdentity() == null ? new byte[0] : userDetails.getIdentity(), App.getInitial(name));

                String company = "";
                if (userDetails.getPosition() != null) {
                    company += userDetails.getPosition();
                }
                if (userDetails.getCompany() != null) {
                    if (company.length() > 0) {
                        company += " @ ";
                    }
                    company += userDetails.getCompany();
                }
                if (company.length() > 0) {
                    holder.keycloakUserPositionTextView.setVisibility(View.VISIBLE);
                    matchAndHighlight(company, holder.keycloakUserPositionTextView);
                } else {
                    holder.keycloakUserPositionTextView.setVisibility(View.GONE);
                }

                if (userDetails.getIdentity() != null) {
                    if (bytesOwnedIdentity != null) {
                        App.runThread(() -> {
                            Contact contact = AppDatabase.getInstance().contactDao().get(bytesOwnedIdentity, userDetails.getIdentity());
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (contact != null) {
                                    holder.keycloakUserKnownImageView.setVisibility(View.VISIBLE);
                                    holder.initialView.setKeycloakCertified(contact.keycloakManaged);
                                    holder.initialView.setInactive(!contact.active);
                                    if (contact.getCustomPhotoUrl() != null) {
                                        holder.initialView.setPhotoUrl(contact.bytesContactIdentity, contact.getCustomPhotoUrl());
                                    }
                                } else {
                                    holder.keycloakUserKnownImageView.setVisibility(View.GONE);
                                }
                            });
                        });
                    }
                }
            } else if ((searchResults == null && position == 0) || (searchResults != null && searchResults.size() == position)) {
                holder.missingCountTextView.setText(App.getContext().getResources().getQuantityString(R.plurals.text_keycloak_missing_search_result, missingResults, missingResults));
            }
        }

        private void matchAndHighlight(String text, TextView textView) {
            int i = 0;
            String unAccented = App.unAccent(text);
            Spannable highlightedString = new SpannableString(text);
            for (Pattern pattern : patterns) {
                if (i == highlightedSpans.length) {
                    break;
                }
                Matcher matcher = pattern.matcher(unAccented);
                if (matcher.find()) {
                    highlightedString.setSpan(highlightedSpans[i], matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i++;
                }
            }
            textView.setText(highlightedString);
        }


        @Override
        public int getItemCount() {
            if (searchResults != null) {
                return searchResults.size() + ((missingResults == 0) ? 0 : 1);
            }
            return ((missingResults == 0) ? 0 : 1);
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onChanged(List<JsonKeycloakUserDetails> userDetails) {
            if (userDetails != null) {
                List<JsonKeycloakUserDetails> filteredResults = new ArrayList<>();
                for (JsonKeycloakUserDetails userDetail : userDetails) {
                    if (userDetail.getIdentity() != null && !Arrays.equals(bytesOwnedIdentity, userDetail.getIdentity())) {
                        filteredResults.add(userDetail);
                    }
                }
                this.searchResults = filteredResults;
            } else {
                this.searchResults = null;
            }
            notifyDataSetChanged();
        }

        @SuppressLint("NotifyDataSetChanged")
        public void onMissingResultsChanged(Integer missingResults) {
            if (missingResults == null) {
                this.missingResults = 0;
            } else {
                this.missingResults = missingResults;
            }
            notifyDataSetChanged();
        }

        public void setFilter(String keycloakSearchString) {
            patterns.clear();
            if (keycloakSearchString == null) {
                return;
            }
            for (String part: keycloakSearchString.trim().split("\\s+")) {
                patterns.add(Pattern.compile(Pattern.quote(App.unAccent(part))));
            }
        }

        static class SearchResultViewHolder extends RecyclerView.ViewHolder {
            private final TextView keycloakUserNameTextView;
            private final TextView keycloakUserPositionTextView;
            private final InitialView initialView;
            private final ImageView keycloakUserKnownImageView;
            private final TextView missingCountTextView;

            private JsonKeycloakUserDetails userDetails;

            public SearchResultViewHolder(@NonNull View itemView, @Nullable ClickListener clickListener) {
                super(itemView);
                keycloakUserNameTextView = itemView.findViewById(R.id.keycloak_user_name);
                keycloakUserPositionTextView = itemView.findViewById(R.id.keycloak_user_position);
                initialView = itemView.findViewById(R.id.initial_view);
                keycloakUserKnownImageView = itemView.findViewById(R.id.keycloak_user_known_image_view);
                missingCountTextView = itemView.findViewById(R.id.keycloak_missing_count);
                if (clickListener != null) {
                    itemView.setOnClickListener(v -> clickListener.onClick(userDetails));
                }
            }

            public void setUserDetails(JsonKeycloakUserDetails userDetails) {
                this.userDetails = userDetails;
            }
        }
    }

    private interface ClickListener {
        void onClick(JsonKeycloakUserDetails userDetails);
    }
}
