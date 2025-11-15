package com.tonic.plugins.farming.enums;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * Enum representing all standard herb patch locations in OSRS.
 * Each location includes coordinates, varbit ID for state tracking, and region ID.
 */
@Getter
public enum HerbPatch {
    FALADOR("Falador", new WorldPoint(3058, 3307, 0), 4774, 11828),
    ARDOUGNE("Ardougne", new WorldPoint(2670, 3374, 0), 4774, 10547),
    CATHERBY("Catherby", new WorldPoint(2813, 3463, 0), 4774, 11061),
    PORT_PHASMATYS("Port Phasmatys", new WorldPoint(3601, 3525, 0), 4774, 14647),
    FARMING_GUILD("Farming Guild", new WorldPoint(1238, 3726, 0), 4775, 4922),
    HARMONY("Harmony Island", new WorldPoint(3789, 2837, 0), 4772, 15148),
    TROLL_STRONGHOLD("Troll Stronghold", new WorldPoint(2824, 3696, 0), 4771, 11321),
    WEISS("Weiss", new WorldPoint(2847, 3931, 0), 4771, 11325);

    private final String name;
    private final WorldPoint location;
    private final int varbit;
    private final int regionId;

    HerbPatch(String name, WorldPoint location, int varbit, int regionId) {
        this.name = name;
        this.location = location;
        this.varbit = varbit;
        this.regionId = regionId;
    }
}
