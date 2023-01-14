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

package io.olvid.messenger.databases.entity;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.settings.SettingsActivity;

@SuppressWarnings("CanBeFinal")
@Entity(tableName = Invitation.TABLE_NAME,
        foreignKeys = @ForeignKey(entity = OwnedIdentity.class,
                parentColumns = OwnedIdentity.BYTES_OWNED_IDENTITY,
                childColumns = Invitation.BYTES_OWNED_IDENTITY,
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(Invitation.BYTES_OWNED_IDENTITY),
        }
)
public class Invitation {
    public static final String TABLE_NAME = "invitation_table";
    public static final String DIALOG_UUID = "dialog_uuid";
    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String BYTES_CONTACT_IDENTITY = "bytes_contact_identity";
    public static final String ASSOCIATED_DIALOG = "associated_dialog";
    public static final String INVITATION_TIMESTAMP = "invitation_timestamp";
    public static final String CATEGORY_ID = "category_id";

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = DIALOG_UUID)
    public UUID dialogUuid;

    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @ColumnInfo(name = BYTES_CONTACT_IDENTITY)
    @Nullable
    public byte[] bytesContactIdentity;

    @ColumnInfo(name = ASSOCIATED_DIALOG)
    @NonNull
    public ObvDialog associatedDialog;

    @ColumnInfo(name = INVITATION_TIMESTAMP)
    public long invitationTimestamp;

    @ColumnInfo(name = CATEGORY_ID)
    public int categoryId;

    // Required by room, but not used
    public Invitation(@NonNull UUID dialogUuid, @NonNull byte[] bytesOwnedIdentity, @Nullable byte[] bytesContactIdentity, @NonNull ObvDialog associatedDialog, long invitationTimestamp, int categoryId) {
        this.dialogUuid = dialogUuid;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = bytesContactIdentity;
        this.associatedDialog = associatedDialog;
        this.invitationTimestamp = invitationTimestamp;
        this.categoryId = categoryId;
    }

    @Ignore
    public Invitation(ObvDialog associatedDialog, long invitationTimestamp) {
        this.dialogUuid = associatedDialog.getUuid();
        this.bytesOwnedIdentity = associatedDialog.getBytesOwnedIdentity();
        this.bytesContactIdentity = associatedDialog.getCategory().getBytesContactIdentity();
        this.associatedDialog = associatedDialog;
        this.invitationTimestamp = invitationTimestamp;
        this.categoryId = associatedDialog.getCategory().getId();
    }


    @NonNull
    public String getStatusText() {
        switch (associatedDialog.getCategory().getId()) {
            case ObvDialog.Category.INVITE_SENT_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_invite_sent);
            case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_accept_invite);
            case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_exchange_sas);
            case ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_give_him_sas);
            case ObvDialog.Category.MUTUAL_TRUST_CONFIRMED_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_mutual_trust_established);
            case ObvDialog.Category.INVITE_ACCEPTED_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_invite_accepted);
            case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_mediator_invite);
            case ObvDialog.Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_mediator_invite_accepted);
            case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
            case ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY:
            case ObvDialog.Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_group_invite);
            case ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY:
            case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_one_to_one_invitation);
            default:
                return "";
        }
    }

    public void displayStatusDescriptionTextAsync(TextView textView) {
        switch (associatedDialog.getCategory().getId()) {
            case ObvDialog.Category.INVITE_SENT_DIALOG_CATEGORY:
                textView.setText(App.getContext().getString(R.string.invitation_status_description_invite_sent, associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails()));
                break;
            case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                try {
                    JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                    textView.setText(App.getContext().getString(R.string.invitation_status_description_accept_invite, identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName())));
                }  catch (Exception e) {
                    textView.setText(null);
                }
                break;
            case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY: {
                try {
                    JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                    textView.setText(App.getContext().getString(R.string.invitation_status_description_enter_their_sas, identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName()), new String(associatedDialog.getCategory().getSasToDisplay(), StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    textView.setText(null);
                }
                break;
            }
            case ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY: {
                try {
                    JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                    textView.setText(App.getContext().getString(R.string.invitation_status_description_give_him_sas, identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName()),  new String(associatedDialog.getCategory().getSasToDisplay(), StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    textView.setText(null);
                }
                break;
            }
            case ObvDialog.Category.MUTUAL_TRUST_CONFIRMED_DIALOG_CATEGORY: {
                textView.setText(null);
                final WeakReference<TextView> textViewWeakReference = new WeakReference<>(textView);
                App.runThread(() -> {
                    final Contact contact = AppDatabase.getInstance().contactDao().get(associatedDialog.getBytesOwnedIdentity(), associatedDialog.getCategory().getBytesContactIdentity());
                    final TextView TV = textViewWeakReference.get();
                    if (contact != null && contact.establishedChannelCount > 0) {
                        TV.setText(App.getContext().getString(R.string.invitation_status_description_mutual_trust_established_with_channel, contact.getCustomDisplayName()));
                    } else {
                        try {
                            JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                            TV.setText(App.getContext().getString(R.string.invitation_status_description_mutual_trust_established, identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName())));
                        } catch (Exception ignored) { }
                    }
                });
                break;
            }
            case ObvDialog.Category.INVITE_ACCEPTED_DIALOG_CATEGORY: {
                try {
                    JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                    textView.setText(App.getContext().getString(R.string.invitation_status_description_invite_accepted, identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName())));
                } catch (Exception e) {
                    textView.setText(null);
                }
                break;
            }
            case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY: {
                textView.setText(null);
                final WeakReference<TextView> textViewWeakReference = new WeakReference<>(textView);
                App.runThread(() -> {
                    final Contact mediator = AppDatabase.getInstance().contactDao().get(associatedDialog.getBytesOwnedIdentity(), associatedDialog.getCategory().getBytesMediatorOrGroupOwnerIdentity());
                    final TextView TV = textViewWeakReference.get();
                    if (TV != null) {
                        try {
                            final JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (mediator != null) {
                                    TV.setText(App.getContext().getString(R.string.invitation_status_description_accept_mediator_invite, mediator.getCustomDisplayName(), identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName())));
                                } else {
                                    TV.setText(App.getContext().getString(R.string.invitation_status_description_accept_mediator_invite_deleted, identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName())));
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                break;
            }
            case ObvDialog.Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY: {
                textView.setText(null);
                final WeakReference<TextView> textViewWeakReference = new WeakReference<>(textView);
                App.runThread(() -> {
                    final Contact mediator = AppDatabase.getInstance().contactDao().get(associatedDialog.getBytesOwnedIdentity(), associatedDialog.getCategory().getBytesMediatorOrGroupOwnerIdentity());
                    final TextView TV = textViewWeakReference.get();
                    if (TV != null) {
                        try {
                            final JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (mediator != null) {
                                    TV.setText(App.getContext().getString(R.string.invitation_status_description_mediator_invite_accepted, identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName()), mediator.getCustomDisplayName()));
                                } else {
                                    TV.setText(App.getContext().getString(R.string.invitation_status_description_mediator_invite_accepted_deleted, identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName())));
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                break;
            }
            case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
            case ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY: {
                textView.setText(null);
                final WeakReference<TextView> textViewWeakReference = new WeakReference<>(textView);
                App.runThread(() -> {
                    final Contact groupOwner = AppDatabase.getInstance().contactDao().get(associatedDialog.getBytesOwnedIdentity(), associatedDialog.getCategory().getBytesMediatorOrGroupOwnerIdentity());
                    final TextView TV = textViewWeakReference.get();
                    if (TV != null) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (groupOwner != null) {
                                TV.setText(App.getContext().getString(R.string.invitation_status_description_accept_group_invite, groupOwner.getCustomDisplayName()));
                            } else {
                                TV.setText(App.getContext().getString(R.string.invitation_status_description_contact_or_mediator_deleted));
                            }
                        });
                    }
                });
                break;
            }
            case ObvDialog.Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY: {
                textView.setText(R.string.invitation_status_description_group_v2_frozen_invitation);
                break;
            }
            case ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY: {
                textView.setText(null);
                final WeakReference<TextView> textViewWeakReference = new WeakReference<>(textView);
                App.runThread(() -> {
                    final Contact contact = AppDatabase.getInstance().contactDao().get(associatedDialog.getBytesOwnedIdentity(), associatedDialog.getCategory().getBytesContactIdentity());
                    final TextView TV = textViewWeakReference.get();
                    if (TV != null) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (contact != null) {
                                TV.setText(App.getContext().getString(R.string.invitation_status_description_one_to_one_invitation_sent, contact.getCustomDisplayName()));
                            } else {
                                TV.setText(App.getContext().getString(R.string.invitation_status_description_one_to_one_invitation_sent_deleted));
                            }
                        });
                    }
                });
                break;
            }
            case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY: {
                textView.setText(null);
                final WeakReference<TextView> textViewWeakReference = new WeakReference<>(textView);
                App.runThread(() -> {
                    final Contact contact = AppDatabase.getInstance().contactDao().get(associatedDialog.getBytesOwnedIdentity(), associatedDialog.getCategory().getBytesContactIdentity());
                    final TextView TV = textViewWeakReference.get();
                    if (TV != null) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (contact != null) {
                                TV.setText(App.getContext().getString(R.string.invitation_status_description_one_to_one_invitation, contact.getCustomDisplayName()));
                            } else {
                                TV.setText(App.getContext().getString(R.string.invitation_status_description_contact_or_mediator_deleted));
                            }
                        });
                    }
                });
                break;
            }
            default:
                textView.setText(null);
        }
    }

    public void listGroupMembersAsync(TextView membersTextView) {
        if (associatedDialog.getCategory().getId() == ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY) {
            final WeakReference<TextView> membersTextViewWeakReference = new WeakReference<>(membersTextView);
            App.runThread(() -> {
                final byte[] bytesOwnedIdentity = associatedDialog.getBytesOwnedIdentity();
                List<CharSequence> memberNames = new ArrayList<>();
                for (final ObvIdentity contactIdentity : associatedDialog.getCategory().getPendingGroupMemberIdentities()) {
                    Contact contact = AppDatabase.getInstance().contactDao().get(bytesOwnedIdentity, contactIdentity.getBytesIdentity());
                    if (contact == null) {
                        memberNames.add(contactIdentity.getIdentityDetails().formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));
                    } else {
                        ClickableSpan clickableSpan = new ClickableSpan() {
                            @Override
                            public void onClick(View view) {
                                App.openContactDetailsActivity(view.getContext(), bytesOwnedIdentity, contactIdentity.getBytesIdentity());
                            }
                        };
                        SpannableString contactName = new SpannableString(contact.getCustomDisplayName());
                        contactName.setSpan(clickableSpan, 0, contactName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        memberNames.add(contactName);
                    }
                }

                Collections.sort(memberNames, (CharSequence cs1, CharSequence cs2) -> {
                    int minLen = Math.min(cs1.length(), cs2.length());
                    for (int i=0; i<minLen; i++) {
                        if (cs1.charAt(i) != cs2.charAt(i)) {
                            return cs1.charAt(i) - cs2.charAt(i);
                        }
                    }
                    return cs2.length() - cs1.length();
                });

                final SpannableStringBuilder builder = new SpannableStringBuilder();
                if (memberNames.isEmpty()) {
                    StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                    SpannableString spannableString = new SpannableString(App.getContext().getString(R.string.text_nobody));
                    spannableString.setSpan(styleSpan, 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    builder.append(spannableString);
                } else {
                    String separator = App.getContext().getString(R.string.text_contact_names_separator);
                    boolean first = true;
                    for (CharSequence memberName : memberNames) {
                        if (!first) {
                            builder.append(separator);
                        }
                        first = false;
                        builder.append(memberName);
                    }
                }

                final TextView membersTV = membersTextViewWeakReference.get();
                if (membersTV != null) {
                    new Handler(Looper.getMainLooper()).post(() -> membersTV.setText(builder));
                }
            });
        } else if (associatedDialog.getCategory().getId() == ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY
                || associatedDialog.getCategory().getId() == ObvDialog.Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY) {
            final WeakReference<TextView> membersTextViewWeakReference = new WeakReference<>(membersTextView);
            App.runThread(() -> {
                final byte[] bytesOwnedIdentity = associatedDialog.getBytesOwnedIdentity();
                List<CharSequence> memberNames = new ArrayList<>();
                for (final ObvGroupV2.ObvGroupV2PendingMember groupV2Member : associatedDialog.getCategory().getObvGroupV2().pendingGroupMembers) {
                    Contact contact = AppDatabase.getInstance().contactDao().get(bytesOwnedIdentity, groupV2Member.bytesIdentity);
                    if (contact == null) {
                        try {
                            JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(groupV2Member.serializedDetails, JsonIdentityDetails.class);
                            memberNames.add(identityDetails.formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));
                        } catch (JsonProcessingException e) {
                            memberNames.add("???");
                        }
                    } else {
                        ClickableSpan clickableSpan = new ClickableSpan() {
                            @Override
                            public void onClick(View view) {
                                App.openContactDetailsActivity(view.getContext(), bytesOwnedIdentity, groupV2Member.bytesIdentity);
                            }
                        };
                        SpannableString contactName = new SpannableString(contact.getCustomDisplayName());
                        contactName.setSpan(clickableSpan, 0, contactName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        memberNames.add(contactName);
                    }
                }

                Collections.sort(memberNames, (CharSequence cs1, CharSequence cs2) -> {
                    int minLen = Math.min(cs1.length(), cs2.length());
                    for (int i=0; i<minLen; i++) {
                        if (cs1.charAt(i) != cs2.charAt(i)) {
                            return cs1.charAt(i) - cs2.charAt(i);
                        }
                    }
                    return cs2.length() - cs1.length();
                });

                final SpannableStringBuilder builder = new SpannableStringBuilder();
                if (memberNames.isEmpty()) {
                    StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
                    SpannableString spannableString = new SpannableString(App.getContext().getString(R.string.text_nobody));
                    spannableString.setSpan(styleSpan, 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    builder.append(spannableString);
                } else {
                    String separator = App.getContext().getString(R.string.text_contact_names_separator);
                    boolean first = true;
                    for (CharSequence memberName : memberNames) {
                        if (!first) {
                            builder.append(separator);
                        }
                        first = false;
                        builder.append(memberName);
                    }
                }

                final TextView membersTV = membersTextViewWeakReference.get();
                if (membersTV != null) {
                    new Handler(Looper.getMainLooper()).post(() -> membersTV.setText(builder));
                }
            });
        }
    }
}
