package org.glstudio.nexus.hooks.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;

/**
 * The only class that references Vault. Loaded by {@link VaultEconomy} after it confirmed Vault is
 * installed, so a server without Vault never resolves these types.
 */
final class VaultBridge {

    private VaultBridge() {
    }

    private static Economy economy() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    static boolean available() {
        try {
            Economy economy = economy();
            return economy != null && economy.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    static String name() {
        Economy economy = economy();
        return economy == null ? "none" : economy.getName();
    }

    static int fractionalDigits() {
        try {
            int digits = economy().fractionalDigits();
            return digits < 0 ? 2 : digits;
        } catch (Exception e) {
            return 2;
        }
    }

    static String format(double amount) {
        return economy().format(amount);
    }

    static String plural() {
        return economy().currencyNamePlural();
    }

    static String singular() {
        return economy().currencyNameSingular();
    }

    static double balance(OfflinePlayer player) {
        try {
            return economy().getBalance(player);
        } catch (Exception e) {
            Diagnostics.logFailure("VaultEconomy#balance(" + describe(player) + ")", e);
            return 0.0;
        }
    }

    static boolean ensureAccount(OfflinePlayer player) {
        try {
            Economy economy = economy();
            return economy.hasAccount(player) || economy.createPlayerAccount(player);
        } catch (Exception e) {
            Diagnostics.logFailure("VaultEconomy#ensureAccount(" + describe(player) + ")", e);
            return false;
        }
    }

    static boolean has(OfflinePlayer player, double amount) {
        try {
            return economy().has(player, amount);
        } catch (Exception e) {
            Diagnostics.logFailure("VaultEconomy#has(" + describe(player) + ")", e);
            return false;
        }
    }

    static EconomyResult withdraw(OfflinePlayer player, double amount) {
        try {
            return result(economy().withdrawPlayer(player, amount), "Withdrawal", amount, player);
        } catch (Exception e) {
            Diagnostics.logFailure("VaultEconomy#withdraw(" + describe(player) + ")", e);
            return EconomyResult.failed(Diagnostics.describeRootCause(e));
        }
    }

    static EconomyResult deposit(OfflinePlayer player, double amount) {
        try {
            ensureAccount(player);
            return result(economy().depositPlayer(player, amount), "Deposit", amount, player);
        } catch (Exception e) {
            Diagnostics.logFailure("VaultEconomy#deposit(" + describe(player) + ")", e);
            return EconomyResult.failed(Diagnostics.describeRootCause(e));
        }
    }

    private static EconomyResult result(EconomyResponse response, String action, double amount, OfflinePlayer player) {
        if (response != null && response.transactionSuccess()) return EconomyResult.ok(amount);
        String error = response == null ? "no response" : response.errorMessage;
        LoggerUtils.logWarn(action + " of " + amount + " for " + describe(player) + " was refused: " + error);
        return EconomyResult.failed(error);
    }

    private static String describe(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }
}
