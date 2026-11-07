package org.glstudio.nexus.hooks.economy;

/**
 * The outcome of a money movement through {@link VaultEconomy}.
 *
 * @param success whether the money moved
 * @param amount  the amount actually moved, after rounding
 * @param error   why it failed, {@code null} on success
 */
public record EconomyResult(boolean success, double amount, String error) {

    /**
     * A successful movement.
     *
     * @param amount the amount moved
     * @return the result
     */
    public static EconomyResult ok(double amount) {
        return new EconomyResult(true, amount, null);
    }

    /**
     * A failed movement.
     *
     * @param error why it failed
     * @return the result
     */
    public static EconomyResult failed(String error) {
        return new EconomyResult(false, 0, error);
    }
}
