<?php
/**
 * Dionysus admin console — your private control panel.
 * Sign in with the admin account created by install.php, then create/edit/disable/
 * delete user logins and manage their bound devices. This is your own service; it
 * impersonates no brand and collects no third-party credentials.
 */

declare(strict_types=1);
require __DIR__ . '/lib.php';

session_start();
$db = db();

/* ── CSRF ─────────────────────────────────────────────────────────────────── */
if (empty($_SESSION['csrf'])) {
    $_SESSION['csrf'] = bin2hex(random_bytes(16));
}
function csrf_field(): string
{
    return '<input type="hidden" name="csrf" value="' . htmlspecialchars($_SESSION['csrf']) . '">';
}
function check_csrf(): void
{
    if (($_POST['csrf'] ?? '') !== ($_SESSION['csrf'] ?? '')) {
        http_response_code(400);
        exit('Bad CSRF token — reload the page and try again.');
    }
}
function h(?string $s): string { return htmlspecialchars((string) $s); }

/* ── Auth ─────────────────────────────────────────────────────────────────── */
$action = $_POST['do'] ?? ($_GET['do'] ?? '');

if ($action === 'login' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    check_csrf();
    $stmt = $db->prepare('SELECT * FROM admins WHERE username = ?');
    $stmt->execute([trim((string) ($_POST['username'] ?? ''))]);
    $admin = $stmt->fetch();
    if ($admin && password_verify((string) ($_POST['password'] ?? ''), $admin['password_hash'])) {
        session_regenerate_id(true);
        $_SESSION['admin_id'] = (int) $admin['id'];
        $_SESSION['admin_user'] = $admin['username'];
        header('Location: admin.php');
        exit;
    }
    $loginError = 'Wrong username or password.';
}

if ($action === 'logout') {
    session_destroy();
    header('Location: admin.php');
    exit;
}

$authed = !empty($_SESSION['admin_id']);

/* ── Mutations (require auth + CSRF) ──────────────────────────────────────── */
$notice = '';
if ($authed && $_SERVER['REQUEST_METHOD'] === 'POST' && $action !== 'login') {
    check_csrf();
    switch ($action) {
        case 'create_user': $notice = create_user($db); break;
        case 'update_user': $notice = update_user($db); break;
        case 'reset_pin':   $notice = reset_pin($db);   break;
        case 'set_status':  $notice = set_status($db);  break;
        case 'deauth':      $notice = deauth_device($db); break;
        case 'delete_user': $notice = delete_user($db); break;
    }
}

function pf(string $k): string { return trim((string) ($_POST[$k] ?? '')); }
function feat(string $k): int { return isset($_POST[$k]) ? 1 : 0; }

function create_user(PDO $db): string
{
    $username = pf('username');
    $pin      = pf('pin');
    if ($username === '' || strlen($pin) < 4) {
        return 'Username required and PIN must be at least 4 digits.';
    }
    $exists = $db->prepare('SELECT id FROM users WHERE username = ?');
    $exists->execute([$username]);
    if ($exists->fetch()) {
        return 'That username is already taken.';
    }
    $db->beginTransaction();
    $db->prepare('INSERT INTO users (username, pin_hash, display_name) VALUES (?, ?, ?)')
       ->execute([$username, password_hash($pin, PASSWORD_DEFAULT), pf('display_name')]);
    $userId = (int) $db->lastInsertId();
    $db->prepare(
        'INSERT INTO profiles
           (user_id, xtream_host, xtream_user, xtream_pass, epg_url, debrid_token,
            default_category, feature_vod, feature_downloads, feature_search)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
    )->execute([
        $userId, pf('xtream_host'), pf('xtream_user'), pf('xtream_pass'),
        pf('epg_url'), pf('debrid_token'), pf('default_category'),
        feat('feature_vod'), feat('feature_downloads'), feat('feature_search'),
    ]);
    $db->commit();
    return 'Created "' . h($username) . '" — hand them the username and PIN.';
}

function update_user(PDO $db): string
{
    $userId = (int) ($_POST['user_id'] ?? 0);
    $db->prepare('UPDATE users SET display_name = ? WHERE id = ?')->execute([pf('display_name'), $userId]);
    $db->prepare(
        'UPDATE profiles SET xtream_host=?, xtream_user=?, xtream_pass=?, epg_url=?,
            debrid_token=?, default_category=?, feature_vod=?, feature_downloads=?, feature_search=?
         WHERE user_id=?'
    )->execute([
        pf('xtream_host'), pf('xtream_user'), pf('xtream_pass'), pf('epg_url'),
        pf('debrid_token'), pf('default_category'),
        feat('feature_vod'), feat('feature_downloads'), feat('feature_search'), $userId,
    ]);
    return 'Saved. Changes reach the TV on its next check-in.';
}

function reset_pin(PDO $db): string
{
    $userId = (int) ($_POST['user_id'] ?? 0);
    $pin = pf('pin');
    if (strlen($pin) < 4) {
        return 'PIN must be at least 4 digits.';
    }
    $db->prepare('UPDATE users SET pin_hash = ? WHERE id = ?')
       ->execute([password_hash($pin, PASSWORD_DEFAULT), $userId]);
    // Force re-login everywhere with the new PIN.
    $db->prepare('DELETE FROM tokens WHERE user_id = ?')->execute([$userId]);
    return 'PIN reset — tell the user their new PIN.';
}

function set_status(PDO $db): string
{
    $userId = (int) ($_POST['user_id'] ?? 0);
    $status = ($_POST['status'] ?? '') === 'disabled' ? 'disabled' : 'active';
    $db->prepare('UPDATE users SET status = ? WHERE id = ?')->execute([$status, $userId]);
    if ($status === 'disabled') {
        $db->prepare('DELETE FROM tokens WHERE user_id = ?')->execute([$userId]);
    }
    return 'User ' . ($status === 'disabled' ? 'disabled.' : 're-enabled.');
}

function deauth_device(PDO $db): string
{
    $userId = (int) ($_POST['user_id'] ?? 0);
    $deviceId = pf('device_id');
    $db->prepare('DELETE FROM devices WHERE user_id = ? AND device_id = ?')->execute([$userId, $deviceId]);
    $db->prepare('DELETE FROM tokens  WHERE user_id = ? AND device_id = ?')->execute([$userId, $deviceId]);
    return 'Device removed — this key can now be used on a new TV.';
}

function delete_user(PDO $db): string
{
    $userId = (int) ($_POST['user_id'] ?? 0);
    // profiles/devices/tokens cascade via FK.
    $db->prepare('DELETE FROM users WHERE id = ?')->execute([$userId]);
    return 'User deleted.';
}

/* ── Data for rendering ───────────────────────────────────────────────────── */
$editId = (int) ($_GET['edit'] ?? 0);
$editUser = null;
$editDevices = [];
if ($authed && $editId) {
    $stmt = $db->prepare(
        'SELECT u.*, p.* FROM users u LEFT JOIN profiles p ON p.user_id = u.id WHERE u.id = ?'
    );
    $stmt->execute([$editId]);
    $editUser = $stmt->fetch();
    if ($editUser) {
        $d = $db->prepare('SELECT * FROM devices WHERE user_id = ? ORDER BY last_seen_at DESC');
        $d->execute([$editId]);
        $editDevices = $d->fetchAll();
    }
}

$users = [];
if ($authed && !$editUser) {
    $q = trim((string) ($_GET['q'] ?? ''));
    if ($q !== '') {
        $stmt = $db->prepare(
            'SELECT u.*, (SELECT COUNT(*) FROM devices d WHERE d.user_id=u.id) dev,
                    (SELECT MAX(last_seen_at) FROM devices d WHERE d.user_id=u.id) seen
             FROM users u WHERE u.username LIKE ? OR u.display_name LIKE ? ORDER BY u.username'
        );
        $stmt->execute(["%$q%", "%$q%"]);
    } else {
        $stmt = $db->query(
            'SELECT u.*, (SELECT COUNT(*) FROM devices d WHERE d.user_id=u.id) dev,
                    (SELECT MAX(last_seen_at) FROM devices d WHERE d.user_id=u.id) seen
             FROM users u ORDER BY u.username'
        );
    }
    $users = $stmt->fetchAll();
}
?>
<!doctype html>
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dionysus Admin</title>
<style>
  :root{--gold:#c9a24a;--bg:#0e0f13;--card:#16171d;--line:#2a2b33;--fg:#eaeaf0;--dim:#9a9aa7}
  *{box-sizing:border-box}
  body{font-family:system-ui,sans-serif;background:var(--bg);color:var(--fg);margin:0;padding:0 16px 60px}
  header{display:flex;align-items:center;justify-content:space-between;max-width:960px;margin:0 auto;padding:18px 0;border-bottom:1px solid var(--line)}
  h1{color:var(--gold);font-size:20px;margin:0;letter-spacing:.5px}
  main{max-width:960px;margin:0 auto}
  a{color:var(--gold);text-decoration:none}
  .notice{margin:16px 0;padding:12px 14px;border-radius:9px;background:#1c2a1c;color:#bfe6bf}
  .error{background:#2a1c1c;color:#e6bfbf}
  .card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:18px;margin:16px 0}
  label{display:block;margin:12px 0 4px;color:var(--dim);font-size:13px}
  input[type=text],input[type=password]{width:100%;padding:10px;border-radius:8px;border:1px solid var(--line);background:#101116;color:#fff}
  .row{display:flex;gap:14px;flex-wrap:wrap}
  .row>div{flex:1;min-width:200px}
  .checks{display:flex;gap:18px;margin-top:12px;flex-wrap:wrap}
  .checks label{display:flex;align-items:center;gap:6px;margin:0;color:var(--fg)}
  button,.btn{margin-top:16px;padding:10px 16px;border:0;border-radius:8px;background:var(--gold);color:#12130f;font-weight:700;cursor:pointer;display:inline-block}
  .btn.secondary{background:#23242c;color:var(--fg);border:1px solid var(--line)}
  .btn.danger{background:#3a1f1f;color:#ffb4b4;border:1px solid #542a2a}
  table{width:100%;border-collapse:collapse;margin-top:8px}
  th,td{text-align:left;padding:10px;border-bottom:1px solid var(--line);font-size:14px}
  th{color:var(--dim);font-weight:600}
  .pill{padding:2px 9px;border-radius:20px;font-size:12px;font-weight:700}
  .pill.active{background:#1c2a1c;color:#9fe09f}
  .pill.disabled{background:#2a1c1c;color:#e6a0a0}
  .search{max-width:280px}
  .muted{color:var(--dim);font-size:13px}
</style>

<?php if (!$authed): ?>
  <main>
    <div class="card" style="max-width:420px;margin:10vh auto">
      <h1>Dionysus Admin</h1>
      <?php if (!empty($loginError)): ?><div class="notice error"><?= h($loginError) ?></div><?php endif; ?>
      <form method="post">
        <?= csrf_field() ?><input type="hidden" name="do" value="login">
        <label>Username</label><input type="text" name="username" autocomplete="username" required>
        <label>Password</label><input type="password" name="password" autocomplete="current-password" required>
        <button type="submit">Sign in</button>
      </form>
    </div>
  </main>
<?php else: ?>
  <header>
    <h1>Dionysus Admin</h1>
    <div class="muted"><?= h($_SESSION['admin_user']) ?> · <a href="admin.php?do=logout">Sign out</a></div>
  </header>
  <main>
    <?php if ($notice): ?><div class="notice"><?= h($notice) ?></div><?php endif; ?>

    <?php if ($editUser): ?>
      <!-- ── Edit one user ─────────────────────────────────────────────── -->
      <p><a href="admin.php">&larr; All users</a></p>
      <div class="card">
        <h2 style="margin-top:0">Edit “<?= h($editUser['username']) ?>”
          <span class="pill <?= h($editUser['status']) ?>"><?= h($editUser['status']) ?></span></h2>
        <form method="post">
          <?= csrf_field() ?><input type="hidden" name="do" value="update_user">
          <input type="hidden" name="user_id" value="<?= (int) $editUser['id'] ?>">
          <label>Display name</label>
          <input type="text" name="display_name" value="<?= h($editUser['display_name']) ?>">
          <div class="row">
            <div><label>Xtream host (http://host:port)</label>
              <input type="text" name="xtream_host" value="<?= h($editUser['xtream_host']) ?>"></div>
          </div>
          <div class="row">
            <div><label>Xtream username</label>
              <input type="text" name="xtream_user" value="<?= h($editUser['xtream_user']) ?>"></div>
            <div><label>Xtream password</label>
              <input type="text" name="xtream_pass" value="<?= h($editUser['xtream_pass']) ?>"></div>
          </div>
          <label>EPG URL (optional)</label>
          <input type="text" name="epg_url" value="<?= h($editUser['epg_url']) ?>">
          <div class="row">
            <div><label>Debrid token (optional)</label>
              <input type="text" name="debrid_token" value="<?= h($editUser['debrid_token']) ?>"></div>
            <div><label>Default Live TV category</label>
              <input type="text" name="default_category" value="<?= h($editUser['default_category']) ?>"></div>
          </div>
          <div class="checks">
            <label><input type="checkbox" name="feature_vod" <?= $editUser['feature_vod'] ? 'checked' : '' ?>> VOD / Movies</label>
            <label><input type="checkbox" name="feature_downloads" <?= $editUser['feature_downloads'] ? 'checked' : '' ?>> Downloads</label>
            <label><input type="checkbox" name="feature_search" <?= $editUser['feature_search'] ? 'checked' : '' ?>> Search</label>
          </div>
          <button type="submit">Save changes</button>
        </form>
      </div>

      <div class="card">
        <h3 style="margin-top:0">Reset PIN</h3>
        <form method="post" class="row" style="align-items:flex-end">
          <?= csrf_field() ?><input type="hidden" name="do" value="reset_pin">
          <input type="hidden" name="user_id" value="<?= (int) $editUser['id'] ?>">
          <div style="max-width:200px"><label>New PIN (min 4 digits)</label>
            <input type="text" name="pin" inputmode="numeric" autocomplete="off"></div>
          <div><button type="submit" class="btn secondary">Reset PIN</button></div>
        </form>
      </div>

      <div class="card">
        <h3 style="margin-top:0">Bound device<?= count($editDevices) === 1 ? '' : 's' ?>
          (limit <?= (int) cfg()['device_limit'] ?>)</h3>
        <?php if (!$editDevices): ?>
          <p class="muted">No device bound yet — the key is free to use on any one TV.</p>
        <?php else: ?>
          <table>
            <tr><th>Device</th><th>ID</th><th>Last seen</th><th></th></tr>
            <?php foreach ($editDevices as $d): ?>
              <tr>
                <td><?= h($d['device_name'] ?: '—') ?></td>
                <td class="muted"><?= h(substr($d['device_id'], 0, 14)) ?>…</td>
                <td class="muted"><?= h($d['last_seen_at']) ?></td>
                <td>
                  <form method="post" onsubmit="return confirm('Free this key from that TV?')">
                    <?= csrf_field() ?><input type="hidden" name="do" value="deauth">
                    <input type="hidden" name="user_id" value="<?= (int) $editUser['id'] ?>">
                    <input type="hidden" name="device_id" value="<?= h($d['device_id']) ?>">
                    <button type="submit" class="btn secondary" style="margin:0">Deauthorize</button>
                  </form>
                </td>
              </tr>
            <?php endforeach; ?>
          </table>
        <?php endif; ?>
      </div>

      <div class="card">
        <h3 style="margin-top:0">Status &amp; danger zone</h3>
        <div class="row">
          <form method="post">
            <?= csrf_field() ?>
            <input type="hidden" name="do" value="set_status">
            <input type="hidden" name="user_id" value="<?= (int) $editUser['id'] ?>">
            <input type="hidden" name="status" value="<?= $editUser['status'] === 'active' ? 'disabled' : 'active' ?>">
            <button type="submit" class="btn secondary" style="margin:0">
              <?= $editUser['status'] === 'active' ? 'Disable login' : 'Re-enable login' ?>
            </button>
          </form>
          <form method="post" onsubmit="return confirm('Delete this user permanently?')">
            <?= csrf_field() ?>
            <input type="hidden" name="do" value="delete_user">
            <input type="hidden" name="user_id" value="<?= (int) $editUser['id'] ?>">
            <button type="submit" class="btn danger" style="margin:0">Delete user</button>
          </form>
        </div>
      </div>

    <?php else: ?>
      <!-- ── New user ──────────────────────────────────────────────────── -->
      <div class="card">
        <h2 style="margin-top:0">New user</h2>
        <form method="post">
          <?= csrf_field() ?><input type="hidden" name="do" value="create_user">
          <div class="row">
            <div><label>Username *</label><input type="text" name="username" autocomplete="off" required></div>
            <div><label>PIN * (min 4 digits)</label><input type="text" name="pin" inputmode="numeric" autocomplete="off" required></div>
            <div><label>Display name</label><input type="text" name="display_name" placeholder="Living room — John"></div>
          </div>
          <div class="row">
            <div><label>Xtream host (http://host:port)</label><input type="text" name="xtream_host"></div>
          </div>
          <div class="row">
            <div><label>Xtream username</label><input type="text" name="xtream_user"></div>
            <div><label>Xtream password</label><input type="text" name="xtream_pass"></div>
          </div>
          <div class="row">
            <div><label>EPG URL (optional)</label><input type="text" name="epg_url"></div>
            <div><label>Default Live TV category</label><input type="text" name="default_category" placeholder="US MOVIES"></div>
          </div>
          <label>Debrid token (optional)</label><input type="text" name="debrid_token">
          <div class="checks">
            <label><input type="checkbox" name="feature_vod" checked> VOD / Movies</label>
            <label><input type="checkbox" name="feature_downloads" checked> Downloads</label>
            <label><input type="checkbox" name="feature_search" checked> Search</label>
          </div>
          <button type="submit">Create user</button>
        </form>
      </div>

      <!-- ── User list ─────────────────────────────────────────────────── -->
      <div class="card">
        <div style="display:flex;justify-content:space-between;align-items:center">
          <h2 style="margin:0">Users (<?= count($users) ?>)</h2>
          <form method="get"><input class="search" type="text" name="q" placeholder="Search…"
            value="<?= h((string) ($_GET['q'] ?? '')) ?>"></form>
        </div>
        <table>
          <tr><th>Username</th><th>Name</th><th>Status</th><th>Devices</th><th>Last seen</th><th></th></tr>
          <?php foreach ($users as $u): ?>
            <tr>
              <td><strong><?= h($u['username']) ?></strong></td>
              <td class="muted"><?= h($u['display_name'] ?: '—') ?></td>
              <td><span class="pill <?= h($u['status']) ?>"><?= h($u['status']) ?></span></td>
              <td><?= (int) $u['dev'] ?> / <?= (int) cfg()['device_limit'] ?></td>
              <td class="muted"><?= h($u['seen'] ?: 'never') ?></td>
              <td><a href="admin.php?edit=<?= (int) $u['id'] ?>">Manage &rarr;</a></td>
            </tr>
          <?php endforeach; ?>
          <?php if (!$users): ?><tr><td colspan="6" class="muted">No users yet.</td></tr><?php endif; ?>
        </table>
      </div>
    <?php endif; ?>
  </main>
<?php endif; ?>
