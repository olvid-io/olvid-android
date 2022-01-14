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

package io.olvid.messenger.webrtc;


import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.Observer;

import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.entity.Contact;

public class WebrtcIncomingCallActivity extends AppCompatActivity implements View.OnClickListener {

    WebrtcServiceConnection webrtcServiceConnection;
    WebrtcCallService webrtcCallService = null;
    CallParticipantsObserver callParticipantsObserver;
    CallStatusObserver callStatusObserver;

    InitialView contactInitialView;
    TextView contactNameTextView;
    TextView othersCountTextView;
    TextView bigCountTextView;
    View rejectCallButton;
    View answerCallButton;

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

        setContentView(R.layout.activity_webrtc_incoming_call);

        rejectCallButton = findViewById(R.id.reject_call_button);
        rejectCallButton.setOnClickListener(this);

        answerCallButton = findViewById(R.id.accept_call_button);
        answerCallButton.setOnClickListener(this);

        contactInitialView = findViewById(R.id.contact_initial_view);
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
                answerCallIntent.putExtra(WebrtcCallActivity.ANSWER_CALL_EXTRA_CALL_IDENTIFIER, webrtcCallService.callIdentifier.toString());
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
            final Contact callParticipanContact = (callParticipants == null || callParticipants.size() == 0) ? null : callParticipants.get(0).contact;
            if (callParticipanContact == null) {
                contactInitialView.setVisibility(View.INVISIBLE);
                contactNameTextView.setText(null);
                othersCountTextView.setVisibility(View.GONE);
                bigCountTextView.setVisibility(View.GONE);
            } else {
                contactInitialView.setVisibility(View.VISIBLE);
                contactInitialView.setKeycloakCertified(callParticipanContact.keycloakManaged);
                contactInitialView.setInactive(!callParticipanContact.active);
                if (callParticipanContact.getCustomPhotoUrl() != null) {
                    contactInitialView.setPhotoUrl(callParticipanContact.bytesContactIdentity, callParticipanContact.getCustomPhotoUrl());
                } else {
                    contactInitialView.setInitial(callParticipanContact.bytesContactIdentity, App.getInitial(callParticipanContact.getCustomDisplayName()));
                }
                contactNameTextView.setText(callParticipanContact.getCustomDisplayName());
                if (webrtcCallService != null) {
                    int count = webrtcCallService.getIncomingParticipantCount() - 1;
                    if (count > 0) {
                        othersCountTextView.setVisibility(View.VISIBLE);
                        othersCountTextView.setText(getResources().getQuantityString(R.plurals.notification_text_incoming_call_participant_count, count, count));
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
            if (this.webrtcCallService.getCallParticipantsLiveData().getValue() == null || this.webrtcCallService.getCallParticipantsLiveData().getValue().size() == 0) {
                // no participants --> hang up the call
                closeActivity();
            }
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
                Logger.e("☎️ Bound to bad service!!!");
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
