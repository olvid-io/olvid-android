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

package io.olvid.messenger.discussion;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

public class EmojiKeyboardFragment extends Fragment {
    private FragmentActivity activity;
    private InputConnection inputConnection;
    private RestoreKeyboardListener restoreKeyboardListener;

    private final EmojiPickerViewFactory.EmojiClickListener emojiClickListener = new EmojiPickerViewFactory.EmojiClickListener() {
        @Override
        public void onClick(String emoji) {
            if (inputConnection != null) {
                inputConnection.commitText(emoji, 1);
            }
        }

        @Override
        public void onHighlightedClick(View emojiView, String emoji) {
            onClick(emoji);
        }

        @Override
        public void onLongClick(String emoji) {
            if (inputConnection != null) {
                inputConnection.commitText(emoji, 1);
            }
        }
    };

    private final EmojiPickerViewFactory.EmojiKeyboardListener emojiKeyboardListener = new EmojiPickerViewFactory.EmojiKeyboardListener() {
        @Override
        public void onBackspace() {
            if (inputConnection != null) {
                if (TextUtils.isEmpty(inputConnection.getSelectedText(0))) {
                    inputConnection.deleteSurroundingText(1, 0);
                } else {
                    inputConnection.commitText("", 1);
                }
            }
        }

        @Override
        public void onRestoreKeyboard() {
            if (restoreKeyboardListener != null) {
                restoreKeyboardListener.onRestoreKeyboard();
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return EmojiPickerViewFactory.createEmojiPickerView(activity, null, emojiClickListener, emojiKeyboardListener, 5, false, null);
    }

    public void setInputConnection(@Nullable InputConnection inputConnection) {
        this.inputConnection = inputConnection;
    }

    public void setRestoreKeyboardListener(RestoreKeyboardListener restoreKeyboardListener) {
        this.restoreKeyboardListener = restoreKeyboardListener;
    }

    interface RestoreKeyboardListener {
        void onRestoreKeyboard();
    }
}
