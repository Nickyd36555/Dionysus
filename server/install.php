<?php
/**
 * One-time setup: creates the tables and your first admin account.
 *
 * Run it ONCE from your browser:
 *   https://your-domain/install.php?secret=YOUR_INSTALL_SECRET
 * It will show a form to create the admin. Then DELETE this file.
 *
 * The secret must match `install_secret` in config.php, so a stranger can't
 * hit this endpoint and seize the database.
 */

declare(strict_types=1);
require __DIR__ . '/lib.php';

$secret = $_GET['secret'] ?? ($_POST['secret'] ?? '');
if (!hash_equals((string) cfg()['install_secret'], (string) $secret)) {
    http_response_code(403);
    exit('Forbidden — wrong or missing ?secret.');
}

$db = db();

// Create tables from schema.sql (idempotent — safe to re-run).
$sql = file_get_contents(__DIR__ . '/schema.sql') ?: '';
foreach (array_filter(array_map('trim', explode(';', $sql))) as $stmt) {
    $db->exec($stmt);
}

$msg = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $u = trim((string) ($_POST['admin_user'] ?? ''));
    $p = (string) ($_POST['admin_pass'] ?? '');
    if ($u === '' || strlen($p) < 8) {
        $msg = 'Username required and password must be at least 8 characters.';
    } else {
        $hash = password_hash($p, PASSWORD_DEFAULT);
        $ins = $db->prepare(
            'INSERT INTO admins (username, password_hash) VALUES (?, ?)
             ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash)'
        );
        $ins->execute([$u, $hash]);
        $msg = 'Admin "' . htmlspecialchars($u) . '" is ready. '
             . 'Now DELETE install.php, then open admin.php to sign in.';
    }
}
?>
<!doctype html>
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dionysus — Setup</title>
<style>
  body{font-family:system-ui,sans-serif;background:#0e0f13;color:#eaeaf0;max-width:460px;margin:6vh auto;padding:0 20px}
  h1{color:#c9a24a;font-weight:700}
  label{display:block;margin:14px 0 4px;color:#b9b9c6}
  input{width:100%;padding:11px;border-radius:9px;border:1px solid #2a2b33;background:#16171d;color:#fff;box-sizing:border-box}
  button{margin-top:20px;width:100%;padding:12px;border:0;border-radius:9px;background:#c9a24a;color:#12130f;font-weight:700;font-size:15px;cursor:pointer}
  .msg{margin-top:18px;padding:12px;border-radius:9px;background:#1c2a1c;color:#bfe6bf}
</style>
<h1>Dionysus setup</h1>
<p>Tables are created. Create your admin account, then delete this file.</p>
<?php if ($msg): ?><div class="msg"><?= $msg ?></div><?php endif; ?>
<form method="post">
  <input type="hidden" name="secret" value="<?= htmlspecialchars((string) $secret) ?>">
  <label>Admin username</label>
  <input name="admin_user" autocomplete="off" required>
  <label>Admin password (min 8 chars)</label>
  <input name="admin_pass" type="password" autocomplete="new-password" required>
  <button type="submit">Create / update admin</button>
</form>
