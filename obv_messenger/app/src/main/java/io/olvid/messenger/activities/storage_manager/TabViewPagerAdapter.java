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

package io.olvid.messenger.activities.storage_manager;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;

class TabViewPagerAdapter extends RecyclerView.Adapter<TabViewPagerAdapter.TabViewPagerViewHolder> {
    @NonNull private final FragmentActivity activity;
    @NonNull private final AudioAttachmentServiceBinding audioAttachmentServiceBinding;
    @NonNull private final StorageManagerViewModel viewModel;

    public TabViewPagerAdapter(@NonNull FragmentActivity activity, @NonNull AudioAttachmentServiceBinding audioAttachmentServiceBinding) {
        this.activity = activity;
        this.audioAttachmentServiceBinding = audioAttachmentServiceBinding;
        this.viewModel = new ViewModelProvider(activity).get(StorageManagerViewModel.class);
    }

    @NonNull
    @Override
    public TabViewPagerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TabViewPagerViewHolder(LayoutInflater.from(activity).inflate(R.layout.item_storage_explorer_page, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TabViewPagerViewHolder holder, int position) {
        LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        AttachmentListAdapter adapter = new AttachmentListAdapter(activity, audioAttachmentServiceBinding);

        holder.emptyRecyclerView.setLayoutManager(layoutManager);
        holder.emptyRecyclerView.setAdapter(adapter);
        holder.emptyRecyclerView.setHideIfEmpty(true);

        final LiveData<List<FyleMessageJoinWithStatusDao.FyleAndOrigin>> source;
        switch (position) {
            case 0:
                source = Transformations.switchMap(viewModel.getOwnedIdentityAndSortOrderLiveData(), (Pair<OwnedIdentity, StorageManagerViewModel.SortOrder> ownedIdentityAndSortOrder) -> {
                    if (ownedIdentityAndSortOrder == null || ownedIdentityAndSortOrder.first == null || ownedIdentityAndSortOrder.second == null) {
                        return null;
                    }

                    switch (ownedIdentityAndSortOrder.second.sortKey) {
                        case DATE:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getMediaFyleAndOriginDateAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getMediaFyleAndOriginDateDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                        case NAME:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getMediaFyleAndOriginNameAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getMediaFyleAndOriginNameDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                        case SIZE:
                        default:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getMediaFyleAndOriginSizeAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getMediaFyleAndOriginSizeDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                    }
                });
                break;
            case 1:
                source = Transformations.switchMap(viewModel.getOwnedIdentityAndSortOrderLiveData(), (Pair<OwnedIdentity, StorageManagerViewModel.SortOrder> ownedIdentityAndSortOrder) -> {
                    if (ownedIdentityAndSortOrder == null || ownedIdentityAndSortOrder.first == null || ownedIdentityAndSortOrder.second == null) {
                        return null;
                    }

                    switch (ownedIdentityAndSortOrder.second.sortKey) {
                        case DATE:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFileFyleAndOriginDateAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFileFyleAndOriginDateDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                        case NAME:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFileFyleAndOriginNameAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFileFyleAndOriginNameDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                        case SIZE:
                        default:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFileFyleAndOriginSizeAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFileFyleAndOriginSizeDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                    }
                });
                break;
            case 2:
                source = Transformations.switchMap(viewModel.getOwnedIdentityAndSortOrderLiveData(), (Pair<OwnedIdentity, StorageManagerViewModel.SortOrder> ownedIdentityAndSortOrder) -> {
                    if (ownedIdentityAndSortOrder == null || ownedIdentityAndSortOrder.first == null || ownedIdentityAndSortOrder.second == null) {
                        return null;
                    }

                    switch (ownedIdentityAndSortOrder.second.sortKey) {
                        case DATE:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getAudioFyleAndOriginDateAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getAudioFyleAndOriginDateDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                        case NAME:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getAudioFyleAndOriginNameAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getAudioFyleAndOriginNameDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                        case SIZE:
                        default:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getAudioFyleAndOriginSizeAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getAudioFyleAndOriginSizeDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                    }
                });
                break;
            case 3:
            default:
                source = Transformations.switchMap(viewModel.getOwnedIdentityAndSortOrderLiveData(), (Pair<OwnedIdentity, StorageManagerViewModel.SortOrder> ownedIdentityAndSortOrder) -> {
                    if (ownedIdentityAndSortOrder == null || ownedIdentityAndSortOrder.first == null || ownedIdentityAndSortOrder.second == null) {
                        return null;
                    }

                    switch (ownedIdentityAndSortOrder.second.sortKey) {
                        case DATE:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFyleAndOriginDateAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFyleAndOriginDateDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                        case NAME:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFyleAndOriginNameAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFyleAndOriginNameDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                        case SIZE:
                        default:
                            if (ownedIdentityAndSortOrder.second.ascending) {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFyleAndOriginSizeAsc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            } else {
                                return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFyleAndOriginSizeDesc(ownedIdentityAndSortOrder.first.bytesOwnedIdentity);
                            }
                    }
                });
                break;
        }
        source.observe(activity, adapter);
    }

    @Override
    public int getItemCount() {
        return 4;
    }

    static class TabViewPagerViewHolder extends RecyclerView.ViewHolder {
        final EmptyRecyclerView emptyRecyclerView;
        final View emptyView;
        final ViewGroup spinnerGroup;

        public TabViewPagerViewHolder(@NonNull View itemView) {
            super(itemView);
            emptyRecyclerView = itemView.findViewById(R.id.recycler_view);
            emptyView = itemView.findViewById(R.id.empty_view);
            spinnerGroup = itemView.findViewById(R.id.loading_spinner);
            emptyRecyclerView.setEmptyView(emptyView);
            emptyRecyclerView.setLoadingSpinner(spinnerGroup);
        }
    }
}
