package org.glstudio.nexus.hooks.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The only class that references WorldGuard. Loaded by {@link WorldGuardHook} after it confirmed
 * WorldGuard is running, so a server without it never resolves these types.
 */
final class WorldGuardBridge {

    private WorldGuardBridge() {
    }

    private static RegionQuery query() {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
    }

    static Set<String> regionsAt(Location location) {
        ApplicableRegionSet here = query().getApplicableRegions(BukkitAdapter.adapt(location));
        Set<String> ids = new HashSet<>();
        for (ProtectedRegion region : here) ids.add(region.getId().toLowerCase(Locale.ROOT));
        return ids;
    }

    static Optional<Boolean> state(Location location, @Nullable Player player, String name) {
        StateFlag flag = stateFlag(name);
        if (flag == null) return Optional.empty();
        LocalPlayer local = player == null ? null : WorldGuardPlugin.inst().wrapPlayer(player);
        StateFlag.State state = query().queryState(BukkitAdapter.adapt(location), local, flag);
        return state == null ? Optional.empty() : Optional.of(state == StateFlag.State.ALLOW);
    }

    static boolean canBuild(Player player, Location location, String[] names) {
        List<StateFlag> flags = new ArrayList<>();
        for (String name : names) {
            StateFlag flag = stateFlag(name);
            if (flag != null) flags.add(flag);
        }
        LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
        if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(local, local.getWorld())) return true;
        return query().testBuild(BukkitAdapter.adapt(location), local, flags.toArray(new StateFlag[0]));
    }

    private static @Nullable StateFlag stateFlag(String name) {
        Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(name.toLowerCase(Locale.ROOT));
        return flag instanceof StateFlag state ? state : null;
    }
}
