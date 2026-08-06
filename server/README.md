# Dionysus Backend (self-hosted on Cloudways)

The login API for the Dionysus app **and** your custom admin console. Plain PHP + MySQL —
no framework, no build step. You run this on Cloudways; the app checks in with it.

```
server/
  index.php    health check  →  {"status":"ok","db":"connected"}
  api.php      app endpoints (login / profile / logout)
  admin.php    your admin console (create/edit/disable/delete users, manage devices)
  install.php  one-time setup (creates tables + first admin) — DELETE after use
  lib.php      shared DB/config helpers
  schema.sql   the MySQL tables (also applied by install.php)
  config.sample.php → copy to config.php and fill in (git-ignored)
```

## What you get

- **Username + PIN** logins you issue. Users can't change their own credentials.
- **1 device per key** (configurable) — a second TV is refused until you deauthorize the first.
- **No expiry** — accounts stay until you disable or delete them.
- Each login carries its own **IPTV/Xtream creds, EPG URL, debrid token, default category,
  and section toggles** (VOD / Downloads / Search), delivered to the TV over HTTPS.

---

## Deploy on Cloudways (~30 min)

### 1. Create the application
- Cloudways → **Add Application** → choose the **PHP** stack (any recent PHP 8.x) on your server.
- Open **Application → Access Details** and note the **Database** name/user/password, and the
  **SFTP/SSH** credentials and the app's **public webroot** (`public_html`).

### 2. Upload the files
- SFTP the **contents of this `server/` folder** into `public_html/` (so `admin.php` is at
  `public_html/admin.php`). Git deployment via Cloudways' GitHub integration works too.

### 3. Configure
- Copy `config.sample.php` to `config.php` and fill in the DB credentials from step 1.
- Set a long random `install_secret`, and set `device_limit` (default `1`).

### 4. Enable HTTPS (required — secrets travel over this)
- Cloudways → **Application → SSL Certificate** → add a free **Let's Encrypt** cert for your
  domain. IPTV/debrid creds and PINs must never go over plain HTTP.

### 5. Run setup, then lock it down
- Visit `https://YOUR-DOMAIN/install.php?secret=YOUR_INSTALL_SECRET`.
- Create your **admin username + password**.
- **Delete `install.php`** from the server (SFTP delete). It refuses to run without the secret,
  but deleting it removes the risk entirely.

### 6. Verify
- `https://YOUR-DOMAIN/` → `{"status":"ok","db":"connected"}`.
- `https://YOUR-DOMAIN/admin.php` → sign in → **New user** → create a test login.

That test username + PIN is what you'll enter in the app once the app's login screen ships
in the next build. Your Cloudways domain is the **Server URL** the app will point at.

---

## Day-to-day (admin console)

- **New user** — fill username, PIN, their IPTV creds, default category, toggles → *Create user*.
  Hand the person the **username + PIN**.
- **Manage → Save changes** — edit any of their config; it reaches the TV on the next check-in.
- **Reset PIN** — sets a new PIN and logs the user out everywhere.
- **Deauthorize device** — frees a key so it can move to a new TV.
- **Disable / Re-enable** — instantly cut off or restore a login.
- **Delete user** — removes the account and all its devices/sessions.

## Security

- PINs and admin passwords are bcrypt-hashed (`password_hash`) — never stored in the clear.
- Every query is a prepared statement; admin forms carry CSRF tokens; output is escaped.
- Tokens are 32 random bytes, issued per device, revoked on disable/reset/deauthorize.
- Keep `config.php` out of git (already git-ignored) and **delete `install.php`** after setup.

## API reference (for the app build)

| Method | Endpoint | Body / Header | Returns |
|---|---|---|---|
| POST | `api.php?action=login` | `{username, pin, device_id, device_name}` | `{token, profile, display_name}` |
| GET  | `api.php?action=profile` | `Authorization: Bearer <token>` | `{profile}` |
| POST | `api.php?action=logout` | `Authorization: Bearer <token>` | `{ok:true}` |

Errors: `401 invalid_credentials` / `unauthorized`, `403 disabled`, `409 device_limit`.
