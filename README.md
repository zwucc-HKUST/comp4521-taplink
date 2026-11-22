# Hong Kong Public Electronic Payment and Stored Value System-TapLink

TapLink Astra is an English Android prototype for campus purchases, simulated stored-value cards and transport around Victoria Harbour. Install the demo APK on Android 10 or newer. An initial wallet includes three cards and a small ledger. No login is required.

**Team members and coding agents:** start with the [project handover](AGENTS.md). It covers the working branch, app rules, build commands, verified flows and outstanding group submission tasks.

## Open the project

Clone the `astra-independent` branch and open the directory containing `settings.gradle.kts` in Android Studio. Select Gradle JDK 21 and install SDK Platform 35. To run from Windows, use `.\gradlew.bat assembleDebug`. On macOS or Linux use `./gradlew assembleDebug`. Select a phone or emulator with API 29+ and run the app configuration.

The launcher label is **TapLink Astra** and the package is `hk.taplink.studio`. Android stores this installation's database and preferences separately.

## Try the merchant QR

Use Wallet → Pay → Choose QR image. Choose [Star Pier Coffee](demo/qr/PIER.png), review HK$18.50, and confirm. On an emulator, copy the image into Downloads first. For a physical camera, display the image on another screen and choose Photograph QR. Each starter code in [demo/qr](demo/qr) uses a locally recognised merchant and a positive HKD amount.

## Where to make changes

The application lives in `app/src/main/java/hk/taplink/studio/`. `Store.kt` owns the SQLite ledger, cards and preferences. `WalletController.kt` runs database operations off the main thread and publishes wallet snapshots to Compose. Each main screen is a separate file. The guide's places live in `ExploreCatalogue.kt`, merchant codes in `MerchantCode.kt`, and fictional pass and reward rules in `ExtrasStore.kt`. The widget and daily job use Android platform APIs.

Build checks: `./gradlew testDebugUnitTest lintDebug assembleDebug`. For instrumented tests, run `./gradlew connectedDebugAndroidTest` with a connected device. Alternatively build and install both debug APKs, then invoke `adb shell am instrument -w hk.taplink.studio.test/androidx.test.runner.AndroidJUnitRunner`.

- [Feature matrix](docs/FEATURE_MATRIX.md)
- [Demo script](docs/DEMO.md)
- [Report draft](docs/REPORT_DRAFT.md)
- [GenAI acknowledgement](docs/AI_USE.md)
- [Executed checks](docs/VERIFICATION.md)
- [Team metadata](TEAM.json)

Before course submission, the group should review the report, complete the cover and actual contribution details, and record the presentation video.
