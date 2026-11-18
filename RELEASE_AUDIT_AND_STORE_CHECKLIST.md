# ZeroX Boost — Final Release Audit & Store Readiness Checklist

## 1. Application Identity & Build Configuration
- **App Name:** `ZeroX Boost` (`res/values/strings.xml`, `metadata.json`, `settings.gradle.kts`)
- **Application ID:** `com.aistudio.gameboost.gmqxw`
- **Namespace:** `com.example`
- **Version:** `versionCode = 1`, `versionName = "1.0.0"`
- **SDK Levels:** `minSdk = 24` (Android 7.0), `targetSdk = 35` (Android 15), `compileSdk = 36`
- **Build Optimization:** `isMinifyEnabled = true` (R8 code shrinking/obfuscation) and `isShrinkResources = true` enabled for `release`
- **Release APK Outputs:**
  - `app/build/outputs/apk/release/app-release.apk`
  - `apk/ZeroX-Boost-Release.apk`
- **Release Signing Key:** Signed with RSA 2048-bit PKCS12 release keystore (`my-upload-key.jks`, alias `upload`, gitignored; no passwords committed in source code)
  - **Certificate SHA-256:** `e42e4e76e9e91d416d0a4ac99bda9b7fc95dfb9a8bdd04950bc0331a23f94c22`

---

## 2. Monetization & Compliance Verification
- **AdMob App ID:** `ca-app-pub-9880038173911569~6505410503`
- **AdMob Banner Unit ID:** `ca-app-pub-9880038173911569/4673454081`
- **AdMob Native Advanced Unit ID:** `ca-app-pub-9880038173911569/1855719057`
- **Release Ad Safety:** `AdMobConfig.IS_TEST_MODE` defaults to `BuildConfig.DEBUG` (`false` in release), `toggleAdMobTestMode()` is locked to `false` in release builds, UMP debug geography/test device IDs are strictly gated on `BuildConfig.DEBUG`, and the developer AdMob diagnostics card in Settings is hidden in release builds (`if (BuildConfig.DEBUG)`).
- **Google Play Billing SKUs:**
  - Monthly Pro Subscription: `gameboost_pro_monthly`
  - Yearly Pro Subscription: `gameboost_pro_yearly`
  - Entitlement verification remains bound strictly to Play Billing query/acknowledge results; no offline bypass or fake entitlement exists.
- **Note for Softonic & Non-Play Android Platforms:**
  - On devices without Google Play Services / Google Play Store, `PlayBillingManager` gracefully enters `SERVICE_UNAVAILABLE`, keeps all core diagnostics, Game Space, Network Lab, Aim Lab, Benchmark Lab, Autopilot, and Floating Sidebar 100% functional for free, and informs users clearly in `PremiumScreen`.

---

## 3. Automated Test & Release Build Verification
- **Unit & Robolectric Test Suite (`gradle :app:testDebugUnitTest`):** **121 / 121 tests passed across 20 test suites** (0 failures, 0 errors, 0 skipped).
- **Release Lint (`gradle :app:lintVitalRelease`):** **Passed** (0 fatal errors).
- **Signed Release APK Build (`gradle :app:assembleRelease`):** **Succeeded** with R8 minification, resource shrinking, and APK Signature Scheme v2 verification.

---

## 4. Physical Device Verification Recommended Before Public Store Submission
Automated JVM/Robolectric tests cannot execute Android 15 HyperOS `WindowManager` IPC, physical battery/gyroscope sensors, live TCP sockets on cellular/Wi-Fi, or Play Console billing sheets. Before publishing on Softonic/stores, smoke-test `ZeroX-Boost-Release.apk` on a physical Android device:
1. **Fresh Install & Permissions:** Verify custom ZeroX Boost launcher icon, onboarding flow, `Granted` / `Not Granted` badges, and `ON_RESUME` status refresh after returning from Android Settings.
2. **Floating Overlay & Background Persistence:** Grant `Display over other apps` (plus Xiaomi HyperOS background pop-up permission if applicable), start a session, drag/expand/collapse the sidebar, and verify clean restoration of only ZeroX Boost-modified settings on exit.
3. **Telemetry & Aim Lab:** Verify real battery temperature (`°C` or `N/A`), display Hz (`Hz`), TCP gateway ping (`ms`), and Aim Trainer Moving/Pulsing modes.
4. **Live Release Billing/Ad Smoke Check:** Confirm AdMob banner/native placement only renders on idle screens with consent and never overlays active gameplay or critical thermal alerts.

---

## 5. Preserving Your Signing Key for Future Updates
**CRITICAL:** Keep `my-upload-key.jks`, alias `upload`, and your store/key password backed up in a secure location outside Git. Every future update (`versionCode = 2+`) on Softonic and Android app platforms must be signed with this exact key so users can update cleanly without uninstalling first.

