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

package io.olvid.messenger.discussion;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.tasks.SetDraftJsonExpirationTask;
import io.olvid.messenger.settings.SettingsActivity;

public class SingleMessageEphemeralSettingsDialogFragment extends DialogFragment implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String DISCUSSION_ID_KEY = "discussion_id";

    private long discussionId;
    private FragmentActivity activity;
    private EphemeralViewModel viewModel;

    private TextView discussionDefaultReadOnce;
    private TextView discussionDefaultVisibility;
    private TextView discussionDefaultExistence;
    private TextView discussionDefaultNotEphemeral;

    private SwitchCompat readOnceSwitch;
    private EditText visibilityEditText;
    private TextView visibilityDropdown;
    private EditText existenceEditText;
    private TextView existenceDropdown;

    private Button okButton;

    private UNIT visibilityUnit;
    private UNIT existenceUnit;
    private VISEX popupMenuVisex;

    private enum UNIT {
        SECONDS,
        MINUTES,
        HOURS,
        DAYS,
        YEARS,
    }

    private enum VISEX {
        VISIBILITY,
        EXISTENCE,
    }

    private SingleMessageEphemeralSettingsDialogFragment() { }

    public static SingleMessageEphemeralSettingsDialogFragment newInstance(long discussionId) {
        SingleMessageEphemeralSettingsDialogFragment fragment = new SingleMessageEphemeralSettingsDialogFragment();
        Bundle args = new Bundle();
        args.putLong(DISCUSSION_ID_KEY, discussionId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activity = requireActivity();
        viewModel = new ViewModelProvider(requireActivity()).get(EphemeralViewModel.class);

        Bundle arguments = getArguments();
        if (arguments != null) {
            discussionId = arguments.getLong(DISCUSSION_ID_KEY);
        } else {
            dismiss();
            return;
        }

        viewModel.setDiscussionId(discussionId);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_single_message_ephemeral_settings, container, false);

        discussionDefaultReadOnce = dialogView.findViewById(R.id.read_once);
        discussionDefaultVisibility = dialogView.findViewById(R.id.visibility);
        discussionDefaultExistence = dialogView.findViewById(R.id.existence);
        discussionDefaultNotEphemeral = dialogView.findViewById(R.id.not_ephemeral);

        readOnceSwitch = dialogView.findViewById(R.id.read_once_setting_switch);
        readOnceSwitch.setOnCheckedChangeListener((CompoundButton buttonView, boolean checked) -> viewModel.setReadOnce(checked));

        visibilityDropdown = dialogView.findViewById(R.id.visible_setting_type_dropdown);
        visibilityDropdown.setOnClickListener(this);
        visibilityEditText = dialogView.findViewById(R.id.visible_setting_count_edit_text);
        visibilityEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                recompute(VISEX.VISIBILITY);
            }
        });

        existenceDropdown = dialogView.findViewById(R.id.existence_setting_type_dropdown);
        existenceDropdown.setOnClickListener(this);
        existenceEditText = dialogView.findViewById(R.id.existence_setting_count_edit_text);
        existenceEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                recompute(VISEX.EXISTENCE);
            }
        });


        Button resetButton = dialogView.findViewById(R.id.reset_button);
        resetButton.setOnClickListener(this);
        Button cancelButton = dialogView.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(this);
        okButton = dialogView.findViewById(R.id.ok_button);
        okButton.setOnClickListener(this);

        loadViewModelValues();

        // only start observing once the views are set
        viewModel.getDiscussionJsonExpirationLiveData().observe(this, this::discussionCustomizationChanged);
        viewModel.getDraftLoaded().observe(this, this::draftLoadedChanged);
        viewModel.getValid().observe(this, (Boolean valid) -> okButton.setEnabled(valid != null && valid));

        dialogView.findViewById(R.id.focus_hugger).requestFocus();

        return dialogView;
    }

    private void loadViewModelValues() {
        readOnceSwitch.setChecked(viewModel.getReadOnce());
        setTimeAndUnit(viewModel.getVisibility(), VISEX.VISIBILITY);
        setTimeAndUnit(viewModel.getExistence(), VISEX.EXISTENCE);
    }

    private void discussionCustomizationChanged(Message.JsonExpiration discussionJsonExpiration) {
        // first display the proper default in discussion settings
        boolean ephemeral = false;
        if (discussionJsonExpiration == null) {
            discussionDefaultReadOnce.setVisibility(View.GONE);
            discussionDefaultVisibility.setVisibility(View.GONE);
            discussionDefaultExistence.setVisibility(View.GONE);
        } else {
            if (discussionJsonExpiration.getReadOnce() != null && discussionJsonExpiration.getReadOnce()) {
                discussionDefaultReadOnce.setVisibility(View.VISIBLE);
                ephemeral = true;
            } else {
                discussionDefaultReadOnce.setVisibility(View.GONE);
            }
            if (discussionJsonExpiration.getVisibilityDuration() != null) {
                discussionDefaultVisibility.setVisibility(View.VISIBLE);
                long duration = discussionJsonExpiration.getVisibilityDuration();
                if (duration < 60L) {
                    discussionDefaultVisibility.setText(getString(R.string.text_visible_timer_s, duration));
                } else if (duration < 3600L) {
                    discussionDefaultVisibility.setText(getString(R.string.text_visible_timer_m, duration / 60));
                } else if (duration < 86400L) {
                    discussionDefaultVisibility.setText(getString(R.string.text_visible_timer_h, duration / 3600));
                } else if (duration < 31536000L) {
                    discussionDefaultVisibility.setText(getString(R.string.text_visible_timer_d, duration / 86400));
                } else {
                    discussionDefaultVisibility.setText(getString(R.string.text_visible_timer_y, duration / 31536000));
                }
                ephemeral = true;
            } else {
                discussionDefaultVisibility.setVisibility(View.GONE);
            }
            if (discussionJsonExpiration.getExistenceDuration() != null) {
                discussionDefaultExistence.setVisibility(View.VISIBLE);
                long duration = discussionJsonExpiration.getExistenceDuration();
                if (duration < 60) {
                    discussionDefaultExistence.setText(getString(R.string.text_existence_timer_s, duration));
                    } else if (duration < 3600) {
                    discussionDefaultExistence.setText(getString(R.string.text_existence_timer_m, duration / 60));
                } else if (duration < 86400) {
                    discussionDefaultExistence.setText(getString(R.string.text_existence_timer_h, duration / 3600));
                } else if (duration < 31536000) {
                    discussionDefaultExistence.setText(getString(R.string.text_existence_timer_d, duration / 86400));
                } else {
                    discussionDefaultExistence.setText(getString(R.string.text_existence_timer_y, duration / 31536000));
                }
                ephemeral = true;
            } else {
                discussionDefaultExistence.setVisibility(View.GONE);
            }
        }
        if (ephemeral) {
            discussionDefaultNotEphemeral.setVisibility(View.GONE);
        } else {
            discussionDefaultNotEphemeral.setVisibility(View.VISIBLE);
        }

        // second update the values to match the new constraints
        if (discussionJsonExpiration != null) {
            // compute the gcd of what is already entered and what the discussion forces
            Message.JsonExpiration jsonExpiration = new Message.JsonExpiration();
            jsonExpiration.setReadOnce(viewModel.getReadOnce());
            jsonExpiration.setVisibilityDuration(viewModel.getVisibility());
            jsonExpiration.setExistenceDuration(viewModel.getExistence());
            jsonExpiration.computeGcd(discussionJsonExpiration);

            readOnceSwitch.setChecked(jsonExpiration.getReadOnce() != null && jsonExpiration.getReadOnce());
            setTimeAndUnit(jsonExpiration.getVisibilityDuration(), VISEX.VISIBILITY);
            setTimeAndUnit(jsonExpiration.getExistenceDuration(), VISEX.EXISTENCE);
        }
    }

    private void draftLoadedChanged(Boolean draftLoaded) {
        if (draftLoaded != null && draftLoaded) {
            readOnceSwitch.setEnabled(true);
            visibilityEditText.setEnabled(true);
            existenceEditText.setEnabled(true);
            visibilityDropdown.setAlpha(1f);
            existenceDropdown.setAlpha(1f);

            Message.JsonExpiration draftJsonExpiration = viewModel.getDraftJsonExpiration();
            if (draftJsonExpiration != null) {
                readOnceSwitch.setChecked(draftJsonExpiration.getReadOnce() != null && draftJsonExpiration.getReadOnce());
                if (draftJsonExpiration.getVisibilityDuration() != null) {
                    setTimeAndUnit(draftJsonExpiration.getVisibilityDuration(), VISEX.VISIBILITY);
                } else {
                    setTimeAndUnit(null, VISEX.VISIBILITY);
                }
                if (draftJsonExpiration.getExistenceDuration() != null) {
                    setTimeAndUnit(draftJsonExpiration.getExistenceDuration(), VISEX.EXISTENCE);
                } else {
                    setTimeAndUnit(null, VISEX.EXISTENCE);
                }
            } else {
                readOnceSwitch.setChecked(false);
                setTimeAndUnit(null, VISEX.VISIBILITY);
                setTimeAndUnit(null, VISEX.EXISTENCE);
            }
        } else {
            readOnceSwitch.setEnabled(false);
            visibilityEditText.setEnabled(false);
            visibilityDropdown.setAlpha(.5f);
            existenceEditText.setEnabled(false);
            existenceDropdown.setAlpha(.5f);
        }
    }

    private void unitChanged(VISEX visex) {
        int resId;
        UNIT unit;
        switch (visex) {
            case VISIBILITY:
                unit = visibilityUnit;
                break;
            case EXISTENCE:
            default:
                unit = existenceUnit;
                break;
        }
        switch (unit) {
            case MINUTES:
                resId = R.string.text_unit_m;
                break;
            case HOURS:
                resId = R.string.text_unit_h;
                break;
            case DAYS:
                resId = R.string.text_unit_d;
                break;
            case YEARS:
                resId = R.string.text_unit_y;
                break;
            case SECONDS:
            default:
                resId = R.string.text_unit_s;
                break;
        }
        switch (visex) {
            case VISIBILITY:
                visibilityDropdown.setText(resId);
                break;
            case EXISTENCE:
                existenceDropdown.setText(resId);
                break;
        }
        recompute(visex);
    }

    private void recompute(VISEX visex) {
        CharSequence count;
        UNIT unit;
        switch (visex) {
            case VISIBILITY:
                count = visibilityEditText.getText();
                unit = visibilityUnit;
                break;
            case EXISTENCE:
            default:
                count = existenceEditText.getText();
                unit = existenceUnit;
                break;
        }
        Long time = null;
        if (count != null) {
            try {
                long unitTime = Long.parseLong(count.toString());
                switch (unit) {
                    case SECONDS:
                        time = unitTime;
                        break;
                    case MINUTES:
                        time = unitTime * 60L;
                        break;
                    case HOURS:
                        time = unitTime * 3_600L;
                        break;
                    case DAYS:
                        time = unitTime * 86_400L;
                        break;
                    case YEARS:
                        time = unitTime * 31_536_000L;
                        break;
                    default:
                        time = null;
                        break;
                }
            } catch (Exception e) {
                // nothing to do
            }
        }
        switch (visex) {
            case VISIBILITY:
                viewModel.setVisibility(time);
                break;
            case EXISTENCE:
                viewModel.setExistence(time);
                break;
        }
    }


    private void setTimeAndUnit(Long time, VISEX visex) {
        UNIT unit;
        String value;
        if (time == null) {
            unit = (visex == VISEX.VISIBILITY) ? UNIT.MINUTES : UNIT.DAYS;
            value = null;
        } else if (time == 0) {
            unit = (visex == VISEX.VISIBILITY) ? UNIT.MINUTES : UNIT.DAYS;
            value = "0";
        } else if ((time % 31_536_000L) == 0) {
            unit = UNIT.YEARS;
            value = Long.toString(time / 31_536_000L);
        } else if ((time % 86_400L) == 0) {
            unit = UNIT.DAYS;
            value = Long.toString(time / 86_400L);
        } else if ((time % 3_600) == 0) {
            unit = UNIT.HOURS;
            value = Long.toString(time / 3_600L);
        } else if ((time % 60L) == 0) {
            unit = UNIT.MINUTES;
            value = Long.toString(time / 60L);
        } else {
            unit = UNIT.SECONDS;
            value = Long.toString(time);
        }
        switch (visex) {
            case VISIBILITY:
                visibilityEditText.setText(value);
                visibilityUnit = unit;
                break;
            case EXISTENCE:
                existenceEditText.setText(value);
                existenceUnit = unit;
                break;
        }
        unitChanged(visex);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.ok_button) {
            if (viewModel.valid.getValue() != null && viewModel.valid.getValue()) {
                Message.JsonExpiration jsonExpiration = new Message.JsonExpiration();
                if (viewModel.readOnce) {
                    jsonExpiration.setReadOnce(viewModel.readOnce);
                }
                jsonExpiration.setVisibilityDuration(viewModel.visibility);
                jsonExpiration.setExistenceDuration(viewModel.existence);
                App.runThread(new SetDraftJsonExpirationTask(discussionId, jsonExpiration));
                dismiss();
            }
        } else if (id == R.id.cancel_button) {
            dismiss();
        } else if (id == R.id.reset_button) {
            viewModel.reset();
            loadViewModelValues();
        } else if (id == R.id.visible_setting_type_dropdown) {
            popupMenuVisex = VISEX.VISIBILITY;
            PopupMenu popup = new PopupMenu(activity, v);
            popup.inflate(R.menu.popup_ephemeral_unit);
            popup.setOnMenuItemClickListener(this);
            popup.show();
        } else if (id == R.id.existence_setting_type_dropdown) {
            popupMenuVisex = VISEX.EXISTENCE;
            PopupMenu popup = new PopupMenu(activity, v);
            popup.inflate(R.menu.popup_ephemeral_unit);
            popup.setOnMenuItemClickListener(this);
            popup.show();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        UNIT unit = (popupMenuVisex == VISEX.VISIBILITY) ? UNIT.MINUTES : UNIT.DAYS;
        if (itemId == R.id.popup_action_unit_s) {
            unit = UNIT.SECONDS;
        } else if (itemId == R.id.popup_action_unit_m) {
            unit = UNIT.MINUTES;
        } else if (itemId == R.id.popup_action_unit_h) {
            unit = UNIT.HOURS;
        } else if (itemId == R.id.popup_action_unit_d) {
            unit = UNIT.DAYS;
        } else if (itemId == R.id.popup_action_unit_y) {
            unit = UNIT.YEARS;
        }
        switch (popupMenuVisex) {
            case VISIBILITY:
                visibilityUnit = unit;
                break;
            case EXISTENCE:
                existenceUnit = unit;
                break;
        }
        unitChanged(popupMenuVisex);
        return true;
    }

    public static class EphemeralViewModel extends ViewModel {
        private final MutableLiveData<Long> discussionIdLiveData = new MutableLiveData<>();
        private final MutableLiveData<Boolean> draftLoaded = new MutableLiveData<>(false);
        private Message.JsonExpiration draftJsonExpiration;
        private Message.JsonExpiration discussionJsonExpiration;

        private boolean readOnce = false;
        private Long visibility = null;
        private Long existence = null;

        private final MutableLiveData<Boolean> valid = new MutableLiveData<>(false);

        private final LiveData<Message.JsonExpiration> discussionJsonExpirationLiveData = Transformations.switchMap(discussionIdLiveData, (Long discussionId) -> {
            if (discussionId != null) {
                return Transformations.map(AppDatabase.getInstance().discussionCustomizationDao().getLiveData(discussionId), (DiscussionCustomization discussionCustomization) -> {
                    if (discussionCustomization != null) {
                        discussionJsonExpiration = discussionCustomization.getExpirationJson();
                    } else {
                        discussionJsonExpiration = null;
                    }
                    checkValid();
                    return discussionJsonExpiration;
                });
            } else {
                discussionJsonExpiration = null;
                checkValid();
            }
            return null;
        });


        void setDiscussionId(final long discussionId) {
            draftLoaded.postValue(false);
            draftJsonExpiration = null;
            // fetch current draft and set its value, just once
            App.runThread(() -> {
                Message draftMessage = AppDatabase.getInstance().messageDao().getDiscussionDraftMessageSync(discussionId);
                if (draftMessage != null) {
                    try {
                        draftJsonExpiration = AppSingleton.getJsonObjectMapper().readValue(draftMessage.jsonExpiration, Message.JsonExpiration.class);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                draftLoaded.postValue(true);
            });
            discussionIdLiveData.postValue(discussionId);
        }

        public LiveData<Message.JsonExpiration> getDiscussionJsonExpirationLiveData() {
            return discussionJsonExpirationLiveData;
        }

        public LiveData<Boolean> getDraftLoaded() {
            return draftLoaded;
        }

        public LiveData<Boolean> getValid() {
            return valid;
        }

        public Message.JsonExpiration getDraftJsonExpiration() {
            return draftJsonExpiration;
        }

        public boolean getReadOnce() {
            return readOnce;
        }

        public void setReadOnce(boolean readOnce) {
            this.readOnce = readOnce;
            checkValid();
        }

        public Long getVisibility() {
            return visibility;
        }

        public void setVisibility(Long visibility) {
            this.visibility = visibility;
            checkValid();
        }

        public Long getExistence() {
            return existence;
        }

        public void setExistence(Long existence) {
            this.existence = existence;
            checkValid();
        }

        public void reset() {
            if (discussionJsonExpiration == null) {
                this.readOnce = false;
                this.visibility = null;
                this.existence = null;
            } else {
                this.readOnce = discussionJsonExpiration.getReadOnce() != null && discussionJsonExpiration.getReadOnce();
                this.visibility = discussionJsonExpiration.getVisibilityDuration();
                this.existence = discussionJsonExpiration.getExistenceDuration();
            }
        }

        private void checkValid() {
            boolean valid = (visibility == null || visibility > 0) && (existence == null || existence > 0);
            if (discussionJsonExpiration != null) {
                if (discussionJsonExpiration.getReadOnce() != null && discussionJsonExpiration.getReadOnce()) {
                    valid &= readOnce;
                }
                if (discussionJsonExpiration.getVisibilityDuration() != null) {
                    valid &= (visibility != null && visibility <= discussionJsonExpiration.getVisibilityDuration());
                }
                if (discussionJsonExpiration.getExistenceDuration() != null) {
                    valid &= (existence != null && existence <= discussionJsonExpiration.getExistenceDuration());
                }
            }
            this.valid.postValue(valid);
        }
    }
}
