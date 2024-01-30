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

package io.olvid.messenger.discussion

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder


class DummyListAdapter : ListAdapter<Void, DummyViewHolder>(DummyDiffCallback) {
    init {
        setHasStableIds(true)
    }

    object DummyDiffCallback : DiffUtil.ItemCallback<Void>() {
        override fun areItemsTheSame(oldItem: Void, newItem: Void): Boolean {
            return true
        }

        override fun areContentsTheSame(oldItem: Void, newItem: Void): Boolean {
            return true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DummyViewHolder {
        val view = View(parent.context)
//        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
        return DummyViewHolder(view)
    }

    override fun onBindViewHolder(holder: DummyViewHolder, position: Int) { }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
}

class DummyViewHolder(itemView: View) : ViewHolder(itemView) {

}