package com.tac5studios.elementseconomy.storage;

/**
 * Names of every data collection. Each collection is a map of key -> JSON value.
 * Layout of each value: docs/STORAGE.md.
 */
public final class Collections {

    /** schema_version, last_start, active_currency, pending switch. Key: setting name. */
    public static final String META = "meta";

    /** Digital money only. Key: player UUID. Value: { currency id: amount as text }. */
    public static final String BALANCES = "balances";

    /** Per-player settings. Key: player UUID. Value: name, pay_toggle, last_seen. */
    public static final String ACCOUNTS = "accounts";

    /** Recent payments per player for /payments. Key: player UUID. Value: list, newest first. */
    public static final String HISTORY = "history";

    /** Player shops. Key: shop id. Value: owner, type (buy/sell), name, container, items, prices. */
    public static final String PLAYER_SHOPS = "player_shops";

    /** Stock vault links. Key: vault position. Value: shop ids it feeds. */
    public static final String STOCK_LINKS = "stock_links";

    /** Server shops (staff only). Key: shop id. Value: name, type, items, prices, stock limits, rank prices. */
    public static final String SERVER_SHOPS = "server_shops";

    /** Auction listings. Key: listing id. Value: seller, item, price, bids, end time. */
    public static final String LISTINGS = "listings";

    /** Items and money waiting to be collected from the auction house. Key: player UUID. */
    public static final String COLLECT = "collect";

    /** Daily sale counters for shop limits. Key: shop id. Value: { item key: count }, reset each day. */
    public static final String DAILY = "daily";

    /** Owner answers for other mods' shops found by the bridge. Key: mod id + shop id. */
    public static final String BRIDGED = "bridged";

    /** Accounts kept for other mods through a bridge (e.g. OctoEconomy bank accounts). Key: bridge + account id. Value: { currency, amount }. */
    public static final String BRIDGE_ACCOUNTS = "bridge_accounts";

    private Collections() {}
}
