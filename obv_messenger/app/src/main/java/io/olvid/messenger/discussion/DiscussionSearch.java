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

import android.text.InputType;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.settings.SettingsActivity;

public class DiscussionSearch implements MenuItem.OnMenuItemClickListener, MenuItem.OnActionExpandListener, SearchView.OnQueryTextListener {
    @NonNull private final FragmentActivity activity;
    @NonNull private final Menu menu;
    @NonNull private final DiscussionActivity.MessageListAdapter messageListAdapter;
    @NonNull private final LinearLayoutManager messageListLinearLayoutManager;
    private MenuItem menuPrev;
    private MenuItem menuNext;

    List<Pattern> patterns = null;
    Integer prevPosition = null;
    Integer currentPosition = null;
    Integer nextPosition = null;

    public DiscussionSearch(@NonNull FragmentActivity activity, @NonNull Menu menu, @NonNull MenuItem searchItem, @NonNull DiscussionActivity.MessageListAdapter messageListAdapter, @NonNull LinearLayoutManager messageListLinearLayoutManager) {
        this.activity = activity;
        this.menu = menu;
        this.messageListAdapter = messageListAdapter;
        this.messageListLinearLayoutManager = messageListLinearLayoutManager;
        menuPrev = null;
        menuNext = null;

        searchItem.setOnActionExpandListener(this);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint(activity.getString(R.string.hint_search_message));
            searchView.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_FILTER);
            if (SettingsActivity.useKeyboardIncognitoMode()) {
                searchView.setImeOptions(searchView.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
            }
            searchView.setOnQueryTextListener(this);
        }

        highlightedSpans = new BackgroundColorSpan[10];
        for (int i=0; i<highlightedSpans.length; i++) {
            highlightedSpans[i] = new BackgroundColorSpan(ContextCompat.getColor(activity, R.color.accentOverlay));
        }
    }

    @Override
    public boolean onMenuItemActionExpand(@NonNull MenuItem searchItem) {
        for (int i=0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getItemId() == R.id.action_call
                    || item.getItemId() == R.id.action_unmute) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
        }
        activity.getMenuInflater().inflate(R.menu.menu_discussion_search, menu);
        menuPrev = menu.findItem(R.id.action_prev);
        menuPrev.setOnMenuItemClickListener(this);
        menuNext = menu.findItem(R.id.action_next);
        menuNext.setOnMenuItemClickListener(this);
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(@NonNull MenuItem searchItem) {
        activity.invalidateOptionsMenu();
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        App.runThread(() -> filter(newText));
        return true;
    }

    @Override
    public boolean onMenuItemClick(@NonNull MenuItem item) {
        try {
            int id = item.getItemId();
            if (id == R.id.action_prev) {
                if (prevPosition != null) {
                    nextPosition = currentPosition;
                    currentPosition = prevPosition;
                    prevPosition = null;
                    messageListAdapter.setMessageHighlightInfo(new MessageHighlightInfo(messageListAdapter.messages.get(currentPosition).id, patterns));
                    messageListAdapter.notifyItemChanged(currentPosition + 1, DiscussionActivity.MessageListAdapter.BODY_OR_HIGHLIGHT_CHANGE_MASK);
                    messageListLinearLayoutManager.scrollToPosition(currentPosition + 1);
                    App.runThread(this::findPrev);
                }
                return true;
            } else if (id == R.id.action_next) {
                if (nextPosition != null) {
                    prevPosition = currentPosition;
                    currentPosition = nextPosition;
                    nextPosition = null;
                    messageListAdapter.setMessageHighlightInfo(new MessageHighlightInfo(messageListAdapter.messages.get(currentPosition).id, patterns));
                    messageListAdapter.notifyItemChanged(currentPosition + 1, DiscussionActivity.MessageListAdapter.BODY_OR_HIGHLIGHT_CHANGE_MASK);
                    messageListLinearLayoutManager.scrollToPosition(currentPosition + 1);
                    App.runThread(this::findNext);
                }
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    synchronized private void findPrev() {
        if (patterns != null && currentPosition != null) {
            List<Message> messages = messageListAdapter.messages;

            for (int i = currentPosition - 1; i >= 0; i--) {
                if (find(messages.get(i), patterns)) {
                    prevPosition = i;
                    break;
                }
            }
            activity.runOnUiThread(() -> {
                if (menuPrev != null) {
                    menuPrev.setEnabled(prevPosition != null);
                }
                if (menuNext != null) {
                    menuNext.setEnabled(nextPosition != null);
                }
            });
        }
    }

    synchronized private void findNext() {
        if (patterns != null && currentPosition != null) {
            List<Message> messages = messageListAdapter.messages;

            for (int i = currentPosition + 1; i < messages.size(); i++) {
                if (find(messages.get(i), patterns)) {
                    nextPosition = i;
                    break;
                }
            }

            activity.runOnUiThread(() -> {
                if (menuPrev != null) {
                    menuPrev.setEnabled(prevPosition != null);
                }
                if (menuNext != null) {
                    menuNext.setEnabled(nextPosition != null);
                }
            });
        }
    }

    synchronized private void filter(String filterString) {
        if (filterString != null) {
            String[] filters = filterString.trim().split("\\s+");
            patterns = new ArrayList<>();
            for (String filter : filters) {
                if (filter.trim().length() != 0) {
                    patterns.add(Pattern.compile(Pattern.quote(StringUtils.unAccent(filter))));
                }
            }

            if (patterns.size() > 0) {
                final List<Message> messages = messageListAdapter.messages;
                if (messages != null) {

                    int firstVisible = Math.max(0, messageListLinearLayoutManager.findFirstVisibleItemPosition() - 1);
                    int lastVisible = messageListLinearLayoutManager.findLastVisibleItemPosition() - 1;

                    final int searchStart;
                    if (currentPosition != null && currentPosition >= firstVisible && currentPosition <= lastVisible) {
                        // start from currentPosition
                        searchStart = currentPosition;
                    } else {
                        // start from firstVisible
                        searchStart = firstVisible;
                    }
                    prevPosition = null;
                    currentPosition = null;
                    nextPosition = null;

                    for (int i = searchStart; i < messages.size(); i++) {
                        if (find(messages.get(i), patterns)) {
                            currentPosition = i;
                            break;
                        }
                    }

                    if (currentPosition == null) {
                        // we could not find a "forward" match --> start searching backwards
                        for (int i = searchStart - 1; i >= 0; i--) {
                            if (find(messages.get(i), patterns)) {
                                currentPosition = i;
                                break;
                            }
                        }
                    } else {
                        // search the next position
                        for (int i = currentPosition + 1; i < messages.size(); i++) {
                            if (find(messages.get(i), patterns)) {
                                nextPosition = i;
                                break;
                            }
                        }
                    }

                    if (currentPosition != null) {
                        // search for previous position
                        for (int i = currentPosition - 1; i >= 0; i--) {
                            if (find(messages.get(i), patterns)) {
                                prevPosition = i;
                                break;
                            }
                        }
                    }


                    if (currentPosition != null) {
                        int cPosition = currentPosition;
                        activity.runOnUiThread(() -> {
                            Message message = messages.get(cPosition);
                            if (message != null) {
                                messageListAdapter.setMessageHighlightInfo(new MessageHighlightInfo(message.id, patterns));
                            }
                            messageListAdapter.notifyItemChanged(cPosition + 1, DiscussionActivity.MessageListAdapter.BODY_OR_HIGHLIGHT_CHANGE_MASK);
                            messageListLinearLayoutManager.scrollToPosition(cPosition + 1);
                            if (menuPrev != null) {
                                menuPrev.setEnabled(prevPosition != null);
                            }
                            if (menuNext != null) {
                                menuNext.setEnabled(nextPosition != null);
                            }
                        });
                        return;
                    }
                }
            }
        }

        // in case we did not find any match
        patterns = null;
        prevPosition = null;
        currentPosition = null;
        nextPosition = null;
        activity.runOnUiThread(() -> {
            messageListAdapter.setMessageHighlightInfo(null);
            if (menuPrev != null) {
                menuPrev.setEnabled(false);
            }
            if (menuNext != null) {
                menuNext.setEnabled(false);
            }
        });
    }

    private boolean find(Message message, List<Pattern> patterns) {
        if (message.contentBody == null || (message.messageType != Message.TYPE_OUTBOUND_MESSAGE && message.messageType != Message.TYPE_INBOUND_MESSAGE)) {
            return false;
        }
        String body = StringUtils.unAccent(message.contentBody);
        for (Pattern pattern : patterns) {
            if (!pattern.matcher(body).find()) {
                return false;
            }
        }
        return true;
    }

    static class MessageHighlightInfo {
        final long messageId;
        final List<Pattern> patterns;

        public MessageHighlightInfo(long messageId, List<Pattern> patterns) {
            this.messageId = messageId;
            this.patterns = patterns;
        }
    }

    private static BackgroundColorSpan[] highlightedSpans = null;

    public static CharSequence highlightString(@NonNull Spannable input, @NonNull List<Pattern> patterns) {
        if (highlightedSpans == null) {
            return input;
        }

        int i = 0;
        String unAccented = StringUtils.unAccent(input.toString());
        for (Pattern pattern : patterns) {
            if (i == highlightedSpans.length) {
                break;
            }
            Matcher matcher = pattern.matcher(unAccented);
            if (matcher.find()) {
                input.setSpan(highlightedSpans[i], StringUtils.unaccentedOffsetToActualOffset(input, matcher.start()), StringUtils.unaccentedOffsetToActualOffset(input, matcher.end()), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i++;
            }
        }

        return input;
    }
}
