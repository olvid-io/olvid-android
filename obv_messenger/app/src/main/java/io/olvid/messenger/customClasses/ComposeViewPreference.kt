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

package io.olvid.messenger.customClasses

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import io.olvid.messenger.R


@Suppress("unused")
class ComposeViewPreference : Preference {
    private var content: @Composable (() -> Unit)? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        layoutResource = R.layout.preference_compose_view
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        layoutResource = R.layout.preference_compose_view
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        layoutResource = R.layout.preference_compose_view
    }
    constructor(context: Context) :  super(context) {
        layoutResource = R.layout.preference_compose_view
    }

    fun setContent(content: @Composable (() -> Unit)?) {
        this.content = content
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        content?.let { content ->
            holder.itemView.findViewById<ComposeView>(R.id.composeView).setContent(content = content)
        }
    }
}