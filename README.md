# Elements: Economy

Currency bridge for NeoForge 1.21.1 servers. Finds the server's currency and connects it to every player shop, server shop and auction house, other mods' and its own. When the owner changes currency, everything follows.

- Server-side only. No client mod, no resource pack.
- Bundles Elements: Vault (the shared economy API).
- Runs on its own or next to Elements: Nexus.

## Entering prices
Prices and amounts are typed as one number in the **smallest coin** of the server's currency, as set in `config/elements_economy/currency_values.toml`. The menus then show the price in coins.

With a Bronze 1 / Silver 100 / Gold 10,000 ladder:

| Price | Type |
|---|---|
| 10 Bronze | `10` |
| 5 Silver | `500` |
| 1 Gold | `10000` or `10k` |
| 1 Gold 50 Silver | `15000` |

`k` = thousand and `m` = million. Don't add a coin letter: a trailing `b` means billion, not Bronze.

## Building
1. In `elements-vault`: `gradlew publishToMavenLocal` (once per Vault version).
2. Here: `gradlew build`. The jar is `build/libs/elements_economy-<version>.jar`.

Design notes are in `docs/`.
