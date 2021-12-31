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

package io.olvid.messenger.plus_button;

import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.Navigation;

import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.webclient.WebClientManager;
import io.olvid.messenger.settings.SettingsActivity;

public class WebClientScannedFragment extends Fragment implements View.OnClickListener {
    private final WebClientServiceConnection serviceConnection = new WebClientServiceConnection();
    private EventBroadcastReceiver eventBroadcastReceiver = null;


    FragmentActivity activity;
    PlusButtonViewModel viewModel;

    LinearLayout protocolInProgress;
    LinearLayout enterSASCode;
    LinearLayout protocolSuccess;
    EditText sasCodeEditText;
    TextView sasCodeError;

    String scannedUri;

    MutableLiveData<String> calculatedSasCode; //code received from WebClientManager
    MutableLiveData<Boolean> isServiceClosed; //handle service closing during process

    Boolean isBound;
    UnifiedForegroundService.WebClientSubService webClientService; //retrieved from bounding to service
    Handler timeOutCloseActivity;
    Runnable closeActivity;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = requireActivity();
        viewModel = new ViewModelProvider(activity).get(PlusButtonViewModel.class);
        calculatedSasCode = new MutableLiveData<>();
        isServiceClosed = new MutableLiveData<>();
        isBound = false;

        eventBroadcastReceiver = new EventBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter(UnifiedForegroundService.WebClientSubService.READY_FOR_BIND_BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(activity).registerReceiver(eventBroadcastReceiver, intentFilter);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_plus_button_webclient_scanned, container, false);

        if (!viewModel.isDeepLinked()) {
            requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    Navigation.findNavController(rootView).popBackStack();
                }
            });
        }
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                if (activity.getWindow().getStatusBarColor() == 0xff000000) {
                    ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.almostWhite)).start();
                } else {
                    activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
                }
            } else {
                ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.olvid_gradient_dark)).start();
            }
        }

        this.sasCodeEditText = view.findViewById(R.id.sas_code);
        this.protocolInProgress = view.findViewById(R.id.wait_protocol_for_sas_step);
        this.enterSASCode = view.findViewById(R.id.enter_sas_code_step);
        this.protocolSuccess = view.findViewById(R.id.protocol_finished_successfully);
        this.sasCodeError = view.findViewById(R.id.error_sas_incorrect);

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                validateSasCode();
            }
        };

        sasCodeEditText.addTextChangedListener(textWatcher);
        view.findViewById(R.id.back_button).setOnClickListener(this);

        String uri = viewModel.getScannedUri();
        if (uri == null) {
            activity.finish();
            return;
        }
        scannedUri = uri;

        //start service and connect to web client. Service will run even after fragment (activity) closes.
        Intent connectIntent = new Intent(activity, UnifiedForegroundService.class);
        connectIntent.putExtra(UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA, UnifiedForegroundService.SUB_SERVICE_WEB_CLIENT);
        connectIntent.setAction(UnifiedForegroundService.WebClientSubService.ACTION_CONNECT);
        connectIntent.putExtra(UnifiedForegroundService.WebClientSubService.CONNECTION_DATA_INTENT_EXTRA, scannedUri);
        activity.startService(connectIntent);

    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity != null) {
            Window window = activity.getWindow();
            if (window != null && sasCodeEditText != null && sasCodeEditText.isFocused()) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }
    }

    private class WebClientServiceConnection implements ServiceConnection {
        public UnifiedForegroundService.ServiceBinder binder;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (UnifiedForegroundService.ServiceBinder) service;
            if (binder == null) {
                activity.onBackPressed();
                return;
            }
            webClientService = binder.getWebClientService();
            if (webClientService == null || webClientService.getManager() == null) {
                activity.onBackPressed();
                return;
            }
            isBound = true;
            if (webClientService.getManager().getCurrentState() == WebClientManager.State.ERROR
                    || webClientService.getManager().getCurrentState() == WebClientManager.State.FINISHING) {
                unBind();
                activity.onBackPressed();
                return;
            }

            // if webclient service is waiting for reconnection do not ask before erasing previous connection
            if (webClientService.isAlreadyRunning()
                    && WebClientManager.State.WAITING_FOR_RECONNECTION.equals(webClientService.getCurrentState())) {
                webClientService.restartService();
                this.launchObservers();
            } else if (webClientService.isAlreadyRunning()) {
                // if webclient was running ask for permission to erase previous connection
                AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                        .setTitle(R.string.label_webclient_already_running)
                        .setMessage(R.string.label_webclient_restart_connection)
                        .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                            ((AlertDialog) dialog).setOnDismissListener(null);
                            webClientService.restartService();
                            launchObservers();
                        })
                        .setOnDismissListener((DialogInterface dialog) -> {
                            unBind();
                            activity.onBackPressed();
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);
                builder.create().show();
                // if no webclient service was launched create it
            } else {
                this.launchObservers();
            }
        }

        private void launchObservers() {
            if (SettingsActivity.useApplicationLockScreen() && SettingsActivity.isUnlockRequiredForWebclient()) {
                UnifiedForegroundService.lockApplication(activity, R.string.message_unlock_before_web_client);
            }

            //live data for sas : get notified when sas code is generated
            calculatedSasCode = webClientService.getSasCodeLiveData();
            // Create the observer which updates the UI.
            final Observer<String> sasObserver = (String computedSasCode) -> {
                if (computedSasCode != null && !"".equals(computedSasCode)) {
                    protocolInProgress.setVisibility(View.INVISIBLE);
                    enterSASCode.setVisibility(View.VISIBLE);
                    sasCodeError.setVisibility(View.INVISIBLE);
                    sasCodeEditText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(sasCodeEditText, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            };

            if (calculatedSasCode != null && activity != null) {
                calculatedSasCode.observe(activity, sasObserver);
            }

            ///live data for service closing : get notified when service is closing due to an error
            isServiceClosed = webClientService.getServiceClosingLiveData();

            if (isServiceClosed != null && activity != null) {
                isServiceClosed.observe(activity, new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean serviceClosed) {
                        if (serviceClosed) {
                            isServiceClosed.removeObserver(this);
                            unBind();
                            activity.onBackPressed();
                        }
                    }
                });
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
            isBound = false;
            activity.onBackPressed();
        }
    }

    public void validateSasCode() {
        if (webClientService == null) {
            return;
        }
        String sas = sasCodeEditText.getText().toString();
        int count = sasCodeEditText.getText().length();
        if (count == 4) {
            if (webClientService.verifySasCode(sas)) { //WebClientManager also changes its state in the function called by verifySasCode
                enterSASCode.setVisibility(View.INVISIBLE);
                protocolSuccess.setVisibility(View.VISIBLE);
                //close activity after 3 seconds
                closeActivity = () -> {
                    unBind();
                    activity.finish();
                };
                timeOutCloseActivity = new Handler(Looper.getMainLooper());
                timeOutCloseActivity.postDelayed(closeActivity, 3000);

                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(enterSASCode.getWindowToken(), 0);
                }
            } else {
                Animation shakeAnimation = AnimationUtils.loadAnimation(activity, R.anim.shake);
                sasCodeEditText.startAnimation(shakeAnimation);
                sasCodeEditText.setSelection(0, sasCodeEditText.getText().length());
                sasCodeError.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.back_button) {
            unBind();
            activity.onBackPressed();
        }
    }

    public void unBind() {
        if (isBound && activity != null) {
            activity.unbindService(serviceConnection);
            isBound = false;
            // manually call the onUnbind method in case unbinding from the UnifiedForegroundService is not enough (onUnbind called when all connections are unbound)
            if (webClientService != null) {
                webClientService.onUnbind();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //remove callbacks in case the back button was pressed before delay
        if (timeOutCloseActivity != null) {
            timeOutCloseActivity.removeCallbacks(closeActivity);
            timeOutCloseActivity = null;
        }
        if (eventBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(activity).unregisterReceiver(eventBroadcastReceiver);
            eventBroadcastReceiver = null;
        }
        unBind();
    }

    class EventBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) {
                return;
            }
            if (UnifiedForegroundService.WebClientSubService.READY_FOR_BIND_BROADCAST_ACTION.equals(intent.getAction())) {
                //bind activity to service
                Intent bindIntent = new Intent(activity, UnifiedForegroundService.class);
                bindIntent.putExtra(UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA, UnifiedForegroundService.SUB_SERVICE_WEB_CLIENT);
                activity.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);

                // unregister receiver as soon as one broadcast intent is received
                if (eventBroadcastReceiver != null) {
                    LocalBroadcastManager.getInstance(activity).unregisterReceiver(eventBroadcastReceiver);
                    eventBroadcastReceiver = null;
                }
            }
        }
    }
}