package org.glstudio.nexus.hooks.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money movements through Vault, safe to use whether or not Vault is installed.
 *
 * <p>The provider is looked up again on every call, so an economy plugin that loads late or is
 * replaced at runtime is picked up instead of leaving the plugin wired to a dead provider. Amounts
 * are rounded to the provider's fractional digits; negative, NaN and infinite amounts are refused.
 *
 * <p>Vault's classes are only touched after checking that Vault is present, so plugins without it
 * keep working ({@link #isAvailable()} is {@code false}).
 */
public final class VaultEconomy {

    private static final VaultEconomy INSTANCE = new VaultEconomy();

    private VaultEconomy() {
    }

    /**
     * Returns the shared instance.
     *
     * @return the economy hook
     */
    public static VaultEconomy get() {
        return INSTANCE;
    }

    private static boolean vaultPresent() {
        try {
            return Bukkit.getPluginManager().getPlugin("Vault") != null;
        } catch (Throwable noServer) {
            return false;
        }
    }

    /**
     * Returns whether Vault and an enabled economy provider are present.
     *
     * @return {@code true} if money can move
     */
    public boolean isAvailable() {
        return vaultPresent() && VaultBridge.available();
    }

    /**
     * Returns the name of the economy provider.
     *
     * @return the name, or {@code "none"}
     */
    public String providerName() {
        return isAvailable() ? VaultBridge.name() : "none";
    }

    /**
     * Rounds {@code amount} to the provider's fractional digits (2 if unknown), half up. A provider
     * that does not limit its digits leaves the amount unchanged.
     *
     * @param amount the amount
     * @return the rounded amount, {@code 0} for NaN or infinite values
     */
    public double round(double amount) {
        if (!Double.isFinite(amount)) return 0.0;
        int digits = isAvailable() ? VaultBridge.fractionalDigits() : 2;
        // Vault reports -1 when the economy does not round; the amount is then passed through as is.
        if (digits < 0) return amount;
        return BigDecimal.valueOf(amount).setScale(Math.min(8, digits), RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Formats an amount with the provider's currency format.
     *
     * @param amount the amount
     * @return the formatted text, or the rounded number if no provider can format it
     */
    public String format(double amount) {
        if (isAvailable()) {
            try {
                return VaultBridge.format(amount);
            } catch (Exception ignored) {
                // Fall through to a plain number.
            }
        }
        return String.valueOf(round(amount));
    }

    /**
     * Returns the plural currency name.
     *
     * @return the name, empty if unavailable
     */
    public String currencyNamePlural() {
        return isAvailable() ? VaultBridge.plural() : "";
    }

    /**
     * Returns the singular currency name.
     *
     * @return the name, empty if unavailable
     */
    public String currencyNameSingular() {
        return isAvailable() ? VaultBridge.singular() : "";
    }

    /**
     * Returns a player's balance.
     *
     * @param player the player
     * @return the balance, {@code 0} if unavailable or on error
     */
    public double balance(OfflinePlayer player) {
        return isAvailable() ? VaultBridge.balance(player) : 0.0;
    }

    /**
     * Makes sure the player has an account, creating it if needed.
     *
     * @param player the player
     * @return {@code true} if the account exists afterwards
     */
    public boolean ensureAccount(OfflinePlayer player) {
        return isAvailable() && VaultBridge.ensureAccount(player);
    }

    /**
     * Returns whether a player can pay {@code amount}.
     *
     * @param player the player
     * @param amount the amount; {@code 0} is always affordable, negative or NaN never
     * @return {@code true} if affordable
     */
    public boolean has(OfflinePlayer player, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        if (amount == 0) return true;
        return isAvailable() && VaultBridge.has(player, round(amount));
    }

    /**
     * Takes money from a player.
     *
     * @param player the player
     * @param amount the amount, rounded first
     * @return the result; withdrawing {@code 0} succeeds without touching the provider
     */
    public EconomyResult withdraw(OfflinePlayer player, double amount) {
        double value = round(amount);
        if (!Double.isFinite(amount) || value < 0) return EconomyResult.failed("Invalid amount " + amount);
        if (value == 0) return EconomyResult.ok(0);
        if (!isAvailable()) return EconomyResult.failed("No economy provider");
        return VaultBridge.withdraw(player, value);
    }

    /**
     * Gives money to a player, creating their account if needed.
     *
     * @param player the player
     * @param amount the amount, rounded first
     * @return the result; depositing {@code 0} succeeds without touching the provider
     */
    public EconomyResult deposit(OfflinePlayer player, double amount) {
        double value = round(amount);
        if (!Double.isFinite(amount) || value < 0) return EconomyResult.failed("Invalid amount " + amount);
        if (value == 0) return EconomyResult.ok(0);
        if (!isAvailable()) return EconomyResult.failed("No economy provider");
        return VaultBridge.deposit(player, value);
    }
}
