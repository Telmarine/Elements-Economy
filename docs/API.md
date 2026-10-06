# Elements: Vault — economy API design (draft for review)

Elements: Vault is the Vault-style API behind Elements: Economy. It is kept in-house (not published), but lives as its own stand-alone project and jar.

Status: proposal, nothing coded yet. Based on research Part 4 (Vault-style APIs) below and Parts 1–3 in currency-research.md.

## Part 4 — What existing Vault-style APIs do (researched 4 Oct 2026)
| API | Platform | Money type | Key ideas worth copying | Gaps |
|---|---|---|---|---|
| **Vault** (MilkBowl/VaultAPI) | Bukkit | `double` | One `Economy` service; `has / withdrawPlayer / depositPlayer / getBalance`; `EconomyResponse` (amount, balance, type SUCCESS/FAILURE/NOT_IMPLEMENTED, error); banks; `format`, currency names. | Single currency, doubles, sync only. |
| **VaultUnlocked** (Vault2) | Bukkit | `BigDecimal` | Multi-currency (`currencies`, `getDefaultCurrency`, `hasCurrency`), shared accounts with `AccountPermission` (DEPOSIT, WITHDRAW, BALANCE, …), `canDeposit / canWithdraw` checks, `transfer`, async variant, `MultiEconomyResponse`. | Bukkit only. |
| **Treasury** (lokka30) | Bukkit/Sponge | `BigDecimal` | Async (`CompletableFuture`), primary currency + `findCurrency`, player and non-player accounts, transactions carry **cause, reason, timestamp, importance**, transaction events. | Heavy. |
| **Common Economy API** (Patbox) | Fabric | `BigInteger` | Provider registry (`CommonEconomy.register(id, provider)`), currencies with `formatValue / parseValue / icon`, accounts with `canIncreaseBalance / canDecreaseBalance` (dry run), `EconomyTransaction` (success, message, previous/final balance). | Fabric only. |
| **Impactor** | Multi (Cobblemon scene) | `BigDecimal` | Service picked by priority (`SuggestEconomyServiceEvent`), virtual accounts, async. | One provider at a time. |
| **Grand Economy** | Fabric/Forge (≤1.19) | `double` | `EconomyAdapter` registry: other economies plug in by mod ID. | Old, doubles. |
| **Tender** | NeoForge/Fabric 1.21.1 | `long` | Providers declare **capabilities** (e.g. `OFFLINE_DEPOSIT`, `OFFLINE_WITHDRAW`). | 129 downloads, no source. |
| **OctoEconomy** (Eights) | Multi | `double` | Provider set through `EconomyProviderEvent.Pre.setEconomy`. | Eights only. |

Conclusions:
- No API covers both sides we need: **money systems** (47 currency mods) **and shops** (item-priced + money-priced). Ours must do both.
- Copy: multi-currency (VaultUnlocked), BigInteger amounts (Patbox; Aiycoin coins reach 10^12+), dry-run checks (Patbox/Vault2), causes and reasons on transactions (Treasury), capability flags (Tender), provider registry (Patbox/Grand Economy).
- Be a good citizen: also present Elements: Economy through the APIs others already use (Impactor, OctoEconomy, SDM Economy), so their mods work without knowing ours.

## RULE — only mod currencies
- A `Currency` comes from a mod (Elements: Economy digital, or a detected currency mod). Vanilla item IDs (`minecraft:*`) are not currency, not counted in balances, and not shown by the side panel or placeholders.
- Exception: vanilla items the owner adds to currency_values.toml **and an admin confirms** (in-game "Are you sure?" prompt). Unconfirmed vanilla entries are ignored.
- `fromItems` / item counting ignore unconfirmed vanilla items. `toItems` pays out only recognised currency items.
- Item-priced shop bridges act only when the price item is a recognised currency item. They never turn an item price into a digital one.

## Design goals
1. One door for every mod: shops, auction houses and economy mods talk to Elements: Economy only.
2. Two plug-in sides: **currency backends** (where money lives) and **shop bridges** (where prices live).
3. Soft dependency: other mods build against Elements: Vault (`compileOnly`); nothing breaks if it is missing.
4. Server-side only, no client classes.
5. Every part switchable in features.toml (`api.*`, `bridges.*`).

## Layout
- **Elements: Vault** — stand-alone project `TAC5 Studios/elements-vault`, mod ID `elements_vault`, package `com.tac5studios.elementsvault`. Its own jar, its own version. In-house only.
- Elements: Economy bundles it (jarJar) so Economy works on its own, and other TAC5 mods (e.g. Nexus) can depend on the stand-alone Vault jar without needing Economy.
```
elements_vault.jar         (package com.tac5studios.elementsvault)
  EconomyAPI          entry point: EconomyAPI.get() → Optional<EconomyService>
  EconomyService      accounts, transactions, currencies, conversion
  Currency, Money     what money is and how much
  Account, Transaction, Result, Cause
  backend/CurrencyBackend      SPI: a money system (digital, Aiycoin, CobbleDollars, ...)
  shop/ShopBridge              SPI: a shop or auction mod (Spud's, Create, Easy NPC, AH Plus, ...)
  event/*                      NeoForge events
elements_economy.jar       (the mod: built-in backends, bridges, shops, commands)
```

## Core types
- **Currency** — `id` (ResourceLocation, e.g. `elements_economy:digital`, `aiycoin:coins`), `name`, `symbol`, `decimals`, `kind` (DIGITAL / ITEMS / EXTERNAL), `sourceMod`, `capabilities`, `format(Money)`, `parse(String)`, `icon()`.
- **Money** — `record Money(Currency currency, BigInteger amount)` in the currency's smallest unit. Helpers: `add`, `subtract`, `isZero`, `compareTo`.
- **Account** — `owner` (UUID), `id` (default player account or a named/shared account), `currency`, `balance()`.
- **Capabilities** — `OFFLINE_READ`, `OFFLINE_WRITE`, `SHARED_ACCOUNTS`, `ASYNC_ONLY`, `ITEM_BACKED` (balance = coins in inventory). From research: Saro's/Miguel/Bubustein/NEC2/Avecoins/EconomyCraft support offline; CobbleDollars and attachment mods are online-only; item currencies are item-backed.
- **Cause** — who/what started it: `player(uuid)`, `shop(bridgeId, pos)`, `auction(listingId)`, `command(source)`, `plugin(modId)`, plus a free-text reason.
- **Result** — `success`, `reason` (OK / INSUFFICIENT_FUNDS / ACCOUNT_MISSING / OFFLINE_NOT_SUPPORTED / LIMIT / CANCELLED / BACKEND_ERROR / NOT_SUPPORTED), `before`, `after`, `amount`, `message` (Component).

## EconomyService (what other mods call)
- `primaryCurrency()` — the default currency shops use (detected or digital).
- `activeCurrencies()` — every currency shops may accept. One or several, set by the server owner (`currency.multiple_currencies`).
- `currencies()`, `currency(id)`
- `balance(UUID, Currency)` · `has(UUID, Money)`
- `canWithdraw(UUID, Money)` / `canDeposit(UUID, Money)` — dry run, no change.
- `withdraw(UUID, Money, Cause)` / `deposit(UUID, Money, Cause)` / `set(UUID, Money, Cause)` → `Result`
- `transfer(UUID from, UUID to, Money, Cause)` → `Result` (all-or-nothing)
- `convert(Money, Currency target)` → `Money` (rates from currency_values.toml / exchange)
- `toItems(Money)` → list of coin stacks (fewest coins) for item currencies; `fromItems(stacks)` → `Money`
- Async twins (`…Async` returning `CompletableFuture<Result>`) for offline or async-only backends. Sync calls on the server thread for online players.

## Currency backend SPI (money systems plug in here)
```java
public interface CurrencyBackend {
    ResourceLocation id();
    Currency currency();
    Set<Capability> capabilities();
    boolean isAvailable();                       // mod present + marker class found
    BigInteger balance(UUID owner);
    Result withdraw(UUID owner, BigInteger amount, Cause cause);
    Result deposit(UUID owner, BigInteger amount, Cause cause);
    default Result set(UUID owner, BigInteger amount, Cause cause) { ... }
}
```
- Built in: digital + all 47 detected mods (API, player-data, file, items — research Parts 1–2).
- Other mod authors can add their own by registering in `RegisterCurrencyBackendsEvent` (game bus, server start).
- Item backends count the inventory and holders (wallets, bags, coin boxes) when `count_holders` is on, and pay out change with `toItems`.

## Shop bridge SPI (shops and auction houses plug in here)
```java
public interface ShopBridge {
    ResourceLocation id();                       // e.g. elements_economy:spuds_shops
    String modId();
    ShopKind kind();                             // ITEM_PRICED or MONEY_PRICED
    boolean isAvailable();
    void onCurrencyChanged(CurrencyChange change, ConversionPlan plan);   // switch-over
    default void onBlockEntityLoad(BlockEntity be) {}                     // lazy convert
    default boolean interceptPayment(PaymentContext ctx) { return false; } // money-priced shops only
}
```
- Item-priced bridges (Spud's, Create, Easy NPC, villagers, Shoppy bartering) act only on mod-coin prices: on switch-over they swap the coin for the new mod coin. Vanilla-item prices are left alone, and item prices are never turned into digital (research Part 3).
- Money-priced bridges (SDM Shop, AH Plus, Arcadia, Lightman's, CobbleDollars, EconomyCraft, Miguel, AdminShop, FreeMarket) route payment through EconomyService or convert stored prices.
- Other mod authors can register their shop with `RegisterShopBridgesEvent`, or skip bridges entirely and just call `EconomyService.withdraw/deposit`.

## Provider facades (we speak other APIs too)
| Facade | How | Covers |
|---|---|---|
| Impactor | `SuggestEconomyServiceEvent` with our service | Auction House Plus, Cobblemon/Impactor mods |
| OctoEconomy | `EconomyProviderEvent.Pre.setEconomy` | Shoppy economy shops, Eights users |
| SDM Economy 2.x | Lists Impactor currencies automatically (needs Impactor + the Impactor provider) | SDM Shop |
| Lightman's | money type (off; needs client add-on) | LC traders/ATMs |

## Events (NeoForge game bus)
- `TransactionEvent.Pre` (cancelable, can change amount) / `TransactionEvent.Post`
- `BalanceChangedEvent` (any backend, including outside changes we notice)
- `CurrencyChangeEvent.Pre/Post` — the switch-over (old → new currency, conversion plan)
- `ShopPurchaseEvent`, `AuctionSoldEvent`, `AuctionListedEvent` — feed Nexus Discord, logs, quests
- `RegisterCurrencyBackendsEvent`, `RegisterShopBridgesEvent` — registration, fired on the game bus at server start

## Switches (to add to features.toml when approved)
- `currency.multiple_currencies` (default off) — server owner decides: one primary currency, or several accepted side by side
- `api.enabled`, `api.read`, `api.write`, `api.async`, `api.events`, `api.external_backends`, `api.external_bridges`
- `bridges.impactor_provider`, `bridges.octo_provider`, `bridges.sdm_currency`, `bridges.lightmans_currency_type`

## Decisions
- One vs several active currencies: server owner's choice through `currency.multiple_currencies`.
- API: in-house, named **Elements: Vault**, kept as its own stand-alone project and jar.
