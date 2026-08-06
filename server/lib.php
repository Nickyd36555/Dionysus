<?php
/**
 * Shared helpers: DB connection, config, JSON I/O, tokens, and the profile shape
 * returned to the app. Included by api.php and admin.php.
 */

declare(strict_types=1);

/** Load config.php (copied from config.sample.php), or die with a clear message. */
function cfg(): array
{
    static $cfg = null;
    if ($cfg === null) {
        $path = __DIR__ . '/config.php';
        if (!is_file($path)) {
            http_response_code(500);
            exit('Missing config.php — copy config.sample.php to config.php and fill it in.');
        }
        $cfg = require $path;
    }
    return $cfg;
}

/** Shared PDO connection (throws on error, returns assoc arrays). */
function db(): PDO
{
    static $pdo = null;
    if ($pdo === null) {
        $c = cfg();
        $dsn = sprintf('mysql:host=%s;dbname=%s;charset=utf8mb4', $c['db_host'], $c['db_name']);
        $pdo = new PDO($dsn, $c['db_user'], $c['db_pass'], [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
    }
    return $pdo;
}

/** Send a JSON response and stop. */
function json_out($data, int $code = 200): void
{
    http_response_code($code);
    header('Content-Type: application/json');
    echo json_encode($data);
    exit;
}

/** Read and decode a JSON request body into an array. */
function json_in(): array
{
    $raw = file_get_contents('php://input') ?: '';
    $data = json_decode($raw, true);
    return is_array($data) ? $data : [];
}

/** A cryptographically-random 64-char hex token. */
function new_token(): string
{
    return bin2hex(random_bytes(32));
}

/** The profile object the app consumes. */
function profile_for(PDO $db, int $userId): array
{
    $stmt = $db->prepare('SELECT * FROM profiles WHERE user_id = ?');
    $stmt->execute([$userId]);
    $p = $stmt->fetch() ?: [];
    return [
        'xtream_host'      => $p['xtream_host'] ?? '',
        'xtream_user'      => $p['xtream_user'] ?? '',
        'xtream_pass'      => $p['xtream_pass'] ?? '',
        'epg_url'          => $p['epg_url'] ?? '',
        'debrid_token'     => $p['debrid_token'] ?? '',
        'default_category' => $p['default_category'] ?? '',
        'feature_vod'       => (int) ($p['feature_vod'] ?? 1) === 1,
        'feature_downloads' => (int) ($p['feature_downloads'] ?? 1) === 1,
        'feature_search'    => (int) ($p['feature_search'] ?? 1) === 1,
    ];
}
