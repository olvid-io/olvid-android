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

package io.olvid.messenger.onboarding.flow

object OnboardingRoutes {
    const val WELCOME_SCREEN = "welcome_screen"

    const val NEW_PROFILE_SCREEN = "new_profile_screen"

    const val EXISTING_PROFILE = "existing_profile"
    const val IDENTITY_CREATION = "identity_creation"
    const val PROFILE_PICTURE = "profile_picture"

    const val TRANSFER_RESTRICTED_WARNING = "transfer_restricted_warning"
    const val TRANSFER_SOURCE_SESSION = "transfer_source_session"
    const val TRANSFER_TARGET_DEVICE_NAME = "transfer_target_device_name"
    const val TRANSFER_TARGET_SESSION_INPUT = "transfer_target_session_input"
    const val TRANSFER_TARGET_SHOW_SAS = "transfer_target_show_sas"
    const val TRANSFER_ACTIVE_DEVICES = "transfer_active_devices"
    const val TRANSFER_SOURCE_CONFIRMATION = "transfer_source_confirmation"
    const val TRANSFER_TARGET_RESTORE_SUCCESSFUL = "transfer_target_restore_successful"
    const val TRANSFER_TARGET_KEYCLOAK_AUTHENTICATION_PROOF_REQUIRED = "transfer_target_keycloak_authentication_proof_required"
    const val TRANSFER_TARGET_AUTHENTICATION_SUCCESSFUL = "transfer_target_authentication_successful"

    const val BACKUP_CHOOSE_FILE = "backup_choose_file"
    const val BACKUP_FILE_SELECTED = "backup_file_selected"
    const val BACKUP_KEY_VALIDATION = "backup_key_validation"

    const val BACKUP_V2_LOAD_OR_INPUT = "backup_v2_load_or_input"
    const val BACKUP_V2_ENTER_KEY = "backup_v2_enter_key"
    const val BACKUP_V2_SELECT_PROFILE = "backup_v2_select_profile"
    const val BACKUP_V2_SELECT_SNAPSHOT = "backup_v2_select_snapshot"
    const val BACKUP_V2_KEYCLOAK_AUTHENTICATION_REQUIRED = "backup_v2_keycloak_authentication_required"
    const val BACKUP_V2_EXPIRING_DEVICES_EXPLANATION = "backup_v2_expiring_devices_explanation"
    const val BACKUP_V2_RESTORE_RESULT = "backup_v2_restore_result"
}