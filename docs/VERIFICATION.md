# Verification · 22 November 2026

Windows host, JDK 21, SDK 35, Gradle 8.10.2, existing Pixel 6 API 35 emulator. Final debug APK SHA-256: `51cfcf5bef584ddd56e78d8dfc496c2584070982829f1abf52477a989c41e6f6`.

- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` and `lintDebug` succeeded. Local checks used a temporary Maven repository populated from the workstation's existing test dependency cache. The project uses the standard Google and Maven Central repositories.
- Five JVM tests passed (four merchant, amount and threshold cases and one saved-draft test). Lint produced an empty issue list.
- Both APKs were installed with ADB. The AndroidJUnitRunner executed **22 tests**: 15 SQLite store cases, six pass/subsidy/offer cases, and one image test decoding all three bundled QR cards. Read [the runner output](verification/android.txt).
- Device walkthrough: selected the supplied PIER image using Android's photo picker, saw Star Pier Coffee and HK$18.50 in the payment form, and confirmed the review. The Campus balance went from HK$159.50 to HK$141.00. The receipt and Activity showed the same debit. In a second run, topping up HK$18.50 restored HK$159.50. Rotating on QR payment review retained the merchant and amount. Confirming again produced HK$141.00. The Campus card and HK$141.00 survived force-stop and relaunch.
- Denied Android approximate location, chose Wan Chai manually and switched to List. The six seeded locations were sorted by distance from that district. The closest displayed point was Tramway Pantry, approximately 150 m away.
- Observed a persisted daily JobScheduler entry and issued one forced run on the emulator. The clock-driven scheduling interval, physical camera capture, real GPS fix, physical shake sensitivity, launcher widget placement and the full notification-permission matrix still need device testing. The QR image-picker and decoder were exercised end to end.
- Independent source review led to fixes for atomic operation/snapshot publication, stale balances following rejection, extras consistency, hidden balances in old alerts and large saved drafts. Regression tests are in the source tree.

[Wallet](../demo/screens/01-wallet.png) · [QR review](../demo/screens/02-qr-review.png) · [Receipt](../demo/screens/03-receipt.png) · [Activity](../demo/screens/04-activity.png) · [Explore](../demo/screens/05-explore.png) · [Passes](../demo/screens/06-passes.png)
