# Elements: Economy — permissions

- Every node is `economy.<feature>.<action>`, registered with the NeoForge PermissionAPI.
- Works with Elements: Nexus ranks (`permissionHandler = "elements_nexus:permissions"`), any other permission mod, or plain OP levels. Nexus is never required.
- A node whose feature switch is off is not registered at all.
- Console and command blocks use OP level (at least 2).
- Limits: number nodes first (set by a permission mod), then the Nexus rank table when `nexus.rank_limits` is on, then the config default.
- Players can buy and sell at server shops, but only admins create, edit or delete them.

| Node | Default | Switches (features.toml) | Notes |
|---|---|---|---|
| economy.balance | Everyone | economy.enabled, economy.balance |  |
| economy.balance.others | OP 2+ | economy.enabled, economy.balance_others |  |
| economy.pay | Everyone | economy.enabled, economy.pay |  |
| economy.pay.offline | Everyone | economy.enabled, economy.pay_offline |  |
| economy.pay.toggle | Everyone | economy.enabled, economy.pay_toggle |  |
| economy.baltop | Everyone | economy.enabled, economy.baltop |  |
| economy.history | Everyone | economy.enabled, economy.history |  |
| economy.history.others | OP 2+ | economy.enabled, economy.history |  |
| economy.eco.give | OP 3+ | economy.enabled, economy.eco_give |  |
| economy.eco.take | OP 3+ | economy.enabled, economy.eco_take |  |
| economy.eco.set | OP 3+ | economy.enabled, economy.eco_set |  |
| economy.eco.reset | OP 3+ | economy.enabled, economy.eco_reset |  |
| economy.exchange.deposit | Everyone | currency.exchange, currency.deposit |  |
| economy.exchange.withdraw | Everyone | currency.exchange, currency.withdraw |  |
| economy.currency.confirm | OP 3+ | admin.enabled, admin.currency_confirm | /economy currency pending, confirm, ignore (vanilla item "Are you sure?"). |
| economy.currency.switch | OP 3+ | admin.enabled, admin.currency_switch, switch_over.enabled | /economy currency switch <new> and switch confirm. |
| economy.shop.create.buy | Everyone | player_shops.enabled, player_shops.create_buy |  |
| economy.shop.create.sell | Everyone | player_shops.enabled, player_shops.create_sell |  |
| economy.shop.rename | Everyone | player_shops.enabled, player_shops.rename |  |
| economy.shop.remove | Everyone | player_shops.enabled, player_shops.remove |  |
| economy.shop.manage | Everyone | player_shops.enabled, player_shops.manage |  |
| economy.shop.alerts | Everyone | player_shops.enabled, player_shops.sale_alerts |  |
| economy.shop.others | OP 2+ | player_shops.enabled | Staff can rename, manage or remove anyone's shop. |
| economy.shop.bypass | OP 2+ | player_shops.enabled, player_shops.protection | Staff can open and break shop containers they don't own. |
| economy.shop.claim.bypass | OP 2+ | player_shops.enabled, player_shops.claim_check | Create shops outside your own claims. |
| economy.shop.free | OP 2+ | player_shops.enabled, player_shops.create_fee | No fee when creating a shop. |
| economy.shop.nolimit | OP 2+ | player_shops.enabled, player_shops.rank_limits | No limit on how many shops you own. |
| economy.stockvault.link | Everyone | stock_vaults.enabled, stock_vaults.link | Link a Create item vault to your shops as a stock vault. |
| economy.servershop.use | Everyone | server_shops.enabled | Buy and sell at server shops (any way they open). |
| economy.servershop.create | OP 3+ | server_shops.enabled, server_shops.create |  |
| economy.servershop.edit | OP 3+ | server_shops.enabled, server_shops.edit |  |
| economy.servershop.delete | OP 3+ | server_shops.enabled, server_shops.delete |  |
| economy.ah.open | Everyone | auction_house.enabled, auction_house.open |  |
| economy.ah.sell | Everyone | auction_house.enabled, auction_house.sell |  |
| economy.ah.buy | Everyone | auction_house.enabled, auction_house.buy_now |  |
| economy.ah.bid | Everyone | auction_house.enabled, auction_house.bids |  |
| economy.ah.cancel | Everyone | auction_house.enabled, auction_house.cancel |  |
| economy.ah.collect | Everyone | auction_house.enabled, auction_house.collect |  |
| economy.ah.alerts | Everyone | auction_house.enabled, auction_house.sale_alerts |  |
| economy.ah.notax | OP 2+ | auction_house.enabled, auction_house.sales_tax | No sales tax. |
| economy.ah.nolimit | OP 2+ | auction_house.enabled, auction_house.rank_limits | No limit on how many listings you have. |
| economy.ah.remove | OP 2+ | auction_house.enabled, auction_house.admin_remove | Remove anyone's listing. |
| economy.ah.blacklist | OP 3+ | auction_house.enabled, auction_house.blacklist | Edit the list of items that can't be sold. |
| economy.admin.reload | OP 3+ | admin.enabled, admin.reload |  |
| economy.admin.version | OP 2+ | admin.enabled, admin.version |  |
| economy.admin.storage | OP 3+ | admin.enabled, admin.storage_convert |  |
| economy.admin.migrate | OP 3+ | migration.enabled |  |
| economy.shop.limit | number (-1) | player_shops.enabled, player_shops.rank_limits | How many shops a player may own. -1 = use the default from the config. |
| economy.ah.limit | number (-1) | auction_house.enabled, auction_house.rank_limits | How many auction listings a player may have. -1 = use the default from the config. |
