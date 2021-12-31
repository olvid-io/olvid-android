/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputEditText;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.messenger.R;
import io.olvid.messenger.settings.SettingsActivity;


public class LedColorPickerDialogFragment extends DialogFragment implements View.OnClickListener {
    private static final String REQUEST_CODE = "request_code";
    private int requestCode;

    private String initialColor = null;
    private WeakReference<OnLedColorSelectedListener> onLedColorSelectedWeakReference;
    private Integer color;

    private TextInputEditText colorEditText;
    private ImageView fullPreview;

    public static LedColorPickerDialogFragment newInstance(int requestCode) {
        LedColorPickerDialogFragment fragment = new LedColorPickerDialogFragment();
        Bundle args = new Bundle();
        args.putInt(REQUEST_CODE, requestCode);
        fragment.setArguments(args);
        return fragment;
    }

    public void setInitialColor(String initialColor) {
        this.initialColor = initialColor;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        if (arguments != null) {
            requestCode = arguments.getInt(REQUEST_CODE);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof OnLedColorSelectedListener) {
            onLedColorSelectedWeakReference = new WeakReference<>((OnLedColorSelectedListener) context);
        }
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
        View dialogView = inflater.inflate(R.layout.dialog_fragment_led_color_picker, container, false);

        ((TextView) dialogView.findViewById(R.id.dialog_title)).setText(R.string.dialog_title_pick_discussion_color);

        colorEditText = dialogView.findViewById(R.id.color_input);
        colorEditText.addTextChangedListener(new TextWatcher() {
            final Pattern pattern = Pattern.compile("^(#?)([0-9a-fA-F]{6}$)");

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                Matcher matcher = pattern.matcher(s);
                if (matcher.find()) {
                    String colorString = matcher.group(2);
                    try {
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
        fullPreview = dialogView.findViewById(R.id.color_preview_full);

        dialogView.findViewById(R.id.color_white).setOnClickListener(this);
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

        setColorText(initialColor);
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
            setColorText(null);
        } else if (id == R.id.button_cancel) {
            dismiss();
        } else if (id == R.id.color_white) {
            colorEditText.setText("#ffffff");
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

    private void setColorText(String color) {
        if (colorEditText != null) {
            colorEditText.setText(color);
        }
    }

    private void update() {
        if (this.color == null) {
            fullPreview.setImageResource(R.drawable.pref_imageview_nothing);
            fullPreview.setBackgroundColor(0x00ffffff);
        } else {
            fullPreview.setImageDrawable(null);
            fullPreview.setBackgroundColor(color + 0xff000000);
        }
    }

    private void save() {
        if (onLedColorSelectedWeakReference != null) {
            OnLedColorSelectedListener onLedColorSelectedListener = onLedColorSelectedWeakReference.get();
            if (onLedColorSelectedListener != null) {
                if (color == null) {
                    onLedColorSelectedListener.onLedColorSelected(requestCode, null);
                } else {
                    onLedColorSelectedListener.onLedColorSelected(requestCode, String.format(Locale.ENGLISH, "#%06x", color));
                }
            }
        }
    }

    public interface OnLedColorSelectedListener {
        void onLedColorSelected(int requestCode, String color);
    }
}
