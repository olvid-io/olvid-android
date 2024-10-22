# Olvid

Olvid is a private and secure end-to-end encrypted messenger.

Contrary to most other messaging applications, Olvid does not rely on a central directory to connect users. As there is no user directory, Olvid does not require access to your contacts and can function without **any** personal information. The absence of directory also prevents unsolicited messages and spam.

Because of this, from a security standpoint, Olvid is **not** "yet another secure messenger". Olvid guarantees the total and definitive confidentiality of exchanges, relying **solely** on the mutual trust of interlocutors. This implies that your privacy does not depend on the integrity of some server. This makes Olvid very different from other messengers that typically rely on some "Trusted Third Party", like a centralized database of users or a public blockchain.

Note that this doesn't mean that Olvid uses no servers (it does). It means that you do not have to trust them: your privacy is ensured by cryptographic protocols running on the client-side (i.e., on your device), and these protocols assume that the servers were compromised from day one. Even then, your privacy is ensured 😊.

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
alt="Download from Google Play"
height="80">](https://play.google.com/store/apps/details?id=io.olvid.messenger)

Signing certificate fingerprint to [verify](https://developer.android.com/studio/command-line/apksigner#usage-verify) the APK:
```
SHA-256: cc83a47d8ed411468580889fb3e058b0b31e82ef812d473531b5f9cd4a88fd33
```

## Help and documentation

If you need help using Olvid, first have a look at our FAQ at [https://olvid.io/faq/](https://olvid.io/faq/). We also have a few short tutorial videos available in [English](https://www.youtube.com/channel/UCO8UuhbgCyVSTRi4QEschqA) and in [French](https://www.youtube.com/channel/UC6aLiDb04Rfh4MoqDpJoLeg).

If you are looking for **technical documentation**, have a look at our [technology page](https://olvid.io/technology/) where you can find some technical specifications and the reports of the audits Olvid went through to get its [CSPN certifications](https://cyber.gouv.fr/produits-certifies?sort_bef_combine=field_date_de_certification_value_DESC&field_cat_target_id%5B587%5D=587).


## Send us feedback

If you find a bug, or have any feedback about Olvid, please contact the team at Olvid at [feedback@olvid.io](mailto:feedback@olvid.io). They will be glad to hear your suggestions.




# Structure of the project

Olvid is built of two main components:
- a **cryptographic engine**, written in pure Java (no Android APIs) located in the `obv_engine` folder
- an **application layer**, using the Android APIs for the graphical interface and OS integration , locate in the `obv_messenger` folder

The engine is in charge of all the encryption, contacts and groups management, and network communications, while the application layer implements the instant messaging functionalities on top of the engine.

As of now, the code is not fully documented and contains very few comments. Still, some aspects of it are very advanced and might be hard getting into. We encourage you to read the code, understand it and make your own modifications and additions. But keep in mind that some mechanisms are rather "fragile" and might break easily 🥚🥚🥚. The Olvid team is doing its best to improve your experience using this code and will try to improve these aspects in future releases.




# Building Olvid from the sources

Olvid is available on [Google Play](https://play.google.com/store/apps/details?id=io.olvid.messenger) or can be compiled from the source. You will get the same app, with the exact same features, but building it from the sources will require a little more work 😁 The Olvid team will do its best to publish the sources of any new version available on Google Play to this repository as soon as possible.

To build Olvid, you will need the latest version of [Android Studio](https://developer.android.com/studio).

- clone this repository
- open the project located in `obv_messenger` in Android Studio (we know, this is weird, but for historic reasons the root project is not at the root of the repository!)
- prepare an emulator or plug in your phone and press "Run app"
  
Compiling and running Olvid should work straight out of the box. If you run into issues, please contact us at [opensource@olvid.io](mailto:opensource@olvid.io).
  
> **⚠ WARNING ⚠**: if you want to run Olvid on your device and already have Olvid installed from Google Play,
> make sure to change the `applicationId` or add an `applicationIdSuffix` in the `productFlavors` 
> of the main [`build.gradle`](obv_messenger/app/build.gradle) to 
> avoid overwriting your app. Or at least, test your build on an emulator before overwriting your app 😂


## Push notifications and `nogoogle` flavor

Olvid provides 2 different build flavors: a `full` version including some Google closed sources libraries (like Firebase, Billing, Google drive API) and a `nogoogle` version containing only FOSS dependencies.

The `full` version is the one distributed on [Google Play](https://play.google.com/store/apps/details?id=io.olvid.messenger). Unfortunately, even if you have Google Services installed on your device, you will not be able to get push notifications when compiling this version from the sources. The Olvid server uses specific credentials to send push notifications, and these credentials are tied to the signature of the "official" Google Play version of Olvid. You may activate the "Maintain a permanent WebSocket connection" option in the privacy settings of the app in order to be notified instantly of new messages.


## The WebRTC library

The source code available here on GitHub includes a pre-compiled version of the WebRTC library that we update with (almost) every new stable release. Bundling pre-compiled open-source libraries in an open-source project is not usually considered good practice, but the effort required to compile WebRTC is such that we decided to make your life easier 😊

We apply some minor modifications to the WebRTC library to allow passing it an HTTP proxy and a user agent. This way, Olvid is able to issue phone calls using a TCP connexion through a proxy on networks where UDP is blocked and a proxy is mandatory (note that, in the browser, WebRTC is able to do this, the APIs are simply not exposed in the Android version of the library).

If you want to compile WebRTC yourself, you should follow the standard WebRTC build procedure, simply applying the provided patch:

- clone the `depot tools`
- fetch `webrtc_android`
- sync with `gclient`
- checkout the `branch-heads/xxxx` you want to compile
- **apply the Olvid patch available in the [`libwebrtc/`](obv_messenger/libwebrtc/) folder corresponding to the `xxxx` version you chose** (you can use the `git apply olvid_xxxx.patch` command)
- compile with `build_aar.py`
- replace the provided [`libwebrtc.aar`](obv_messenger/libwebrtc/libwebrtc.aar) with your version


## The SQLite-JDBC library

Olvid also comes with a slightly modified version of the [SQLite-JDBC](https://github.com/xerial/sqlite-jdbc) library. Compared to the original we simply:

- make changes to the build script to also build an `amr64-v8a` version of the library
- enable the `legacy_alter_table` pragma that is used in some migration operation of the engine
- remove most of the OS/arch discovery mechanisms found in `OSInfo.java` that can be seen as "intrusive"
- simplify the native library loading mechanism as the `libsqlitejdbc.so` is no longer bundled in the jar, but directly provided in the standard Android folder (this decreases the size of the APK as only the required lib is included)




# Contributing to Olvid

Olvid, as a company, has not yet put in place all the necessary processes to easily accept external contributions. In particular, a Contributor License Agreement should be made available at some point in time. Until then, please contact us at [opensource@olvid.io](mailto:opensource@olvid.io) if you would like to contribute. 




# License

Olvid for Android is licensed under the GNU Affero General Public License v3. The full license is available in [`LICENSE`](LICENSE).


    Olvid for Android
    Copyright © 2019-2024 Olvid SAS
    
    Olvid is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License, version 3,
    as published by the Free Software Foundation.
    
    Olvid is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.
    
    You should have received a copy of the GNU Affero General Public License
    along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
