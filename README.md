# SMS2Telegram

Lightweight event-driven app that forwards selected phone events into a Telegram chat.
This program does not connect to any intermediate server. It sends requests directly to Telegram Bot API only when configured events happen.


## Supported System

Only tested on

1. Oneplus 5T on Android 10 (dual SIM)
2. Oneplus 7t on Android 11 (dual SIM)
3. Pixel 3 on Android 11


## Configuration

You'll need (1) a telegram bot key (2) the telegram chat id to which you want the bot to forward.
You should be able to follow https://dev.to/rizkyrajitha/get-notifications-with-telegram-bot-537l to obtain your telegram bot key and chat id.

On some OEM firmware, disabling battery optimization may improve reliability for background delivery.

### Forwarded events

- SMS
- Missed calls
- Battery low
- Power connected/disconnected
- Airplane mode changed
- Device boot completed
- Device shutdown
- SIM state changed

New event types are disabled by default after update. Configure them in Settings -> Event Forwarding.

### Telegram control panel

Set `Admin Chat Ids` in settings (comma separated). Only these chats can use commands:

- `/status`
- `/list_events`
- `/enable <event|all>`
- `/disable <event|all>`
- `/help`

Remote control polling is disabled by default to save battery. Enable it in settings only if you need bot commands.

### Battery optimization

- App works in event-driven mode (no permanent foreground service).
- Broadcast receivers are enabled only when sync is enabled.
- Telegram command polling is optional and low-frequency.
- Network work uses WorkManager constraints and exponential backoff.


## Download

See https://github.com/hyhugh/SMS2Telegram/tree/master/app/release

## CI/CD (GitHub Actions)

- On every push/PR to `master`, workflow builds a release APK and stores it as workflow artifact.
- On tag push `v*` (for example `v1.3.0`), workflow additionally:
  - publishes APK as GitHub Release asset,
  - publishes APK into GitHub Packages (GHCR) as OCI artifact.
