# Stream Pop (Fabric 1.21.11, client-side)

Twitch chat as small, see-through black cards with light-blue accents in a corner of your screen.
No login needed - it reads the public chat of any channel.

## Keys (change them in Options > Controls > Key Binds > Stream Pop)
- K  open the settings window
- J  connect / disconnect chat

## Settings window
Channel, CHAT ON/OFF, send test message, corner (4 choices), background see-through slider,
cards on screen, seconds shown, card width, role badges, hide !commands, auto-connect, sound ping.
History tab: recent messages with a "block" button per user.

## Commands
/streampop channel <name> | connect | disconnect | test <text>
/streampop banword add|remove <word>   /streampop blockuser add|remove <name>
/streampop offset <x> <y>    (distance from the screen edge)

Config: config/streampop.json
