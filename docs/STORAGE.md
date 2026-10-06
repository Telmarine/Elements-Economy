# Elements: Economy — data storage

Same storage system as Elements: Nexus, with its own config, folder and table. The two mods never share data files.

## storage.toml (config/elements_economy/storage.toml)
| Setting | Default | What it does |
|---|---|---|
| backend | json | json, yaml, sqlite or mysql. Falls back to json if it can't start. (restart) |
| folder | elements_economy/store | Where json/yaml files and economy.db live. |
| save_interval_seconds | 60 | Seconds between saves of changed data. |
| quick_save_money | true | Money changes save within 2 seconds instead of waiting for the timer. |
| backups.keep | 10 | Backups kept. Oldest deleted first. 0 = all. |
| logs.folder | logs/elements_economy | Daily transaction log files. |
| logs.keep_days | 30 | Days of log files kept. 0 = all. |
| logs.history_size | 50 | Payments kept per player for /payments. |
| mysql.* | localhost:3306 / elements_economy | Only for backend = mysql. |

## How it works
- All data lives in memory and saves in batches on its own thread. The server never waits on disk or database.
- Each collection is a map of key → JSON value. json/yaml: one file per collection. sqlite/mysql: one table `economy_data (collection, data_key, data_value)`.
- Files save through a temp file and a move, so a crash never leaves half a file.
- A broken file is copied to `<name>.json.broken-<time>` and that collection starts empty. A collection that fails to load is never saved that run, so stored data isn't overwritten.
- `/economy storage convert <type>` copies everything to another backend while the server runs. Change `backend` and restart to use it.
- Drivers (sqlite-jdbc, mariadb client, snakeyaml) are bundled with jarJar, same version ranges as Nexus, so only one copy loads when both mods are installed.

## Money safety
- Amounts are saved as **text** (`"125000"`), in the currency's smallest unit. No decimals, no rounding, no size limit (Aiycoin reaches 10^12+). Matches Elements: Vault `Money(BigInteger)`.
- Only **digital** balances are stored here. Mod currencies stay in their own mod (API, player data, or coins in the inventory). Economy reads and writes them through adapters and never keeps a second copy.
- `Storage.moneyChanged()` after every money movement; `Storage.saveNow()` before and after big changes.

## Backups
- Taken before a currency switch-over, a migration and a storage convert. If the backup fails, the change does not happen.
- Always plain json in `<folder>/backups/<yyyy-MM-dd_HH-mm-ss>-<reason>/`, whatever the backend, so an owner can read or restore by hand.

## Transaction log
- `logs/elements_economy/transactions-YYYY-MM-DD.log`, one plain line per money movement. Switched by `economy.transaction_log`.
- Kept outside the store: append-only, for people to read, and readable even if the store breaks.
- Example: `14:02:11 PAY Steve -> Alex 250 Coins (elements_economy:digital) "for the iron"`
- Tags: PAY, GIVE, TAKE, SET, RESET, SHOP_BUY, SHOP_SELL, AH_SALE, AH_BID, AH_FEE, AH_TAX, EXCHANGE, SWITCH, MIGRATE.

## Items
- Items are saved with the full item codec (all components: enchantments, names, box contents).
- If an item can't be read back (its mod was removed), the original JSON is kept, so the item returns when the mod does. The shop row or listing shows as unavailable until then.

## Collections
| Collection | Key | Value |
|---|---|---|
| meta | setting | `schema_version`, `last_start`, `switches` (every currency switch: `from`, `to`, `from_rate`, `to_rate`, `when`), `migrations` (per source: `when`, `count`, `currency`) |
| balances | player UUID | `{ "elements_economy:digital": "125000" }` (one entry per digital currency when multiple currencies is on) |
| accounts | player UUID | `name`, `pay_toggle`, `last_seen`, `switch_step` (how many switches this player's balance has been through) |
| history | player UUID | list of the last N payments: `time`, `kind`, `other`, `currency`, `amount`, `note` (newest first) |
| player_shops | shop id | `owner`, `type` (buy/sell), `name`, `dimension`, `pos`, `container` (block id), `created`, `rows[]` (below) |
| stock_links | `dimension|x,y,z` of the vault | `shops[]` (shop ids it feeds) |
| server_shops | shop id | `name`, `type`, `opener` (command / npc / villager / block + target), `rows[]`, `stock_limits`, `rank_prices` |
| listings | listing id | `seller`, `item`, `qty`, `currency`, `buy_now`, `start_bid`, `bids[]` (`bidder`, `amount`, `time`), `created`, `ends` |
| collect | player UUID | `items[]`, `money[]` (`currency`, `amount`) waiting from won, expired or cancelled auctions |
| daily | shop id | `day`, `{ row key: count }`; resets when `day` changes |
| bridged | `modid:shop id` | owner answers and last-known price item for other mods' shops |

Shop row: `item` (item JSON), `currency`, `price` (text), `per` (quantity per sale), `daily_limit`, `enabled`.

IDs: shops and listings use short random ids (8 base-36 chars), so moving or renaming never breaks a link.

## Files
- config/StorageConfig.java
- storage/StorageBackend, JsonBackend, YamlBackend, SqlBackend, SqliteBackend, MysqlBackend, DataStore, StorageConvert — ported from Nexus
- storage/Storage — start/tick/stop on server events, quick save, schema version
- storage/Collections — collection names
- storage/Backups — full json backups, pruned to `keep`
- storage/TransactionLog — daily log files, pruned to `keep_days`
- storage/ItemData — item ↔ JSON
