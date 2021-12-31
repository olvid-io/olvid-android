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

package io.olvid.messenger.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.RecyclerViewDividerDecoration;

@RequiresApi(api = Build.VERSION_CODES.N)
public class StorageExplorer extends LockableActivity {
    private static final int REQUEST_CODE_SAVE_FILE = 518;

    ExplorerViewModel viewModel;
    RecyclerView recyclerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ExplorerViewModel.class);

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            finish();
            return;
        }

        setContentView(R.layout.activity_storage_explorer);


        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.pref_storage_explorer_title);
            viewModel.getPathLiveData().observe(this, (String path) -> {
                if ("".equals(path)) {
                    actionBar.setSubtitle("/");
                } else {
                    actionBar.setSubtitle(path);
                }
            });
        }


        recyclerView = findViewById(R.id.storage_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new RecyclerViewDividerDecoration(this, 0, 0));
        StorageExplorerAdapter adapter = new StorageExplorerAdapter(getLayoutInflater(), this::onItemClicked);
        recyclerView.setAdapter(adapter);

        viewModel.getListing().observe(this, adapter);
    }

    @Override
    public void onBackPressed() {
        if ("".equals(viewModel.getPath())) {
            super.onBackPressed();
        } else {
            int pos = viewModel.getPath().lastIndexOf(File.separatorChar);
            viewModel.setPath(viewModel.getPath().substring(0, pos));
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    private ExplorerElement elementToSave;

    void onItemClicked(ExplorerElement element) {
        if (element.type == ELEMENT_TYPE.FOLDER) {
            viewModel.setPath(element.path);
        } else {
            try {
                elementToSave = element;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_TITLE, element.name);
                App.startActivityForResult(this, intent, REQUEST_CODE_SAVE_FILE);
            } catch (Exception e) {
                e.printStackTrace();
                App.toast(R.string.toast_message_failed_to_save_file, Toast.LENGTH_SHORT);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            final Uri uri = data.getData();
            if (uri != null) {
                App.runThread(() -> {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os == null) {
                            throw new Exception("Unable to write to provided Uri");
                        }
                        if (elementToSave == null) {
                            throw new Exception();
                        }
                        try (FileInputStream fis = new FileInputStream(new File(App.getContext().getDataDir(), elementToSave.path))) {
                            byte[] buffer = new byte[262_144];
                            int c;
                            while ((c = fis.read(buffer)) != -1) {
                                os.write(buffer, 0, c);
                            }
                        }
                        App.toast(R.string.toast_message_file_saved, Toast.LENGTH_SHORT);
                    } catch (Exception e) {
                        App.toast(R.string.toast_message_failed_to_save_file, Toast.LENGTH_SHORT);
                    }
                });
            }
        }
    }

    interface ItemClickListener {
        void onItemClick(ExplorerElement element);
    }

    interface ViewHolderClickListener {
        void onViewHolderClick(int adapterPosition);
    }

    public static class StorageExplorerAdapter extends RecyclerView.Adapter<StorageItemViewHolder> implements Observer<List<ExplorerElement>> {
        private List<ExplorerElement> elements;
        private final LayoutInflater inflater;
        private final ItemClickListener itemClickListener;

        public StorageExplorerAdapter(LayoutInflater layoutInflater, ItemClickListener itemClickListener) {
            this.inflater = layoutInflater;
            this.itemClickListener = itemClickListener;
        }

        @NonNull
        @Override
        public StorageItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new StorageItemViewHolder(inflater.inflate(R.layout.item_view_storage_explorer_element, parent, false), (int adapterPosition) -> {
                if (elements != null && elements.size() > adapterPosition && adapterPosition>= 0) {
                    itemClickListener.onItemClick(elements.get(adapterPosition));
                }
            });
        }

        @Override
        public void onBindViewHolder(@NonNull StorageItemViewHolder holder, int position) {
            if (elements != null) {
                ExplorerElement element = elements.get(position);

                holder.fileNameTextView.setText(element.name);

                if (element.type == ELEMENT_TYPE.FOLDER) {
                    holder.folderChevronImageView.setVisibility(View.VISIBLE);
                    holder.sizeTextView.setVisibility(View.GONE);
                } else {
                    holder.folderChevronImageView.setVisibility(View.GONE);
                    holder.sizeTextView.setVisibility(View.VISIBLE);
                    holder.sizeTextView.setText(Formatter.formatShortFileSize(App.getContext(),element.size));
                }
                if (element.modificationTimestamp != 0) {
                    holder.creationTimestampTextView.setText(App.getPreciseAbsoluteDateString(App.getContext(), element.modificationTimestamp, " "));
                } else {
                    holder.creationTimestampTextView.setText(null);
                }
            }
        }

        @Override
        public int getItemCount() {
            if (elements != null) {
                return elements.size();
            }
            return 0;
        }

        @Override
        public void onChanged(List<ExplorerElement> explorerElements) {
            this.elements = explorerElements;
            notifyDataSetChanged();
        }
    }

    public static class StorageItemViewHolder extends RecyclerView.ViewHolder {
        final TextView fileNameTextView;
        final TextView sizeTextView;
        final TextView creationTimestampTextView;
        final ImageView folderChevronImageView;

        public StorageItemViewHolder(@NonNull View itemView, ViewHolderClickListener viewHolderClickListener) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.file_name_text_view);
            sizeTextView = itemView.findViewById(R.id.file_size_text_view);
            creationTimestampTextView = itemView.findViewById(R.id.modification_date_text_view);
            folderChevronImageView = itemView.findViewById(R.id.folder_chevron_image);

            itemView.setOnClickListener((View v) -> viewHolderClickListener.onViewHolderClick(getAbsoluteAdapterPosition()));
        }
    }


    enum ELEMENT_TYPE {
        FILE,
        FOLDER,
    }

    public static class ExplorerElement {
        final String path;
        final String name;
        final long size;
        final long modificationTimestamp;
        final ELEMENT_TYPE type;

        public ExplorerElement(String path, String name, long size, long modificationTimestamp, ELEMENT_TYPE type) {
            this.path = path;
            this.name = name;
            this.size = size;
            this.modificationTimestamp = modificationTimestamp;
            this.type = type;
        }
    }

    public static class ExplorerViewModel extends ViewModel {
        private String path;
        private final MutableLiveData<String> pathLiveData;
        private final LiveData<List<ExplorerElement>> listing;

        public ExplorerViewModel() {
            path = "";
            pathLiveData = new MutableLiveData<>("");
            listing = Transformations.map(pathLiveData, (String path) -> {
                if (path != null) {
                    File folder = new File(App.getContext().getDataDir(), path);
                    try {
                        if (folder.isDirectory()) {
                            List<ExplorerElement> list = new ArrayList<>();
                            for (String fileName : folder.list()) {
                                File file = new File(folder, fileName);
                                list.add(new ExplorerElement(
                                        path + File.separator + fileName,
                                        fileName,
                                        file.length(),
                                        file.lastModified(),
                                        file.isDirectory() ? ELEMENT_TYPE.FOLDER : ELEMENT_TYPE.FILE
                                ));
                            }
                            Collections.sort(list, (ExplorerElement o1, ExplorerElement o2) -> {
                                if ((o1.type == ELEMENT_TYPE.FOLDER) && (o2.type != ELEMENT_TYPE.FOLDER)) {
                                    return -1;
                                } else if ((o2.type == ELEMENT_TYPE.FOLDER) && (o1.type != ELEMENT_TYPE.FOLDER)) {
                                    return 1;
                                }
                                return o1.name.compareTo(o2.name);
                            });
                            return list;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
                return null;
            });
        }

        public void setPath(String path) {
            this.path = path;
            this.pathLiveData.postValue(path);
        }

        public LiveData<String> getPathLiveData() {
            return pathLiveData;
        }

        public String getPath() {
            return path;
        }

        public LiveData<List<ExplorerElement>> getListing() {
            return listing;
        }
    }
}
