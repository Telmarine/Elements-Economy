# Elements: Economy — shop & auction house UI design (draft for review)

## Research summary (4 Oct 2026)
- **Server-side menus work with no client mod.** The server opens a vanilla chest menu (`MenuType.GENERIC_9x1` … `GENERIC_9x6`, up to 54 slots) and fills it with item "buttons". The vanilla client draws it. Verified in EconomyCraft (`AuctionUi`, plain `SimpleContainer` + chest menu) and Auction House Plus (Patbox's sgui, `GENERIC_9x6`), both server-side only.
- **Multiple pages work.** Both mods page with a bottom navigation row: previous / page indicator / next, `page * itemsPerPage` offsets. EconomyCraft also has search, sort and "mine only" toggles in the same row.
- **Text input works server-side.** EconomyCraft opens a vanilla anvil menu (`AnvilMenu`, reads `setItemName`) for search and prices. No client mod needed.
- **Library option checked:** sgui (Patbox, LGPL-3.0) has a NeoForge 1.21.1 build (`eu.pb4:sgui:1.9.1+1.21.1-neoforge`, maven.nucleoid.xyz). Not needed: EconomyCraft proves plain vanilla menus are enough, and an in-house framework keeps Economy dependency-free like Nexus.

## Do we need a resource pack?
**No, not for v1.** Pages, buttons, search, sorting, confirm screens and prices all work with vanilla items, names and lore.

A resource pack is only needed for themed visuals:
- Custom menu backgrounds (a custom font glyph drawn in the menu title).
- Custom button icons (`custom_model_data` on 1.21.1; the newer `item_model` component isn't available on 1.21.1).
- If added later, it ships as an optional server resource pack (vanilla `resource-pack` setting in server.properties), behind a `ui.theme_pack` switch, default off. Menus must look complete without it.

## Shared framework (in-house, `ui` package)
- `Menu` — server-only chest menu (6 rows by default). Every click is cancelled and handled by code: no item can be taken, shift-clicked, dragged, number-key swapped or dropped. Re-syncs on close.
- `PagedMenu` — content area + navigation row. Page size = rows × 9 minus the nav row (45 on 6 rows).
- `Button` — vanilla item + name + lore + click action (left, right, shift-left).
- `InputPrompt` — anvil menu for typed text (search words, prices, amounts). Validates input; invalid input shows the reason and keeps the prompt open.
- `ConfirmMenu` — 3-row Yes / No screen for purchases, listings and cancels.
- Sounds on page turn, buy, error (switchable).
- **Prices come from the detected currency.** Every price, offer, bid, total and balance on every screen is built by `currency/CurrencyDisplay` from the currency in use:
  - Item currencies (e.g. Aiycoin): coins and values from `currency_values.toml` via `CurrencyDetector`. An amount is broken into the fewest coins, largest first; the price columns show the two largest coin types as the real coin items (count on the stack; over 64 shows as one coin named "N x coin"), and any extra coin types are listed in the last price slot's lore. Item names are translated on each player's client.
  - Digital money: one icon (paper by default, set in `ui.toml` with the currency name) named with the formatted amount, e.g. "1,250 Coins".
  - Vanilla items only appear once an admin has confirmed them as currency.
  - Editing `currency_values.toml`, confirming a vanilla item, or a currency switch-over changes every screen on the next open; nothing is hard-coded per mod.
  - With `currency.multiple_currencies` on, each listing shows its own currency, and the balance button lists every active currency.

## Navigation row (bottom row of every paged menu)
| Slot | Button | Item (vanilla) |
|---|---|---|
| 1 | Back / close | barrier |
| 2 | Category | chest / item of the category |
| 3 | Previous page (hidden on page 1) | arrow |
| 4 | Page x / y | paper |
| 5 | Next page (hidden on last page) | arrow |
| 6 | Sort | hopper |
| 7 | Search / clear search | name tag / red glass pane |
| 8 | Your balance | currency icon |
| 9 | Context (shop: sell mode · AH: my listings) | varies |

## Screens
### Server (admin) shops — LOCKED (approved 4 Oct 2026)
- **Players can never create, own or edit a server shop or NPC shop.** Server shops are made and run by the server owner/staff only.
- Player shops: `/shop create buy|sell` (players only), and only on a container type the server owner allows (chests, barrels and anything added to the allow tag). Never on an NPC, villager or other block.
- Server shops: a separate staff command, `/economy shop create buy|sell <name>` (permission: admin). Same buy/sell screens and row layout as player shops; no owner funds or container needed (unlimited by default).
- How players open a server shop is the server owner's choice (`[server_shops]` in features.toml, one switch each):
  - `npc_hook` — link the shop to an NPC from an NPC mod (e.g. Easy NPC); clicking the NPC opens the shop. Optional, only when that mod is installed.
  - `villager` — a vanilla villager (no AI, no trades) that opens the shop.
  - `block` — any block the staff member clicks (sign, barrel, etc.) opens the shop.
  - `command` — `/shop open <name>`.
- Stock limits (limited stock that refills on a timer) and rank prices (different prices per rank; needs a rank mod such as Nexus) are **server-owner-only** settings on server shops, off by default. Never available on player shops.

### Shop types and table layout — LOCKED (approved 4 Oct 2026)
Two separate shop types, each with its own screen. Created with:
- `/shop create buy` — players **buy** from the shop (shop sells its stock).
- `/shop create sell` — players **sell** to the shop (shop buys items, paying from the owner's funds).
Run while looking at a valid container. `/shop rename <name>` sets the shop name.

**Shop name** shows top left, in the menu title (e.g. `Steve's Building Supplies`). Defaults to `<owner>'s shop`.

**Row layout** (one listing per row, 5 rows per page, nav row at the bottom):
| Column | Buy shop | Sell shop |
|---|---|---|
| 1 | Stock (paper, count; "64+" over a stack) | Wanted — how many the shop still accepts (owner-set limit). Never shows the owner's money or coin stock. |
| 2 | Item | Item |
| 3 | Filler pane | Filler pane |
| 4–5 | Price per item (up to two coin types) | Offer per item (up to two coin types) |
| 6 | Filler pane | Filler pane |
| 7 | Amount: 1 / 8 / 16 / 64, capped by stock | Amount: 1 / 8 / 16 / 64, capped by what the player carries and what's wanted |
| 8 | Green **Buy** button | Gold **Sell** button |
| 9 | Filler pane | Filler pane |

- A sell row whose owner can't pay shows "Not buying right now". The amount owed is never shown.
- Auction house uses the buy layout; column 1 = amount listed, Buy becomes Bid on auction listings.

**Hover tooltips (every column; approved addition to the locked layout).** Same for player container shops and NPC/admin shops.
| Column | Buy shop | Sell shop |
|---|---|---|
| 1 | Available to buy (shop + linked stock vault combined) · "Restocks from vault" when a vault is linked · NPC/admin: "Unlimited" unless stock limits are on | Shop accepts N more · "Not buying right now" when closed or the owner can't pay · NPC/admin: "Unlimited" unless a limit is set. Never shows the owner's money. |
| 2 | Item's full tooltip (name, enchantments, durability, lore, mod name) + shop name and owner | Item's full tooltip + shop name and owner + "You carry: N" |
| 3 | No tooltip (filler) | No tooltip (filler) |
| 4–5 | Price per item · price for a full stack · "Your rank price" when rank prices are on | Offer per item · offer for a full stack · "Your rank price" when on |
| 6 | No tooltip (filler) | No tooltip (filler) |
| 7 | Chosen amount · choices (capped by stock) · cost · "Click to change" | Chosen amount · choices (capped by what you carry and what's wanted) · you get · "Click to change" |
| 8 | Buy N x item · total · balance now and after (or "Not enough money") · "Click to confirm" | Sell N x item · you get · balance now and after · "Click to confirm" |
| 9 | No tooltip (filler) | No tooltip (filler) |

### Owner screen (player shops) — auction house template — LOCKED (approved 4 Oct 2026)
- Same row layout as the auction house browse screen; the owner opens it by clicking their own shop (or `/shop manage`).
- **Adding items:** drop items into the stock vault linked to the shop (or the shop container). Every new item type is identified automatically from the item itself and shows up as a row marked "No price set" (hidden from buyers).
- Click a row → **pricing screen** (auction-house sell-screen template): price, quantity per sale (sold in bundles of N), max per player per day (optional), for sale on/off. Sell shops: offer and wanted limit instead.
- Row tooltips show in shop / in vault, sold today, earnings for that item.
- Nav row extras: earnings (collect), vault status, rename shop.

### Container shops (player) — chests, barrels and modded storage
One container can sell **many items**. Every item type inside becomes a row in the shop's table, browsed with pages.
- Buyer view: paged table (45 per page). Each row = one item type: price, stock (total of that item across all slots), owner. Sort and search work the same as everywhere else.
- Click a row → amount picker (1 / 8 / 16 / 64 / all) → confirm.
- Owner view: the same table with a price per row (anvil), "not for sale" toggle per row, buy-back (shop buys from players) per row, earnings, remove shop.
- New items the owner puts in show up as "no price set" (hidden from buyers) until priced.
- Stock is read live from the container, so large storage (Create vaults, thousands of items) just means more pages.

### Auction house — `/ah` — LOCKED (approved 4 Oct 2026)
**Browse** (5 listings per page, same row style as shops; title `Auction House · <category>`):
| Column | Content |
|---|---|
| 1 | Seller (player head) |
| 2 | Item |
| 3 | Filler pane |
| 4–5 | Buy-now price or current bid (auction): digital in col 4; item currencies (coins, admin-confirmed vanilla items) show up to two coin/item types, largest first (extra types in the last slot's lore) |
| 6 | Filler pane |
| 7 | Amount up for auction (static — what the seller listed) |
| 8 | Green **Buy** (buy now) or blue **Bid** (auction) |
| 9 | Time left (clock) |
Nav row: close · category · prev · page · next · sort (newest, ending soon, price ↑/↓) · search · my listings · collection box.

**Hover tooltips (every column, full details):**
| Column | Tooltip |
|---|---|
| 1 Seller | Seller name · their active listings in the AH · "You" tag if it's your own listing |
| 2 Item | The item's own full tooltip (name, enchantments, durability, lore, mod name) + listing type (buy now / auction) + listing number |
| 3, 6 Blank | No tooltip (empty filler) |
| 4–5 Price | Buy now: price. Auction: current bid, lowest next bid, number of bids, "You're the highest bidder" when true, buy-now price if the listing has both |
| 7 Amount | "Amount: N" only |
| 8 Buy / Bid | Action and amount · total cost · your balance now and after · "Click to confirm" (buy) or "Opens the bid screen" (bid) |
| 9 Time | Time left · ends at (server date/time) · listed at |

**Bid screen** (3 rows): item, current bid, lowest next bid, your bid; +1 step / +5 steps / type amount (anvil) / reset; cancel · confirm. Bid money is held until outbid (refunded) or won.

**Sell screen** (`/ah sell`, 3 rows): item from hand, price (anvil), type buy now ↔ auction, duration (1h / 6h / 12h / 24h), fee slot, cancel (item returned) · confirm.

**Listing fee and sales tax are optional — server owner's choice.**
- `auction_house.listing_fee` (default **off**) and `auction_house.sales_tax` (default **off**) in features.toml.
- Amounts in `auction_house.toml`: fee as a flat amount or a percent of the price, tax as a percent of the sale. Bid step and durations live there too.
- With the fee off, the fee slot on the sell screen is a plain filler and no fee line appears anywhere.

**My listings:** active / sold / expired rows with cancel. **Collection box:** money from sales and won/expired/cancelled items waiting to be picked up.

### Which containers can be shops
Rule: **chests, barrels and storage of the same nature only.** Cabinets, furniture and machines are not shop containers.

Recognised by default:
1. Any block that **is a chest** (`ChestBlock`, not ender chest) — vanilla and every mod chest built on it. Double chests count as one shop (vanilla `ChestBlock.getContainer` joins both halves).
2. Any block that **is a barrel** (`BarrelBlock`).
3. Any block that **is a shulker box** (`ShulkerBoxBlock`).
4. Anything in the common tags `c:chests` or `c:barrels` (minus `c:chests/ender`) — catches mod chests/barrels not built on the vanilla classes.
5. Anything in the allow tag `elements_economy:shop_containers` — owner adds "similar" storage here.

Never recognised (tag `elements_economy:not_shop_containers`): ender chests, Lootr (`lootr`, per-player loot), Tom's Storage (`toms_storage`, network terminals), furnaces, hoppers, droppers, dispensers, crafters, and everything not matched above (cabinets, furniture, machines).

Switch `containers.any_inventory` (default **off**): when on, any block that holds items counts (minus the never list). For server owners who want it.

How stock is read: through the block's item handler (`Capabilities.ItemHandler.BLOCK`) when the mod provides one. Some mods don't (see BCLib below), so Economy provides one itself for any recognised chest/barrel that has none: at `RegisterCapabilitiesEvent` (lowest priority) it registers a wrapper for every `ChestBlock`/`BarrelBlock`/`ShulkerBoxBlock` with no provider (`isBlockRegistered` false). That also routes vanilla hoppers through the capability, so the shop guard applies to them.

### Tested mod containers (checked 4 Oct 2026)
| Mod (jar) | Chest/barrel-type blocks | Has item handler? | c: tags | Shop container? |
|---|---|---|---|---|
| Vanilla | chest, trapped chest, barrel, shulker boxes | Yes (NeoForge) | — | Yes |
| Better End 21.0.35 (BCLib 21.0.26) | 10 wood chests, wood barrels | **No** — BCLib's own block-entity types, no capability registered (BCLib, WorldWeaver, WunderLib checked) | `c:chests/wooden`, `c:barrels/wooden` | Yes — Economy adds the missing handler (blocks extend `ChestBlock`/`BarrelBlock`) |
| Better Nether 21.0.27 (BCLib) | wood chests, wood barrels | **No** (same as Better End) | `c:chests/wooden`, `c:barrels/wooden` | Yes — same fix |
| Chipped 4.0.2 | 18 barrel variants (vanilla `BarrelBlock`, added to vanilla barrel block-entity type) | Yes (via vanilla barrel type) | `chipped:barrel` | Yes |
| Supplementaries 3.9.9 | Safe, Sack (also presents, lunch basket, cannon) | Yes (registered for safe, sack, present, trapped present, cannon, lunch basket) | — | **No.** Safes lock with a craftable key; sacks left out too. Presents, lunch basket, cannon: no |
| Create 6.0.10 | Item vault (bulk storage), toolboxes, depot | Yes (vault `registerCapabilities`) | — | Not a shop container. Item vault = **stock vault** (overflow + distribution hub, below). Toolbox, depot: no |
| Lootr | loot chests/barrels | — | — | No (per-player loot) |
| Tom's Storage | terminals/connectors | — | — | No (network) |
| Handcrafted, Macaw's Furniture, Farmer's Delight cabinets | cabinets, drawers, counters | — | — | No (furniture) |
| Sophisticated Backpacks (placed), Gravestone | backpacks, graves | — | — | No |

Default allow tag (`elements_economy:shop_containers`): empty. Lockable containers (safes) and sacks are never added by default.
Default stock-vault tag (`elements_economy:stock_vaults`): `create:item_vault`.

### Stock vaults — overflow and distribution hub
A stock vault holds a shop owner's overflow stock and feeds their shop chests and barrels. Buyers never open or buy from the vault directly.
- **Link:** owner looks at the vault and runs `/shop vault link`, then links shop containers to it (`/shop vault add` while looking at each shop, or a link mode that adds every shop container the owner clicks). One vault can feed many shops. Same owner, same dimension, within a set range (`ui.toml`: `vault_range`, default 64 blocks).
- **Restock:** when a shop row runs low (below the owner's threshold, default a quarter stack), Economy moves that item from the vault into the shop container until full or the vault runs out. Runs on a timer (default every 10 seconds) and right after a sale.
- **Overflow:** if a buyer wants more than the shop container holds, the rest is taken straight from the linked vault in the same purchase. Buyer-facing stock = shop container + linked vault.
- **Filling the vault:** anything can put items in (players, Create belts, funnels, farms, hoppers). Taking items out is blocked for everything except the owner, staff, and Economy's own restock.
- **Loaded chunks only:** both the vault and the shop must be loaded for a restock. Overflow purchases only use loaded vaults.
- **Create multiblock:** a vault made of many blocks works as one; Create exposes one inventory for the whole vault, so linking any of its blocks links the vault.
- **Owner view:** in the shop's owner table, each row shows "in shop / in vault" and the restock threshold. A vault screen lists every linked shop and what it's low on.
- **Switches:** `stock_vaults.enabled`, `link`, `auto_restock`, `overflow_sales`, `restock_after_sale`.
- Other bulk-storage mods can be added to `elements_economy:stock_vaults`.

### Protecting shop containers
- First capability provider that answers wins (NeoForge `BlockCapability.getCapability`). Economy registers its provider first (`RegisterCapabilitiesEvent`, highest priority) for every block entity block. For shop positions it returns a guarded handler (no extraction by hoppers, pipes, funnels, Create, other mods); for everything else it returns nothing and the normal provider answers.
- Vanilla hoppers go through this too: NeoForge's hopper hook asks the block capability first.
- Shop creation/removal calls `level.invalidateCapabilities(pos)` so cached handlers (Create, pipes) pick up the change.
- Breaking or opening the container is blocked for everyone but the owner and staff.

### Other mods' shops
- Left as they are. Elements: Economy bridges their currency (see SPEC); it does not replace their screens.

## Config
- `features.toml` → `[ui]`: `pages`, `search`, `sort`, `categories`, `confirm_purchases`, `sounds`, `balance_button`, `theme_pack` (off).
- `ui.toml` (hand-written, `/economy reload`): menu rows, button items and names, colours, title texts, sort order, category list. Every line commented in plain words.
