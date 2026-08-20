# SMS FORWARDER

SMS FORWARDER is an Android SMS notification relay for personal, consent-based use. It recognizes Bangladesh mobile-finance alerts from **bKash**, **Nagad**, and **Rocket**, applies privacy filters on-device, and can send a redacted event to a Telegram channel through a bot.

> **Safety default:** financial SMS messages can contain OTPs, PIN hints, account identifiers, and balances. SMS FORWARDER must not forward OTPs, PINs, or an unredacted message. Use it only on a phone and Telegram channel you own or have explicit permission to monitor.

## Current foundation

- Kotlin + Jetpack Compose + Material 3
- Premium dark dashboard UI with Dashboard, Rules, and Settings sections
- Provider rules for bKash, Nagad, and Rocket
- On-device provider matching and sensitive-number redaction
- Duplicate transaction ID detection with a clearly marked Telegram scam alert
- Pure Kotlin filter and detection logic that can be unit tested
- Android SMS receiver and Telegram delivery worker skeleton
- First-launch setup guide for Telegram, trusted senders, privacy filters, and relay controls

## First-launch setup guide

New installs open a guided setup flow before the dashboard. It explains the privacy model, walks through Telegram bot and channel setup, prompts the user to choose trusted SMS senders, and explains redaction, OTP blocking, balance hiding, and relay controls. The guide can be skipped and reopened later from **Settings → Open setup guide**.

## Build locally

1. Open this folder in Android Studio Ladybug (or newer).
2. Let Android Studio create/download the Gradle wrapper if this checkout does not already contain one.
3. Run the `app` configuration on an Android 10+ device/emulator (API 29+).
4. Grant SMS permission only on a test device where you have consent.
5. Configure the Telegram bot token and channel ID in the app. Never commit either value.

The app is intentionally not configured with a real bot token, chat ID, or production signing key.

## Android support matrix

- **Minimum supported:** Android 10 / API 29.
- **Target SDK:** API 35 (Android 15).
- **Android 10–15:** the project is configured and testable against these platform levels when the matching SDKs are installed.
- **Android 16–17:** the app is designed to remain compatible through Android's forward-compatibility behavior, but Android 17-specific APIs cannot be compiled until the corresponding official SDK is available. Re-test each new platform release before distribution.

The release APK is an unsigned release artifact for sideload testing. A signed APK/AAB requires a private keystore that must never be committed.

## Duplicate / scam detection

Every accepted receipt is checked for a transaction reference (TrxID, Transaction ID, Txn ID, or Tx ID). The first time an ID appears it is recorded in encrypted local storage and the receipt is relayed normally. If the same ID appears again, SMS FORWARDER:

1. Marks the event as a scam alert in the app (the dashboard scam counter increments).
2. Sends a distinct `🚨 SCAM ALERT — duplicate transaction ID` message to Telegram instead of a normal receipt.

Phone numbers are never mistaken for transaction IDs, and the tracked-ID registry is capped to keep storage bounded.

## Telegram setup

1. Create a bot with BotFather.
2. Add it as an administrator to a private channel with permission to post messages.
3. Find the channel chat ID using a controlled test chat or a trusted Telegram API client.
4. Save the token only in the app's encrypted local settings.
5. Test with a non-financial sample notification before enabling the receiver.

SMS FORWARDER sends Telegram status notifications when configuration or forwarding state changes:

- `✅` Telegram API setup succeeded.
- `❌` Telegram API setup failed (sent through the previous working configuration when available).
- `🟢` SMS forwarding is ON.
- `⚪` SMS forwarding is OFF.

A failure notification cannot be delivered when Telegram itself is unreachable or the only available credentials are invalid; the app shows that failure locally instead.

## Android policy and platform notes

SMS permissions are restricted by Google Play policy. A production Play release may require SMS FORWARDER to be the default SMS handler or qualify for an allowed exception. For a personal sideloaded build, obtain informed consent and follow local law. Most bKash/Nagad/Rocket transaction alerts arrive as SMS; MMS/WAP push is not a reliable source for these notifications and should not be treated as equivalent to SMS.

## Planned phases

1. **Foundation:** UI, rules, redaction, local encrypted configuration.
2. **Reliable relay:** receiver, WorkManager queue, retry/backoff, delivery status, offline handling.
3. **Production hardening:** encrypted local history, notification permission, battery-exemption education, export/delete controls, accessibility, localization, and device testing across Android versions.
4. **Release:** security review, abuse/consent safeguards, Play policy decision, signed APK/AAB, and operational documentation.
