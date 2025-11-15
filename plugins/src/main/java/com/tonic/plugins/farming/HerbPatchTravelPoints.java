package com.tonic.plugins.farming;

import com.tonic.plugins.farming.enums.HerbPatch;
import net.runelite.api.coords.WorldPoint;

import java.util.EnumMap;
import java.util.Map;

/**
 * Dedicated walk targets for herb patches.
 * Coordinates mirror the Farming Helper data (Farming-Helper-master) so Walker paths land
 * on tiles proven to be reachable instead of the patch center itself, which can be blocked.
 */
public final class HerbPatchTravelPoints {

    private static final Map<HerbPatch, WorldPoint> WALK_POINTS = new EnumMap<>(HerbPatch.class);

    static {
        WALK_POINTS.put(HerbPatch.FALADOR, new WorldPoint(3058, 3310, 0));
        WALK_POINTS.put(HerbPatch.ARDOUGNE, new WorldPoint(2670, 3376, 0));
        WALK_POINTS.put(HerbPatch.CATHERBY, new WorldPoint(2813, 3465, 0));
        WALK_POINTS.put(HerbPatch.PORT_PHASMATYS, new WorldPoint(3606, 3531, 0));
        WALK_POINTS.put(HerbPatch.FARMING_GUILD, new WorldPoint(1239, 3728, 0));
        WALK_POINTS.put(HerbPatch.HARMONY, new WorldPoint(3790, 2839, 0));
        WALK_POINTS.put(HerbPatch.TROLL_STRONGHOLD, new WorldPoint(2828, 3694, 0));
        WALK_POINTS.put(HerbPatch.WEISS, new WorldPoint(2847, 3935, 0));
    }

    private HerbPatchTravelPoints() {
    }

    public static WorldPoint get(HerbPatch patch) {
        return WALK_POINTS.getOrDefault(patch, patch.getLocation());
    }
}
