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

package io.olvid.messenger.customClasses;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AlphaAnimation;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class EmptyRecyclerView extends RecyclerView {
    private View emptyView;
    private View loadingSpinner;
    private AlphaAnimation loadingSpinnerAlphaAnimation;
    private boolean loadingDone = false;
    private int emptyThreshold = 0;
    private boolean hideIfEmpty = false;

    final private RecyclerView.AdapterDataObserver observer = new AdapterDataObserver() {
        @Override
        public void onChanged() {
            if (getAdapter() instanceof LoadAwareAdapter) {
                loadingDone = ((LoadAwareAdapter<?>) getAdapter()).isLoadingDone();
            }
            checkIfEmpty();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            checkIfEmpty();
        }
    };

    public EmptyRecyclerView(Context context) {
        super(context);
    }

    public EmptyRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EmptyRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    void checkIfEmpty() {
        if (getAdapter() != null) {
            final boolean adapterEmpty = getAdapter().getItemCount() <= emptyThreshold;
            if (loadingSpinner != null && !loadingDone) {
                loadingSpinner.setVisibility(VISIBLE);
                loadingSpinner.clearAnimation();
                loadingSpinnerAlphaAnimation.reset();
                loadingSpinner.startAnimation(loadingSpinnerAlphaAnimation);
                if (emptyView != null) {
                    emptyView.setVisibility(GONE);
                }
            } else {
                if (loadingSpinner != null) {
                    loadingSpinner.clearAnimation();
                    loadingSpinner.setVisibility(GONE);
                }
                if (emptyView != null) {
                    emptyView.setVisibility((loadingDone && adapterEmpty) ? VISIBLE : GONE);
                }
            }
            if (hideIfEmpty) {
                setVisibility(adapterEmpty ? GONE : VISIBLE);
            }
        }
    }

    public void setHideIfEmpty(boolean hideIfEmpty) {
        this.hideIfEmpty = hideIfEmpty;
    }

    @Override
    public void setAdapter(Adapter adapter) {
        final Adapter<?> oldAdapter = getAdapter();
        if (oldAdapter != null) {
            oldAdapter.unregisterAdapterDataObserver(observer);
        }

        super.setAdapter(adapter);
        if (adapter != null) {
            adapter.registerAdapterDataObserver(observer);
            loadingDone = !(adapter instanceof LoadAwareAdapter) || ((LoadAwareAdapter<?>) getAdapter()).isLoadingDone();
        } else {
            loadingDone = true;
        }

        checkIfEmpty();
    }

    public void setEmptyView(View emptyView) {
        this.emptyView = emptyView;
        checkIfEmpty();
    }

    public void setEmptyThreshold(int threshold) {
        this.emptyThreshold = threshold;
    }

    public void setLoadingSpinner(View loadingSpinner) {
        setLoadingSpinner(loadingSpinner, 500, 250);
    }

    public void setLoadingSpinner(View loadingSpinner, int fadeInDelay, int fadeInDuration) {
        if (this.loadingSpinner != null) {
            loadingSpinner.clearAnimation();
        }
        this.loadingSpinner = loadingSpinner;
        loadingSpinnerAlphaAnimation = new AlphaAnimation(0, 1);
        loadingSpinnerAlphaAnimation.setStartOffset(fadeInDelay);
        loadingSpinnerAlphaAnimation.setDuration(fadeInDuration);
        loadingSpinnerAlphaAnimation.setFillAfter(true);
        checkIfEmpty();
    }

    final private ArrayList<OnScrollStateChangedListener> listeners = new ArrayList<>();

    public void addOnScrollStateChangedListener(OnScrollStateChangedListener listener) {
        listeners.add(listener);
    }

    public void removeOnScrollStateChangedListener(OnScrollStateChangedListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);

        for (OnScrollStateChangedListener listener : listeners) {
            listener.onScrollStateChanged(state);
        }
    }

    public interface OnScrollStateChangedListener {
        void onScrollStateChanged(int state);
    }
}