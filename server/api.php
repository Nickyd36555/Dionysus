<?php
/**
 * Dionysus device API — the endpoints the Android app calls.
 * Single entry point so no URL-rewrite config is needed on Cloudways:
 *
 *   POST api.php?action=login    { username, pin, device_id, device_name }
 *   GET  api.php?action=profile  (Authorization: Bearer <token>)
 *   POST api.php?action=logout   (Authorization: Bearer <token>)
 */

declare(strict_types=1);
require __DIR__ . '/lib.php';

$action = $_GET['action'] ?? '';

try {
    switch ($action) {
        case 'login':   handle_login();   break;
        case 'profile': handle_profile(); break;
        case 'logout':  handle_logout();  break;
        default:        json_out(['error' => 'unknown_action'], 404);
    }
} catch (Throwable $e) {
    json_out(['error' => 'server_error'], 500);
}

function handle_login(): void
{
    $in = json_in();
    $username   = trim((string) ($in['username'] ?? ''));
    $pin        = (string) ($in['pin'] ?? '');
    $deviceId   = trim((string) ($in['device_id'] ?? ''));
    $deviceName = trim((string) ($in['device_name'] ?? ''));

    if ($username === '' || $pin === '' || $deviceId === '') {
        json_out(['error' => 'missing_fields'], 400);
    }

    $db = db();
    $stmt = $db->prepare('SELECT * FROM users WHERE username = ?');
    $stmt->execute([$username]);
    $user = $stmt->fetch();

    // Same generic response for unknown user and wrong PIN (no account enumeration).
    if (!$user || !password_verify($pin, $user['pin_hash'])) {
        json_out(['error' => 'invalid_credentials'], 401);
    }
    if ($user['status'] !== 'active') {
        json_out(['error' => 'disabled'], 403);
    }

    $userId = (int) $user['id'];

    // Device binding / cap enforcement.
    $stmt = $db->prepare('SELECT id FROM devices WHERE user_id = ? AND device_id = ?');
    $stmt->execute([$userId, $deviceId]);
    $known = $stmt->fetch();

    if ($known) {
        $db->prepare('UPDATE devices SET last_seen_at = NOW(), device_name = ? WHERE id = ?')
           ->execute([$deviceName, $known['id']]);
    } else {
        $cnt = $db->prepare('SELECT COUNT(*) FROM devices WHERE user_id = ?');
        $cnt->execute([$userId]);
        $limit = (int) cfg()['device_limit'];
        if ((int) $cnt->fetchColumn() >= $limit) {
            json_out(['error' => 'device_limit'], 409);
        }
        $db->prepare('INSERT INTO devices (user_id, device_id, device_name) VALUES (?, ?, ?)')
           ->execute([$userId, $deviceId, $deviceName]);
    }

    // Issue a fresh token for this device (drop any old ones for it).
    $db->prepare('DELETE FROM tokens WHERE user_id = ? AND device_id = ?')
       ->execute([$userId, $deviceId]);
    $token = new_token();
    $db->prepare('INSERT INTO tokens (token, user_id, device_id) VALUES (?, ?, ?)')
       ->execute([$token, $userId, $deviceId]);

    json_out([
        'token'   => $token,
        'profile' => profile_for($db, $userId),
        'display_name' => $user['display_name'],
    ]);
}

function handle_profile(): void
{
    [$db, $tok] = require_token();
    $db->prepare('UPDATE tokens SET last_seen_at = NOW() WHERE token = ?')->execute([$tok['token']]);
    $db->prepare('UPDATE devices SET last_seen_at = NOW() WHERE user_id = ? AND device_id = ?')
       ->execute([(int) $tok['user_id'], $tok['device_id']]);
    json_out(['profile' => profile_for($db, (int) $tok['user_id'])]);
}

function handle_logout(): void
{
    [$db, $tok] = require_token();
    $db->prepare('DELETE FROM tokens WHERE token = ?')->execute([$tok['token']]);
    json_out(['ok' => true]);
}

/** Validate the Bearer token and the user's active status; returns [db, tokenRow]. */
function require_token(): array
{
    $auth = $_SERVER['HTTP_AUTHORIZATION'] ?? ($_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '');
    if (!preg_match('/Bearer\s+([a-f0-9]{64})/i', $auth, $m)) {
        json_out(['error' => 'unauthorized'], 401);
    }
    $db = db();
    $stmt = $db->prepare(
        'SELECT t.*, u.status FROM tokens t JOIN users u ON u.id = t.user_id WHERE t.token = ?'
    );
    $stmt->execute([$m[1]]);
    $tok = $stmt->fetch();
    if (!$tok) {
        json_out(['error' => 'unauthorized'], 401);
    }
    if ($tok['status'] !== 'active') {
        json_out(['error' => 'disabled'], 403);
    }
    return [$db, $tok];
}
