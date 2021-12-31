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

package io.olvid.messenger.customClasses;

import android.content.ClipData;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.view.ContentInfoCompat;
import androidx.core.view.OnReceiveContentListener;
import androidx.core.view.ViewCompat;

import io.olvid.engine.Logger;
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
            Pair<ContentInfoCompat, ContentInfoCompat> split = contentInfo.partition(item -> item.getUri() != null);
            ContentInfoCompat uriContent = split.first;
            ContentInfoCompat remaining = split.second;
            if (uriContent != null) {
                ClipData clip = uriContent.getClip();
                String fileName = null;
                try {
                    fileName = clip.getDescription().getLabel().toString();
                } catch (Exception ignored) {}

                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    String mimeType = null;
                    try {
                        mimeType = clip.getDescription().getMimeType(i);
                    } catch (Exception ignored) {
                    }
                    imeContentCommittedHandler.handler(uri, fileName, mimeType, null);
                }
            }
            return remaining;
        }
        return contentInfo;
    };

    public interface ImeContentCommittedHandler {
        void handler(Uri contentUri, String fileName, String mimeType, Runnable callMeWhenDone);
    }
}