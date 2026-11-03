# Google Navigation inside DriverGuard

## Google Cloud setup

1. Open https://console.cloud.google.com/ and select the project you want to bill.
2. Link an active billing account (add your payment card).
3. In APIs & Services > Library, enable **Navigation SDK**.
4. In APIs & Services > Credentials, create a separate API key for Android navigation.
5. Restrict the key to **Android apps**, using the installed app's package name and signing certificate SHA-1:
   - Online debug/release: `com.example.driverguard`
   - Local emulator build: `com.example.driverguard.local`
   - Debug and release signing certificates have different SHA-1 fingerprints. Use `gradlew.bat :app:signingReport` in Mobile/DriverGuard to inspect them. Add only the combinations you actually install.
6. Under API restrictions, allow **Navigation SDK**. Do not reuse or alter the Firebase key.
7. Set usage quotas and billing alerts in Cloud Console. Alerts alone do not stop usage.

## Configure the build

Add this line to `Mobile/DriverGuard/local.properties` (keep any existing sdk.dir entry):

```properties
googleNavigationApiKey=YOUR_ANDROID_NAVIGATION_KEY
```

Alternatively set `GOOGLE_NAVIGATION_API_KEY` in the environment before building. It takes precedence over local.properties. Do not commit the key. Android API keys are packaged into APKs, so package/SHA-1 and API restrictions are required.

Build the APK again with the existing Build-OnlineAndroid.ps1 or Build-LocalAndroid.ps1 script. A key added after the APK was built will not affect that APK.

## Behavior and verification

- Start a trip and enter the monitoring screen. The map uses Google Navigation SDK in the same activity as the camera analysis and drowsiness alerts.
- Grant precise location, then tap **Bắt đầu dẫn đường**. Accept Google's Navigation terms when prompted. Only an explicit start requests a route; recomposing UI or receiving GPS updates does not request a new destination.
- Camera preview stays hidden until the camera-check control is opened. Only one camera preview is mounted at a time.
- Verify voice instructions, location following, drowsiness alerts and the camera-check dialog on a physical phone.
- Tap **Dừng dẫn đường**, navigate away, finish the trip, and verify guidance stops. Returning to the map requires an explicit start.
- Test missing key, denied precise location, invalid address, lost network and SDK authorization errors. The app reports errors without launching external Maps.
- Use a full delivery address or `latitude,longitude`. Maps URLs are rejected rather than guessing a destination. Address geocoding currently uses Android Geocoder; it may be unavailable or return an ambiguous address. Check the displayed destination before driving. Do not treat this as verified truck routing; the current routing profile is DRIVING.
- No physical-device or billing-enabled test has been completed in this environment. Gradle compilation was attempted, but the sandbox blocked the default cache and connections to a Gradle daemon with a writable cache. Build and acceptance testing remain required.

SDK version 7.9.0 is pinned to match the current compile/target API 36 toolchain. Moving to 8.x requires reviewing Google's API 37 requirements.

Official references:
- https://developers.google.com/maps/documentation/navigation/android-sdk/setup-overview
- https://developers.google.com/maps/documentation/navigation/android-sdk/route
- https://developers.google.com/maps/documentation/navigation/android-sdk/pricing
