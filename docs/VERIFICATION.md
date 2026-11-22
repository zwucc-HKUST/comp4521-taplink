# Verification · team edition 1.1

Verified on 22 November 2026 on a Pixel 6 API 35 emulator. Implementation revision `70ec160`. Local debug APK SHA-256: `533e10a6c79e21eea6c6d4173c1deb354a20b60c39d31ba26e77fc397fb2c3d3`. The archive's BUILD_INFO.json identifies the final handover commit. A GitHub-hosted debug APK can differ due to signing and build environment.

**12 JVM tests and 26 Android instrumentation tests passed. Android lint reported zero issues.** The Windows build used JDK 21, Gradle 8.10.2 and Android SDK 35. The final tasks were assembleDebug, assembleDebugAndroidTest, testDebugUnitTest and lintDebug. A temporary repository built from the workstation dependency cache supported offline local verification.

## Automated checks

| Executed check | Coverage |
| --- | --- |
| JVM · 12 | Amount and merchant QR validation (4), saved draft (1), existing-row classification (3), receipt amounts, linking and masking (4). |
| Android · 26 | Store (15), pass and offers (6), QR image decoder (1), widget migration/consent/redaction (3), hosted widget recovery (1). |
| Android widget recovery | A test host bound a widget, injected failures in both the ordinary and masking publications, then ran the scheduled recovery job. It verified that the host received the balance visibility stored at recovery time. |
| Lint | [Machine-readable result](verification/lint.xml). |

The Android APK and test APK were installed using ADB. The [instrumentation output](verification/android.txt) records the device-run result. JVM suite reports are in the same directory.

## Installed-app acceptance

| Action | Expected and observed |
| --- | --- |
| Upgrade an existing Astra installation | Before upgrading, the wallet held 4 cards and 11 entries. After in-place installation and opening the app, read-only SQLite snapshots matched all cards, entries and existing preferences. Database version advanced from 1 to 2 and widget balance consent was off. |
| Top up HK$100, pay HK$30, reload HK$50 below HK$80 | The purchase receipt showed HK$70 immediately after payment and HK$120 after reload. Opening the reload row from history returned the same receipt #12 and final HK$120. [Receipt screenshot](../demo/screens/07-team-reload-receipt.png) |
| Hide balances and share the historical receipt | Android’s chooser preview masked both the intermediate and final balances. The purchase amount and linked reload amount remained visible. |
| Buy the HK$60 pass, then claim HK$12 | Filtering Activity by **Pass purchase** isolated one HK$60 debit and HK$60 of spending. Filtering by **Subsidy credit** isolated one HK$12 credit and HK$0 of spending. [Pass filter](../demo/screens/08-team-pass-filter.png) · [Subsidy filter](../demo/screens/09-team-subsidy-filter.png) |
| Reconcile the ledger | Six stored rows on the Team card reconciled to HK$122 after a second automatic reload and the subsidy. Only the purchase and pass contributed to the total HK$90 spending. SQLite integrity check returned `ok`. [Read-only evidence](verification/team-ledger.json) |

The 1.0 walkthrough additionally exercised the Android image picker, rotation during QR review, force-stop/relaunch and location denial with manual district selection. [Archived baseline evidence](https://github.com/zwucc-HKUST/comp4521-taplink/blob/3edb217/docs/VERIFICATION.md)

## Remaining device coverage

Physical camera capture, a real location fix, physical shake sensitivity, normal scheduling under power restrictions and a user's actual launcher layout remain to be exercised. The hosted-widget regression used an emulator test host and a forced job run. If Android rejects all widget updates, immediate masking on the launcher cannot be guaranteed. The app attempts redaction and schedules bounded retries that read the current privacy preference.
