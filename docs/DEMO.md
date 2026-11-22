# TapLink Astra — Host walkthrough

Use an Android 10 or newer demo device with Astra installed. For file selection, copy the PNGs below into its Downloads folder. For the camera route, show a PNG on a second screen and use **Photograph QR**. Reading a QR fills in the merchant and amount on the payment form. The host decides whether to confirm.

| Demo file | Merchant | Amount |
| --- | --- | ---: |
| [PIER.png](../demo/qr/PIER.png) | Star Pier Coffee | HK$18.50 |
| [FERRY.png](../demo/qr/FERRY.png) | Harbour Ferry | HK$8.20 |
| [BOOKS.png](../demo/qr/BOOKS.png) | Paper Lantern Books | HK$42.00 |

## One purchase, including a rejected attempt

1. In **Settings**, turn off **Automatic reload** and **Hide balances** for this example. In **Wallet → Cards → Create card**, create a fresh card named *Demo run*. Select its row, tap **Done** and point out the HK$0.00 starting balance.
2. Tap **Top up**, choose **HK$100**, then **Review top-up → Confirm simulated top-up**. Point to the funding source and resulting balance on the receipt. Tap **Done**.
3. Tap **Pay**, enter **100.01** and tap **Review payment**. Give the audience a moment to identify why the card cannot cover the purchase. Show the insufficient-funds message. Tap **Cancel** and compare the balance and recent activity with step 2. The expected state is HK$100.00 and a single top-up.
4. Open **Pay → Choose QR image** and select **PIER.png**. Point out *Star Pier Coffee* and *18.50* before tapping **Review payment**. If the picker or camera is unavailable, select the same merchant and enter **18.50**. At review, identify the card and let the audience decide whether to edit or confirm. **Confirm simulated payment** should produce a receipt for −HK$18.50 with HK$81.50 remaining.
5. Tap **Done**, open **Activity → Filters**, choose *Demo run* and tap **Apply filters**. Show both entries. The current month’s spending should total HK$18.50 under Food. Search for *Star Pier*, open the payment and tap **Share receipt**. Dismiss the chooser and close the receipt.

## Pass, subsidy and coupon expiry (F15–F16)

1. From **Wallet → Passes & offers → Transport**, tap **Review pass purchase** and confirm **Pay HK$60.00**. Read the displayed start and last valid dates. On the example card, this purchase alone reaches the transport threshold. Tap **Review subsidy claim → Claim credit** and show the HK$12.00 claim in the card’s history.
2. Open **Offers → Offer details** for *Pocket sketchbook*. Choose **Save to favorites**, note its last valid date and leave it unredeemed. To show one-time redemption, redeem a **different** offer and reopen its details.
3. **Optional live expiry.** On a disposable demo emulator, note the pass and sketchbook dates first. Set the emulator to Hong Kong time, turn off automatic date/time and move to noon on the day after the **later** last valid date. Reopen **Passes & offers**. Under **Transport**, point to **Expired** on the original card. Under **Offers → Favorites**, open the sketchbook and show **Demo coupon expired** and the absence of the redemption action. Restore automatic date/time afterwards. On a shared device, leave the clock alone and use the displayed validity dates to explain the boundary.

## Short extensions

- **Reload and alert.** To demonstrate from step 4’s HK$81.50 balance, do this before buying the pass. Set the threshold to HK$80 and the reload amount to HK$25. With automatic reload on, pay using **FERRY.png**. The receipt should show HK$73.30 after payment and HK$98.30 after reload. Turn reload off, enable low-balance alerts and pay using **BOOKS.png**. Open **Settings → Inbox** to show the alert at HK$56.30. Top up before continuing to the HK$60 pass.
- **Location fallback.** In **Explore**, tap **Use my location** and decline if prompted. Choose **Choose district → Central**, switch to **List** and open a transport stop. Ask where its distance is measured from, then switch to **Map** to show the same stop.
- **Freeze and privacy.** In **Wallet → Cards**, select the demo card and confirm **Freeze**. Return to Wallet to show the disabled **Pay** and **Top up** actions. Unfreeze it to continue. Hide the balance with the eye button and show the masked amount on the launcher widget if one is installed.


## Show the team edition

- After a linked reload, open the debit and reload separately in Activity. Both identify the same purchase receipt and show the intermediate balance, credit and final balance. Tap **Share receipt** to inspect the text in the Android chooser.
- Buy a pass and claim the eligible fictional credit. In **Activity → Filters**, choose **Pass purchase**, then **Subsidy credit**. Each type isolates the relevant record. A subsidy contributes zero to spending.
- In **Settings**, **Show balance on widget** controls the launcher balance. **Hide balances** overrides it. For a launcher demo, add the widget before recording.
