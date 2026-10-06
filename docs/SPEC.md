# Elements: Economy — v1 design notes

- Mod ID: elements_economy · Package: com.tac5studios.elementseconomy
- Target: NeoForge 21.1.x / Minecraft 1.21.1, Java 21
- License: same custom license as Title Scrolls / Nexus (free to use, credit Telmarine, no resale)

## PURPOSE
Elements: Economy is a **currency bridge**. It detects and identifies the currency a server uses, then connects that currency to every player shop, NPC shop and auction house — other mods' and its own. When the owner changes currency, the mod carries the new currency into every part of the economy so the change-over is smooth: prices, listings, trades and balances all follow.
- Built-in chest shops, NPC shops and auction house exist for servers that have no shop mods, and can be switched off.

## RULE — only mod currencies are currencies
- A legitimate currency is one that **comes with a mod** (digital from Elements: Economy, or a detected currency mod's money/coins).
- **Vanilla items are not currency by default** (emeralds, diamonds, gold, any `minecraft:` item). They don't affect any balance.
- Exception: if the server owner **adds a vanilla item to currency_values.toml** (under `[minecraft]`), Economy detects it as the likely currency medium — but always verifies first. Every online admin gets an "Are you sure?" message with [Confirm] / [Ignore] buttons (`/economy currency confirm|ignore <item>`, `/economy currency pending`). Until confirmed, the item does not count. Answers are saved in the file under `[confirmed]`.
- Shops and trades priced in unconfirmed vanilla items are **left alone**: no conversion, no payment interception, no switch-over.
- The API (Elements: Vault), balances, the Nexus side panel and placeholders recognise **mod currencies only**, plus vanilla items an admin has confirmed.
- Item-priced shops are **never converted to digital**. Switch-over only swaps one mod coin for another mod coin. Changing an item-priced shop to digital could break it.

## STANDING RULE — keep it simple
- Every setting, switch, and toggle has a short, plain name that says exactly what it does.
- Every `#` comment is one short, direct sentence. No jargon, no side notes.
- If a setting needs more than one line to explain, every line follows the same rule.

## REQUIRED: master control file
`config/elements_economy/features.toml` (source: config/Features.java).
- One boolean per feature and per subcommand/function. Feature off = everything under it off. Feature on = each child decides.
- A disabled command or permission node is not registered at all.
- Switches that need a restart say "(restart)" in their comment.
- Currency detection is the exception to per-item switches: **one switch (`currency.auto_detect`) covers every supported mod.**

## Side
- **Server-side only**, same as Nexus. Players install nothing.
- Vanilla chest menus, signs and vanilla items for all UI. No custom items, blocks or screens.
- No resource pack. No web dashboard (research: neither is needed).
- Holograms are not in Economy. They live in Nexus.

## v1 scope
1. **Economy core** — digital balance (default), /bal, /pay, /baltop, /eco give|take|set|reset, payment history, transaction log, starting balance, pay toggle.
2. **Currency**
   - Auto-detect (done): `currency/KnownCurrencies` lists 47 mods; `currency/CurrencyDetector` finds them at server start and logs them.
   - Adapters for **all 47** detected mods, using three methods:
     - **API** — call the mod's own code (CobbleDollars, Lightman's, SDM Economy (both APIs), Numismatics, SG-Economy, Impactor, EconomyCraft, Eights/OctoEconomy, BankSystem, DiceMC, Saro's Money, MiguelEconomy, Bubustein, Arcadia Lib, Avecoins, NEC2, Simple Economy, OmniEconomy).
     - **Player data** — read the balance field stored on the player (M Economy, Kuro's, Mezzo's, Better Economy) and Saro's Essentials' world file.
     - **Items** — count coins in the inventory and inside holders (wallets, bags, coin boxes) using `config/elements_economy/currency_values.toml`.
   - Two coin modes, each switchable:
     - **Exchange** (`currency.exchange`, `deposit`, `withdraw`) — players turn coins into digital money and back.
     - **Pay with coins** (`currency.pay_with_coins`, default off) — shops take and give coins straight from the inventory.
   - Item values are owner-set. 0 = not counted. Example: Aiycoin's own chain is 50:1; a server that trades coins at 100:1 sets that ladder here.
   - Offline changes only for mods that support them (Saro's, Miguel, Bubustein, NEC2, Avecoins, EconomyCraft). Others change only while the player is online.
3. **Shop bridge** (core) — connects the detected currency to installed shop and auction mods.
   - Item-priced shops (Spud's Shops, Create table cloth, Easy NPC, vanilla villagers, Shoppy bartering): bridged **only when the price item is a recognised mod currency**. On switch-over the price item is swapped for the new mod coin. Never converted to digital. Vanilla-item prices are ignored.
   - Own-money shops (SDM Shop, Shoppy economy, Auction House Plus, Arcadia AH, Lightman's traders, CobbleDollars shops, EconomyCraft, MiguelEconomy, AdminShop, FreeMarket): plug in as their money provider where a hook exists (Impactor, OctoEconomy, SDM currency registration); otherwise keep balances in step through adapters and convert prices.
4. **Currency switch-over** (core, kept simple) — one admin command: `/economy currency switch <new currency>`. It shows a preview (rate example, balances now / at next login, shop and server shop prices, shop tills, auction listings, collection boxes, other mods' shops), then `/economy currency switch confirm` (within 2 minutes) converts everything and makes the new currency primary. Rates come from `rates` in economy.toml. Backup taken first; a failed backup stops the switch. Converted prices never drop to zero. Coin-priced shops only follow a switch to another coin currency (never digital); vanilla-item prices are left alone. Balances that can't change right now (coins, online-only money) convert at that player's next login at the rate saved with the switch; players who miss several switches catch up in order. Other mods can stop a switch (CurrencyChangeEvent.Pre). Done (currency/SwitchOver, bridge/ShopBridges).
5. **Bridges** (each switchable, `bridges.*`)
   - **Impactor provider** (default on): suggested as Impactor's economy service (priority 10; Impactor's own is 0) during dedicated server setup, so Impactor-based mods (AH Plus, Cobblemon mods) use Elements: Economy money. Impactor currencies are views of ours (same ids). Done (bridge/impactor).
   - **OctoEconomy provider** (default on): set as the economy in Eights Economy P's provider event (server starting), so Eights' commands and Shoppy use the primary currency. Fake (named) accounts kept in `bridge_accounts`. Done (bridge/octo).
   - **SDM currency** (default on): SDM Economy 2.x lists every Impactor currency as an SDM currency, so this works through the Impactor provider and needs Impactor installed. Turning it off hides our currencies from SDM only. SDM 2.x custom currencies without Impactor, and SDM 1.6, keep their own balances, so no live bridge is possible there.
   - While Elements: Economy is a mod's provider, currency detection skips that mod. Its old balances are not moved by the bridge (see Migration).
   - Other mods' APIs are compiled against stubs in src/main/java (net/impactdev, net/kyori, com/epherical), excluded from the jar.
   - **Lightman's Currency money type** (default off): needs a client add-on. Lightman's clients error on unknown money types ("No CurrencyType … could be found"). Lightman's money is still read and written through its API adapter.
6. **Container shops** — chests, barrels, shulker boxes and similar storage (vanilla classes, `c:chests`/`c:barrels`, allow tag) can be shops; cabinets, furniture and machines cannot. One container sells many items through a paged table, one price per item type. Create, remove, buy, sell/buy-back, admin shops, info, sale alerts, protection (guarded capability: no hoppers/pipes/Create extraction; pistons and Create drills, saws and contraptions can't break or move shops or linked vaults), explosion block, claim check (OPAC / FTB Chunks), rank limits, create fee. Create item vaults (tag `elements_economy:stock_vaults`) act as **stock vaults**: overflow storage that auto-restocks linked shop chests/barrels and covers large orders; never sold from directly. Safes (key-lockable) and sacks are never shop containers. Details: docs/UI.md.
7. **Auction house** — chest-menu GUI, sell, buy now, bids, cancel, collect, search, categories, my listings, sale alerts, sales tax, rank limits, admin remove, blacklist.
8. **NPC / admin shops** — open, buy, sell, create, edit, delete, shopkeeper NPC, /shop, stock limits, rank prices.
9. **API** for other mods — read and change balances (switchable).

## Overlap guard
On startup, Economy finds installed shop and auction mods and turns off its own duplicate built-in feature, with a log line. features.toml is not edited: the feature is off only while the other mod is installed. `overlap_guard.force_on` keeps a built-in feature on anyway (names: `player_shops`, `server_shops`, `auction_house`, `economy_commands`). Done (overlap/OverlapGuard; Features.on asks it).
- Player shops: Spud's Shops (`spudaciousshops`)
- Server shops: Easy NPC (`easy_npc`), SDM Shop (`sdmshop`), AdminShop (`adminshop`)
- Auction house: Arcadia AH (`arcadia_ah`), EconomyCraft, MiguelEconomy
- Money commands (/bal, /pay, /baltop, /payments, /eco): EconomyCraft, MiguelEconomy
- Shoppy has no 1.21.1 build (latest is 1.20.4), so it is not listed.

Found shops are bridged (`overlap_guard.bridge_found_shops`, plus `shop_bridge.item_priced`):
- Spud's Shops: price = slot 76 of the shop's `shopInventory` (one stack). Easy NPC: each trade's cost A and B (`getTradingOffers` / `setTradingOffers`).
- Coin prices follow coin-to-coin switches; item prices never become digital, other items are left alone. A converted price stays within its slots (Spud's: one stack; Easy NPC: two), exact when it fits, otherwise the closest price.
- Loaded shops convert during the switch; the rest when their chunk loads (`shop_bridge.convert_on_load`) or right before they are used. Each shop saves how many switches it has had (`elements_economy_switch` in its persistent data).
- Other found mods are covered elsewhere: SDM Shop through the Impactor provider; EconomyCraft and MiguelEconomy balances through their adapters.

## Migration
- `/economy migrate` lists sources whose data is on this server; `/economy migrate <source>` previews (target currency, players, named accounts, total, rates used); `/economy migrate <source> confirm` imports within 2 minutes. Originals are never changed; a backup is taken first; amounts are added to current balances. A source can only be imported once unless `confirm again` is used (kept in meta `migrations`). Done (migrate/).
- Target: the primary currency when offline players can receive it, otherwise digital.
- Rates: a `rates` line for the source key (e.g. `impactor:dollars=1`, `eights_economy_p:balance=1`, `wanoeconomy:balance=1`) says what one stored unit is worth in digital; with no line, 1:1 (the preview says so). Currency-mod sources use their normal currency rate.
- File sources (formats checked against each mod's source):
  - `impactor` — config/impactor/economy/accounts/users/<xx>/<uuid>.conf (JSON, YAML or HOCON; SQL storage not readable). Virtual accounts skipped.
  - `eights` — <world>/eights_economy_p/<uuid>.json; named accounts in sub-folders become OctoEconomy bridge accounts.
  - `wano` — config/wanoeconomy/data/balances.json.
- Currency-mod sources: every detected currency that can be read offline (EconomyCraft, Saro's Money, Saro's Essentials, Numismatics, CobbleDollars, Never Enough Currency, BankSystem, OmniEconomy, Simple Economy, SDM Economy 2.x). Source id = the mod id. Coins carried in inventories are not migrated.
- Not covered: MiguelEconomy and Bubustein (balances need the player online / no offline reader), FreeMarket (listings, not balances).

## Independence & interoperability (REQUIRED)
- Runs fully alone or alongside Elements: Nexus. Neither lists the other as required (`type = "optional"` only).
- Soft integration: detected with `ModList.get().isLoaded("elements_nexus")`; integration classes only touched when Nexus is present.
- Permissions through NeoForge PermissionAPI — works with Nexus, any other handler, or vanilla OP levels.
- Nexus touch-points (switchable, `nexus.*`): Nexus ranks for shop and listing limits and rank prices. Nexus leads its own side panel/chat/tab list (no balance feed from Economy), and trades are private (nothing is posted to Discord).
- Each mod keeps its own config, data and commands.

## Commands (lean: one root per feature)
- /bal [player] · /pay <player> <amount> · /pay toggle · /baltop · /payments
  - LOCKED: `/bal` shows the balance from the active currency (the detected currency mod, read through its adapter). Digital money's name, symbol and starting balance only apply when no currency mod is in use.
- /economy shop create buy|sell <name> (server shops, admin)
- /eco give|take|set|reset <player> <amount>
- /exchange deposit [amount] · /exchange withdraw <amount>
- /shop create buy · /shop create sell · /shop rename <name> · /shop remove
- /shop (server shops) · /ah (auction house)
- /economy reload (messages.toml, currency_values.toml, currency list; .toml settings reload on save, "(restart)" ones need a restart) | version (version, primary currency, storage) | migrate [source] [confirm [again]] | storage convert <json|yaml|sqlite|mysql> (backup first, copy only; set the type in storage.toml and restart; the final data is copied again when the server stops). Done (commands/AdminCommands, migrate/).
- Node pattern: `economy.<feature>.<action>`.

## Files so far
- config/Features.java — master switches
- currency/CurrencyKind.java, KnownCurrency.java, KnownCurrencies.java, CurrencyDetector.java — detection
