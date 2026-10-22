# Elements: Economy changelog

## 1.0.2

### Changed
- Elements: Vault is no longer inside the Economy jar. Install it separately (1.0.0 or newer); Economy won't start without it.

### Currency
Economy shows money in the server's own currency names, e.g. "3 Block of Emerald, 5 Emerald".

- Currency setup in chat: when no currency mod is found, admins are asked in chat whether the server uses digital money or vanilla items, with a button for each (`/economy currency setup` asks again any time). For vanilla items, add each item and what one of it is worth in your smallest unit (example buttons for Emerald and Block of Emerald, or `[+ Add item]`), then click `[Confirm]`. Confirming is the approval: vanilla items are never money otherwise. They become the main currency.
- `currency_values.toml` is filled in by Economy and only lists what's in use: the items of each currency mod found, and the vanilla items picked in setup. One line per full item ID (e.g. `"minecraft:emerald" = 1`) under one `[values]` heading. Lines for a currency mod that's removed are dropped, and the old file is backed up first. Files from earlier versions are converted, keeping your values.
- Change currency values in game: `/economy currency values` lists every currency item with `[Change]` and `[Remove]` buttons; `/economy currency value <item> <value>` and `/economy currency remove <item>` do the same by command.
- Digital money can be renamed under `[digital]` in `config/elements_economy/economy.toml`: `name` (one), `plural` (more than one), `symbol` (shown before amounts) and `icon` (the item shown in menus).
- `primary_currency` in economy.toml also accepts the full ID of a currency item, e.g. `"minecraft:emerald"`.
- New: the bank (digital money) is shown in the server's currency items everywhere, including the transaction log, e.g. "3 Block of Emerald, 5 Emerald". Without an item currency it uses the digital money name. Switch: `bank_as_coins` under `[currency]` in features.toml (on by default).
- Fixed: `/exchange` was missing. `/exchange deposit [amount] [item]` puts the currency items you carry into your bank as digital money (no amount = all of them). `/exchange withdraw <amount> [item]` takes digital money out of your bank as currency items, paid in the fewest items possible.
  - Works whether the item currency or digital money is the main currency, as long as the server has an item currency.
  - The amount is counted in your smallest item unless you add an item ID: `/exchange deposit 2 minecraft:emerald_block` deposits 2 Blocks of Emerald's worth (18 Emerald) and `/exchange withdraw 20 minecraft:emerald` withdraws 20 Emeralds' worth. Press Tab to see the IDs your server uses.
  - Always 1:1: one of your smallest currency items = one digital money.
  - When the item currency is the main currency, `/bal` shows the items you carry plus a "Bank:" line once your bank has money in it.
  - Switches: `exchange`, `deposit` and `withdraw` under `[currency]` in features.toml.
  - Permissions: `economy.exchange.deposit` and `economy.exchange.withdraw` (everyone).
- Money is now 1:1 between all currencies: 1 of any currency is worth 1 of any other. `rates` in economy.toml is only used when a server moves from digital money to a physical currency (`/economy currency switch` or `/economy migrate`); there it sets what one of the new smallest items is worth in the old digital money. The currency switch no longer refuses to run without a rate.
- Currency mods where money is an account balance instead of items you carry: Economy uses that balance for `/bal`, shops and the auction house. The bank and `/exchange` only work with currency players can hold as items.

### Fixed
- An empty balance shows the currency name, e.g. "0 Emerald", instead of just "0".
- The transaction log shows currency names instead of internal ids.

### Messages
- messages.toml now updates itself. After an update, lines you never edited get the new built-in text and new lines are added, while lines you edited are kept. A backup of the old file is saved next to it first. Switch: `messages_auto_update` under `[admin]` in features.toml (on by default).
- Backups of messages.toml and currency_values.toml: only the newest 2 of each are kept, older ones are deleted. Set `backups_kept` under `[files]` in economy.toml (0-20). Changes made in chat are saved without a backup.

### Upgrading
- Add the Elements: Vault jar to the mods folder.

## 1.0.1

### New
- **Auction house links** - staff can make an NPC, a villager or a block open the auction house, the same way server shops can. Look at it and run `/ah link npc` or `/ah link block`, or stand where it should go and run `/ah link villager` to spawn one. `/ah unlink` removes the link you're looking at (and the villager, if `/ah link villager` made it). Linked blocks can only be broken by staff with `economy.ah.link`. Something already linked to a server shop keeps opening the server shop. Switch: `links` under `[auction_house]` in features.toml. Permission: `economy.ah.link` (OP 3+). The villager's name is `villager_name` under `[ah]` in messages.toml.

### Changed
- **SQLite and MySQL drivers are no longer included**, so the jar is much smaller. json (default) and yaml work as before. To use sqlite or mysql, put the driver jar in `<server>/elements_economy/drivers`: `sqlite-jdbc` (org.xerial) for sqlite, `mariadb-java-client` (org.mariadb.jdbc) for mysql. A driver another mod already provides is used first. Without one, Economy logs which jar is missing and the server stops starting, so no data is lost or split.

### Upgrading
- Servers on sqlite or mysql: add the driver jar to `elements_economy/drivers` before updating, or the server won't start.

## 1.0.0
- First release.
