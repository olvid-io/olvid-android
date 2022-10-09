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

package io.olvid.messenger.customClasses;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.view.ContentInfoCompat;
import androidx.core.view.OnReceiveContentListener;
import androidx.core.view.ViewCompat;

import io.olvid.messenger.App;
import io.olvid.messenger.R;


public class DiscussionInputEditText extends AppCompatEditText {
    private ImeContentCommittedHandler imeContentCommittedHandler = null;

    public DiscussionInputEditText(Context context) {
        super(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setBackgroundResource(R.drawable.background_discussion_edit_text);
        }
    }

    public DiscussionInputEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setBackgroundResource(R.drawable.background_discussion_edit_text);
        }
    }

    public DiscussionInputEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setBackgroundResource(R.drawable.background_discussion_edit_text);
        }
    }



    public void setImeContentCommittedHandler(ImeContentCommittedHandler imeContentCommittedHandler) {
        this.imeContentCommittedHandler = imeContentCommittedHandler;
        ViewCompat.setOnReceiveContentListener(this, new String[]{"image/jpeg", "image/png", "image/gif", "image/*", "video/*", "text/*", "audio/*", "application/*"}, receiver);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (id == android.R.id.paste) {
                id = android.R.id.pasteAsPlainText;
            }
        }
        return super.onTextContextMenuItem(id);
    }


    private final OnReceiveContentListener receiver = (View view, ContentInfoCompat contentInfo) -> {
        if (imeContentCommittedHandler != null) {
            Pair<ContentInfoCompat, ContentInfoCompat> split = contentInfo.partition((ClipData.Item item) -> item.getUri() != null || item.getText() != null);
            ContentInfoCompat pastableContent = split.first;
            ContentInfoCompat remaining = split.second;
            if (pastableContent != null) {
                ContentResolver contentResolver = App.getContext().getContentResolver();

                ClipData clip = pastableContent.getClip();
                String clipName = null;
                try {
                    clipName = clip.getDescription().getLabel().toString();
                } catch (Exception ignored) {}
                String fallbackFileName = clipName;

                for (int i = 0; i < clip.getItemCount(); i++) {
                    ClipData.Item item = clip.getItemAt(i);
                    if (item.getText() != null) {
                        insertTextAtSelection(item.getText());
                    } else if (StringUtils.validateUri(item.getUri())) {
                        Uri uri = item.getUri();
                        String mimeType = clip.getDescription().getMimeTypeCount() > i ? clip.getDescription().getMimeType(i) : null;
                        App.runThread(() -> {
                            String fileName = fallbackFileName;
                            String[] projection = {OpenableColumns.DISPLAY_NAME};
                            try (Cursor cursor = contentResolver.query(uri, projection, null, null, null)) {
                                if ((cursor != null) && cursor.moveToFirst()) {
                                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                                    if (nameIndex >= 0) {
                                        fileName = cursor.getString(nameIndex);
                                    }
                                }
                            }

                            imeContentCommittedHandler.handler(uri, fileName, mimeType, null);
                        });
                    }
                }
            }
            return remaining;
        }
        return contentInfo;
    };

    public void insertTextAtSelection(CharSequence input) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start != -1 && end != -1) {
            Editable text = getText();
            if (text == null) {
                setText(input);
                setSelection(input.length());
            } else {
                text.replace(start, end, input);
                setSelection(start + input.length());
            }
        } else {
            Editable text = getText();
            if (text == null) {
                setText(input);
                setSelection(input.length());
            } else {
                text.append(input);
                setSelection(text.length() + input.length());
            }
        }
    }

    public interface ImeContentCommittedHandler {
        // this handler should always be called from a background thread
        void handler(Uri contentUri, String fileName, String mimeType, Runnable callMeWhenDone);
    }
}