# TapLink Astra — Technical report draft

## Purpose and scope

A student paying for a ferry ride needs to know which card will be charged, whether it can cover the fare and how much will remain. TapLink Astra brings those decisions into one Android workflow. The original proposal draws on familiar Hong Kong stored-value card and transport services. Astra uses fictional HKD balances, merchants, passes and subsidies. The intended users are students and commuters. Their needs are design assumptions from the proposal, pending direct feedback.

The standalone implementation was developed with assistance from GPT-6.2 Astra (OpenAI Codex). [AI-use disclosure](AI_USE.md)

## From a card to a spending record

Wallet opens on the selected card and shows its name, masked identifier, status and three most recent entries. A user can switch cards, freeze a lost card or start a payment from the same screen. Choosing a merchant QR fills in the merchant and amount. The user can edit both, review the card and amount together, then confirm. A receipt records the outcome, and Activity lets the user find it again by merchant, card, type, category or date.

The card balance and the spending summary answer different questions. For example, a new card with a HK$100 top-up and a single HK$18.50 coffee payment has HK$81.50 left, assuming automatic reload is off. Filtering Activity to that card and month gives HK$18.50 in spending. The top-up belongs in the history, but counting it in the spending chart would overstate purchases.

## Architecture and transaction integrity

The app uses Kotlin, a single Android activity and Jetpack Compose. It targets API 35 and supports API 29 onward. Screen state holds the amount being edited and the identity of a reviewed payment. An Android ViewModel runs store operations on an I/O dispatcher and publishes the resulting wallet snapshot to the screens. SQLite holds the durable card balances, ledger entries, preferences and inbox. A local database also lets the host demonstrate the wallet offline. [WalletController.kt](../app/src/main/java/hk/taplink/studio/WalletController.kt) · [Store.kt](../app/src/main/java/hk/taplink/studio/Store.kt)

Amounts are stored as whole HK cents. Inside one database transaction, the store checks the card and its available funds, records the payment and updates the balance. If automatic reload is enabled and the payment takes the balance strictly below the threshold, the store records one linked credit. It checks the **final** balance before adding a low-balance message. An unaffordable payment fails before a reload can occur. Each reviewed operation has a unique key. Retrying that operation returns the original receipt ID, which prevents a second charge. The store rejects reuse of the key with different payment details.

## Android integration and design limits

Activity groups records by Hong Kong date and draws category shares in a custom Android Canvas view. Explore ranks six bundled service points by straight-line distance from a district centre or a foreground approximate location. A district choice keeps the guide usable when a visitor declines location access. Android supplies the optional camera, map and sharing apps. The launcher widget follows the selected card and its balance-visibility setting.

A periodic Android job rotates the featured local stop and writes eligible weekly and expiry reminders. Delivery time depends on Android scheduling, and system notifications depend on permission and channel settings. Pass dates and coupon expiry use Hong Kong calendar days. A HK$60 monthly pass counts as transport spending toward one fictional HK$12 credit per card per qualifying month. These are local demonstration rules. The prototype does not issue travel tickets, link bank accounts or settle payments with Octopus or FPS.

## Evaluation questions

Can a first-time user identify the charged card before confirming, explain a linked reload from the receipt and recover from an insufficient-funds error? Can a visitor find a service point after declining location access? These questions connect the proposal’s usability goals to observable actions. The host walkthrough supplies examples for demonstration. Device observations, test outcomes and participant feedback belong in the separate verification record. [Host walkthrough](DEMO.md) · [Feature matrix](FEATURE_MATRIX.md)

## Executed checks

The debug build passed five JVM and 22 Android instrumentation tests on the API 35 emulator, with zero Android lint findings. Selecting the coffee QR image produced a review for HK$18.50. Rotating the device retained that review. Confirmation reduced the selected card from HK$159.50 to HK$141.00, and a restart retained the balance. After approximate location was denied, choosing Wan Chai still produced a distance-sorted service list. Physical camera capture, shake sensitivity, launcher placement and normal daily scheduling remain for device evaluation. [Verification record](VERIFICATION.md)

## Generative AI acknowledgement

The group used GPT-6.2 Astra through OpenAI Codex for design guidance, Kotlin and Compose implementation, test generation, code review and documentation. Group members should record any further assistance and confirm their own contributions before submission. [AI-use record](AI_USE.md)
