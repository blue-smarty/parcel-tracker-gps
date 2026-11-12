# Mobile Courier App

This Flutter client targets iOS and Android. It shares the existing courier API and supports sign-in, active deliveries, accept/reject, status updates, foreground GPS, navigation handoff, barcode/photo/signature proof, and encrypted offline retries.

## Bootstrap and run

Install the Flutter SDK and platform toolchains, then generate the native project files from this directory:

```sh
flutter create --platforms=android,ios --org com.parceltracker .
flutter pub get
flutter run --dart-define=API_URL=https://your-api.example.com
```

The Android project is generated locally and is not included in this repository. If Flutter reports that Android Gradle Plugin (AGP) 9.1.0 is below its minimum supported version, update the `com.android.application` plugin version in the `plugins` block of `android/settings.gradle.kts` (or `android/settings.gradle`) to at least `9.1.1`, then rerun the build. For example, in the Kotlin file the declaration is:

```kotlin
id("com.android.application") version "9.1.1" apply false
```

Keep any newer compatible version already present. If Gradle reports an incompatible wrapper version after the AGP upgrade, update `android/gradle/wrapper/gradle-wrapper.properties` to the Gradle version required by that AGP release. Do not bypass Flutter's dependency validation.

Set `API_URL` to the backend's HTTPS origin. For a local Android emulator use `http://10.0.2.2:8080`; for an iOS simulator use `http://127.0.0.1:8080`. Physical devices need a reachable host address. Do not use plain HTTP outside local development.

Android 9+ blocks plain HTTP by default. For local development with an `http://` `API_URL`, add `android:usesCleartextTraffic="true"` to the `<application>` tag in `android/app/src/main/AndroidManifest.xml`, and remove it for release builds. iOS also blocks plain HTTP unless an App Transport Security exception is added to `Info.plist`; prefer HTTPS where possible. If sign-in reports "Connection refused" or a timeout, check that the backend is running and the `API_URL` host is reachable from the device.

## Native permissions

Add the following Android permissions to `android/app/src/main/AndroidManifest.xml` under the root `<manifest>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.CAMERA" />
```

Add usage descriptions to `ios/Runner/Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>Share your location while you are delivering assigned parcels.</string>
<key>NSCameraUsageDescription</key>
<string>Scan parcel barcodes and capture proof of delivery.</string>
<key>NSPhotoLibraryUsageDescription</key>
<string>Capture a drop-off photo for proof of delivery.</string>
```

Location is shared only while the app is active and permission is granted; this app does not request background location. The app asks for location when signing in and the OS camera permission when scanning or capturing proof.

## OpenStreetMap

The delivery map shows the courier's current device location and assigned delivery destinations with coordinates. Map tiles are provided by OpenStreetMap's standard tile service and the map displays the required contributor attribution. The public tile service has a usage policy and is not a guaranteed production tile host; use a tile provider suited to your expected traffic for production deployments.

## Backend accounts and security

Create the first courier account by setting `PARCEL_BOOTSTRAP_COURIER_ID` and `PARCEL_BOOTSTRAP_PASSWORD` when starting the backend. The password must be at least 12 characters. The bootstrap is non-overwriting and can stay configured across restarts; provision additional accounts through a trusted operator. Courier accounts are stored in PostgreSQL with BCrypt password hashes. Basic credentials are sent with each API request, so deploy the backend behind HTTPS and use a trusted certificate.

The encrypted offline queue is separate for each courier on a device. It retries queued actions when the courier refreshes the delivery list or returns to the app. Pending queued actions remain on the device after sign-out. The client obtains a CSRF token from `GET /api/csrf` and sends it with state-changing API requests.

## Device verification and releases

Before release, test sign-in and denied access with two courier accounts, each status transition, location permission denial/recovery, GPS updates while foregrounded, camera and barcode permissions, proof uploads, offline queuing/retry, and sign-out on physical iOS and Android devices.

Build Android with `flutter build appbundle --release --dart-define=API_URL=https://your-api.example.com`. Build iOS with `flutter build ipa --release --dart-define=API_URL=https://your-api.example.com`. Android signing configuration, an Apple Developer account, provisioning profiles, certificates, App Store Connect metadata, privacy disclosures, and store review remain deployment-owner responsibilities; release signing and physical-device tests require those external assets and devices.
