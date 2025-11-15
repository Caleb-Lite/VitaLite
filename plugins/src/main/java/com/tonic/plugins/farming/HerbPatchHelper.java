package com.tonic.plugins.farming;

import com.tonic.Logger;
import com.tonic.api.entities.TileObjectAPI;
import com.tonic.api.widgets.InventoryAPI;
import com.tonic.data.LayoutView;
import com.tonic.data.wrappers.ItemEx;
import com.tonic.data.wrappers.TileObjectEx;
import com.tonic.util.ClickManagerUtil;
import com.tonic.plugins.farming.enums.HerbPatch;
import com.tonic.plugins.farming.enums.PlantState;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Helper class for interacting with herb patches.
 * Combines state detection with TileObjectAPI/InventoryAPI interactions.
 */
public class HerbPatchHelper {

    // Herb patch object IDs
    private static final List<Integer> HERB_PATCH_IDS = Arrays.asList(
            33176, 27115, 8152, 8150, 8153, 18816, 8151, 9372, 33979, 50697
    );

    // Compost item IDs (priority order: ultra > super > regular)
    private static final int ULTRACOMPOST = 21483;
    private static final int SUPERCOMPOST = 6034;
    private static final int COMPOST = 6032;
    private static final int BOTTOMLESS_COMPOST_BUCKET = 22997;

    // Farming tool IDs
    private static final int RAKE = 5341;
    private static final int SPADE = 952;
    private static final int PLANT_CURE = 6036;
    private static final int WEEDS = 6055;

    private final Client client;

    public HerbPatchHelper(Client client) {
        this.client = Objects.requireNonNull(client, "Client cannot be null");
    }

    /**
     * Finds the TileObjectEx for a herb patch at the specified location.
     *
     * @param location The world point of the herb patch
     * @return The TileObjectEx if found, null otherwise
     */
    public TileObjectEx findPatchAt(WorldPoint location) {
        return TileObjectAPI.search()
                .withId(HERB_PATCH_IDS.stream().mapToInt(Integer::intValue).toArray())
                .atLocation(location)
                .first();
    }

    /**
     * Finds the TileObjectEx for a herb patch enum.
     *
     * @param patch The herb patch enum
     * @return The TileObjectEx if found, null otherwise
     */
    public TileObjectEx findPatch(HerbPatch patch) {
        return findPatchAt(patch.getLocation());
    }

    /**
     * Rakes weeds from a herb patch.
     *
     * @param patch The herb patch to rake
     * @return true if the rake action was initiated, false otherwise
     */
    public boolean rakePatch(HerbPatch patch) {
        TileObjectEx patchObj = findPatch(patch);
        if (patchObj == null) {
            return false;
        }

        if (!patchObj.hasAction("Rake")) {
            return false;
        }

        ClickManagerUtil.queueClickBox(patchObj);
        TileObjectAPI.interact(patchObj, "Rake");
        return true;
    }

    /**
     * Plants a seed in an empty herb patch.
     *
     * @param patch The herb patch to plant in
     * @param seedId The item ID of the seed to plant
     * @return true if the planting action was initiated, false otherwise
     */
    public boolean plantSeed(HerbPatch patch, int seedId) {
        TileObjectEx patchObj = findPatch(patch);
        if (patchObj == null) {
            return false;
        }

        ItemEx seed = InventoryAPI.getItem(seedId);
        if (seed == null) {
            return false;
        }

        ClickManagerUtil.queueClickBox(LayoutView.SIDE_MENU.getWidget());
        InventoryAPI.useOn(seed, patchObj);
        return true;
    }

    /**
     * Applies the best available compost to a herb patch.
     * Priority: Bottomless bucket > Ultracompost > Supercompost > Compost
     *
     * @param patch The herb patch to apply compost to
     * @return true if the compost action was initiated, false otherwise
     */
    public boolean applyCompost(HerbPatch patch) {
        TileObjectEx patchObj = findPatch(patch);
        if (patchObj == null) {
            return false;
        }

        // Check for bottomless bucket first (infinite uses)
        ItemEx compost = InventoryAPI.getItem(BOTTOMLESS_COMPOST_BUCKET);

        // Fall back to regular compost buckets
        if (compost == null) {
            compost = InventoryAPI.getItem(ULTRACOMPOST);
        }
        if (compost == null) {
            compost = InventoryAPI.getItem(SUPERCOMPOST);
        }
        if (compost == null) {
            compost = InventoryAPI.getItem(COMPOST);
        }

        if (compost == null) {
            return false;
        }

        ClickManagerUtil.queueClickBox(LayoutView.SIDE_MENU.getWidget());
        InventoryAPI.useOn(compost, patchObj);
        return true;
    }

    /**
     * Cures a diseased herb patch with plant cure.
     *
     * @param patch The herb patch to cure
     * @return true if the cure action was initiated, false otherwise
     */
    public boolean curePatch(HerbPatch patch) {
        TileObjectEx patchObj = findPatch(patch);
        if (patchObj == null) {
            return false;
        }

        ItemEx plantCure = InventoryAPI.getItem(PLANT_CURE);
        if (plantCure == null) {
            return false;
        }

        ClickManagerUtil.queueClickBox(LayoutView.SIDE_MENU.getWidget());
        InventoryAPI.useOn(plantCure, patchObj);
        return true;
    }

    /**
     * Clears dead herbs from a patch.
     *
     * @param patch The herb patch to clear
     * @return true if the clear action was initiated, false otherwise
     */
    public boolean clearPatch(HerbPatch patch) {
        TileObjectEx patchObj = findPatch(patch);
        if (patchObj == null) {
            return false;
        }

        if (!patchObj.hasAction("Clear")) {
            return false;
        }

        ClickManagerUtil.queueClickBox(patchObj);
        TileObjectAPI.interact(patchObj, "Clear");
        return true;
    }

    /**
     * Harvests herbs from a patch.
     *
     * @param patch The herb patch to harvest
     * @return true if the harvest action was initiated, false otherwise
     */
    public boolean harvestPatch(HerbPatch patch) {
        TileObjectEx patchObj = findPatch(patch);
        if (patchObj == null) {
            Logger.info("[Farming] [" + patch.getName() + "]: Patch object not found at " + patch.getLocation());
            return false;
        }

        if (!patchObj.hasAction("Pick")) {
            Logger.info("[Farming] [" + patch.getName() + "]: No 'Pick' action available. Actions: " +
                    java.util.Arrays.toString(patchObj.getActions()));
            return false;
        }

        Logger.info("[Farming] [" + patch.getName() + "]: Harvesting patch with 'Pick' action");
        ClickManagerUtil.queueClickBox(patchObj);
        TileObjectAPI.interact(patchObj, "Pick");
        return true;
    }

    /**
     * Drops all weeds from inventory.
     *
     * @return The number of weeds dropped
     */
    public int dropWeeds() {
        return InventoryAPI.dropAll(WEEDS);
    }

    /**
     * Checks if inventory has the required items for a given patch state.
     *
     * @param state The plant state to check requirements for
     * @return true if inventory has required items, false otherwise
     */
    public boolean hasRequiredItems(PlantState state) {
        switch (state) {
            case WEEDS:
                return InventoryAPI.contains(RAKE);
            case PLANT:
                // Any herb seed would work - this is a basic check
                return InventoryAPI.search()
                        .withNameContains("seed")
                        .count() > 0;
            case DISEASED:
                return InventoryAPI.contains(PLANT_CURE);
            case DEAD:
                return InventoryAPI.contains(SPADE);
            case GROWING:
                // Check for any compost
                return InventoryAPI.containsAny(
                        BOTTOMLESS_COMPOST_BUCKET,
                        ULTRACOMPOST,
                        SUPERCOMPOST,
                        COMPOST
                );
            case HARVESTABLE:
                return true; // No items needed to harvest
            default:
                return false;
        }
    }

    /**
     * Processes a herb patch by checking its state and performing the appropriate action.
     *
     * @param patch The herb patch to process
     * @return true if an action was performed, false otherwise
     */
    public boolean processHerbPatch(HerbPatch patch) {
        PlantState state = HerbPatchChecker.checkHerbPatch(client, patch);

        switch (state) {
            case WEEDS:
                return rakePatch(patch);

            case PLANT:
                // Try to plant the first available seed
                ItemEx seed = InventoryAPI.search()
                        .withNameContains("seed")
                        .first();
                if (seed != null) {
                    return plantSeed(patch, seed.getId());
                }
                break;

            case GROWING:
                return applyCompost(patch);

            case DISEASED:
                return curePatch(patch);

            case DEAD:
                return clearPatch(patch);

            case HARVESTABLE:
                return harvestPatch(patch);

            case UNKNOWN:
            default:
                return false;
        }

        return false;
    }

    /**
     * Processes a herb patch at a specific location.
     *
     * @param location The world point of the herb patch
     * @return true if an action was performed, false otherwise
     */
    public boolean processHerbPatchAt(WorldPoint location) {
        for (HerbPatch patch : HerbPatch.values()) {
            if (patch.getLocation().equals(location)) {
                return processHerbPatch(patch);
            }
        }
        return false;
    }

    // ========== CONFIG-BASED METHODS ==========

    /**
     * Gets the ItemID for the configured herb seed.
     *
     * @param config The farming config
     * @return The item ID of the selected herb seed
     */
    public static int getConfiguredSeedId(HerbFarmingConfig config) {
        switch (config.herbType()) {
            case GUAM:
                return 5291;  // ItemID.GUAM_SEED
            case MARRENTILL:
                return 5292;  // ItemID.MARRENTILL_SEED
            case TARROMIN:
                return 5293;  // ItemID.TARROMIN_SEED
            case HARRALANDER:
                return 5294;  // ItemID.HARRALANDER_SEED
            case RANARR:
                return 5295;  // ItemID.RANARR_SEED
            case TOADFLAX:
                return 5296;  // ItemID.TOADFLAX_SEED
            case IRIT:
                return 5297;  // ItemID.IRIT_SEED
            case AVANTOE:
                return 5298;  // ItemID.AVANTOE_SEED
            case KWUARM:
                return 5299;  // ItemID.KWUARM_SEED
            case SNAPDRAGON:
                return 5300;  // ItemID.SNAPDRAGON_SEED
            case CADANTINE:
                return 5301;  // ItemID.CADANTINE_SEED
            case LANTADYME:
                return 5302;  // ItemID.LANTADYME_SEED
            case DWARF_WEED:
                return 5303;  // ItemID.DWARF_WEED_SEED
            case TORSTOL:
                return 5304;  // ItemID.TORSTOL_SEED
            default:
                return 5295;  // Default to Ranarr
        }
    }

    /**
     * Gets the ItemID for the configured compost type.
     *
     * @param config The farming config
     * @return The item ID of the selected compost, or -1 for NONE
     */
    public static int getConfiguredCompostId(HerbFarmingConfig config) {
        switch (config.compostType()) {
            case COMPOST:
                return COMPOST;
            case SUPERCOMPOST:
                return SUPERCOMPOST;
            case ULTRACOMPOST:
                return ULTRACOMPOST;
            case NONE:
            default:
                return -1;
        }
    }

    /**
     * Plants the configured seed from config in an empty herb patch.
     *
     * @param patch The herb patch to plant in
     * @param config The farming config
     * @return true if the planting action was initiated, false otherwise
     */
    public boolean plantConfiguredSeed(HerbPatch patch, HerbFarmingConfig config) {
        int seedId = getConfiguredSeedId(config);
        return plantSeed(patch, seedId);
    }

    /**
     * Applies the configured compost to a herb patch.
     * Respects the config preference for bottomless bucket.
     *
     * @param patch The herb patch to apply compost to
     * @param config The farming config
     * @return true if the compost action was initiated, false otherwise
     */
    public boolean applyConfiguredCompost(HerbPatch patch, HerbFarmingConfig config) {
        if (config.compostType() == HerbFarmingConfig.CompostType.NONE) {
            return false; // User doesn't want to apply compost
        }

        TileObjectEx patchObj = findPatch(patch);
        if (patchObj == null) {
            return false;
        }

        ItemEx compost = null;

        // Check for bottomless bucket first if enabled in config
        if (config.useBottomlessBucket()) {
            compost = InventoryAPI.getItem(BOTTOMLESS_COMPOST_BUCKET);
        }

        // If no bottomless bucket or config disabled it, use the configured compost type
        if (compost == null) {
            int compostId = getConfiguredCompostId(config);
            if (compostId != -1) {
                compost = InventoryAPI.getItem(compostId);
            }
        }

        if (compost == null) {
            return false;
        }

        ClickManagerUtil.queueClickBox(LayoutView.SIDE_MENU.getWidget());
        InventoryAPI.useOn(compost, patchObj);
        return true;
    }

    /**
     * Processes a herb patch using configuration settings.
     * This method uses the configured seed and compost types.
     *
     * @param patch The herb patch to process
     * @param config The farming config
     * @return true if an action was performed, false otherwise
     */
    public boolean processHerbPatchWithConfig(HerbPatch patch, HerbFarmingConfig config) {
        PlantState state = HerbPatchChecker.checkHerbPatch(client, patch);

        switch (state) {
            case WEEDS:
                return rakePatch(patch);

            case PLANT:
                return plantConfiguredSeed(patch, config);

            case GROWING:
                return applyConfiguredCompost(patch, config);

            case DISEASED:
                return curePatch(patch);

            case DEAD:
                return clearPatch(patch);

            case HARVESTABLE:
                return harvestPatch(patch);

            case UNKNOWN:
            default:
                return false;
        }

    }

    /**
     * Checks if a patch is enabled in the config.
     *
     * @param patch The herb patch to check
     * @param config The farming config
     * @return true if the patch is enabled, false otherwise
     */
    public static boolean isPatchEnabled(HerbPatch patch, HerbFarmingConfig config) {
        switch (patch) {
            case FALADOR:
                return config.enableFalador();
            case ARDOUGNE:
                return config.enableArdougne();
            case CATHERBY:
                return config.enableCatherby();
            case PORT_PHASMATYS:
                return config.enablePortPhasmatys();
            case FARMING_GUILD:
                return config.enableFarmingGuild();
            case HARMONY:
                return config.enableHarmony();
            case TROLL_STRONGHOLD:
                return config.enableTrollStronghold();
            case WEISS:
                return config.enableWeiss();
            default:
                return false;
        }
    }
}
