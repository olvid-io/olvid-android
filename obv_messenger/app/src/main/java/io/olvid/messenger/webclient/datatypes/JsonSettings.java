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

package io.olvid.messenger.webclient.datatypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonSettings {
    private String language;
    private String theme;
    private boolean sendOnEnter;
    private boolean notificationSound;
    private boolean showNotifications;

    private String appDefaultLanguage;
    private String appDefaultTheme;
    private String webDefaultLanguage;

    public JsonSettings() {}

    public JsonSettings(String language, String theme, boolean sendOnEnter, boolean notifications, boolean showNotifications, String appDefaultLanguage, String appDefaultTheme, String webDefaultLanguage){
        this.language = language;
        this.theme = theme;
        this.sendOnEnter = sendOnEnter;
        this.notificationSound = notifications;
        this.showNotifications = showNotifications;
        this.appDefaultLanguage = appDefaultLanguage;
        this.appDefaultTheme = appDefaultTheme;
        this.webDefaultLanguage = webDefaultLanguage;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isSendOnEnter() {
        return sendOnEnter;
    }

    public void setSendOnEnter(boolean sendOnEnter) {
        this.sendOnEnter = sendOnEnter;
    }

    public boolean isNotificationSound() {
        return notificationSound;
    }

    public void setNotificationSound(boolean notificationSound) {
        this.notificationSound = notificationSound;
    }

    public boolean isShowNotifications() {
        return showNotifications;
    }

    public void setShowNotifications(boolean showNotifications) {
        this.showNotifications = showNotifications;
    }

    public String getAppDefaultLanguage() {
        return appDefaultLanguage;
    }

    public void setAppDefaultLanguage(String appDefaultLanguage) {
        this.appDefaultLanguage = appDefaultLanguage;
    }

    public String getAppDefaultTheme() {
        return appDefaultTheme;
    }

    public void setAppDefaultTheme(String appDefaultTheme) {
        this.appDefaultTheme = appDefaultTheme;
    }

    public String getWebDefaultLanguage() {
        return webDefaultLanguage;
    }

    public void setWebDefaultLanguage(String webDefaultLanguage) {
        this.webDefaultLanguage = webDefaultLanguage;
    }
}
