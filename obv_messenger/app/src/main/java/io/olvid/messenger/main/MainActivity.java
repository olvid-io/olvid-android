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

package io.olvid.messenger.main;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.inputmethod.EditorInfoCompat;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.fragment.app.FragmentOnAttachListener;
import androidx.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import androidx.fragment.app.FragmentPagerAdapter;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.viewpager.widget.ViewPager;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.ObvBase64;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.ObvBackupKeyInformation;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.messenger.activities.storage_manager.StorageManagerActivity;
import io.olvid.messenger.activities.CallLogActivity;
import io.olvid.messenger.discussion.DiscussionActivity;
import io.olvid.messenger.activities.ObvLinkActivity;
import io.olvid.messenger.activities.OwnedIdentityDetailsActivity;
import io.olvid.messenger.customClasses.ConfigurationPojo;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.fragments.dialog.OwnedIdentitySelectionDialogFragment;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.fragments.dialog.DiscussionSearchDialogFragment;
import io.olvid.messenger.onboarding.OnboardingActivity;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.plus_button.PlusButtonActivity;
import io.olvid.messenger.settings.SettingsActivity;

public class MainActivity extends LockableActivity implements View.OnClickListener, FragmentOnAttachListener {
    private CoordinatorLayout root;
    private InitialView ownInitialView;
    private View pingConnectivityDot;
    private View pingConnectivityLine;
    private View pingConnectivityFull;
    private TextView pingConnectivityFullTextView;
    private TextView pingConnectivityFullPingTextView;
    private TabsPagerAdapter tabsPagerAdapter;
    private ViewPager viewPager;
    private MainActivityPageChangeListener mainActivityPageChangeListener;
    private PingListener pingListener;

    private int pingRed;
    private int pingGolden;
    private int pingGreen;
    private int pingGrey;

    private ContactListFragment contactListFragment;
    private InvitationListFragment invitationListFragment;


    ImageView[] tabImageViews;

    public static final String LINK_ACTION = "link_action";
    public static final String LINK_URI_INTENT_EXTRA = "link_uri";
    public static final String FORWARD_ACTION = "forward_action";
    public static final String FORWARD_TO_INTENT_EXTRA = "forward_to";
    public static final String TAB_TO_SHOW_INTENT_EXTRA = "tab_to_show";
    public static final String ALREADY_CREATED_BUNDLE_EXTRA = "already_created";
    public static final String KEYCLOAK_AUTHENTICATION_NEEDED_EXTRA = "keycloak_authentication_needed";
    public static final String BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA = "owned_identity";
    public static final String HEX_STRING_BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA = "hex_owned_identity";
    public static final String NETWORK_CONNECTIVITY_CHANGED_BROADCAST_ACTION = "network_connectivity_changed_broadcast_action";

    private static final Set<String> ALLOWED_FORWARD_TO_VALUES = new HashSet<>(Arrays.asList(
            DiscussionActivity.class.getName(),
            OwnedIdentityDetailsActivity.class.getName(),
            PlusButtonActivity.class.getName(),
            OnboardingActivity.class.getName(),
            SettingsActivity.class.getName()
    ));

    public static final int DISCUSSIONS_TAB = 0;
    public static final int CONTACTS_TAB = 1;
    public static final int GROUPS_TAB = 2;
    public static final int INVITATIONS_TAB = 3;

    private static float previousFontScale = 1.0f;

    @Override
    protected void attachBaseContext(Context baseContext) {
        // problem: we cannot know if the baseContext we get has already been scaled by our setting or not
        // solution 1: ignore the system scaling and simply override the fontScale --> not very satisfying
        // solution 2: "mark" the fontScale by forcing its customized value to have a 17 in the third & fourth decimal place (like 1.5017) --> much more fun!
        final Context newContext;
        float customFontScale = SettingsActivity.getFontScale();
        Configuration configuration = baseContext.getResources().getConfiguration();
        Logger.d("⚖ MainActivity started, baseContext font scale: " + configuration.fontScale);
        if (Math.round(configuration.fontScale * 10000) % 100 == 17) {
            Logger.d("⚖ case 1: fontScale already \"marked\"");
            if (customFontScale != previousFontScale) { // this detects changes in the settings
                Logger.d("⚖ case 1.1: customFontScale != previousFontScale --> compensating");
                configuration.fontScale *= customFontScale / previousFontScale;
                previousFontScale = customFontScale;
                // we re-"mark" the font scale
                configuration.fontScale = (Math.round(configuration.fontScale * 100) + 0.17f)/100f;
                newContext = baseContext.createConfigurationContext(configuration);
            } else {
                Logger.d("⚖ case 1.2: fontScale already \"marked\" with the right scale --> do nothing");
                // fontScale is already marked --> do nothing
                newContext = baseContext;
            }
        } else {
            Logger.d("⚖ case 2: fontScale not \"marked\" --> scale it");
            // fontScale not marked --> this is most probably the default system scaling
            // - we scale it appropriately
            // - we mark it
            if (customFontScale != previousFontScale) { // this detects changes in the settings, or the first launch of the app
                previousFontScale = customFontScale;
            }
            configuration.fontScale *= customFontScale;
            configuration.fontScale = (Math.round(configuration.fontScale * 100) + 0.17f)/100f;
            newContext = baseContext.createConfigurationContext(configuration);
        }
        Logger.d("⚖ MainActivity started, font scale: " + newContext.getResources().getConfiguration().fontScale);
        super.attachBaseContext(newContext);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        App.runThread(() -> {
            int identityCount;
            try {
                identityCount = AppSingleton.getEngine().getOwnedIdentities().length;
            } catch (Exception e) {
                // if we fail to query ownIdentity count from Engine, fallback to querying on App side
                identityCount = AppDatabase.getInstance().ownedIdentityDao().countAll();
            }
            if (identityCount == 0) {
                Intent onboardingIntent = new Intent(getApplicationContext(), OnboardingActivity.class);
                onboardingIntent.putExtra(OnboardingActivity.FIRST_ID_INTENT_EXTRA, true);
                startActivity(onboardingIntent);
                finish();
            } else {
                runOnUiThread(() -> Utils.showDialogs(this));
            }
        });
        try {
            SplashScreen.installSplashScreen(this);
        } catch (Exception e) {
            setTheme(R.style.AppTheme_NoActionBar);
        }
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root_coordinator);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }

        ownInitialView = findViewById(R.id.owned_identity_initial_view);
        if (ownInitialView != null) {
            ownInitialView.setOnClickListener(v -> new OwnIdentitySelectorPopupWindow(this, ownInitialView).open());
        }

        pingConnectivityDot = findViewById(R.id.ping_indicator_dot);
        pingConnectivityLine = findViewById(R.id.ping_indicator_line);
        pingConnectivityFull = findViewById(R.id.ping_indicator_full);
        pingConnectivityFullTextView = findViewById(R.id.ping_indicator_full_text_view);
        pingConnectivityFullPingTextView = findViewById(R.id.ping_indicator_full_ping_text_view);

        pingRed = ContextCompat.getColor(this, R.color.red);
        pingGolden = ContextCompat.getColor(this, R.color.golden);
        pingGreen = ContextCompat.getColor(this, R.color.green);
        pingGrey = ContextCompat.getColor(this, R.color.greyTint);

        pingListener = new PingListener();
        AppSingleton.getWebsocketConnectivityStateLiveData().observe(this, pingListener);

        tabsPagerAdapter = new TabsPagerAdapter(getSupportFragmentManager(),
                findViewById(R.id.tab_discussions_notification_dot),
                findViewById(R.id.tab_contacts_notification_dot),
                findViewById(R.id.tab_groups_notification_dot),
                findViewById(R.id.tab_invitations_notification_dot));

        tabImageViews = new ImageView[4];
        tabImageViews[0] = findViewById(R.id.tab_discussions_button);
        tabImageViews[1] = findViewById(R.id.tab_contacts_button);
        tabImageViews[2] = findViewById(R.id.tab_groups_button);
        tabImageViews[3] = findViewById(R.id.tab_invitations_button);
        for (ImageView imageView: tabImageViews) {
            if (imageView != null) {
                imageView.setOnClickListener(this);
            }
        }

        mainActivityPageChangeListener = new MainActivityPageChangeListener(tabImageViews);

        viewPager = findViewById(R.id.view_pager_container);
        viewPager.setAdapter(tabsPagerAdapter);
        viewPager.addOnPageChangeListener(mainActivityPageChangeListener);
        viewPager.setPageMargin(getResources().getDimensionPixelSize(R.dimen.main_activity_page_margin));
        viewPager.setOffscreenPageLimit(3);

        getSupportFragmentManager().addFragmentOnAttachListener(this);

        ImageView addContactButton = findViewById(R.id.tab_plus_button);
        addContactButton.setOnClickListener(this);

        View focusHugger = findViewById(R.id.focus_hugger);
        focusHugger.requestFocus();

        // observe owned Identity (for initial view)
        ImageView ownedIdentityMutedImageView = findViewById(R.id.owned_identity_muted_marker_image_view);
        AppSingleton.getCurrentIdentityLiveData().observe(this, (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                App.runThread(() -> {
                    if (AppDatabase.getInstance().ownedIdentityDao().countAll() == 0) {
                        Intent onboardingIntent = new Intent(getApplicationContext(), OnboardingActivity.class);
                        onboardingIntent.putExtra(OnboardingActivity.FIRST_ID_INTENT_EXTRA, true);
                        startActivity(onboardingIntent);
                        finish();
                    }
                });
                ownInitialView.setInactive(false);
                ownInitialView.setKeycloakCertified(false);
                ownInitialView.setInitial(new byte[0], " ");
                ownedIdentityMutedImageView.setVisibility(View.GONE);
                return;
            }
            ownInitialView.setKeycloakCertified(ownedIdentity.keycloakManaged);
            ownInitialView.setInactive(!ownedIdentity.active);
            if (ownedIdentity.photoUrl == null) {
                ownInitialView.setInitial(ownedIdentity.bytesOwnedIdentity, App.getInitial(ownedIdentity.getCustomDisplayName()));
            } else {
                ownInitialView.setPhotoUrl(ownedIdentity.bytesOwnedIdentity, ownedIdentity.photoUrl);
            }

            if (ownedIdentity.shouldMuteNotifications()) {
                ownedIdentityMutedImageView.setVisibility(View.VISIBLE);
            } else {
                ownedIdentityMutedImageView.setVisibility(View.GONE);
            }

            if (!ownedIdentity.keycloakManaged) {
                Utils.verifyPurchases(ownedIdentity.bytesOwnedIdentity, getBaseContext());
            }
        });

        // observe unread messages
        Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().messageDao().hasUnreadMessages(ownedIdentity.bytesOwnedIdentity);
        }).observe(this, unreadMessages -> {
            if (unreadMessages != null && unreadMessages) {
                tabsPagerAdapter.showNotificationDot(DISCUSSIONS_TAB);
            } else {
                tabsPagerAdapter.hideNotificationDot(DISCUSSIONS_TAB);
            }
        });

        // observe invitations
        Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().invitationDao().getAll(ownedIdentity.bytesOwnedIdentity);
        }).observe(this, invitations -> {
            if (invitations != null) {
                for (Invitation invitation: invitations) {
                    switch (invitation.associatedDialog.getCategory().getId()) {
                        case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                        case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY:
                        case ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY:
                        case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                        case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
                        case ObvDialog.Category.INCREASE_MEDIATOR_TRUST_LEVEL_DIALOG_CATEGORY:
                        case ObvDialog.Category.INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_CATEGORY:
                        case ObvDialog.Category.AUTO_CONFIRMED_CONTACT_INTRODUCTION_DIALOG_CATEGORY:
                            tabsPagerAdapter.showNotificationDot(INVITATIONS_TAB);
                            return;
                    }
                }
            }
            tabsPagerAdapter.hideNotificationDot(INVITATIONS_TAB);
        });

        ImageView unreadMarker = findViewById(R.id.owned_identity_unread_marker_image_view);
        Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().ownedIdentityDao().otherNotHiddenOwnedIdentityHasMessageOrInvitation(ownedIdentity.bytesOwnedIdentity);
        }).observe(this, (Boolean hasUnread) -> {
            if (hasUnread != null && hasUnread) {
                unreadMarker.setVisibility(View.VISIBLE);
            } else {
                unreadMarker.setVisibility(View.GONE);
            }
        });

        showReminderSnackBarIfNeeded();


        if (savedInstanceState != null && savedInstanceState.getBoolean(ALREADY_CREATED_BUNDLE_EXTRA, false)) {
            setIntent(null);
        } else {
            finishAndRemoveExtraTasks();
        }

        handleIntent(getIntent());
    }

    private void showReminderSnackBarIfNeeded() {
        App.runThread(() -> {
            Snackbar snackbar = null;
            int dialogMessageResourceId = -1;
            int dialogTitleResourceId = -1;
            try {
                long lastReminderTimestamp = SettingsActivity.getLastBackupReminderTimestamp();
                if (AppDatabase.getInstance().contactDao().countAll() == 0) {
                    // no contacts --> do nothing
                    return;
                }

                ObvBackupKeyInformation info;
                try {
                    info = AppSingleton.getEngine().getBackupKeyInformation();
                } catch (Exception e) {
                    // this will be retried the next time MainActivity is started
                    Logger.e("Unable to retrieve backup info");
                    return;
                }

                if (info == null) {
                    if (((lastReminderTimestamp + 7 * 86_400_000L) < System.currentTimeMillis())) {
                        // no backup key generated
                        snackbar = Snackbar.make(root, R.string.snackbar_message_setup_backup, BaseTransientBottomBar.LENGTH_INDEFINITE);
                        dialogMessageResourceId = R.string.dialog_message_setup_backup_explanation;
                        dialogTitleResourceId = R.string.dialog_title_setup_backup_explanation;
                    }
                    return;
                }

                if (((lastReminderTimestamp + 7 * 86_400_000L) < System.currentTimeMillis()) &&
                        !SettingsActivity.useAutomaticBackup() &&
                        ((info.lastBackupExport + 7 * 86_400_000L) < System.currentTimeMillis())) {
                    // no automatic backups, and no backups since more that a week
                    snackbar = Snackbar.make(root, R.string.snackbar_message_remember_to_backup, BaseTransientBottomBar.LENGTH_INDEFINITE);
                    dialogTitleResourceId = R.string.snackbar_message_remember_to_backup;
                    if (ConnectionResult.SUCCESS == GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)) {
                        dialogMessageResourceId = R.string.dialog_message_remember_to_backup_explanation;
                    } else {
                        dialogMessageResourceId = R.string.dialog_message_remember_to_backup_explanation_no_google;
                    }
                    return;
                }

                if (((lastReminderTimestamp + 7 * 86_400_000L) < System.currentTimeMillis()) &&
                            ((info.keyGenerationTimestamp + 14 * 86_400_000L) < System.currentTimeMillis()) &&
                            ((info.lastSuccessfulKeyVerificationTimestamp + 30 * 86_400_000L) < System.currentTimeMillis())) {
                    // all backup stuff is good, but key not verified since more than 30 days
                    snackbar = Snackbar.make(root, R.string.snackbar_message_verify_backup_key, BaseTransientBottomBar.LENGTH_INDEFINITE);
                    dialogTitleResourceId = R.string.snackbar_message_verify_backup_key;
                    dialogMessageResourceId = R.string.dialog_message_verify_backup_key_explanation;
                    //noinspection UnnecessaryReturnStatement
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (snackbar != null) {
                    int finalDialogMessageResourceId = dialogMessageResourceId;
                    int finalDialogTitleResourceId = dialogTitleResourceId;
                    Snackbar finalSnackBar = snackbar;

                    snackbar.setAction(R.string.button_label_show_me, v -> {
                        AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                                .setTitle(finalDialogTitleResourceId)
                                .setMessage(finalDialogMessageResourceId)
                                .setPositiveButton(R.string.button_label_backup_settings, (dialog, which) -> {
                                    Intent intent = new Intent(this, SettingsActivity.class);
                                    intent.putExtra(SettingsActivity.SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA, SettingsActivity.PREF_HEADER_KEY_BACKUP);
                                    startActivity(intent);
                                })
                                .setNegativeButton(R.string.button_label_remind_me_later, (dialog, which) -> SettingsActivity.setLastBackupReminderTimestamp(System.currentTimeMillis()));
                        builder.create().show();
                    });
                    //noinspection Convert2MethodRef
                    new Handler(Looper.getMainLooper()).post(() -> finalSnackBar.show());
                }
            }
        });

    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ALREADY_CREATED_BUNDLE_EXTRA, true);
    }

    private void handleIntent(@Nullable Intent intent) {
        AndroidNotificationManager.clearNeutralNotification();
        if (intent == null) {
            return;
        }


        byte[] bytesOwnedIdentityToSelect;
        String hexBytesOwnedIdentityToSelect = intent.getStringExtra(HEX_STRING_BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA);
        if (hexBytesOwnedIdentityToSelect != null) {
            try {
                bytesOwnedIdentityToSelect = Logger.fromHexString(hexBytesOwnedIdentityToSelect);
            } catch (Exception e) {
                bytesOwnedIdentityToSelect = null;
            }
        } else {
            bytesOwnedIdentityToSelect = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA);
        }
        if (bytesOwnedIdentityToSelect != null) {
            // if a profile switch is required, execute only once the identity is switched
            intent.removeExtra(BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA);
            AppSingleton.getInstance().selectIdentity(bytesOwnedIdentityToSelect, (OwnedIdentity ownedIdentity) -> executeIntentAction(intent));
        } else {
            // otherwise execute intent action instantly
            executeIntentAction(intent);
        }

        byte[] ownedIdentityRequiringAuthentication = intent.getByteArrayExtra(KEYCLOAK_AUTHENTICATION_NEEDED_EXTRA);
        if (ownedIdentityRequiringAuthentication != null) {
            AppSingleton.getInstance().selectIdentity(ownedIdentityRequiringAuthentication, (OwnedIdentity ownedIdentity) -> KeycloakManager.forceSelfTestAndReauthentication(ownedIdentityRequiringAuthentication));
        }

        int tabToShow = intent.getIntExtra(TAB_TO_SHOW_INTENT_EXTRA, -1);
        if (tabToShow != -1) {
            intent.removeExtra(TAB_TO_SHOW_INTENT_EXTRA);
            viewPager.setCurrentItem(tabToShow);
        }
    }

    private void executeIntentAction(Intent intent) {
        if (intent.getAction() == null) {
            return;
        }
        switch (intent.getAction()) {
            case FORWARD_ACTION: {
                String forwardTo = intent.getStringExtra(FORWARD_TO_INTENT_EXTRA);
                if (forwardTo != null && ALLOWED_FORWARD_TO_VALUES.contains(forwardTo)) {
                    Intent forwardIntent = new Intent();
                    forwardIntent.setClassName(this, forwardTo);
                    if (intent.getExtras() != null) {
                        forwardIntent.putExtras(intent.getExtras());
                    }
                    startActivity(forwardIntent);
                }
                break;
            }
            case LINK_ACTION: {
                // first detect the type of link to show an appropriate dialog
                String uri = intent.getStringExtra(LINK_URI_INTENT_EXTRA);
                if (uri != null) {
                    Matcher configurationMatcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(uri);
                    if (configurationMatcher.find()) {
                        try {
                            // detect if it is a licence or a keycloak
                            ConfigurationPojo configurationPojo = AppSingleton.getJsonObjectMapper().readValue(ObvBase64.decode(configurationMatcher.group(2)), ConfigurationPojo.class);
                            final int dialogTitleResourceId;
                            if (configurationPojo.getKeycloak() != null) {
                                // offer to chose a profile or create a new one for profile binding
                                dialogTitleResourceId = R.string.dialog_title_chose_profile_keycloak_configuration;
                            } else {
                                // offer to chose a profile or create a new one for licence activation or configuration
                                dialogTitleResourceId = R.string.dialog_title_chose_profile_configuration;
                            }

                            OwnedIdentitySelectionDialogFragment ownedIdentitySelectionDialogFragment = OwnedIdentitySelectionDialogFragment.newInstance(this, dialogTitleResourceId, new OwnedIdentitySelectionDialogFragment.OnOwnedIdentitySelectedListener() {
                                @Override
                                public void onOwnedIdentitySelected(@NonNull byte[] bytesOwnedIdentity) {
                                    AppSingleton.getInstance().selectIdentity(bytesOwnedIdentity, ownedIdentity -> {
                                        Intent plusIntent = new Intent(MainActivity.this, PlusButtonActivity.class);
                                        plusIntent.putExtra(PlusButtonActivity.LINK_URI_INTENT_EXTRA, uri);
                                        startActivity(plusIntent);
                                    });
                                }

                                @Override
                                public void onNewProfileCreationSelected() {
                                    Intent onboardingIntent = new Intent(MainActivity.this, OnboardingActivity.class);
                                    onboardingIntent.putExtra(OnboardingActivity.FIRST_ID_INTENT_EXTRA, false);
                                    onboardingIntent.putExtra(PlusButtonActivity.LINK_URI_INTENT_EXTRA, uri);
                                    startActivity(onboardingIntent);
                                }
                            });
                            ownedIdentitySelectionDialogFragment.show(getSupportFragmentManager(), "ownedIdentitySelectionDialogFragment");
                        } catch (Exception e) {
                            // nothing to do
                            e.printStackTrace();
                        }
                    } else if (ObvLinkActivity.INVITATION_PATTERN.matcher(uri).find()) {
                        // offer to chose a profile
                        OwnedIdentitySelectionDialogFragment ownedIdentitySelectionDialogFragment = OwnedIdentitySelectionDialogFragment.newInstance(this, R.string.dialog_title_chose_profile_invitation, new OwnedIdentitySelectionDialogFragment.OnOwnedIdentitySelectedListener() {
                            @Override
                            public void onOwnedIdentitySelected(@NonNull byte[] bytesOwnedIdentity) {
                                AppSingleton.getInstance().selectIdentity(bytesOwnedIdentity, ownedIdentity -> {
                                    Intent plusIntent = new Intent(MainActivity.this, PlusButtonActivity.class);
                                    plusIntent.putExtra(PlusButtonActivity.LINK_URI_INTENT_EXTRA, uri);
                                    startActivity(plusIntent);
                                });
                            }

                            @Override
                            public void onNewProfileCreationSelected() {
                                Intent onboardingIntent = new Intent(MainActivity.this, OnboardingActivity.class);
                                onboardingIntent.putExtra(OnboardingActivity.FIRST_ID_INTENT_EXTRA, false);
                                onboardingIntent.putExtra(OnboardingActivity.LINK_URI_INTENT_EXTRA, uri);
                                startActivity(onboardingIntent);
                            }
                        });
                        ownedIdentitySelectionDialogFragment.show(getSupportFragmentManager(), "ownedIdentitySelectionDialogFragment");
                    } else if (ObvLinkActivity.WEB_CLIENT_PATTERN.matcher(uri).find()) {
                        // offer to chose a profile
                        OwnedIdentitySelectionDialogFragment ownedIdentitySelectionDialogFragment = OwnedIdentitySelectionDialogFragment.newInstance(this, R.string.dialog_title_chose_profile_web_client, new OwnedIdentitySelectionDialogFragment.OnOwnedIdentitySelectedListener() {
                            @Override
                            public void onOwnedIdentitySelected(@NonNull byte[] bytesOwnedIdentity) {
                                AppSingleton.getInstance().selectIdentity(bytesOwnedIdentity, ownedIdentity -> {
                                    Intent plusIntent = new Intent(MainActivity.this, PlusButtonActivity.class);
                                    plusIntent.putExtra(PlusButtonActivity.LINK_URI_INTENT_EXTRA, uri);
                                    startActivity(plusIntent);
                                });
                            }

                            @Override
                            public void onNewProfileCreationSelected() {
                                Intent onboardingIntent = new Intent(MainActivity.this, OnboardingActivity.class);
                                onboardingIntent.putExtra(OnboardingActivity.FIRST_ID_INTENT_EXTRA, false);
                                startActivity(onboardingIntent);
                                App.toast(R.string.toast_message_create_new_profile_then_reopen_this_link, Toast.LENGTH_SHORT);
                            }
                        });
                        ownedIdentitySelectionDialogFragment.show(getSupportFragmentManager(), "ownedIdentitySelectionDialogFragment");
                    }
                }
                break;
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getSupportFragmentManager().removeFragmentOnAttachListener(this);
        viewPager = null;
        tabsPagerAdapter = null;
        contactListFragment = null;
        invitationListFragment = null;
    }

    public class TabsPagerAdapter extends FragmentPagerAdapter {
        final View[] notificationDots;

        TabsPagerAdapter(FragmentManager fm, View... notificationDots) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.notificationDots = notificationDots;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case DISCUSSIONS_TAB: {
                    return new DiscussionListFragment();
                }
                case CONTACTS_TAB: {
                    return new ContactListFragment();
                }
                case GROUPS_TAB: {
                    return new GroupListFragment();
                }
                case INVITATIONS_TAB:
                default: {
                    return new InvitationListFragment();
                }
            }
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (position == CONTACTS_TAB) {
                if (fragment instanceof ContactListFragment) {
                    contactListFragment = (ContactListFragment) fragment;
                }
            } else if (position == INVITATIONS_TAB) {
                if (fragment instanceof InvitationListFragment) {
                    invitationListFragment = (InvitationListFragment) fragment;
                }
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return 4;
        }

        void showNotificationDot(int position) {
            if (position<0 || position >= getCount()) {
                return;
            }
            notificationDots[position].setVisibility(View.VISIBLE);
        }

        void hideNotificationDot(int position) {
            if (position<0 || position >= getCount()) {
                return;
            }
            notificationDots[position].setVisibility(View.GONE);
        }
    }

    @NonNull private SettingsActivity.PingConnectivityIndicator previousPingConnectivityIndicator = SettingsActivity.PingConnectivityIndicator.NONE;

    private void showPingIndicator(SettingsActivity.PingConnectivityIndicator pingConnectivityIndicator) {
        if (Objects.equals(pingConnectivityIndicator, previousPingConnectivityIndicator)) {
            return;
        }
        previousPingConnectivityIndicator = pingConnectivityIndicator;
        switch (pingConnectivityIndicator) {
            case NONE:
                pingConnectivityDot.setVisibility(View.GONE);
                pingConnectivityLine.setVisibility(View.GONE);
                pingConnectivityFull.setVisibility(View.GONE);
                break;
            case DOT:
                pingConnectivityDot.setVisibility(View.VISIBLE);
                pingConnectivityLine.setVisibility(View.GONE);
                pingConnectivityFull.setVisibility(View.GONE);
                break;
            case LINE:
                pingConnectivityDot.setVisibility(View.GONE);
                pingConnectivityLine.setVisibility(View.VISIBLE);
                pingConnectivityFull.setVisibility(View.GONE);
                break;
            case FULL:
                pingConnectivityDot.setVisibility(View.GONE);
                pingConnectivityLine.setVisibility(View.GONE);
                pingConnectivityFull.setVisibility(View.VISIBLE);
                pingConnectivityFullPingTextView.setText(null);
                break;
        }
    }



    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && AppSingleton.getBytesCurrentIdentity() != null) {
            Utils.showDialogs(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (invitationListFragment != null) {
            invitationListFragment.setInvitationsAreVisible(false);
        }
        Utils.stopPinging();
        if (previousPingConnectivityIndicator != SettingsActivity.PingConnectivityIndicator.NONE) {
            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.PING_LOST, pingListener);
            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.PING_RECEIVED, pingListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mainActivityPageChangeListener.onPageSelected(viewPager.getCurrentItem());
        SettingsActivity.PingConnectivityIndicator pingSetting = SettingsActivity.getPingConnectivityIndicator();
        if (previousPingConnectivityIndicator != SettingsActivity.PingConnectivityIndicator.NONE || pingSetting != SettingsActivity.PingConnectivityIndicator.NONE) {
            showPingIndicator(pingSetting);
            if (pingSetting != SettingsActivity.PingConnectivityIndicator.NONE) {
                pingListener.refresh();
                Utils.startPinging();
                AppSingleton.getEngine().addNotificationListener(EngineNotifications.PING_LOST, pingListener);
                AppSingleton.getEngine().addNotificationListener(EngineNotifications.PING_RECEIVED, pingListener);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if(!moveTaskToBack(true)) {
            finishAndRemoveTask();
        }
    }

    private void finishAndRemoveExtraTasks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                List<ActivityManager.AppTask> appTasks = activityManager.getAppTasks();
                if (appTasks != null && appTasks.size() > 1) {
                    for (ActivityManager.AppTask appTask : appTasks) {
                        ActivityManager.RecentTaskInfo taskInfo = appTask.getTaskInfo();
                        if (!taskInfo.isRunning) {
                            Logger.d("Removing empty task");
                            appTask.finishAndRemoveTask();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        if ((viewPager != null) && (mainActivityPageChangeListener != null)) {
            mainActivityPageChangeListener.onPageSelected(viewPager.getCurrentItem());
        }
    }


    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        if (viewPager != null) {
            if (viewPager.getCurrentItem() == CONTACTS_TAB) {
                getMenuInflater().inflate(R.menu.menu_main_contact_list, menu);
                final SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
                searchView.setQueryHint(getString(R.string.hint_search_contact_name));
                searchView.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_FILTER);
                if (SettingsActivity.useKeyboardIncognitoMode()) {
                    searchView.setImeOptions(searchView.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
                }
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    final EditText editText = new AppCompatEditText(searchView.getContext());

                    {
                        if (contactListFragment != null) {
                            contactListFragment.setContactFilterEditText(editText);
                        }
                    }

                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        editText.setText(newText);
                        return true;
                    }
                });
            } else if (viewPager.getCurrentItem() == DISCUSSIONS_TAB) {
                getMenuInflater().inflate(R.menu.menu_main_discussion_list, menu);
            } else if (viewPager.getCurrentItem() == GROUPS_TAB) {
                getMenuInflater().inflate(R.menu.menu_main_group_list, menu);
            }
        }
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_storage_management) {
            startActivity(new Intent(this, StorageManagerActivity.class));
        } else if (itemId == R.id.action_about) {
            AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog);
            List<String> extraFeatures = new ArrayList<>();
            if (SettingsActivity.getBetaFeaturesEnabled()) {
                extraFeatures.add("beta");
            }
            int uptimeSeconds = (int) ((System.currentTimeMillis() - App.appStartTimestamp) / 1000);
            final String uptime;
            if (uptimeSeconds > 86400) {
                uptime = getResources().getQuantityString(R.plurals.text_app_uptime_days, uptimeSeconds / 86400, uptimeSeconds / 86400, (uptimeSeconds % 86400) / 3600, (uptimeSeconds % 3600) / 60, uptimeSeconds % 60);
            } else if (uptimeSeconds > 3600) {
                uptime = getString(R.string.text_app_uptime_hours, uptimeSeconds / 3600, (uptimeSeconds % 3600) / 60, uptimeSeconds % 60);
            } else {
                uptime = getString(R.string.text_app_uptime, uptimeSeconds / 60, uptimeSeconds % 60);
            }
            builder.setTitle(R.string.dialog_title_about_olvid)
                    .setPositiveButton(R.string.button_label_ok, null);
            StringBuilder sb = new StringBuilder();
            if (extraFeatures.isEmpty() || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                sb.append(getString(R.string.dialog_message_about_olvid, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Constants.SERVER_API_VERSION, Constants.CURRENT_ENGINE_DB_SCHEMA_VERSION, AppDatabase.DB_SCHEMA_VERSION, uptime));
            } else {
                String features = String.join(getString(R.string.text_contact_names_separator), extraFeatures);
                sb.append(getString(R.string.dialog_message_about_olvid_extra_features, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Constants.SERVER_API_VERSION, Constants.CURRENT_ENGINE_DB_SCHEMA_VERSION, AppDatabase.DB_SCHEMA_VERSION, features, uptime));
            }
            sb.append("\n\n");

            String link = getString(R.string.activity_title_open_source_licenses);
            sb.append(link);
            SpannableString spannableString = new SpannableString(sb);
            spannableString.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    OssLicensesMenuActivity.setActivityTitle(getString(R.string.activity_title_open_source_licenses));
                    startActivity(new Intent(MainActivity.this, OssLicensesMenuActivity.class));
                }
            }, spannableString.length()-link.length(), spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setMessage(spannableString);
            Dialog dialog = builder.create();

            dialog.setOnShowListener(dialog1 -> {
                if (dialog1 instanceof AlertDialog) {
                    TextView messageTextView = ((AlertDialog) dialog1).findViewById(android.R.id.message);
                    if (messageTextView != null) {
                        messageTextView.setMovementMethod(LinkMovementMethod.getInstance());
                    }
                }
            });
            dialog.show();

            return true;
        } else if (itemId == R.id.action_backup_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(SettingsActivity.SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA, SettingsActivity.PREF_HEADER_KEY_BACKUP);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_check_update) {
            final String appPackageName = getPackageName();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
            }
            return true;
        } else if (itemId == R.id.action_help_faq) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://olvid.io/faq/")));
            return true;
        } else if (itemId == R.id.action_call_log) {
            startActivity(new Intent(this, CallLogActivity.class));
            return true;
        } else if (itemId == R.id.action_add) {
            App.openGroupCreationActivity(this);
            return true;
        } else if (itemId == R.id.action_search) {
            if (viewPager.getCurrentItem() == DISCUSSIONS_TAB) {
                DiscussionSearchDialogFragment discussionSearchDialogFragment = DiscussionSearchDialogFragment.newInstance();
                discussionSearchDialogFragment.show(getSupportFragmentManager(), "dialog");
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.tab_plus_button) {
            startActivity(new Intent(this, PlusButtonActivity.class));
        } else if (id == R.id.tab_discussions_button) {
            viewPager.setCurrentItem(DISCUSSIONS_TAB);
        } else if (id == R.id.tab_contacts_button) {
            viewPager.setCurrentItem(CONTACTS_TAB);
        } else if (id == R.id.tab_groups_button) {
            viewPager.setCurrentItem(GROUPS_TAB);
        } else if (id == R.id.tab_invitations_button) {
            viewPager.setCurrentItem(INVITATIONS_TAB);
        }
    }

    class PingListener implements EngineNotificationListener, Observer<Integer> {
        private Long registrationNumber = null;
        private int websocketConnectionState = 0;

        public void refresh() {
            onChanged(websocketConnectionState);
        }

        @Override
        public void onChanged(Integer websocketConnectionState) {
            if (websocketConnectionState == -1 && this.websocketConnectionState == 0) {
                return;
            }
            this.websocketConnectionState = websocketConnectionState;

            final int stateColor;
            switch (websocketConnectionState) {
                case 1:
                    stateColor = pingGolden;
                    break;
                case 2:
                    stateColor = pingGreen;
                    break;
                case -1:
                    stateColor = pingGrey;
                    break;
                case 0:
                default:
                    stateColor = pingRed;
                    break;
            }

            switch (previousPingConnectivityIndicator) {
                case NONE:
                    break;
                case DOT:
                    pingConnectivityDot.setBackgroundColor(stateColor);
                    break;
                case LINE:
                    pingConnectivityLine.setBackgroundColor(stateColor);
                    break;
                case FULL:
                    pingConnectivityFull.setBackgroundColor(stateColor);
                    switch (websocketConnectionState) {
                        case -1:
                            // ping was lost --> keep previous connectivity state
                            break;
                        case 1:
                            pingConnectivityFullTextView.setText(R.string.label_ping_connectivity_connecting);
                            break;
                        case 2:
                            pingConnectivityFullTextView.setText(R.string.label_ping_connectivity_connected);
                            break;
                        case 0:
                        default:
                            pingConnectivityFullTextView.setText(R.string.label_ping_connectivity_none);
                            pingConnectivityFullPingTextView.setText("-");
                            break;
                    }
                    break;
            }
        }

        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            if (notificationName == null || userInfo == null || previousPingConnectivityIndicator != SettingsActivity.PingConnectivityIndicator.FULL) {
                return;
            }
            switch (notificationName) {
                case EngineNotifications.PING_LOST:
                    runOnUiThread(() -> {
                        pingConnectivityFullPingTextView.setText(getString(R.string.label_over_max_ping_delay, 10));
                        onChanged(-1);
                    });
                    break;
                case EngineNotifications.PING_RECEIVED:
                    Long delay = (Long) userInfo.get(EngineNotifications.PING_RECEIVED_DELAY_KEY);
                    if (delay != null) {
                        runOnUiThread(() -> {
                            pingConnectivityFullPingTextView.setText(getString(R.string.label_ping_delay, delay));
                        });
                    }
                    break;
            }
        }

        @Override
        public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
            this.registrationNumber = registrationNumber;
        }

        @Override
        public long getEngineNotificationListenerRegistrationNumber() {
            return registrationNumber;
        }

        @Override
        public boolean hasEngineNotificationListenerRegistrationNumber() {
            return registrationNumber != null;
        }
    }

    class MainActivityPageChangeListener implements ViewPager.OnPageChangeListener {
        private final InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        private final ImageView[] imageViews;
        private final int inactiveColor;
        private final int activeColor;

        public MainActivityPageChangeListener(ImageView[] imageViews) {
            this.imageViews = imageViews;
            this.inactiveColor = ContextCompat.getColor(MainActivity.this, R.color.greyTint);
            this.activeColor = ContextCompat.getColor(MainActivity.this, R.color.olvid_gradient_light);
        }

        @Override
        public void onPageSelected(int position) {
            if (imm != null) {
                imm.hideSoftInputFromWindow(viewPager.getWindowToken(), 0);
            }

            // Update the menu as it depends on the tab
            invalidateOptionsMenu();
            if (invitationListFragment != null) {
                invitationListFragment.setInvitationsAreVisible(position == INVITATIONS_TAB);
            }
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            for (int i=0; i<imageViews.length; i++) {
                if (i==position) {
                    int color = 0xff000000;
                    color |= (int) (positionOffset*(inactiveColor&0xff) + (1-positionOffset)*(activeColor&0xff)) & 0xff;
                    color |= (int) (positionOffset*(inactiveColor&0xff00) + (1-positionOffset)*(activeColor&0xff00)) & 0xff00;
                    color |= (int) (positionOffset*(inactiveColor&0xff0000) + (1-positionOffset)*(activeColor&0xff0000)) & 0xff0000;
                    imageViews[i].setColorFilter(color);
                } else if ( i == position + 1) {
                    int color = 0xff000000;
                    color |= (int) (positionOffset*(activeColor&0xff) + (1-positionOffset)*(inactiveColor&0xff)) & 0xff;
                    color |= (int) (positionOffset*(activeColor&0xff00) + (1-positionOffset)*(inactiveColor&0xff00)) & 0xff00;
                    color |= (int) (positionOffset*(activeColor&0xff0000) + (1-positionOffset)*(inactiveColor&0xff0000)) & 0xff0000;
                    imageViews[i].setColorFilter(color);
                } else {
                    imageViews[i].setColorFilter(inactiveColor);
                }
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) { }
    }
}
