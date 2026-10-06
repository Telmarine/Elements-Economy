package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.currency.CurrencyDetector;
import com.tac5studios.elementseconomy.currency.CurrencyKind;
import com.tac5studios.elementseconomy.currency.KnownCurrency;
import com.tac5studios.elementsvault.backend.CurrencyBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Builds a backend for every detected currency mod that keeps money in its own data.
 * Coin-only mods are handled by the item backend instead. Detection (currency.auto_detect)
 * decides what's found; this only wires up what was found.
 *
 * Not covered here, with the reason:
 *  - Impactor, Eights/OctoEconomy: Economy plugs into them as their provider (bridges), not the other way round.
 *  - Bubustein's Money: many accounts in many currencies per player and no main account to pick.
 *  - Avecoins, Sweconm: coins are counted as items; their wallets are a second store.
 *  - DiceMC Money: no 1.21.1 build. KROIA BankSystem 1.x: older API, only 2.x is covered.
 */
public final class Adapters {

    private Adapters() {}

    public static List<CurrencyBackend> forDetected() {
        List<CurrencyBackend> out = new ArrayList<>();
        for (KnownCurrency k : CurrencyDetector.found()) {
            if (k.kind() == CurrencyKind.ITEMS) continue;
            Supplier<? extends ReflectBackend> make = factory(k);
            if (make == null) continue;
            try {
                ReflectBackend b = make.get();
                if (b.isAvailable()) {
                    out.add(b);
                    ElementsEconomy.LOGGER.info("[Economy] {} balances connected.", k.name());
                } else {
                    ElementsEconomy.LOGGER.warn("[Economy] {} was found but its balance API isn't ready. Skipped.", k.name());
                }
            } catch (RuntimeException | LinkageError e) {
                ElementsEconomy.LOGGER.warn("[Economy] {} adapter could not start: {}", k.name(), e.toString());
            }
        }
        return out;
    }

    /** The adapter for a detected mod (matched by its marker class, since some mods share an id). */
    private static Supplier<? extends ReflectBackend> factory(KnownCurrency k) {
        String marker = k.markers().isEmpty() ? "" : k.markers().get(0);
        return switch (k.modId()) {
            case "cobbledollars" -> CobbleDollarsAdapter::new;
            case "lightmanscurrency" -> LightmansAdapter::new;
            case "sdmeconomy" -> SdmEconomyAdapter::newer;
            case "sdm_economy" -> SdmEconomyAdapter::older;
            case "numismatics" -> NumismaticsAdapter::new;
            case "sg_economy" -> SgEconomyAdapter::create;
            case "economycraft" -> EconomyCraftAdapter::new;
            case "banksystem" -> BankSystemAdapter::new;
            case "saros__money_mod" -> SarosMoneyAdapter::new;
            case "sarosessentialsmod" -> SarosEssentialsAdapter::new;
            case "migueleconomy" -> MiguelEconomyAdapter::new;
            case "arcadia_lib" -> ArcadiaAdapter::new;
            case "simpleeconomy" -> marker.startsWith("com.simpleeconomy") ? SimpleEconomyAdapter::new : null;
            case "omnieconomy" -> OmniEconomyAdapter::new;
            case "kuronomy" -> McreatorVariablesAdapter::kuronomy;
            case "mezzos_money_mod" -> McreatorVariablesAdapter::mezzos;
            case "betterecon" -> BetterEconomyAdapter::new;
            case "currency" -> marker.startsWith("com.zundrel") ? NeverEnoughCurrencyAdapter::new
                    : marker.startsWith("currency.network") ? McreatorVariablesAdapter::mEconomy : null;
            default -> null;
        };
    }
}
