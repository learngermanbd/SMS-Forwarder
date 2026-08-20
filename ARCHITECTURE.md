# SMS FORWARDER blueprint

## Product promise

A calm, transparent relay for a phone owner who wants selected Bangladesh mobile-finance notifications mirrored to a private Telegram channel. The user can see what will be matched, what will be redacted, and whether the last delivery succeeded.

## Privacy and abuse boundaries

- Matching happens locally before any network request.
- The default allow-list is only bKash, Nagad, and Rocket.
- OTPs, PINs, verification codes, and long sensitive digit sequences are dropped or redacted before delivery.
- Do not forward contact lists, arbitrary SMS, or messages from unknown senders.
- Store the Telegram bot token in encrypted storage; never place it in source, logs, analytics, screenshots, or WorkManager input data.
- Require an explicit enable action and a visible connection state.
- Provide pause, delete-history, and test-send controls.

## Runtime data flow

```text
SMS_RECEIVED
   -> SmsReceiver
   -> MessageFilter (on-device allow-list + sensitive-content guard)
   -> redacted relay payload
   -> WorkManager queue (network constraint + exponential retry)
   -> Telegram Bot API sendMessage
   -> delivery status + local audit entry
   -> Dashboard / Activity UI
```

MMS/WAP push is intentionally not used as a financial-message source. Bangladesh wallet notifications are normally SMS, and parsing arbitrary MMS parts would expand data access without improving the core use case.

## Suggested package structure

```text
app/src/main/java/com/pulserelay/app/
├── MainActivity.kt                 # Compose entry point
├── data/
│   ├── LocalConfigStore.kt         # Encrypted token, chat ID, rule settings
│   ├── RelayHistoryStore.kt        # Encrypted or SQLCipher-backed audit data
│   └── RelayRepository.kt
├── domain/
│   ├── MessageFilter.kt             # Pure matching/redaction rules
│   ├── RelayModels.kt
│   └── ValidateTelegramConfig.kt
├── platform/
│   ├── SmsReceiver.kt
│   ├── RelayWorker.kt
│   └── NotificationChannels.kt
├── network/
│   └── TelegramBotClient.kt
└── MainActivity.kt                     # Compose UI entry point
```

## UI structure

- **First launch:** guided setup flow covering Telegram connection, trusted sender selection, privacy filters, and relay controls; can be reopened from Settings.
- **Dashboard:** connection health, matched count, queue state, last safe event, pause/resume.
- **Rules:** three provider cards, sender matching explanation, privacy redaction switch, rejected-message count.
- **Settings:** Telegram token and channel ID, test send, notification settings, delete data, privacy explanation.
- **Activity (next phase):** redacted delivery timeline with success/retry/rejected states.

## Delivery and failure behavior

- Enqueue only an already-filtered/redacted payload.
- Use WorkManager with network connectivity and exponential backoff.
- Never retry a message that failed because it contains sensitive content.
- Keep a bounded, redacted local audit history; allow one-tap deletion.
- Surface actionable errors: invalid token, bot lacks channel permission, offline, rate-limited, or paused.

## Definition of done for v1

- A test SMS from each provider is matched correctly.
- Unknown senders and OTP messages never leave the device.
- The app survives reboot and transient network failure.
- Bot token remains encrypted and absent from logs.
- The user can pause, delete, and verify the relay without inspecting raw SMS content.
- Android permission and Play policy implications are documented before distribution.
