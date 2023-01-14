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

package io.olvid.messenger.webrtc;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.settings.SettingsActivity;

public class WebrtcIncomingCallActivity extends AppCompatActivity implements View.OnClickListener {

    WebrtcServiceConnection webrtcServiceConnection;
    WebrtcCallService webrtcCallService = null;
    CallParticipantsObserver callParticipantsObserver;
    CallStatusObserver callStatusObserver;
    DiscussionCustomizationObserver discussionCustomizationObserver;

    InitialView contactInitialView;
    ImageView contactColorCircleImageView;
    TextView contactNameTextView;
    TextView othersCountTextView;
    TextView bigCountTextView;
    View rejectCallButton;
    View answerCallButton;

    @Override
    protected void attachBaseContext(Context baseContext) {
        super.attachBaseContext(SettingsActivity.overrideContextScales(baseContext));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        super.onCreate(savedInstanceState);

        webrtcServiceConnection = new WebrtcServiceConnection();
        Intent serviceBindIntent = new Intent(this, WebrtcCallService.class);
        bindService(serviceBindIntent, webrtcServiceConnection, 0);

        callParticipantsObserver = new CallParticipantsObserver();
        callStatusObserver = new CallStatusObserver();
        discussionCustomizationObserver = new DiscussionCustomizationObserver();

        setContentView(R.layout.activity_webrtc_incoming_call);

        rejectCallButton = findViewById(R.id.reject_call_button);
        rejectCallButton.setOnClickListener(this);

        answerCallButton = findViewById(R.id.accept_call_button);
        answerCallButton.setOnClickListener(this);

        contactInitialView = findViewById(R.id.contact_initial_view);
        contactColorCircleImageView = findViewById(R.id.portrait_color_circle_image_view);
        contactNameTextView = findViewById(R.id.contact_name_text_view);
        othersCountTextView = findViewById(R.id.others_count_text_view);
        bigCountTextView = findViewById(R.id.big_count_text_view);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(webrtcServiceConnection);
        webrtcCallService = null;
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.accept_call_button) {
            if (webrtcCallService != null) {
                Intent answerCallIntent = new Intent(this, WebrtcCallActivity.class);
                answerCallIntent.setAction(WebrtcCallActivity.ANSWER_CALL_ACTION);
                answerCallIntent.putExtra(WebrtcCallActivity.ANSWER_CALL_EXTRA_CALL_IDENTIFIER, Logger.getUuidString(webrtcCallService.callIdentifier));
                answerCallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(answerCallIntent);
            }
            closeActivity();
        } else if (id == R.id.reject_call_button) {
            if (webrtcCallService != null) {
                webrtcCallService.recipientRejectCall();
            }
            closeActivity();
        }
    }

    private class CallParticipantsObserver implements Observer<List<WebrtcCallService.CallParticipantPojo>> {
        @Override
        public void onChanged(List<WebrtcCallService.CallParticipantPojo> callParticipants) {
            final Contact callParticipantContact = (callParticipants == null || callParticipants.size() == 0) ? null : callParticipants.get(0).contact;
            if (callParticipantContact == null) {
                contactInitialView.setVisibility(View.INVISIBLE);
                contactNameTextView.setText(null);
                othersCountTextView.setVisibility(View.GONE);
                bigCountTextView.setVisibility(View.GONE);
            } else {
                contactInitialView.setVisibility(View.VISIBLE);
                contactInitialView.setContact(callParticipantContact);
                contactNameTextView.setText(callParticipantContact.getCustomDisplayName());
                if (webrtcCallService != null) {
                    int count = webrtcCallService.getIncomingParticipantCount() - 1;
                    if (count > 0) {
                        othersCountTextView.setVisibility(View.VISIBLE);
                        othersCountTextView.setText(getResources().getQuantityString(R.plurals.text_and_x_other, count, count));
                        bigCountTextView.setVisibility(View.VISIBLE);
                        bigCountTextView.setText(getString(R.string.plus_count, count));
                    } else {
                        othersCountTextView.setVisibility(View.GONE);
                        bigCountTextView.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    private class CallStatusObserver implements Observer<WebrtcCallService.State> {
        @Override
        public void onChanged(WebrtcCallService.State state) {
            switch (state) {
                case INITIAL:
                case WAITING_FOR_AUDIO_PERMISSION:
                case GETTING_TURN_CREDENTIALS:
                case RINGING:
                case BUSY:
                    break;
                case CALL_ENDED:
                case FAILED:
                case INITIALIZING_CALL:
                case CALL_IN_PROGRESS: {
                    closeActivity();
                    break;
                }
            }
        }
    }

    private class DiscussionCustomizationObserver implements Observer<DiscussionCustomization> {
        @Override
        public void onChanged(DiscussionCustomization discussionCustomization) {
            if (discussionCustomization != null) {
                DiscussionCustomization.ColorJson colorJson = discussionCustomization.getColorJson();
                if (colorJson != null) {
                    contactColorCircleImageView.setVisibility(View.VISIBLE);
                    contactColorCircleImageView.setColorFilter(0xff000000 + colorJson.color, android.graphics.PorterDuff.Mode.SRC_IN);
                    return;
                }
            }

            contactColorCircleImageView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        closeActivity();
    }

    private void closeActivity() {
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.cancel(WebrtcCallService.NOT_FOREGROUND_NOTIFICATION_ID);
        } catch (Exception e) {
            // do nothing
        }
        finishAndRemoveTask();
    }

    private void setWebrtcCallService(WebrtcCallService webrtcCallService) {
        if (webrtcCallService != null) {
            this.webrtcCallService = webrtcCallService;
            this.webrtcCallService.getCallParticipantsLiveData().observe(this, callParticipantsObserver);
            this.webrtcCallService.getState().observe(this, callStatusObserver);
            LiveData<DiscussionCustomization> discussionCustomizationLiveData = Transformations.switchMap(this.webrtcCallService.getCallParticipantsLiveData(), (List<WebrtcCallService.CallParticipantPojo> callParticipants) -> {
                final Contact callParticipantContact = (callParticipants == null || callParticipants.size() == 0) ? null : callParticipants.get(0).contact;
                if (callParticipantContact == null) {
                    return null;
                }
                return Transformations.switchMap(AppDatabase.getInstance().discussionDao().getByContactLiveData(callParticipantContact.bytesOwnedIdentity, callParticipantContact.bytesContactIdentity), (Discussion discussion) -> {
                    if (discussion == null) {
                        return null;
                    }
                    return AppDatabase.getInstance().discussionCustomizationDao().getLiveData(discussion.id);
                });
            });
            discussionCustomizationLiveData.observe(this, discussionCustomizationObserver);
        } else {
            if (this.webrtcCallService != null) {
                // remove observers
                this.webrtcCallService.getState().removeObservers(this);
                this.webrtcCallService.getCallParticipantsLiveData().removeObservers(this);
            }
            this.webrtcCallService = null;
        }
    }

    private class WebrtcServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!(service instanceof WebrtcCallService.WebrtcCallServiceBinder)) {
                Logger.e("☎ Bound to bad service!!!");
                closeActivity();
                return;
            }
            WebrtcCallService.WebrtcCallServiceBinder binder = (WebrtcCallService.WebrtcCallServiceBinder) service;
            setWebrtcCallService(binder.getService());
        }

        @Override
        public void onNullBinding(ComponentName name) {
            closeActivity();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            setWebrtcCallService(null);
            closeActivity();
        }
    }
}
