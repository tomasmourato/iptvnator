---
type: fix
area: playback
---

Streams whose video the built-in player cannot decode (for example HEVC in some
live channels) no longer sit on the loading screen with sound only: after a few
seconds the player reports an unsupported codec, and the Android app offers another player.
