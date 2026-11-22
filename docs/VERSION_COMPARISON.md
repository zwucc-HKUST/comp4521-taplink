# Choosing a version for the group demonstration

This comparison describes the original two prototypes. The team edition now incorporates the receipt, transaction filters and widget privacy improvements on `astra-independent`. [Current handover](../AGENTS.md) · [Current verification](VERIFICATION.md)

The standalone Astra implementation was built and tested before this comparison. Both versions run side by side on the same API 35 emulator. Open the [screen comparison](comparison.html) as a downloaded HTML file to switch between four pairs of screenshots offline.

| Decision | Main · `1858cff` | Astra · `2334eac` |
| --- | --- | --- |
| Live payment demo | Select a merchant manually. The full-screen receipt makes the debit, automatic reload and final balance easy to show together. | Select one of the supplied QR images. The real decoder fills merchant and amount, rotation retains the review, and a second tap confirms. |
| Spending and nearby guide | A ring chart and separate pass/subsidy transaction types support a detailed spending walkthrough. | A compact chart leaves room for transactions. A labelled harbour map and six numbered places make district selection visible. Passes use the Payment type and subsidies use Top-up. |
| Development and evidence | Room, an explicit repository contract and WorkManager. 51 JVM and 29 Android tests. The demo ledger was also inspected after the run. | SQLite transactions and Android JobScheduler. 5 JVM and 22 Android tests cover monetary rollback, competing operations, pass eligibility, coupons and all three QR files. |
| Same negative input | HK$99,999 at Campus Cafe produced an insufficient-balance message. Home still showed HK$120.00. | HK$99,999 at Star Pier Coffee produced an insufficient-funds message. Wallet still showed HK$141.00. |

For a short presentation, choose Astra and demonstrate QR → review → receipt → Activity, followed by service discovery with location denied. For an engineering discussion, Main has the more detailed acceptance and ledger evidence. Both are educational simulations. Physical camera capture, widget placement and long-duration scheduling need hardware testing.

[Main source](https://github.com/zwucc-HKUST/comp4521-taplink/tree/1858cff) · [Main verification](https://github.com/zwucc-HKUST/comp4521-taplink/blob/1858cff/docs/VERIFICATION.md) · [Astra verification](VERIFICATION.md)
