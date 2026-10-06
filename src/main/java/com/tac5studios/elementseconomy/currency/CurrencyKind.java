package com.tac5studios.elementseconomy.currency;

/** How a currency mod keeps its money. */
public enum CurrencyKind {
    /** Balance is read and changed through the mod's own code. */
    API,
    /** Balance is a field stored on the player (attachment or player data). */
    PLAYER_DATA,
    /** Balance is kept in a plain file in the world folder. */
    FILE,
    /** Money is physical items counted in the inventory. */
    ITEMS
}
