# PJ Signals

Live trade signals (entry, SL, TP, closes) from the PJ MT5 demo bots. No backend, no cost.

```
MT5 bots -> PJ_Live service -> PJ_live.json -> signal_push.py (Jay's PC) -> ntfy.sh topic -> web page (Vercel) + Android app
```

- `web/` static page on Vercel. Join with the group code, or open an invite link `https://<site>/#<code>`.
- `android/` app (built by GitHub Actions, download from Releases). Listens in the background and rings on every signal.
- The group code is the only key: anyone who has it can read the signals. Share it only with friends.

Demo bots under test. Not financial advice.
