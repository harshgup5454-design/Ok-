# GAMEBOOST — Final Release Audit & Multi-Store Readiness Checklist

## 1. Application Identity & Build Configuration
- **App Name:** `GameBoost` (`res/values/strings.xml`, `metadata.json`, `settings.gradle.kts`)
- **Application ID:** `com.aistudio.gameboost.gmqxw`
- **Namespace:** `com.example`
- **Version:** `versionCode = 1`, `versionName = "1.0.0"`
- **SDK Levels:** `minSdk = 24` (Android 7.0), `targetSdk = 36`, `compileSdk = 36.1`
- **Signing Status:**
  - **Debug APK Built:** `apk/GAMEBOOST-Final-Debug.apk` (signed with `debug.keystore`)
  - **Release APK / AAB Signing:** **BLOCKED PENDING USER RELEASE KEYSTORE** — `app/build.gradle.kts` `signingConfigs.release` reads `KEYSTORE_PATH` (or `my-upload-key.jks`), `STORE_PASSWORD`, and `KEY_PASSWORD` (alias `upload`), which are not present in this environment. No placeholder keystore was fabricated.

---

## 2. Monetization & Compliance Verification
- **AdMob App ID:** `ca-app-pub-9880038173911569~6505410503`
- **AdMob Banner Unit ID:** `ca-app-pub-9880038173911569/4673454081`
- **AdMob Native Advanced Unit ID:** `ca-app-pub-9880038173911569/1855719057`
- **Release Ad Safety:** `AdMobConfig.IS_TEST_MODE` defaults to `BuildConfig.DEBUG` (`false` in release), `toggleAdMobTestMode()` is locked to `false` in release builds, UMP debug geography/test device IDs are strictly gated on `BuildConfig.DEBUG`, and the developer AdMob diagnostic/unit-ID card in Settings is hidden in release builds.
- **Google Play Billing SKUs:**
  - Monthly Pro Subscription: `gameboost_pro_monthly`
  - Yearly Pro Subscription: `gameboost_pro_yearly`
  - Entitlement verification remains bound strictly to Play Billing query/acknowledge results; no offline bypass or fake entitlement exists.
- **Note for Non-Play Stores (Uptodown, Aptoide, Samsung Galaxy Store, Huawei AppGallery, Xiaomi GetApps):**
  - On devices without Google Play Services / Google Play Store, `PlayBillingManager` gracefully enters `SERVICE_UNAVAILABLE`, keeps all core diagnostics, overlay tools, smart gaming, and benchmark features 100% functional for free, and surfaces an honest unavailability banner in `PremiumScreen`.
  - For Huawei HMS-only phones without GMS, distribute Play Billing-dependent Pro upgrades as unavailable or integrate Huawei IAP in a dedicated store flavor if commercial Pro sales on AppGallery are targeted.

---

## 3. Automated Test & Compilation Verification
- **Unit & Robolectric Test Suite (`gradle :app:testDebugUnitTest`):** **120 / 120 tests passed across 20 test suites** (0 failures, 0 errors, 0 skipped).
- **Debug APK Build (`gradle :app:assembleDebug`):** **Succeeded**.
- **Release Kotlin Compilation (`gradle :app:compileReleaseKotlin`):** Verified separately for release-mode `BuildConfig.DEBUG == false` branches.

---

## 4. Physical Device Verification Required Before Public Store Release
Automated Robolectric tests cannot execute Android 15 HyperOS WindowManager IPC, hardware sensors, or live OEM background kills. Verify on a real device (specifically Xiaomi Android 15 / HyperOS) before uploading:
1. **First-Launch Onboarding & Permission Center:** Verify explanation dialogs, `Granted` / `Not Granted` / `Not Applicable` badges, Skip/Continue flow, and automatic `ON_RESUME` re-checking after returning from MIUI/HyperOS Settings.
2. **Xiaomi HyperOS Overlay & Background Launch:** Grant `Display over other apps` and enable Xiaomi *Display pop-up windows while running in the background* if prompted by HyperOS. Start a game session -> verify floating sidebar appears -> switch from GAMEBOOST into a third-party game -> drag, expand, and collapse sidebar across both horizontal orientations -> end session -> verify clean overlay dismissal and selective restoration of only GAMEBOOST-modified settings.
3. **Telemetry Honesty Check:** Confirm Battery Temp displays `°C` on real sensors or `N/A` if unsupported, display refresh rate (`Hz`) is never labeled as game FPS, gateway ping (`ms`) is labeled as local gateway latency, and Aim calibration reflects actual swipe path distance.
4. **Live AdMob & Billing Smoke Test (Signed Release Build):** Install signed release APK from Play Internal Testing track to verify live AdMob production fills, UMP consent prompt, and Play Billing product catalog (`gameboost_pro_monthly`, `gameboost_pro_yearly`).

---

## 5. Multi-Store Submission Checklist

| Store | Package Format | Billing / GMS Notes | Required Store Assets & Metadata |
|---|---|---|---|
| **Google Play** | Signed `.aab` | Native Play Billing v7 & AdMob live | Privacy Policy URL, Data Safety (AdMob + UMP + Usage Stats disclosure), Target API 36 compliant, Special Use FGS declaration in Play Console |
| **Samsung Galaxy Store** | Signed `.apk` | Works with GMS installed; Pro degrades gracefully without Play | Age rating, App icon (512x512), 4–8 screenshots, Special Permissions rationale |
| **Xiaomi GetApps** | Signed `.apk` | Works with GMS installed; document MIUI overlay/autostart UX | HyperOS compatibility notes, ICP/developer verification if publishing in CN region |
| **Huawei AppGallery** | Signed `.apk` | Non-GMS devices show Play Billing `SERVICE_UNAVAILABLE` | HMS privacy statement; keep free utility positioning clear if GMS absent |
| **Uptodown / Aptoide** | Signed `.apk` | Standard Android APK distribution | SHA-256 checksum of signed release APK, changelog for `v1.0.0 (1)` |

---

## 6. How to Produce the Final Signed Release Build
1. Place your real upload keystore file (`my-upload-key.jks`, alias `upload`) at the project root or set `KEYSTORE_PATH`.
2. Export signing credentials in your build environment:
   ```bash
   export KEYSTORE_PATH=/path/to/my-upload-key.jks
   export STORE_PASSWORD=your_store_password
   export KEY_PASSWORD=your_key_password
   ```
3. Run:
   ```bash
   gradle :app:assembleRelease :app:bundleRelease
   ```
