# Build 261 (3.5.1)
2024-01-03

- Fix an NPE in user directory search results
- Improve messages load time in discussion

# Build 260 (3.5)
2024-12-29

- Fix some issues with internal PDF viewer

# ~~Build 259 (3.5)~~
2024-12-22

- optimized message download at app startup
- better GPS management when sharing your location
- show sent message status in discussion list
- recognize text content inside images (OCR)
- avoid some useless messages during phone calls in a multi-device settings
- (experimental) internal PDF viewer (opt-in in "other settings")
- several small bug fixes

# Build 258 (3.4.1)
2024-12-03

- Fix a few occasional crashes

# Build 257 (3.4)
2024-11-28

- Olvid now targets Android 15 (API 35)
- Improved global search to also match link title/description
- Allow contact introduction directly from the discussion screen
- [optimization] attachment download/upload progresses are no longer written to database
- several small bug fixes

# Build 256 (3.3.1)
2024-11-16

- Fix an issue with notification channels not found after a device-wide "reset app preferences" 

# Build 255 (3.3)
2024-11-14

- Improved global search layout and performance (tabs, search in group member names, search in personal notes, etc.)
- Support for incoming call notification during a call
- Improved RTL language support

# Build 254 (3.2.2)
2024-10-18

- Fix a layout issue with vide attachments
- Fix a crash with endless location sharing

# Build 253 (3.2.1)
2024-10-11

- Fix a bug when viewing read-once images and videos
- Fix a bug with message swipe being too sensitive (and preventing tap/long press)
- Allow to configure an alternate TURN server through MDM

# Build 252 (3.2)
2024-09-25

- Bug fix for the Compose discussion view

# ~~Build 251 (3.2)~~
2024-09-19

- Complete re-write of the discussion view in Compose

# Build 250 (3.1.1)
2024-07-14

- Re-enable android auto support
- Fix some markdown edge cases
- Remove attachment count from global search as it was too slow

# Build 249 (3.1)
2024-07-10

- Fix a crash wish URL truncation
- Improve double-scan and invitation contact addition flows 

# Build 248 (3.0)
2024-07-05

- Remove Android Auto support because app was rejected from the store...
- Update WebRTC

# ~~Build 247 (3.0)~~
2024-06-26

- Change the way phone call ringing timeouts are handled
- Truncate messages ending with a link for which a preview is available
- Add a global location sharing map, with all locations currently shared with you (in all discussions)

# ~~Build 246 (2.3)~~
2024-06-22

- Multiple small bugs fixed

# ~~Build 245 (2.3)~~
2024-06-19

- Make some engine operations more "bulletproof"

# ~~Build 243 (2.3)~~
2024-06-18

- Support for pre-keys allowing to receive messages before the end of the channel creation
- Optimize device discovery and channel creation attempts
- Detect contact that have been offline for an extended period of time

# Build 242 (2.2.1)
2024-06-10

- Fix a multi-profile issue with bookmarked messages

# ~~Build 241 (2.2.1)~~
2024-06-06

- Display an explanation of the message status in message details
- Fix message count taking too long in global search

# ~~Build 239 (2.2.1)~~
2024-06-04

- Better handling of messages received out-of-order (typically via WebSocket while a listing is in progress)
- Implement a few tweaks in cryptographic engine 
- Some engine db optimizations

# Build 238 (2.2)
2024-06-03

- Bookmark messages
- Change default group permissions and adjust group types (deleting other people's messages is no longer allowed by default)
- New message status icons to improve the vision you have on messages you send
- Allow to reorder pinned discussions
- Select discussions from the discussion list for bulk operations
- Several improvements to the global search (show the discussion in which messages were posted, better highlight, etc.)

# Build 237 (2.1.2)
2024-05-06

- Hotfix: recreate indexes that might have got dropped during the SQLcipher migration

# Build 236 (2.1.1)
2024-05-01

- Hotfix: when sqlcipher migration fails, continue using the old un-encrypted database
- Fix some edge case where one-to-one status could be lost in multi-device

# Build 235 (2.1)
2024-04-25

- Implement global full text search across all discussions
- Encrypt databases with SQLCipher
- Updated voice message UI
- Allow to choose alternate app icons
- Fix various minor issues with video calls
- Allow using only the first name when mentioning someone (press backspace after inserting the mention) 
- Batch operations for return receipts and message deletion from server

# Build 234 (2.0.1)
2024-03-20

- Several improvements to the video calls UI
- Fix a api key registration bug on first Keycloak binding
- Minor improvements to OpenStreetMaps integration
- Redesign of the message upload coordinator for improved performances when sending many messages

# Build 233 (2.0)
2024-03-14

- Fix an issue with video calls reconnection
- Minor improvements to video calls

# ~~Build 232 (2.0)~~
2024-03-10

- Support for video calls (up to 3 participants)!
- Allow to delay message sending if a preview is being loaded
- Add a "passive" listener when sharing location

# Build 230 (1.5)
2024-03-04

- Backward compatibility with ongoin location sharing
- Fix a small markdown bug

# ~~Build 229 (1.5)~~
2024-02-26

- Location sharing comes out of beta!
- Fix a crash with large 16-bit color depth PNG images
- Restart button in troubleshooting activity
- Update WebRTC and Sqlite-JDBC
- Emoji 15.1 support

# Build 227 (1.4)
2024-01-30

- Minor fixes

# ~~Build 226 (1.4)~~
2024-01-28

- New UI for ephemeral settings
- New gallery view for discussions (with links and media)
- Use of first name instead of "display name" when appropriate
- Playback speed control for audio messages
- [beta] possibility to use a custom OpenStreetsMap server
- [beta] possibility to use a custom Pelias server for address lookup

# Build 225 (1.3.1)
2023-12-07

- Minor fixes

# ~~Build 224 (1.3.1)~~
2023-12-06

- Fix for keycloak contact addition when keycloak is unreachable

# ~~Build 223 (1.3.1)~~
2023-12-05

- Various fixes for keycloak/one-to-one interactions

# ~~Build 222 (1.3.1)~~
2023-12-05

- Fix for invite all group members


# ~~Build 221 (1.3.1)~~
2023-12-04

- Make it possible to invite all group members to private discussions

# Build 220 (1.3)
2023-12-04

- Split contact tabs for easier access to "other users"
- Make message listing on server "ordered" for better multi-device synchronisation
- Fixes for Android 14 foreground service permissions
- Update WebRTC
- Confirmation before opening a link
- Allow to purchase multi-device licence during identity import

# Build 219 (1.2)
2023-11-02

- New troubleshooting activity
- Improved group cloning: it now preserves group admins

# ~~Build 218 (1.1.1)~~
2023-10-28

- Internal release

# Build 217 (1.1)
2023-10-24

- Fixes for various protocols in some multi-device corner cases
- Support for "write only" WebDAV automatic backup server (PUT permission is sufficient)
- Possibility to push settings configuration links through an MDM
- Possibility to push a WebDAV automatic backup configuration through an MDM and to enable Olvid backup key escrow on this WebDAV server

# Build 216 (1.0)
2023-10-10

- Fixes for compatibility with iOS and Windows versions

# ~~Build 215 (1.0)~~
2023-10-07

- Final adjustments to the transfer protocol

# ~~Build 214 (1.0)~~
2023-09-30

- Several improvements to the transfer protocol
- Notifications when a new device is added or the current device is set to be deactivated

# ~~Build 213 (1.0)~~
2023-09-27

- Fix keycloak issue in the transfer protocol

# ~~Build 211 (1.0)~~
2023-09-26

- Android 14 compatibility
- Transfer protocol: copy a profile to a new device without using a backup/restore

# Build 210 (0.15.0.3)
2023-09-05

- Fix for a bug in engine backup manager
- Update SQLite JDBC to 3.42.0.1

# Build 209 (0.15.0.2)
2023-08-11

- Hotfix for a crash on weird Markdown input

# Build 208 (0.15.0.1)
2023-08-03

- Hotfix for a billing library crash

# Build 207 (0.15.0)
2023-07-31

- Minor fixes for group types

# ~~Build 206 (0.15.0)~~
2023-07-28

- introduction of group "types" allowing, for example, the creation of read-only discussions
- preliminary support for multi-device (device re-synchronization not fully implemented yet)

# Build 205 (0.14.2)
2023-07-13

- Link previews support no longer in beta
- Fix an issue with contact display name format and sorting
- Changed the order of images in the gallery
- Last few fixes for markdown formatting
- [beta] Improved location integration selection 

# ~~Build 204 (0.14.2)~~
2023-06-16

- Update WebRTC
- Some markdown fixes
- Fix discussion background image rotation issue

# ~~Build 203 (0.14.2)~~
2023-06-05

- Markdown support
- Bug fixes

# Build 202 (0.14.1)
2023-05-15

- Bug fixes

# Build 201 (0.14.0)
2023-04-19

- Dependency updates
- Various fixes

# ~~Build 200 (0.14.0)~~
2023-04-05

- Support for emoji 15
- Update sqlite-jdbc and webrtc to latest stable versions

# ~~Build 199 (0.14.0)~~
2023-04-03

- Support for keycloak groups
- Invitations in discussions

# ~~Build 197 (0.14)~~
2023-03-17

- Fixes and improvements for user mentions
- Integration of ML Kit for QR code scanning
- [beta] Added precision circle on contact live-shared position

# ~~Build 196 (0.14)~~
2023-02-21

- Support for user mentions in messages
- [beta] integration with Google Maps and OpenStreetMap when sharing/receiving location

# Build 195 (0.13.3)
2023-02-06

- Fix bug with group v2 custom photos being deleted in periodic cleanup task 

# ~~Build 194 (0.13.3)~~
2023-02-05

- [beta] Improved preview link detection/validation

# ~~Build 193 (0.13.3)~~
2023-01-25

- Add missing ping to websocket

# ~~Build 192 (0.13.3)~~
2023-01-24

- [beta] rich link preview, sent as attachment
- Upgrade webRTC to 5481

# Build 191 (0.13.2)
2023-01-14

- Enable call initiation from another profile as long as one profile has a license 
- Swipe your profile picture to switch profile

# ~~Build 190 (0.13.2)~~
2023-01-10

- Create a "no google" flavor of the app
- Improve the profile deletion with contact notification protocol

# Build 189 (0.13.1)
2022-11-18

- Support for HTTP proxy inside webRTC

# ~~Build 188 (0.13.1)~~
2022-11-09

- Fixes for webclient with okHttp

# ~~Build 187 (0.13.1)~~
2022-11-07

- Replace the Java-Websocket library by okHttp to allow HTTP proxy on websocket

# Build 184 (0.13.0)
2022-10-24

- Fix a crash

# ~~Build 183 (0.13.0)~~
2022-10-20

- make groups v2 the default for new groups
- update to webRTC 5304
- sort discussions by pinned first when appropriate (share, forward, etc.)

# Build 182 (0.12.6)
2022-09-22

- fix for one time ephemeral and Gif keyboard

# ~~Build 181 (0.12.6)~~
2022-09-19

- possibility to block connection with untrusted certificate
- added an ETA estimator for attachment upload/downloads
- fix message sending from widget

# ~~Build 180 (0.12.6)~~
2022-09-14

- initial support for upcoming "multi-admin" groups
- possibility to pin a discussion
- implement anti-bruteforce for PIN and an "emergency PIN" to wipe sensitive data
- support for new Android 13 features (per app language, notification permission)
- fix for buggy UUID.toString() implementations (good job Motorola!)

# ~~Build 179 (0.12.5.1)~~
2022-08-22

- internal tests build

# Build 178 (0.12.5.1)
2022-07-28

- fix a bug with shared ephemeral settings not working in groups

# ~~Build 177 (0.12.5.1)~~
2022-07-28

- small fixes
- better way to import settings from a configuration link

# Build 176 (0.12.5)
2022-07-10

- [beta] fix a bug with location message containing an attachment

# ~~Build 175 (0.12.5)~~
2022-07-05

- possibility to configure most settings from a configuration.olvid.io link

# ~~Build 174 (0.12.5)~~
2022-07-01

- bug fix

# ~~Build 171 (0.12.5)~~
2022-06-30

- [beta] possibility to share a location within Olvid
- added a setting to disable automatic reply suggestions and actions
- added call duration in discussion messages

# Build 170 (0.12.4)
2022-05-24

- Backward compatiblity for Android API <26

# ~~Build 169 (0.12.4)~~
2022-05-21

- Added the possibility to use custom notifications for incoming messages/calls for each discussion
- Added a setting to scale the overall Olvid UI (independently of the existing font scaling)

# Build 168 (0.12.3.1)
2022-05-19

- Hotfix

# Build 167 (0.12.3)
2022-05-06

- Fix a crash when starting a voice message recording
- Fix a problem where remote deleting a message would not delete its attachments locally

# ~~Build 166 (0.12.3)~~
2022-05-05

- Fix a bug in video player not using correct file path for imported files

# ~~Build 165 (0.12.3)~~
2022-05-04

- Fixes for the webclient

# ~~Build 164 (0.12.3)~~
2022-05-02

- Several bug fixes
- Re-introduced left swipe to open the message long-press menu

# Build 163 (0.12.2)
2022-04-18

- Bugfix

# ~~Build 162 (0.12.2)~~
2022-04-07

- Unified message long press menu

# Build 161 (0.12.1)
2022-03-28

- Various bug fix

# ~~Build 160 (0.12.1)~~
2022-03-26

- Fix automatic backups requesting to sign in on simple network error
- Fix many small things in engine fetch and send coordinators to avoid "lost" messages in inbox or outbox
- Added "rejected" status to phone calls

# Build 159 (0.12.0)
2022-03-24

- Improved search in contacts tab: now also searches in other users and in identity provider (for managed profiles)

# ~~Build 158 (0.12.0)~~
2022-03-14

- Bugfix for "zealous lint"

# ~~Build 157 (0.12.0)~~
2022-03-12

- 2-level address book: distinguish between contacts (with a private discussion) and users you encountered (typically, in a group)
- forward one or several messages in one or several discussions at a time
- message search inside a discussion
- added a cloud backup manager
- added a foreground service when sending a message to avoid unwanted interruption
- possibility to set custom mute time/date for discussion or profile
- improvements to how group calls work
- bugfix for widget and possibility to confirm message upload with a vibration
- connectivity indicator no longer in beta
- fix font scaling bugs
- update to libWebRTC 4844
- update to Emoji 14.0


# Build 156 (0.11.1)
2022-01-27

- Bugfix for continuous ICE gathering

# ~~Build 155 (0.11.1)~~
2022-01-25

- Bugfix for storage management

# ~~Build 154 (0.11.1)~~
2022-01-24

- Added a storage management activity to see all attachments


# ~~Build 153 (0.11.1)~~
2022-01-20

- Added an internal emoji keyboard
- Receive notifications for reactions to your own messages
- Possiblity to use custom reactions
- Olvid devices can now negociate "capabilities" with one another
- Implemented continuous gathering in WebRTC to improve connection time: will be used with capable devices

# Build 152 (0.11.0)
2022-01-14

- [security] fix a potential timing attack on the implementation of scalar multiplication on EC (many thanks to Ryad Benadjila for pointing it out to us!)
- use emoji2 to support modern amojis on older android
- you can now create predefined messages to send in one click
- workaround for the presence of multiple Olvid tasks in recente tasks
- copy message can now copy complete message with attachments
- update to libWebRTC 4664
- update to AppAuth 0.11.1

# Build 151 (0.10.1.2)
2021-12-27

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
- [beta] Export your app databases for personal forensics

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
