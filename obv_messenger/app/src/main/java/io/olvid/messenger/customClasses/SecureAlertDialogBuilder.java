/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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


import android.content.Context;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import io.olvid.messenger.settings.SettingsActivity;

public class SecureAlertDialogBuilder extends AlertDialog.Builder {
    public SecureAlertDialogBuilder(@NonNull Context context, int themeResId) {
        super(context, themeResId);
    }

    @NonNull
    @Override
    public AlertDialog create() {
        AlertDialog alertDialog = super.create();
        if (SettingsActivity.preventScreenCapture()) {
            Window window = alertDialog.getWindow();
            if (window != null) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
        return alertDialog;
    }
}
