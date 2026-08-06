<?php
/** Health check / landing. Confirms PHP + DB are wired without exposing anything. */
declare(strict_types=1);
require __DIR__ . '/lib.php';

$dbOk = false;
try { db()->query('SELECT 1'); $dbOk = true; } catch (Throwable $e) { $dbOk = false; }

header('Content-Type: application/json');
echo json_encode([
    'service' => 'dionysus-backend',
    'status'  => 'ok',
    'db'      => $dbOk ? 'connected' : 'error',
]);
