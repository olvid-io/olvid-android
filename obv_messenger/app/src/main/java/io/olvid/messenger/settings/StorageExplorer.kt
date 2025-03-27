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
package io.olvid.messenger.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ItemDecorationSimpleDivider
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.StringUtils
import java.io.File
import java.io.FileInputStream
import java.util.Collections

@RequiresApi(api = Build.VERSION_CODES.N)
class StorageExplorer : LockableActivity() {
    var viewModel: ExplorerViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this).get(ExplorerViewModel::class.java)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        setContentView(R.layout.activity_storage_explorer)

        findViewById<ConstraintLayout>(R.id.root_constraint_layout)?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
                view.updatePadding(
                    top = insets.top,
                    bottom = insets.bottom,
                    left = insets.left,
                    right = insets.right
                )
                windowInsets
            }
        }


        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setTitle(R.string.pref_storage_explorer_title)
            viewModel!!.getPathLiveData().observe(this) { path: String ->
                if ("" == path) {
                    actionBar.subtitle = "/"
                } else {
                    actionBar.subtitle = path
                }
            }
        }


        findViewById<RecyclerView>(R.id.storage_recycler_view)?.let {
            it.setLayoutManager(LinearLayoutManager(this))
            it.addItemDecoration(ItemDecorationSimpleDivider(this, 0, 0))
            val adapter = StorageExplorerAdapter(layoutInflater) { element: ExplorerElement ->
                this.onItemClicked(element)
            }
            it.setAdapter(adapter)
            viewModel!!.listing.observe(this, adapter)
        }

    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if ("" == viewModel!!.getPath()) {
            super.onBackPressed()
        } else {
            val pos = viewModel!!.getPath().lastIndexOf(File.separatorChar)
            viewModel!!.setPath(viewModel!!.getPath().substring(0, pos))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    private var elementToSave: ExplorerElement? = null

    private fun onItemClicked(element: ExplorerElement) {
        if (element.type == ELEMENT_TYPE.FOLDER) {
            viewModel!!.setPath(element.path)
        } else {
            try {
                elementToSave = element
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_TITLE, element.name)
                App.startActivityForResult(this, intent, REQUEST_CODE_SAVE_FILE)
            } catch (e: Exception) {
                e.printStackTrace()
                App.toast(R.string.toast_message_failed_to_save_file, Toast.LENGTH_SHORT)
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val uri = data.data
            if (StringUtils.validateUri(uri)) {
                App.runThread {
                    try {
                        contentResolver.openOutputStream(uri!!).use { os ->
                            if (os == null) {
                                throw Exception("Unable to write to provided Uri")
                            }
                            if (elementToSave == null) {
                                throw Exception()
                            }
                            FileInputStream(File(App.getContext().dataDir, elementToSave!!.path)).use { fis ->
                                val buffer = ByteArray(262144)
                                var c: Int
                                while ((fis.read(buffer).also { c = it }) != -1) {
                                    os.write(buffer, 0, c)
                                }
                            }
                            App.toast(R.string.toast_message_file_saved, Toast.LENGTH_SHORT)
                        }
                    } catch (e: Exception) {
                        App.toast(R.string.toast_message_failed_to_save_file, Toast.LENGTH_SHORT)
                    }
                }
            }
        }
    }

    class StorageExplorerAdapter(private val inflater: LayoutInflater, private val itemClickListener: (ExplorerElement) -> Unit) : RecyclerView.Adapter<StorageItemViewHolder>(), Observer<List<ExplorerElement>?> {
        private var elements: List<ExplorerElement>? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StorageItemViewHolder {
            return StorageItemViewHolder(inflater.inflate(R.layout.item_view_storage_explorer_element, parent, false)) { adapterPosition: Int ->
                if (elements != null && elements!!.size > adapterPosition && adapterPosition >= 0) {
                    itemClickListener(elements!![adapterPosition])
                }
            }
        }

        override fun onBindViewHolder(holder: StorageItemViewHolder, position: Int) {
            if (elements != null) {
                val element = elements!![position]

                holder.fileNameTextView.text = element.name

                if (element.type == ELEMENT_TYPE.FOLDER) {
                    holder.folderChevronImageView.visibility = View.VISIBLE
                    holder.sizeTextView.visibility = View.GONE
                } else {
                    holder.folderChevronImageView.visibility = View.GONE
                    holder.sizeTextView.visibility = View.VISIBLE
                    holder.sizeTextView.text = Formatter.formatShortFileSize(App.getContext(), element.size)
                }
                if (element.modificationTimestamp != 0L) {
                    holder.creationTimestampTextView.text = StringUtils.getPreciseAbsoluteDateString(App.getContext(), element.modificationTimestamp, " ")
                } else {
                    holder.creationTimestampTextView.text = null
                }
            }
        }

        override fun getItemCount(): Int {
            if (elements != null) {
                return elements!!.size
            }
            return 0
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onChanged(value: List<ExplorerElement>?) {
            this.elements = value
            notifyDataSetChanged()
        }
    }

    class StorageItemViewHolder(itemView: View, viewHolderClickListener: (Int) -> Unit) : RecyclerView.ViewHolder(itemView) {
        val fileNameTextView: TextView = itemView.findViewById(R.id.file_name_text_view)
        val sizeTextView: TextView = itemView.findViewById(R.id.file_size_text_view)
        val creationTimestampTextView: TextView = itemView.findViewById(R.id.modification_date_text_view)
        val folderChevronImageView: ImageView = itemView.findViewById(R.id.folder_chevron_image)

        init {
            itemView.setOnClickListener { v: View? -> viewHolderClickListener(absoluteAdapterPosition) }
        }
    }


    enum class ELEMENT_TYPE {
        FILE,
        FOLDER,
    }

    class ExplorerElement(val path: String, val name: String, val size: Long, val modificationTimestamp: Long, val type: ELEMENT_TYPE)

    class ExplorerViewModel : ViewModel() {
        private var path = ""
        private val pathLiveData = MutableLiveData("")
        val listing: LiveData<List<ExplorerElement>?> = pathLiveData.map { path: String? ->
            if (path != null) {
                val folder = File(App.getContext().dataDir, path)
                try {
                    if (folder.isDirectory) {
                        val list: MutableList<ExplorerElement> = ArrayList()
                        val fileNames = folder.list()
                        if (fileNames != null) {
                            for (fileName in fileNames) {
                                val file = File(folder, fileName)
                                list.add(
                                    ExplorerElement(
                                        path + File.separator + fileName,
                                        fileName,
                                        file.length(),
                                        file.lastModified(),
                                        if (file.isDirectory) ELEMENT_TYPE.FOLDER else ELEMENT_TYPE.FILE
                                    )
                                )
                            }
                            Collections.sort(list) { o1: ExplorerElement, o2: ExplorerElement ->
                                if ((o1.type == ELEMENT_TYPE.FOLDER) && (o2.type != ELEMENT_TYPE.FOLDER)) {
                                    return@sort -1
                                } else if ((o2.type == ELEMENT_TYPE.FOLDER) && (o1.type != ELEMENT_TYPE.FOLDER)) {
                                    return@sort 1
                                }
                                o1.name.compareTo(o2.name)
                            }
                            return@map list
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            null
        }

        fun setPath(path: String) {
            this.path = path
            pathLiveData.postValue(path)
        }

        fun getPathLiveData(): LiveData<String> {
            return pathLiveData
        }

        fun getPath(): String {
            return path
        }
    }

    companion object {
        private const val REQUEST_CODE_SAVE_FILE = 518
    }
}
