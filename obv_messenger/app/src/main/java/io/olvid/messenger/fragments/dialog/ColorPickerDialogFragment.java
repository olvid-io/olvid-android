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

package io.olvid.messenger.fragments.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.DiscussionCustomization;


public class ColorPickerDialogFragment extends DialogFragment implements View.OnClickListener {
    private static final String DISCUSSION_ID_KEY = "discussion_id";

    private long discussionId;
    private Integer initialColor;
    private Float initialAlpha;
    private boolean initialized;

    private Integer color;
    private Float alpha;

    private TextInputEditText colorEditText;
    private TextInputEditText alphaEditText;
    private SeekBar alphaSeekBar;
    private View fullPreview;
    private View alphaPreview;

    public static ColorPickerDialogFragment newInstance(long discussionId) {
        ColorPickerDialogFragment fragment = new ColorPickerDialogFragment();
        Bundle args = new Bundle();
        args.putLong(DISCUSSION_ID_KEY, discussionId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initialized = false;
        Bundle arguments = getArguments();
        if (arguments != null) {
            discussionId = arguments.getLong(DISCUSSION_ID_KEY);
        }
        App.runThread(() -> {
            DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussionId);
            if (discussionCustomization == null) {
                initialColor = null;
                initialAlpha = null;
            } else {
                DiscussionCustomization.ColorJson colorJson = discussionCustomization.getColorJson();
                if (colorJson == null) {
                    initialColor = null;
                    initialAlpha = null;
                } else {
                    initialColor = colorJson.color;
                    initialAlpha = colorJson.alpha;
                }
            }
            initialized = true;
            setColorAndAlpha(initialColor, initialAlpha);
        });
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
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_color_picker, container, false);

        ((TextView) dialogView.findViewById(R.id.dialog_title)).setText(R.string.dialog_title_pick_discussion_color);

        colorEditText = dialogView.findViewById(R.id.color_input);
        colorEditText.addTextChangedListener(new TextChangeListener() {
            final Pattern pattern = Pattern.compile("^(#?)([0-9a-fA-F]{6}$)");

            @Override
            public void afterTextChanged(Editable s) {
                Matcher matcher = pattern.matcher(s);
                if (matcher.find()) {
                    String colorString = matcher.group(2);
                    try {
                        //noinspection ConstantConditions
                        int color = Integer.parseInt(colorString, 16);
                        setColor(color);
                    } catch (Exception e) {
                        setColor(null);
                    }
                } else {
                    setColor(null);
                }
            }
        });
        alphaEditText = dialogView.findViewById(R.id.alpha_input);
        alphaEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    float alpha = Float.parseFloat(s.toString());
                    if (alpha < 0) {
                        alpha = 0f;
                    } else if (alpha > 1) {
                        alpha = 1f;
                    }
                    setAlpha(alpha);
                    alphaSeekBar.setProgress((int) (100*alpha));
                } catch (Exception e) {
                    setAlpha(null);
                }
            }
        });
        alphaSeekBar = dialogView.findViewById(R.id.alpha_seekbar);
        alphaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean tracking = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (tracking) {
                    alphaEditText.setText(String.format(Locale.ENGLISH, "%.2f", (float) progress / 100));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                tracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                tracking = false;
            }
        });
        fullPreview = dialogView.findViewById(R.id.color_preview_full);
        alphaPreview = dialogView.findViewById(R.id.color_preview_alpha);

        dialogView.findViewById(R.id.color_black).setOnClickListener(this);
        dialogView.findViewById(R.id.color_red).setOnClickListener(this);
        dialogView.findViewById(R.id.color_pink).setOnClickListener(this);
        dialogView.findViewById(R.id.color_purple).setOnClickListener(this);
        dialogView.findViewById(R.id.color_deep_purple).setOnClickListener(this);
        dialogView.findViewById(R.id.color_indigo).setOnClickListener(this);
        dialogView.findViewById(R.id.color_blue).setOnClickListener(this);
        dialogView.findViewById(R.id.color_light_blue).setOnClickListener(this);
        dialogView.findViewById(R.id.color_cyan).setOnClickListener(this);
        dialogView.findViewById(R.id.color_teal).setOnClickListener(this);
        dialogView.findViewById(R.id.color_green).setOnClickListener(this);
        dialogView.findViewById(R.id.color_light_green).setOnClickListener(this);
        dialogView.findViewById(R.id.color_lime).setOnClickListener(this);
        dialogView.findViewById(R.id.color_yellow).setOnClickListener(this);
        dialogView.findViewById(R.id.color_amber).setOnClickListener(this);
        dialogView.findViewById(R.id.color_orange).setOnClickListener(this);
        dialogView.findViewById(R.id.color_deep_orange).setOnClickListener(this);
        dialogView.findViewById(R.id.color_brown).setOnClickListener(this);
        dialogView.findViewById(R.id.color_grey).setOnClickListener(this);
        dialogView.findViewById(R.id.color_blue_grey).setOnClickListener(this);

        dialogView.findViewById(R.id.button_ok).setOnClickListener(this);
        dialogView.findViewById(R.id.button_clear).setOnClickListener(this);
        dialogView.findViewById(R.id.button_cancel).setOnClickListener(this);

        if (initialized) {
            setColorAndAlpha(initialColor, initialAlpha);
        }
        return dialogView;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_ok) {
            save();
            dismiss();
        } else if (id == R.id.button_clear) {
            setColorAndAlpha(null, null);
        } else if (id == R.id.button_cancel) {
            dismiss();
        } else if (id == R.id.color_black) {
            colorEditText.setText("#000000");
        } else if (id == R.id.color_red) {
            colorEditText.setText("#f44336");
        } else if (id == R.id.color_pink) {
            colorEditText.setText("#e91e63");
        } else if (id == R.id.color_purple) {
            colorEditText.setText("#9c27b0");
        } else if (id == R.id.color_deep_purple) {
            colorEditText.setText("#673ab7");
        } else if (id == R.id.color_indigo) {
            colorEditText.setText("#3f51b5");
        } else if (id == R.id.color_blue) {
            colorEditText.setText("#2196f3");
        } else if (id == R.id.color_light_blue) {
            colorEditText.setText("#03a9f4");
        } else if (id == R.id.color_cyan) {
            colorEditText.setText("#00bcd4");
        } else if (id == R.id.color_teal) {
            colorEditText.setText("#009688");
        } else if (id == R.id.color_green) {
            colorEditText.setText("#4caf50");
        } else if (id == R.id.color_light_green) {
            colorEditText.setText("#8bc34a");
        } else if (id == R.id.color_lime) {
            colorEditText.setText("#cddc39");
        } else if (id == R.id.color_yellow) {
            colorEditText.setText("#ffeb3b");
        } else if (id == R.id.color_amber) {
            colorEditText.setText("#ffc107");
        } else if (id == R.id.color_orange) {
            colorEditText.setText("#ff9800");
        } else if (id == R.id.color_deep_orange) {
            colorEditText.setText("#ff5722");
        } else if (id == R.id.color_brown) {
            colorEditText.setText("#795548");
        } else if (id == R.id.color_grey) {
            colorEditText.setText("#9e9e9e");
        } else if (id == R.id.color_blue_grey) {
            colorEditText.setText("#607d8b");
        }
    }

    private void setColor(Integer color) {
        this.color = color;
        update();
    }

    private void setAlpha(Float alpha) {
        this.alpha = alpha;
        update();
    }

    private void setColorAndAlpha(Integer color, Float alpha) {
        if (colorEditText == null || alphaEditText == null) {
            return;
        }
        if (color == null) {
            colorEditText.setText(null);
        } else {
            colorEditText.setText(String.format("#%06x", color));
        }
        if (alpha == null) {
            alpha = .2f;
        }
        alphaEditText.setText(String.format(Locale.ENGLISH, "%.2f", alpha));
    }

    private void update() {
        int color;
        float alpha;
        if (this.color == null) {
            color = 0xffffff;
        } else {
            color = this.color;
        }
        if (this.alpha == null) {
            alpha = .2f;
        } else {
            alpha = this.alpha;
        }
        fullPreview.setBackgroundColor(color + 0xff000000);
        alphaPreview.setBackgroundColor(color + ((int) (alpha*255)<<24));
    }

    private void save() {
        final DiscussionCustomization.ColorJson colorJson;
        if (color == null || alpha == null) {
            colorJson = null;
        } else {
            colorJson = new DiscussionCustomization.ColorJson(color, alpha);
        }
        App.runThread(() -> {
            DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussionId);
            if (discussionCustomization == null) {
                try {
                    discussionCustomization = new DiscussionCustomization(discussionId);
                    discussionCustomization.setColorJson(colorJson);
                } catch (Exception ignored) {}
                AppDatabase.getInstance().discussionCustomizationDao().insert(discussionCustomization);
            } else {
                try {
                    discussionCustomization.setColorJson(colorJson);
                    AppDatabase.getInstance().discussionCustomizationDao().update(discussionCustomization);
                } catch (Exception ignored) {}
            }
        });
    }
}
