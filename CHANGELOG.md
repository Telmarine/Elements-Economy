# Elements: Economy changelog

## 1.0.1

### New
- **Auction house links** - staff can make an NPC, a villager or a block open the auction house, the same way server shops can. Look at it and run `/ah link npc` or `/ah link block`, or stand where it should go and run `/ah link villager` to spawn one. `/ah unlink` removes the link you're looking at (and the villager, if `/ah link villager` made it). Linked blocks can only be broken by staff with `economy.ah.link`. Something already linked to a server shop keeps opening the server shop. Switch: `links` under `[auction_house]` in features.toml. Permission: `economy.ah.link` (OP 3+). The villager's name is `villager_name` under `[ah]` in messages.toml.

### Changed
- **SQLite and MySQL drivers are no longer included**, so the jar is much smaller. json (default) and yaml work as before. To use sqlite or mysql, put the driver jar in `<server>/elements_economy/drivers`: `sqlite-jdbc` (org.xerial) for sqlite, `mariadb-java-client` (org.mariadb.jdbc) for mysql. A driver another mod already provides is used first. Without one, Economy logs which jar is missing and the server stops starting, so no data is lost or split.

### Upgrading
- Servers on sqlite or mysql: add the driver jar to `elements_economy/drivers` before updating, or the server won't start.

## 1.0.0
- First release.
