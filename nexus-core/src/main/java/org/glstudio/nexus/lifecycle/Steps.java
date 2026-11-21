package org.glstudio.nexus.lifecycle;

import org.glstudio.nexus.utils.Diagnostics;

/**
 * Runs the steps of starting or stopping a plugin, each guarded on its own: a failing step is logged
 * as {@code Owner#step} with its cause. Starting stops at the first step that fails; stopping goes on
 * with the rest.
 */
public final class Steps {

    private final String owner;

    private Steps(String owner) {
        this.owner = owner;
    }

    /**
     * Creates the steps of a class.
     *
     * @param owner the class that runs them, such as {@code StartupService}
     * @return the steps
     */
    public static Steps of(String owner) {
        return new Steps(owner);
    }

    /**
     * Runs a step of starting.
     *
     * @param name the step, such as {@code loadConfigs}
     * @param step returns whether it worked
     * @return {@code false} if it returned so or threw
     */
    public boolean run(String name, Step step) {
        try {
            return step.run();
        } catch (Exception e) {
            Diagnostics.logFailure(owner + "#" + name, e);
            return false;
        }
    }

    /**
     * Runs a step of stopping; a failure is logged and the next step still runs.
     *
     * @param name   the step, such as {@code closeDatabase}
     * @param action what it does
     */
    public void always(String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            Diagnostics.logFailure(owner + "#" + name, e);
        }
    }

    /**
     * A step of starting.
     */
    @FunctionalInterface
    public interface Step {

        /**
         * Runs the step.
         *
         * @return whether it worked
         * @throws Exception if it failed
         */
        boolean run() throws Exception;
    }
}
