# Dionysus — Admin-Controlled Multi-User Logins (Design + Locked Decisions)

**Status:** Decisions locked — building. Backend first, then the app.
**Goal:** One admin (you) provisions and controls every user's login. Users sign in
with a **username + PIN** you issue, they **cannot** change their own credentials or
locked settings, and when something breaks you fix it on your end against that user's
account. When a key is abused you delete it and reissue.

---

## 0. Locked decisions

| # | Decision | Choice |
|---|---|---|
| 1 | Backend host | **Self-hosted on Cloudways** (PHP + MySQL) |
| 2 | Login identifier | **Username + PIN** |
| 3 | Managed vs personal | **Managed only** — there is no login-free build |
| 4 | Device cap | **1 device per key** (configurable constant, default 1) |
| 5 | Expiry | **None** — accounts live until you disable or delete them |
| 6 | Admin console | **Custom admin page** ships with the first backend build |

Everything below reflects these choices.

---

## 1. Why a backend at all

Today every Dionysus install stores its own settings on the device — there is nothing
central to control. To let you own every login, the app must **check in with a server
you run**. That server is the source of truth for:

- **Who can log in** — accounts you create (username + PIN).
- **What each user gets** — their IPTV/Xtream creds, debrid key, enabled sections,
  default Live TV category.
- **Whether a login still works** — you can disable or delete it instantly, and move a
  key to a new TV by deauthorizing its device.

## 2. Stack: PHP + MySQL on Cloudways

Cloudways' turnkey stack is PHP-FPM + MySQL/MariaDB behind Nginx/Apache, with
phpMyAdmin and a public webroot (`public_html`). We deploy by uploading a small set of
PHP files and pointing them at the auto-provisioned MySQL database — no Node runtime,
no build step, no framework to patch.

The backend is intentionally **dependency-light**: plain PHP with PDO (prepared
statements everywhere), `password_hash`/`password_verify` for PINs, `random_bytes`
tokens, CSRF-protected admin forms, and `htmlspecialchars` on all output. Small enough
to read top to bottom, which is the right security posture for something you self-host.

```
        you ──▶ /admin  (session-protected console)
                   │ create / edit / disable / delete users, deauthorize devices
                   ▼
        ┌───────────────────────────┐
        │  MySQL (source of truth)  │
        │  users · profiles · devices · admins · tokens │
        └───────────┬───────────────┘
                   ▲ │
     login + sync  │ ▼  (HTTPS, Bearer token)
        ┌───────────────────────────┐
        │   Dionysus on the TV       │  username+PIN → token+profile → managed mode
        └───────────────────────────┘
```

---

## 3. Data model (MySQL)

**`admins`** — who can sign into the console
| column | meaning |
|---|---|
| `id` | pk |
| `username` | unique |
| `password_hash` | bcrypt (`password_hash`) |
| `created_at` | |

**`users`** — one row per login you issue
| column | meaning |
|---|---|
| `id` | pk |
| `username` | the name you hand out (unique) |
| `pin_hash` | bcrypt of the PIN — never stored or shown in plain text |
| `display_name` | e.g. "Living room — John" |
| `status` | `active` / `disabled` |
| `created_at`, `updated_at` | |

No `expires_at` column — expiry was declined; you disable or delete instead.

**`profiles`** — the config each user runs with (1:1 with `users`)
| column | meaning |
|---|---|
| `user_id` | → users.id (unique) |
| `xtream_host` / `xtream_user` / `xtream_pass` | their IPTV account |
| `epg_url` | optional custom guide URL |
| `debrid_token` | optional debrid key |
| `default_category` | their Live TV landing category |
| `feature_vod` / `feature_downloads` / `feature_search` | 0/1 section toggles |

**`devices`** — the TV(s) a key is bound to (cap = 1)
| column | meaning |
|---|---|
| `id` | pk |
| `user_id` | → users.id |
| `device_id` | the TV's install id (unique per user) |
| `device_name` | model/name reported at login |
| `created_at`, `last_seen_at` | bind time + last check-in |

**`tokens`** — active device sessions
| column | meaning |
|---|---|
| `token` | opaque 32-byte random (pk) |
| `user_id` / `device_id` | who/where |
| `created_at`, `last_seen_at` | |

---

## 4. API (consumed by the app)

All JSON over HTTPS. Single entry point `api.php?action=…` so no URL-rewrite config is
needed on Cloudways.

- **`POST api.php?action=login`** `{ username, pin, device_id, device_name }`
  → verifies username+PIN and `status=active`; enforces the device cap:
  - device already bound to this user → refresh `last_seen`, issue token.
  - new device and user is under the cap → bind it, issue token.
  - new device and user is at the cap → **reject** `409 device_limit` ("This key is
    already in use on another TV — ask your provider to reset it").
  → returns `{ token, profile }`.
- **`GET api.php?action=profile`** (`Authorization: Bearer <token>`)
  → re-checks status + token, bumps `last_seen`, returns the current `profile`.
  Returns `401` (bad/again token) or `403 disabled` so the app can lock out live.
- **`POST api.php?action=logout`** → deauthorizes the current token.

The app calls `login` once, then `profile` on every launch and periodically, so your
edits and disables propagate on the next check-in.

---

## 5. Admin console (`admin.php`)

Session-protected, Dionysus-branded (plainly your own service — it impersonates no one
and collects no third-party credentials). Pages:

- **Sign in** — admin username + password.
- **Users list** — username, display name, status, bound-device count, last seen; search.
- **New user** — username, PIN, display name, IPTV host/user/pass, EPG URL, debrid token,
  default category, section toggles → one submit creates the user + profile.
- **Edit user** — update any profile field, **reset PIN**, enable/disable, **deauthorize
  device** (frees the key for a new TV), **delete user**.

CSRF tokens on every form; all writes via POST; output escaped.

---

## 6. What changes in the app (next build, after the server is deployed)

- **Login screen** (username + PIN) shown before Home; managed mode is the only mode.
- **Auth client** — talks to your server, stores the token in Android EncryptedShared
  Preferences, refreshes on launch + periodically.
- **Managed mode** — config comes from the server profile, not local settings; the
  playlist/credentials/owner-only settings screens are hidden. Playback preferences
  (aspect, speed, audio) stay local/personal.
- **Graceful lockout** — disabled/invalid → clear local data, show "Contact your
  provider."
- **Server URL** — a single configurable base URL (set once to your Cloudways domain).

Playback, EPG, and scrapers are unchanged — only *where config comes from* and *what is
editable* change.

---

## 7. Security notes

- PINs are bcrypt-hashed; you set/reset them (you always know what you set), the DB never
  stores them in the clear.
- Every DB call is a prepared statement (no SQL injection); admin forms are CSRF-guarded.
- IPTV/debrid secrets live server-side, delivered only to an authenticated device over
  HTTPS (enable Cloudways' free Let's Encrypt SSL).
- 1-device binding + last-seen surfaces and stops a shared/leaked key; deauthorize to move.
- The app keeps its token in encrypted storage, not plain prefs.

---

## 8. Rollout

| Phase | What | State |
|---|---|---|
| 0 | Backend: schema + API + admin console + Cloudways deploy guide | **this build** |
| 1 | You deploy to Cloudways, create your admin + first user | you, ~30 min |
| 2 | App: login screen + auth client + managed-mode lock + lockout | next build |
| 3 | App: periodic sync polish | follow-up |

The Phase 0 backend does not touch the current app — the existing APK keeps working
until Phase 2 ships the login.
