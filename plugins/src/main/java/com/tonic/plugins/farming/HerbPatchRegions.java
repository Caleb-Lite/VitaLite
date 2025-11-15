package com.tonic.plugins.farming;

import com.tonic.plugins.farming.enums.HerbPatch;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors the region-to-patch mapping from RuneLite's time tracking plugin so we
 * can tell which herb patch varbits are currently transmitted.
 */
public final class HerbPatchRegions {

    private static final Map<Integer, Set<HerbPatch>> REGION_TO_PATCHES = new HashMap<>();
    private static final Map<HerbPatch, Set<Integer>> PATCH_TO_REGIONS = new EnumMap<>(HerbPatch.class);

    static {
        register(HerbPatch.FALADOR, 12083);
        register(HerbPatch.ARDOUGNE, 10548);
        register(HerbPatch.CATHERBY, 11062, 11061, 11318, 11317);
        register(HerbPatch.PORT_PHASMATYS, 14391, 14390);
        register(HerbPatch.FARMING_GUILD, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667);
        register(HerbPatch.HARMONY, 15148);
        register(HerbPatch.TROLL_STRONGHOLD, 11321);
        register(HerbPatch.WEISS, 11325);
    }

    private HerbPatchRegions() {
    }

    private static void register(HerbPatch patch, int primaryRegion, int... extraRegions) {
        addMapping(patch, primaryRegion);
        for (int regionId : extraRegions) {
            addMapping(patch, regionId);
        }
    }

    private static void addMapping(HerbPatch patch, int regionId) {
        REGION_TO_PATCHES.computeIfAbsent(regionId, id -> EnumSet.noneOf(HerbPatch.class)).add(patch);
        PATCH_TO_REGIONS.computeIfAbsent(patch, key -> new HashSet<>()).add(regionId);
    }

    public static Collection<HerbPatch> getPatchesForRegion(int regionId) {
        return REGION_TO_PATCHES.getOrDefault(regionId, Collections.emptySet());
    }

    public static boolean isRegionForPatch(HerbPatch patch, int regionId) {
        Set<Integer> regions = PATCH_TO_REGIONS.get(patch);
        return regions != null && regions.contains(regionId);
    }
}
