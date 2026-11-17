---
type: fix
area: tv-shell
---

On Android, other apps on the device can no longer use the app's built-in local
server to fetch web addresses: its backend now answers only the app itself,
with a new secret on every launch. Nothing changes in how the app looks or plays.
