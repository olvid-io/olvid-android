# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-printusage build/outputs/unused.txt

# ----------------------------------------------------------------------------
# Olvid Engine Rules
# ----------------------------------------------------------------------------

# MetaManager uses reflection to find implementations
# We must keep setDelegate methods on any class implementing ObvManager
-keepclassmembers class * implements io.olvid.engine.metamanager.ObvManager {
    public void setDelegate(...);
}

# ConcreteProtocol uses reflection to instantiate States, Messages and Steps
-keep class * extends io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState {
    public <init>(...);
}

-keep class * extends io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage {
    public <init>(...);
}

-keep class * extends io.olvid.engine.protocol.protocol_engine.ProtocolStep {
    public <init>(...);
}

# ----------------------------------------------------------------------------
# ViewModels
# ----------------------------------------------------------------------------

# ComposeMessageViewModel instantiated via reflection in DiscussionActivity
-keep class io.olvid.messenger.discussion.compose.ComposeMessageViewModel {
    public <init>(androidx.lifecycle.LiveData, androidx.lifecycle.LiveData);
}

# ----------------------------------------------------------------------------
# AppSingleton
# ----------------------------------------------------------------------------

# Engine listeners are kept as WeekRefences --> we need to keep a strong reference to them
-keep class io.olvid.messenger.AppSingleton {
    private final io.olvid.messenger.EngineNotificationProcessor engineNotificationProcessor;
    private final io.olvid.messenger.EngineNotificationProcessorForContacts engineNotificationProcessorForContacts;
    private final io.olvid.messenger.EngineNotificationProcessorForGroups engineNotificationProcessorForGroups;
    private final io.olvid.messenger.EngineNotificationProcessorForGroupsV2 engineNotificationProcessorForGroupsV2;
    private final io.olvid.messenger.EngineNotificationProcessorForMessages engineNotificationProcessorForMessages;
}

-keep class io.olvid.engine.engine.Engine$NotificationWorker { *; }
-keep class io.olvid.engine.engine.Engine { *; }

# ----------------------------------------------------------------------------
# Jackson / JSON Rules
# ----------------------------------------------------------------------------

# Keep all Jackson annotations
-keepattributes *Annotation*, EnclosingMethod, Signature

# Keep classes used for JSON serialization/deserialization
# Keep members with JsonProperty annotations (general fallback)
-keep class io.olvid.engine.engine.types.** { *; }
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class * { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.JsonProperty *;
    @com.fasterxml.jackson.annotation.JsonCreator *;
}


# Keep TypeReference subclasses to preserve generic types in Jackson
-keep class * extends com.fasterxml.jackson.core.type.TypeReference

# Keep ObjectMapper dependencies if not automatically handled
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-keepnames interface com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# ----------------------------------------------------------------------------
# sqlite
# ----------------------------------------------------------------------------

-keep class org.sqlite.** { *; }
-keep class org.sqlite.database.** { *; }

-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**


# ----------------------------------------------------------------------------
# Third Party Library Fixes
# ----------------------------------------------------------------------------

# Firebase Analytics is explicitly excluded but referenced by Firebase Messaging
-dontwarn com.google.firebase.analytics.connector.**

# JDBC classes are referenced by sqlite-jdbc (used in engine) but not valid on Android
-dontwarn java.sql.JDBCType
-dontwarn org.sqlite.jdbc3.JDBC3PreparedStatement

# OkHttp internal Util referenced by Sardine (WebDAV)
-dontwarn okhttp3.internal.Util

# Jsoup optional dependency on re2j
-dontwarn com.google.re2j.**
