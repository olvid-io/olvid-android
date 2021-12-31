# Build 151 (0.10.1.2)

- Hotfix for attaching file with instant screen lock

# Build 150 (0.10.1.1)
2021-12-24

- Hotfix for locked discussion scroll and message single tap

# Build 149 (0.10.1)
2021-12-23

- [beta] Network connectivity indicator
- Fix font scaling
- Reactions no longer in beta

# ~~Build 148 (0.10.1)~~
2021-12-17

- Missed message indicator no longer in beta
- New message composition area
- New setting to change font scale
- Option to send messages with Enter key

# Build 147 (0.10.0)
2021-12-13

- Support for configuring keycloak via an MDM (RestrictionsManager)

# ~~Build 146 (0.10.0)~~

- return receipts on attachments
- improved onboarding when clicking an invitation link
- table to store reactions received before the message
- fix some screen brighness issues after displaying a QR code

# ~~Build 145 (0.10.0)~~
2021-12-06

- introducing multi-profile
- several fixes for keycloak plugin v2.0

# Build 144 (0.9.15.2)
2021-11-29

- Hotfix for a backup key systematically cannot be read from engine

# Build 143 (0.9.15.1)
2021-11-27

- Hotfix for restore backup from cloud

# Build 142 (0.9.15)
2021-11-20

- Allow rotating profile picture during selection
- revocation seems functional
- block and unblock revoked contacts


# ~~Build 141 (0.9.15)~~
2021-11-17

- contact introduction always asks for user confirmation
- check that the keycloak signature key is the same for added contacts/for the green shield
- do not insert a "discussion deleted" message for remote deletion of an empty discussion 
- backup reminder snackbar
- multicalls no longer in beta
- possible to "Save all" attachments in message
- first implementation of keycloak revocation
- warning dialog when opening with external app

# Build 140 (0.9.14.2)
2021-10-28

- Hotfix for startup dialogs threads

# Build 139 (0.9.14.1)
2021-10-21

- Hotfix for android 6 (bug in splashscreen library)

# Build 138 (0.9.14)
2021-10-18

- [beta] Indicator of missing messages in discussions
- attachment popup menu now properly opens in external viewer when needed
- empty gallery message
- better handling of when audio permission are requested
- [tech] preparation for keycloak push topics
- improve voice message quality


# ~~Build 137 (0.9.14)~~
2021-10-10

- Compatibility with Android 12 (many changes under the hood)
- New splash screen
- Backups include keycloak stuff
- Backups include app stuff (nicknames, settings, etc.)
- Personal notes for contacts and groups
- Warning when Olvid version is outdated
- Better audio permission denied handling in calls and voice messages


# Build 136 (0.9.13)
2021-09-21

- [beta] Possible to choose which regional TURN server to use


# ~~Build 135 (0.9.13)~~

- It is now possible to choose which browser to use for keyclaok authentication
- You can choose if audio messages are played on the loudspeaker or on the earpiece
- Fixed a bug introduced in build 134 when attaching a file of unknown size (gif from keyboard)


# Build 134 (0.9.12)
2021-09-15

- Fyle sha256 are now nullable --> better draft attachment experience for large files
- Loading spinner for discussion activity
- Better shortcut icon with profile picture
- Focus message composition zone when entering discussion
- Fix m4a mime type for iOS compatibility
- New icons for some file types


# Build 133 (0.9.11)
2021-07-31

- Fix for build 131 contacts cache bugs
- Better mutual scan screen design
- Click on shared disucssion settings update message to go to settings


# ~~Build 131 (0.9.11)~~
2021-07-27

- Implemented mutual scan trust establishment
- Contact pictures in group discussions
- Marker for unplayed audio attachment
- Inertia on fling in image viewer
- Added attachment miniatures in messages


# Build 130 (0.9.10.1)
2021-07-19

- Hotfix for bugs in build 128
- Refreshed download icon and download/upload progress meter
- Added timer for expiring messages in gallery

# Build 128 (0.9.10)
2021-07-15

- Added a gallery for viewing multiple images
- Improved message swipe in discussion
- Better preview of animated gifs, with the correct aspect ratio
- [tech] refactored the way previews are computed (PreviewUtils)
- [tech] added image resolution to db, with background worker to compute them
- [tech] call credentials cached for 12h
- [tech] implement dialog to re-sign in with Google Drive when needed

# Build 126 (0.9.9.3)
2021-07-05

- Bugfix for replayed activity intent
- Bugfix for websocket reconnection
- No more preview of unsupported image types
- Refactor of the image thumbnail computation and cache mecanism
- Dialog to warn when low on available device space

# Build 125 (0.9.9.2)
2021-06-28

- Option to be notified of server certificate changes
- Options to disable Google push notifications and use a permanent WebSocket connection instead
- Require to unlock the app before connecting to the web client (can be disabled in settings)
- Better resilience to connexion loss of WebRTC calls
- Fix lock screen biometric prompt showing over phone lock screen
- [tech] implemented a unified foreground service
- [tech] update the way build and versions are sent to the server
- [tech] implemented message delivery through websocket
- [tech] update lib sqlite-jdbc

# ~~Build 123 (0.9.9.2)~~
2021-05-25

- Added color temperature and saturation correction to profile picture selection
- Assign custom colors to your contacts to better distinguish them in group chats
- [beta] Support for multi call
- [tech] update to libWebRTC 4472


# Build 122 (0.9.9.1)
2021-05-12

- Confirmation dialog when choosing a retention policy that would delete messages
- [beta] File explorer for internal Olvid storage

# ~~Build 121 (0.9.9.1)~~
2021-05-10

- Bugfix for when retention policy would fail to delete too many messages

# ~~Build 120 (0.9.9.1)~~
2021-05-10

- New options to customize how your contact names are displayed
- Reorganization of the settings
- You can set a custom profile picture for your contacts

# Build 119 (0.9.9)
2021-04-23

- New features! You can now:
  - Edit your messages after they are sent
  - "Delete everywhere" any message
  - Send a single ephemeral messages
- [beta] Export your app databases for personnal forensics

# Build 118 (0.9.4)
2021-02-13

- Support for keycloak onboarding and binding/unbinding

# Build 117 (0.9.3)
2021-01-28

- New Feature: beta test our Web Client by visiting https://web.olvid.io
- Basic support for animated GIFs in discussions, from Android 9 (Pie) and up
- You can now send ephemeral messages (see https://olvid.io/faq/ for more details)

# ~~Build 116 (0.9.3)~~
2021-01-28

# ~~Build 115 (0.9.3)~~
2021-01-26

# ~~Build 114 (0.9.3)~~
2021-01-22

# Build 113 (0.9.2)
2021-01-13

# Build 111 (0.9.1)
2021-01-03

- Mute "noisy" discussions directly from the notification

# Build 110 (0.9.0)
2020-12-13

- You can now record and send voice messages
- Preparation for the upcoming ephemeral messages

# ~~Build 109 (0.9.0)~~
2020-12-10

# Build 108 (0.8.7)
2020-11-19

- [WARNING] Existing direct shortcuts to discussions may no longer work and will need to be recreated
- Drafts now appear in discussion list

# Build 107 (0.8.6)
2020-11-16

- In-app subscription and free trial enabled

# ~~Build 106 (0.8.6)~~
2020-11-10

# ~~Build 104 (0.8.6)~~
2020-11-05

# ~~Build 103 (0.8.6)~~
2020-11-04

# Build 101 (0.8.5)
2020-10-13

- Main screen layout refresh
- Swipe messages to reply/delete them
- Rework of the scan/invite process
- Settings to resize pictures and remove sensitive EXIF data

# Build 100 (0.8.5)
2020-10-12

# Build 99 (0.8.4)
2020-09-04

- First beta version of secure voice calls
- You can now pick a profile picture (for you, and for groups you create)
- Dark mode support
- Complete rework of settings
- Compatibility with Android 11



