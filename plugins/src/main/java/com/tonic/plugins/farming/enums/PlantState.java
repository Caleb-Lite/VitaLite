package com.tonic.plugins.farming.enums;

/**
 * Represents the current state of a herb patch.
 * Used to determine what action is needed (raking, planting, curing, harvesting, etc.)
 */
public enum PlantState {
    /** Patch has weeds and needs to be raked */
    WEEDS,

    /** Patch is clear and ready for planting a seed */
    PLANT,

    /** Herb is actively growing */
    GROWING,

    /** Herb is diseased and needs plant cure */
    DISEASED,

    /** Herb is dead and needs to be cleared */
    DEAD,

    /** Herb is fully grown and ready to harvest */
    HARVESTABLE,

    /** State cannot be determined from varbit value */
    UNKNOWN
}
