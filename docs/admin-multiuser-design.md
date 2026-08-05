# Dionysus — Admin-Controlled Multi-User Logins (Design)

**Status:** Design proposal for review (no code yet)
**Author:** Claude Code
**Goal:** Let one admin (you) provision and control every user's login to Dionysus. Users sign in with credentials you issue, they **cannot** change their own credentials or settings, and when something breaks you fix it on your end against that user's "key."

---

## 1. What you asked for

> "I need to be able to be an admin. I control logins for everyone on the Dionysus. I enter the credentials on my end and they get a login. They cannot change creds or anything like that. Any time an issue comes up, I get to fix it on my end, on their key. Is this possible?"

**Yes, it's possible.** Concretely, it means every install of Dionysus stops being a standalone app that stores its own settings and instead **checks in with a server you control**. That server is the source of truth for:

- **Who can log in** (accounts you create).
- **What each user gets** (their IPTV playlist/Xtream creds, debrid keys, enabled features).
- **Whether a login still works** (you can disable or reset it instantly).

Today Dionysus has **no backend** — every setting lives on the device. There is nothing central to control. So the one non-negotiable is: **we add a small backend service.** Everything below is about doing that with the least cost and maintenance.

---

## 2. The core idea: the "user key"

Each user gets a **login key** you generate — think of it like a username + PIN, or a single license code. That key is the anchor for everything:

```
          you (admin console)
                 │  create / edit / disable
                 ▼
        ┌──────────────────────┐
        │  Backend (accounts)  │   ← source of truth
        │  key → profile       │
        └──────────┬───────────┘
                   │  user logs in with their key
                   ▼
        ┌──────────────────────┐
        │  Dionysus on the TV  │   pulls its config, locks settings
        └──────────────────────┘
```

- **You create the key**, set the user's password/PIN, and attach their config (IPTV creds, debrid, feature flags, expiry date).
- **The user logs in** with the key on their TV. The app fetches that user's profile and runs with it.
- **The user cannot change** their credentials or the locked settings — the app hides/greys those screens when it's in "managed" mode.
- **When an issue comes up**, you open the admin console, find the user by their key, and fix it — reset their password, swap their IPTV credentials, extend their expiry, or disable them. The change takes effect on their next check-in (and can be pushed near-instantly).

---

## 3. Recommended architecture

Two realistic ways to build the backend. My recommendation is **Option A (managed backend)** — it's the fastest to ship, cheapest to run at your scale, and gives you an admin console without building one from scratch.

### Option A — Managed backend (recommended): Supabase

[Supabase](https://supabase.com) gives us, out of the box:

- **Auth** — email/username + password accounts you create; sign-in returns a token the app uses.
- **Postgres database** — the users/profiles tables below.
- **Auto-generated REST + row-level security** — the app reads only its own profile; nobody can read anyone else's.
- **A hosted dashboard** — you can create/edit/disable users from Supabase's own table editor on day one, before we even build a custom admin screen.

**Cost:** Free tier covers up to ~50k monthly active users and 500MB DB — almost certainly free for you. Paid tier is **$25/month** if you outgrow it. No servers to maintain.

### Option B — Custom server

A small Node/Go/Python service + Postgres on a VPS (or Cloudflare Workers + D1). Full control, but **you host, patch, back up, and secure it**, and we build the admin console and the auth ourselves. More work and more ongoing responsibility for no benefit at your scale. Only worth it if you specifically want to avoid a third-party host.

**Recommendation: Option A (Supabase).** Everything below assumes it, but the data model and app changes are identical either way.

### What about Firebase?

Also viable (Google's managed auth + Firestore). Supabase is recommended over Firebase here because Postgres + row-level security maps more cleanly to "one admin owns rows, each user reads one row," and its table dashboard doubles as a zero-effort admin console. Not a strong preference — if you'd rather use Firebase, the design carries over.

---

## 4. Data model

Three tables (Supabase/Postgres):

**`users`** — one row per login you issue
| column | meaning |
|---|---|
| `id` | internal id |
| `login_key` | the human-facing key/username you hand out (unique) |
| `display_name` | e.g. "Living room — John" |
| `status` | `active` / `disabled` / `expired` |
| `expires_at` | optional auto-expiry date |
| `created_at`, `updated_at` | timestamps |

(The password itself is managed by Supabase Auth, hashed — never stored in plain text, not even you see it; you *set/reset* it, you don't read it.)

**`profiles`** — the config each user runs with (1:1 with `users`)
| column | meaning |
|---|---|
| `user_id` | → users.id |
| `xtream_host` / `xtream_user` / `xtream_pass` | their IPTV account |
| `epg_url` | their guide URL |
| `debrid_token` | optional debrid key |
| `feature_flags` | JSON — which sections they can see (VOD, downloads, etc.) |
| `default_category` | their Live TV landing page |

**`devices`** (optional, recommended) — one row per TV a key is used on
| column | meaning |
|---|---|
| `user_id` | → users.id |
| `device_id` | the TV's install id |
| `last_seen_at` | for "is this user online / when did they last check in" |

`devices` lets you **see who's using a key**, cap it to N devices, and spot a shared/leaked key.

---

## 5. Login & sync flow (in the app)

1. **First launch** → app shows a **Login screen** (key + password/PIN) instead of the current settings-driven setup.
2. App sends them to the backend → gets back an **auth token** + the user's **profile**.
3. App stores the token securely on-device, applies the profile (IPTV creds, flags, default category), and enters **managed mode**.
4. **On every launch (and periodically)** the app re-validates the token and re-pulls the profile, so your edits propagate. If `status != active` or `expires_at` has passed → the app logs them out and shows "Contact your provider."
5. **Managed mode locks the UI**: the "add/edit playlist," "credentials," and other owner-only settings screens are hidden or read-only. The user can still use playback preferences (volume, aspect, etc.) — we decide per-setting what's locked vs. personal.

### Near-instant fixes (optional but nice)

A periodic re-pull already lets you fix things (takes effect next check-in). To make a fix land **immediately**, the app can subscribe to its own profile row (Supabase Realtime) — the moment you change it in the console, the TV updates live. Good for "kick a user right now" or "swap their IPTV creds while they're watching."

---

## 6. The admin side (you)

**Phase 1 — use Supabase's dashboard.** From day one you can create a user, set their password, fill in their profile row, flip `status` to `disabled`, or set `expires_at` — all from Supabase's built-in table editor in a browser. No custom UI needed to get started.

**Phase 2 — a simple admin web page** (optional, later). A one-page private admin site ("Dionysus Admin") with:
- **New user** — generates a key, sets password, fills IPTV creds → one button.
- **User list** — search by key/name, see status, last-seen, device count.
- **Per-user actions** — reset password, edit IPTV creds, extend expiry, disable/enable, deauthorize a device.

This is a small build on top of the same backend; we can add it once the core works.

> Note: I will not build an admin console that impersonates a real brand or collects credentials under a false identity — this is your own service for your own users, which is fine. The login screens and admin pages will be plainly "Dionysus."

---

## 7. What changes in the Dionysus app

- **New Login screen** (key + password), shown before Home when in managed mode.
- **Auth client** — talks to the backend, stores/refreshes the token securely (Android EncryptedSharedPreferences / DataStore).
- **Managed mode** — a flag that, when on, (a) loads config from the backend instead of local settings, and (b) hides/locks the owner-only settings screens.
- **Profile sync** — pull on launch + periodic + (optional) realtime.
- **Graceful lockout** — disabled/expired/invalid → clear local data, show a "contact your provider" message.
- **Build flavor / toggle** — keep a "personal mode" build (today's behavior, no login) if you still want an unmanaged version for yourself, or make managed mode the only mode. Your call.

Nothing about playback, EPG, or scrapers changes — only where the **config** comes from and whether settings are editable.

---

## 8. Security notes

- Passwords are hashed by the auth provider; **even you never see them** — you set and reset, not read. (If you truly want to see PINs, we'd store a separate admin-set PIN, which is less secure — I'd advise against it.)
- Each user can read **only their own** profile (row-level security). One user can never see another's IPTV creds.
- IPTV/debrid secrets live server-side and are delivered over HTTPS to the authenticated device only.
- Device binding + last-seen lets you detect and cut off a shared/leaked key.
- The app stores its token in Android's encrypted storage, not plain prefs.

---

## 9. Rough effort & rollout

| Phase | What | Effort |
|---|---|---|
| 0 | Stand up Supabase, create tables, RLS rules | ~half a day |
| 1 | App: login screen + auth client + profile load + managed-mode lock | the bulk of the work |
| 2 | App: periodic/realtime sync + graceful lockout | small |
| 3 | Admin: use Supabase dashboard (no build) | none |
| 4 | Admin: custom one-page admin site | optional, later |

We can ship Phases 0–2 first and run the admin side from the Supabase dashboard, then add the pretty admin page once it's proven.

---

## 10. Decisions I need from you before building

1. **Backend host:** Supabase (recommended), Firebase, or self-hosted?
2. **Login identifier:** a single **license key**, or **username + PIN**? (Key is simplest to hand out; username+PIN is friendlier.)
3. **Managed vs. personal:** should managed mode be the only mode, or keep a login-free "personal" build for yourself?
4. **Device cap:** limit each key to N TVs? (Recommended — stops key sharing.)
5. **Expiry:** do you want per-user expiry dates (e.g. monthly), or accounts that stay until you disable them?
6. **Admin console:** start on the Supabase dashboard, or do you want the custom admin page as part of the first build?

Once you answer these, I'll turn this into an implementation plan and start on Phase 0/1.
