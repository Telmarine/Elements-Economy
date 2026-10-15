# Elements: Economy

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=for-the-badge) ![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-E68A2E?style=for-the-badge) ![Side](https://img.shields.io/badge/Side-Server-5865F2?style=for-the-badge) ![Type](https://img.shields.io/badge/Type-Economy%20%2F%20Shops-8A2BE2?style=for-the-badge) [![Source](https://img.shields.io/badge/Source-GitHub-181717?style=for-the-badge&logo=github)](https://github.com/Telmarine/TAC5-Studios/tree/main/elements-economy)

Elements: Economy is a server-side economy mod for NeoForge 1.21.1. I couldn't find an economy setup for 1.21.1 that worked with the mods I already run, so I made my own. It finds whatever currency your server uses and connects it to your shops and auction house, so everything runs on the same money. When you change currency later, everything follows.

It runs on its own or next to Elements: Nexus. Neither one needs the other.

## Features

- **Currency detection:** finds currency mods automatically (like Aiycoin), or falls back to plain digital money. Coin values are set in `currency_values.toml`, so you can make 100 Bronze = 1 Silver or any ratio you want.
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
- `/exchange deposit [amount] [coin]` and `/exchange withdraw <amount> [coin]`: move coins into your bank (digital money) and back, e.g. `/exchange deposit 30 gold`
- `/shop create buy` or `/shop create sell` (look at a chest or barrel), `/shop rename <name>`, `/shop remove`
- `/shop`: server shops
- `/ah`: auction house
- `/ah link npc | villager | block` and `/ah unlink`: link or unlink the auction house (admin)
- `/eco give | take | set | reset <player> <amount>` (admin)
- `/economy reload | version | migrate | storage convert` (admin)
- `/economy currency switch <new currency>` (admin)

## Permissions

Nodes are `economy.<feature>.<action>` and work with Elements: Nexus ranks, other permission mods, or OP levels. Player commands default to everyone, staff tools to OP 2 and admin commands to OP 3. Shop and listing limits can be set per rank with the `economy.shop.limit` and `economy.ah.limit` number nodes.

The full list is in [docs/PERMISSIONS.md](docs/PERMISSIONS.md).

## Entering prices

Prices are typed as one number in your smallest coin. With a Bronze 1 / Silver 100 / Gold 10,000 ladder:

| Price | Type |
| --- | --- |
| 10 Bronze | `10` |
| 5 Silver | `500` |
| 1 Gold | `10000` or `10k` |
| 1 Gold 50 Silver | `15000` |

`k` = thousand and `m` = million. Don't add a coin letter: a trailing `b` means billion, not Bronze.

## Good to know

- Vanilla chest menus only, no resource pack needed.
- Coin currencies use whole coins only. Anything smaller stays in the shop till instead of disappearing.
- SQLite and MySQL need their driver jar in `<server>/elements_economy/drivers` (not included): `sqlite-jdbc` for SQLite, `mariadb-java-client` for MySQL. Without it the server stops at startup and logs which jar is missing, so no data gets split.
- **Needs Elements: Vault.** Install the Elements: Vault jar next to Economy. Other mods hook into your economy through it.

## Building

1. In `elements-vault`: `gradlew publishToMavenLocal` (once per Vault version).
2. Here: `gradlew build`. The jar is `build/libs/elements_economy-<version>.jar`.

Design notes are in `docs/`.

## License

Free to use in any modpack, paid or free, with credit to **Telmarine**. It may not be resold or repackaged as a paid product on its own. See [LICENSE](LICENSE).
