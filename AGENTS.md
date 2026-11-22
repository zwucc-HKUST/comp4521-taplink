# TapLink team handover

Hong Kong Public Electronic Payment and Stored Value System-TapLink is an English COMP 4521 Android course project. A student can load a demo card, pay a simulated merchant, inspect spending and explore service points around Victoria Harbour. All money and entitlements are local demo data. A fresh installation works without an account, network service or API key.

## Start here

The team handoff is on **`astra-independent`**. The repository opens on `main` by default. Both branches contain complete apps with independent Git histories, Android package IDs and databases. Use the Astra branch as the base for further work on this version. The original version remains useful as an engineering reference. [Earlier side-by-side comparison](docs/VERSION_COMPARISON.md)

```sh
git clone --branch astra-independent https://github.com/zwucc-HKUST/comp4521-taplink.git
cd comp4521-taplink
git switch -c team/my-change
```

Open the folder containing `settings.gradle.kts` in Android Studio. Select **Gradle JDK 21**, install Android SDK Platform 35, and choose an API 29+ emulator or device. The launcher label is **TapLink Astra**, package `hk.taplink.studio`. An existing clone can fetch the remote and switch branches in a clean checkout. Target `astra-independent` when opening a pull request.

For a demo without rebuilding, download **TapLink-Team-Handoff** from a successful push or manual run on this branch. Extract the handoff ZIP to find the debug APK, source, screenshots, QR samples and report materials. The SQLite database belongs to the installation. A debug APK from a different signer may require a separate emulator or signing with the same key to retain existing demo data. [GitHub Actions](https://github.com/zwucc-HKUST/comp4521-taplink/actions/workflows/android.yml?query=branch%3Aastra-independent)

## Current delivery

Version 1.1 keeps Astra’s QR flow and harbour map. The original main branch contributed three design improvements: a receipt that explains an automatic reload, distinguishable pass and subsidy entries, and separate launcher balance consent. Stored transactions retain their original ledger kinds. Wallet and Activity derive display labels from the operation and share one receipt model. The SQLite v1-to-v2 upgrade adds a default-off widget preference.

On the emulator a HK$30 payment plus a HK$50 reload appeared under receipt #12 with HK$120 remaining. A linked reload opened the same receipt. With balances hidden, Android’s share preview masked both balance fields. The demo ledger later included a HK$60 pass, a second reload and a HK$12 subsidy. [Executed acceptance checks](docs/VERIFICATION.md) · [Read-only ledger evidence](docs/verification/team-ledger.json)

## What to understand before changing the app

| Change | Start in `app/src/main/java/hk/taplink/studio/` | Behavior to preserve |
| --- | --- | --- |
| Payment, top-up and linked automatic reload | `Store.kt`, `WalletController.kt`, `WalletTransaction.kt` | Amounts use integer HK cents. Validate the original debit before reload. Commit the money movement, ledger, alert and new snapshot consistently. Replaying a confirmed operation key returns the original result. |
| Receipt, history and chart | `ReceiptDetails.kt`, `ReceiptDialog.kt`, `HistoryKind.kt`, `ActivityScreen.kt` | Both entry points describe the same stored operation. Only debits contribute to spending. A pass counts once. A subsidy or reload contributes zero. Semantic types use the persisted operation key and ledger kind, so older records retain their meaning. Preserve explicit balance hiding in both display and share text. |
| Travel pass and coupons | `ExtrasStore.kt`, `Extras.kt`, `ExtrasOffers.kt` | The HK$60 pass and HK$12 subsidy use fictional rules and Hong Kong calendar dates. The main prototype uses different amounts and eligibility periods. Pass, subsidy and coupon writes must remain safe on retry. |
| QR and service discovery | `MerchantCode.kt`, `MerchantScan.kt`, `ExploreCatalogue.kt`, `ExploreLocation.kt` | QR fills the form for user review. Local PNG fixtures are in `demo/qr`. Location denial must still allow manual district selection. |
| Preferences, widget and reminders | `SettingsScreen.kt`, `TapWalletWidget.kt`, `Platform.kt`, `TapMaintenanceJob.kt` | The widget needs its own balance opt-in, and the wallet-wide hide switch takes precedence. Preserve old installations when updating the schema. Reminder scheduling is deferrable. |

Check the [18 proposal requirements](docs/proposal/feature_requirements.json), [current feature matrix](docs/FEATURE_MATRIX.md), and [verification results and limits](docs/VERIFICATION.md) before describing a feature in the report. The screenshot comparison records the two original builds. Use the current verification record for changes made since that comparison.

## Build, verify and package

From the project root run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`. On Windows use `.\gradlew.bat`. Run instrumented tests on a connected emulator with `connectedDebugAndroidTest` or the following direct runner after the APKs have been built.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w hk.taplink.studio.test/androidx.test.runner.AndroidJUnitRunner
```

For a wallet change, trace one operation through the receipt and Activity. A fresh card funded with HK$100, auto reload set to HK$50 at a threshold of HK$80, and a HK$30 purchase should finish at HK$120 with HK$30 in spending. Check the declined-payment path too. For a schema change also install over the previous Astra version with existing cards and history. For UI changes look at the actual emulator screen and exercise rotation or the permission fallback relevant to the edit. [Demo walkthrough](docs/DEMO.md)

After committing the revision you intend to deliver, run `python tools/package.py` from a clean checkout with JDK 21 and the Android SDK available. The packager invokes Gradle for that checkout before bundling the APK. `BUILD_INFO.json` in the archive identifies the source commit and APK checksum. Hosted CI covers unit tests, lint, compilation and handoff packaging on pushes. The Android runner above supplies separate device evidence.

## Remaining group decisions and submission

- The original proposal supplies the scope. The [course submission notes](docs/COURSE_REQUIREMENTS.md) link the Canvas rubric, cover template, program/report submission and presentation submission.
- By **30 November 2026 at 23:59 Hong Kong time**, the group needs the complete program ZIP and a Word or PDF report of at most 5,000 words. The presentation video is a separate deliverable, at most 20 minutes. Use the assigned group number in the course filenames.
- [TEAM.json](TEAM.json) contains the currently known member. Confirm the group number, missing names/SIDs and actual human contributions with the team. Apply the official cover and review the [report draft](docs/REPORT_DRAFT.md) before exporting it.
- The [GenAI disclosure](docs/AI_USE.md) covers substantial design, coding, testing, review and documentation assistance. Update both the program disclosure and report for subsequent work. Each person should confirm their own contribution.
- Hardware checks still open include physical camera capture, actual launcher widget placement and shake sensitivity, plus normal daily reminder timing under Android power restrictions. The emulator QR image path and database tests have separate recorded evidence. Pick a physical-device check or a report/demo task if joining the project late.
