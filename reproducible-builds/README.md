# Olvid for Android reproducible builds

[Reproducible builds](https://reproducible-builds.org/docs/definition/) allow anyone to check that the software they are downloading from a store (or more generally the internet) matches the source code published on an open-source repository. If you are able to recreate a bit-by-bit copy of the software that is distributed, you are sure that this software does exactly what can be seen and the source code, and nothing more.

However, reproducible build are not easy to achieve as the whole compilation process has to be **perfectly deterministic**. This is particularly hard for native code where compilers tend to optimize based on specific architecture features depending on the hardware it is running on. For Java bytecode, and in particular Android applications, this is a bit easier. This is why [F-Droid pushes for reproducible builds](https://f-droid.org/docs/Reproducible_Builds/) whenever this is possible.

This folder contains the tools to replicate a docker environment similar to the CI environment in which apps distributed on [F-Droid](https://f-droid.org/) are compiled.

## Publication process on F-Droid

Publication on F-Droid follows these steps:
- We publish a YAML metadata file to the [F-Droid metadata](https://gitlab.com/fdroid/fdroiddata) repository, targeting a specific version of our code published on GitHub.
- The gitlab CI compiles the code and produces an unsigned APK.
- We provide a signed APK, following the instructions on this page.
- F-Droid takes the signature from our APK and copies it to the APK they compiled.
- If everything worked as expected, the signature is valid and the signed APK can be published to F-Droid.

## Compiling in our docker environment

When we create a signed APK to provide to F-Droid, or when you want to verify the reproducibility of the build yourself, the process is the same. The only difference is we only need to perform the signature for F-Droid.

### Building the APK

1. Clone this repository of the Olvid for Android source code.
```bash
git clone https://github.com/olvid-io/olvid-android.git
```
2. Enter the cloned repository folder.
```bash
cd olvid-android
```
2. Make sure to check out the tag corresponding exactly to the version of Olvid you want to verify. For example for version 4.2.2 (build 291) you should run:
```bash
git checkout v4.2.2_291
```
3. Enter the `reproducible-builds` folder.
```bash
cd reproducible-builds
```
4. Build the docker image in which to run the compilation of Olvid.
```bash
bash build.sh
```
5. Build the APK for the `ProdNogoogleRelease` flavor.
```bash
cd ..
docker run --rm -v "$(pwd)":/olvid -w /olvid/obv_messenger --user "$(id -u):$(id -g)" olvid-android ./gradlew assembleProdNogoogleRelease
```

Compilation may take a few minutes, but should result in a nice APK located here `obv_messenger/app/build/outputs/apk/prodNogoogle/release/app-prod-nogoogle-release-unsigned.apk`. If you cannot find it, you may use find:
```bash
find . -name '*.apk'
```

### Signing the APK

This step is what we do to provide a signed APK to F-Droid, but it is not needed to simply verify the reproducibility.

1. Move the APK to an easy-to-access folder.
```bash
mv obv_messenger/app/build/outputs/apk/prodNogoogle/release/app-prod-nogoogle-release-unsigned.apk olvid-unsigned.apk
```
2. Perform the signature with `apksigner`. This tool is part of the Android SDK build tools. ⚠️⚠️Warning: recent versions of `apksigner` modify the offsets inside the zip, making it impossible to verify (see the [F-Droid documentation(https://f-droid.org/docs/Reproducible_Builds/#apksigner-from-build-tools--3500-rc1-outputs-unverifiable-apks) on this). We use the build tools **version 34.0.0**, assuming they are installed in `/opt/`, adjust the location depending on your install.
```bash
/opt/android-sdk/build-tools/34.0.0/apksigner sign --ks ~/olvid_fdroid_keystore.jks --ks-key-alias fdroid --out olvid-signed.apk olvid-unsigned.apk
```

Of course, our keystore is not provided here, so if you want to sign your own APK, use your own keystore. You may follow the official [Android documentation](https://developer.android.com/build/building-cmdline#sign_cmdline) on this.


## Verifying the reproducibility yourself

To check if the APKs match, first download the official Olvid APK from the [F-Droid Olvid page](https://f-droid.org/en/packages/io.olvid.messenger.nogoogle/) and save it next to the APK you generated. We assume you have both APK in the same folders, with the names `olvid-unsigned.apk` (the one you generated) and `olvid-fdroid.apk` (the one from F-Droid).

To verify that the APKs match, you should use [apksigcopier](https://github.com/obfusk/apksigcopier) which can be installed using `pip`, typically in a [Python virtual environment](https://docs.python.org/3/library/venv.html) as you won't be needing this tool on a daily basis 😉

```bash
apksigcopier compare olvid-fdroid.apk --unsigned olvid-unsigned.apk
```

**If you do not get any error message, then the comparison did work and the signatures match.** If unsure, you can further check this by copying the signature from the F-Droid APK to your own unsigned APK and then verifying the signature with the official `apksigner`.

```bash
apksigcopier copy olvid-fdroid.apk  olvid-unsigned.apk olvid-signed.apk
apksigner verify --print-certs olvid-signed.apk
```

You should get an output with quite a few warnings (some parts of the APK, like the LICENSE files, do not need to be signed and result in a warning), but it should start like this.
```
Signer #1 certificate DN: CN=Olvid, O=Olvid, L=Paris, C=France
Signer #1 certificate SHA-256 digest: 486d981d590c1e28ba218d4227f1c10dd7c5b158dfa1336055be752fd933f8bc
Signer #1 certificate SHA-1 digest: 2b3f9df459cb277ae182d341b27be690c3a63778
Signer #1 certificate MD5 digest: 072e5152e3ae9f5c65e36e5e26c0a7ad
```


## The next steps

For now, we have only focused on reproducible builds for the version of Olvid distributed through F-Droid, but ideally we would like to provide similar guarantees for the version distributed through Google Play. Several factors make this a little more difficult:
- the app is published as an app bundle (`.aab` package), which is then recomposed into different APKs depending on the target device,
- our app contains some credentials (typically, API keys for access to Google Maps or Google Drive) which we currently do not publish.

When we have time to focus on this, we will try to change our publication process to allow such reproducibility on Google Play as well.
