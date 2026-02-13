# SMS2Telegram

Barebone background service that forwards your incoming new SMS (text message) into a chat with a telegram bot.
This program does not connect to any intermediate server, it simply issues a new request to telegram bot API each time the phone gets a new SMS.


## Supported System

Only tested on

1. Oneplus 5T on Android 10 (dual SIM)
2. Oneplus 7t on Android 11 (dual SIM)
3. Pixel 3 on Android 11


## Configuration

You'll need (1) a telegram bot key (2) the telegram chat id to which you want the bot to forward.
You should be able to follow https://dev.to/rizkyrajitha/get-notifications-with-telegram-bot-537l to obtain your telegram bot key and chat id.

You'll also want to turn off battery optimisation for this APP to avoid it being killed by the system.

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


## Download

See https://github.com/hyhugh/SMS2Telegram/tree/master/app/release
