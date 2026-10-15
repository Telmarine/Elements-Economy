# Elements: Economy changelog

## 1.0.2

### Changed
- **Elements: Vault is no longer inside the Economy jar.** Install the Elements: Vault jar next to Economy (1.0.0 or newer); the server won't start Economy without it.
- **Create hooks only load when Create is installed.** Servers without Create never touch Create's classes.

### New
- **Bank shown in coins.** When a coin mod is found, digital money (the bank) is shown as coins everywhere, e.g. "177 Gold Coin, 14 Silver Coin, 11 Bronze Coin", including the transaction log. Switch: `bank_as_coins` under `[currency]` in features.toml (on by default).

### Fixed
- **`/exchange` was missing.** `/exchange deposit [amount]` turns the coins you carry into digital money (no amount = all of them) and `/exchange withdraw <amount>` turns digital money back into coins. It works whether coins or digital money is the main currency, as long as a coin mod is installed. The rate comes from `rates` in economy.toml; with no line for the coin, one of the smallest coin is worth one digital unit. Add a coin name after the amount to count in that coin: `/exchange deposit 30 gold`, `/exchange withdraw 5 silver` (tab-completes; withdrawals pay out in the fewest coins). When coins are the main currency, `/bal` also shows a "Bank:" line. Switches: `exchange`, `deposit` and `withdraw` under `[currency]` in features.toml. Permissions: `economy.exchange.deposit` and `economy.exchange.withdraw` (everyone).

### Upgrading
- Add the Elements: Vault jar to the mods folder.
- `messages.toml` isn't rewritten on update. To get the new bank wording, change `deposited`, `withdrew` and `remainder` under `[exchange]`, or delete those lines to use the new defaults.

## 1.0.1

### New
- **Auction house links** - staff can make an NPC, a villager or a block open the auction house, the same way server shops can. Look at it and run `/ah link npc` or `/ah link block`, or stand where it should go and run `/ah link villager` to spawn one. `/ah unlink` removes the link you're looking at (and the villager, if `/ah link villager` made it). Linked blocks can only be broken by staff with `economy.ah.link`. Something already linked to a server shop keeps opening the server shop. Switch: `links` under `[auction_house]` in features.toml. Permission: `economy.ah.link` (OP 3+). The villager's name is `villager_name` under `[ah]` in messages.toml.

### Changed
- **SQLite and MySQL drivers are no longer included**, so the jar is much smaller. json (default) and yaml work as before. To use sqlite or mysql, put the driver jar in `<server>/elements_economy/drivers`: `sqlite-jdbc` (org.xerial) for sqlite, `mariadb-java-client` (org.mariadb.jdbc) for mysql. A driver another mod already provides is used first. Without one, Economy logs which jar is missing and the server stops starting, so no data is lost or split.

### Upgrading
- Servers on sqlite or mysql: add the driver jar to `elements_economy/drivers` before updating, or the server won't start.

## 1.0.0
- First release.
