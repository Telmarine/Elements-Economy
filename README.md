# Elements: Economy

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=for-the-badge) ![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-E68A2E?style=for-the-badge) ![Environment](https://img.shields.io/badge/Environment-Server-5865F2?style=for-the-badge) ![Type](https://img.shields.io/badge/Type-Economy%20%2F%20Shops-8A2BE2?style=for-the-badge) [![Source](https://img.shields.io/badge/Source-GitHub-181717?style=for-the-badge&logo=github)](https://github.com/Telmarine/elements-economy)

Elements: Economy is a server-side economy mod for NeoForge 1.21.1. I couldn't find an economy setup for 1.21.1 that worked with the mods I already run, so I made my own. It finds whatever currency your server uses and connects it to your shops and auction house, so everything runs on the same money. When you change currency later, everything follows.

It runs on its own or next to Elements: Nexus. Neither one needs the other.

## Features

- **Currency detection:** finds supported currency mods automatically, can use vanilla items such as emeralds when an admin picks them in chat, or falls back to plain digital money. Item values are set in `currency_values.toml`, so you can make 1 Block of Emerald = 9 Emerald or any ratio you want. Money is always shown with your currency's own item names, and digital money can be given any name. See [Currency setups](#currency-setups).
- **Player shops:** chest and barrel buy/sell shops with a sign display, owner menu, sale alerts, daily limits and per-rank shop limits. Create Item Vaults can be linked as stock vaults that restock your shops.
- **Server shops:** admin shops that open with `/shop` or from a linked villager, NPC or block.
- **Auction house:** buy-now and bid listings, search, categories, sales tax, a blacklist, and a collection box for your money and items.
- **Auction house links:** staff can make an NPC, a villager or a block open the auction house, the same way server shops can.
- **Currency switch-over:** `/economy currency switch` shows a preview, then converts balances, shop prices, shop tills, listings and collection boxes to the new currency. A backup is made first.
- **Migration:** imports balances from Impactor, Eights Economy, Wano Economy and most currency mods, with a preview before anything is written.
- **Works with other mods:** bridges for Impactor, OctoEconomy, SDM, Spud's Shops and Easy NPC. If another mod already handles shops or the auction house, Economy turns its own off so they don't clash (you can force them back on).
- **Shop protection:** hoppers, pistons and Create drills/saws/contraptions can't steal from or break shops.
- **Master Control File:** every feature and command has its own switch in `features.toml`. Anything switched off isn't registered at all.
- **Private trades:** purchases are never announced in chat or Discord. `/payments` shows your own history.
- **Storage:** JSON (default), YAML, SQLite or MySQL. Switch with `/economy storage convert`.

## Commands

- `/bal [player]`, `/pay <player> <amount>`, `/pay toggle`, `/baltop`, `/payments`
- `/exchange deposit [amount] [item]` and `/exchange withdraw <amount> [item]`: move currency items into your bank (digital money) and back, e.g. `/exchange deposit 2 minecraft:emerald_block`
- `/shop create buy` or `/shop create sell` (look at a chest or barrel), `/shop rename <name>`, `/shop remove`
- `/shop`: server shops
- `/ah`: auction house
- `/ah link npc | villager | block` and `/ah unlink`: link or unlink the auction house (admin)
- `/eco give | take | set | reset <player> <amount>` (admin)
- `/economy reload | version | migrate | storage convert` (admin)
- `/economy currency switch <new currency>` (admin)
- `/economy currency setup`: pick digital money or vanilla items, from chat (admin)
- `/economy currency values`, `/economy currency value <item> <value>`, `/economy currency remove <item>`: see and change currency values in game (admin)

## Permissions

Nodes are `economy.<feature>.<action>` and work with Elements: Nexus ranks, other permission mods, or OP levels. Player commands default to everyone, staff tools to OP 2 and admin commands to OP 3. Shop and listing limits can be set per rank with the `economy.shop.limit` and `economy.ah.limit` number nodes.

The full list is in [docs/PERMISSIONS.md](docs/PERMISSIONS.md).

## Currency setups

Pick whichever fits your server. Economy shows money in your currency's own names everywhere, e.g. "3 Block of Emerald, 5 Emerald" or "1,250 Credits".

Money is always 1:1 between currencies: 1 Emerald is worth 1 digital money, the same as any other currency. Values between items of the same currency come from `currency_values.toml`. The one exception is moving a server from digital money to a physical currency with `/economy currency switch` or `/economy migrate`: `rates` in `economy.toml` can set what one of the new smallest items is worth in the old digital money.

**Digital money only.** Nothing to install. Name it under `[digital]` in `config/elements_economy/economy.toml`: `name` (one), `plural` (more than one), `symbol` (shown before amounts, e.g. `$`), `decimals` and `icon` (the item shown in menus). Defaults: Coin / Coins, no symbol, 0 decimals, paper.

**A currency mod.** Install a supported currency mod and Economy finds it at startup. Economy fills `config/elements_economy/currency_values.toml` with that mod's items, one line per full item ID with what one of it is worth in your smallest unit. Change the values there and run `/economy reload`, or in game with `/economy currency values`.

**No currency mod.** Admins are asked in chat, on joining, whether the server uses digital money or vanilla items, with a button for each (`/economy currency setup` shows the question again any time). Digital money is used until someone picks.

- **Digital money:** nothing else to do. `currency_values.toml` stays empty.
- **Vanilla items:** add each item that counts as money and what one of it is worth in your smallest unit, using the example buttons or `[+ Add item]` (e.g. `minecraft:emerald` = 1, `minecraft:emerald_block` = 9). Click `[Confirm]` when the list is right. Confirming is the approval: vanilla items are never money otherwise. The items are written to `currency_values.toml` and become the main currency.

`currency_values.toml` only lists what's in use. Lines for a currency mod that's removed from the server are dropped, and the old file is backed up first. Changes made in chat are saved without a backup.

Backups of `messages.toml` and `currency_values.toml` are saved next to them as `<file>.<date-time>.bak`. Only the newest 2 of each are kept; set `backups_kept` under `[files]` in `economy.toml` (0-20).

**In game:** `/economy currency values` lists every currency item with its value and `[Change]` / `[Remove]` buttons; `/economy currency value <item> <value>` and `/economy currency remove <item>` do the same by command. With vanilla items, `[+ Add item]` adds another one.

**A currency mod where money is an account balance** instead of items players carry. Economy finds it at startup and uses that balance for `/bal`, shops and the auction house. The bank and `/exchange` below only work with currency players can hold as items.

### The bank and /exchange

With an item currency (a currency mod with items, or vanilla items), players can also keep digital money in a bank:

- `/exchange deposit [amount] [item]` puts the currency items you carry into your bank (no amount = all of them). `/exchange withdraw <amount> [item]` takes money out as currency items, paid in the fewest items possible.
- The amount is counted in your smallest item unless you add an item ID: `/exchange deposit 2 minecraft:emerald_block` deposits 2 Blocks of Emerald's worth (18 Emerald), `/exchange withdraw 20 minecraft:emerald` withdraws 20 Emeralds' worth. Press Tab to see the IDs your server uses.
- 1:1: one of your smallest currency items = one digital money.
- When the item currency is the main currency, `/bal` shows the items you carry plus a "Bank:" line once your bank has money in it.
- `bank_as_coins` in `features.toml` (on by default) shows bank amounts in your currency items, e.g. "3 Block of Emerald, 5 Emerald". Off = they always use the digital money name.

## Messages

Every line players and admins see is in `config/elements_economy/messages.toml`. After an update, lines you never edited get the new built-in text and new lines are added, while lines you edited are kept. A backup of the old file is saved next to it first. Turn this off with `messages_auto_update` under `[admin]` in `features.toml`.

## Entering prices

Prices are typed as one number in your smallest currency unit. With Emerald 1 / Block of Emerald 9:

| Price | Type |
| --- | --- |
| 10 Emerald | `10` |
| 2 Block of Emerald | `18` |
| 3 Block of Emerald 5 Emerald | `32` |
| 1,000 Emerald | `1000` or `1k` |

`k` = thousand and `m` = million. Don't add a currency letter: a trailing `b` means billion.

## Good to know

- Vanilla chest menus only, no resource pack needed.
- Item currencies use whole items only. Anything smaller stays in the shop till instead of disappearing.
- SQLite and MySQL need their driver jar in `<server>/elements_economy/drivers` (not included): `sqlite-jdbc` for SQLite, `mariadb-java-client` for MySQL. Without it the server stops at startup and logs which jar is missing, so no data gets split.
- **Needs Elements: Vault.** Install the Elements: Vault jar next to Economy. Other mods hook into your economy through it.

## Building

1. In `elements-vault`: `gradlew publishToMavenLocal` (once per Vault version).
2. Here: `gradlew build`. The jar is `build/libs/elements_economy-<version>.jar`.

Design notes are in `docs/`.

## License

Free to use in any modpack, paid or free, with credit to **Telmarine**. It may not be resold or repackaged as a paid product on its own. See [LICENSE](LICENSE).
