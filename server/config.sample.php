<?php
/**
 * Copy this file to `config.php` and fill in your Cloudways MySQL credentials.
 * `config.php` is git-ignored so your secrets never land in the repo.
 *
 * Find these values in the Cloudways panel:
 *   Servers → (your server) → your Application → "Access Details" → Database.
 */

return [
    // ── MySQL (from Cloudways "Access Details") ────────────────────────────
    'db_host' => '127.0.0.1',
    'db_name' => 'YOUR_DB_NAME',
    'db_user' => 'YOUR_DB_USER',
    'db_pass' => 'YOUR_DB_PASSWORD',

    // ── Policy ─────────────────────────────────────────────────────────────
    // How many TVs a single username+PIN may be bound to at once. You chose 1.
    'device_limit' => 1,

    // One-time setup password. `install.php` only runs if the caller supplies
    // this exact value, so a stranger can't create the tables/admin. Change it,
    // run install once, then delete install.php.
    'install_secret' => 'CHANGE_ME_TO_A_LONG_RANDOM_STRING',
];
