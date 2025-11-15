package com.tonic.plugins.farming;

import com.google.inject.Provides;
import com.tonic.Logger;
import com.tonic.data.wrappers.PlayerEx;
import com.tonic.plugins.farming.enums.HerbPatch;
import com.tonic.plugins.farming.enums.PlantState;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@PluginDescriptor(
        name = "Herb Farming",
        description = "Automated herb farming system with state tracking",
        tags = {"farming", "herbs", "automation", "tonic"}
)
public class HerbFarmingPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private HerbFarmingConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Getter
    private HerbPatchHelper patchHelper;

    private HerbFarmingOverlay overlay;

    // Track last processed tick for each patch to avoid spam
    private final Map<HerbPatch, Integer> lastProcessedTick = new HashMap<>();
    private static final int PROCESS_COOLDOWN_TICKS = 10; // 6 seconds between auto-processes

    // Track last action tick globally to prevent multiple actions per tick
    private int lastActionTick = 0;

    // Cache patch states to avoid reading varbits on every render
    @Getter
    private final Map<HerbPatch, PlantState> patchStateCache = new HashMap<>();

    @Provides
    HerbFarmingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HerbFarmingConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        // Initialize helper
        patchHelper = new HerbPatchHelper(client);

        // Create and register overlay
        overlay = new HerbFarmingOverlay(this, config, client, patchHelper);
        overlayManager.add(overlay);

        // Initialize last processed ticks and patch state cache
        for (HerbPatch patch : HerbPatch.values()) {
            lastProcessedTick.put(patch, 0);
            patchStateCache.put(patch, PlantState.UNKNOWN);
        }
    }

    @Override
    protected void shutDown() throws Exception {
        // Unregister overlay
        if (overlay != null) {
            overlayManager.remove(overlay);
            overlay = null;
        }

        // Clear helper
        patchHelper = null;

        // Clear tracking
        lastProcessedTick.clear();
        patchStateCache.clear();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // Check if player is logged in
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        // Check if player exists
        Player player = client.getLocalPlayer();
        if (player == null) {
            return;
        }

        // Get player's current region
        WorldPoint playerLocation = player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }
        int playerRegionId = playerLocation.getRegionID();

        // Update patch state cache for all patches
        for (HerbPatch patch : HerbPatch.values()) {
            PlantState state = HerbPatchChecker.checkHerbPatch(client, patch);
            patchStateCache.put(patch, state);
        }

        int currentTick = client.getTickCount();

        // Don't queue actions if player is busy (WinterTodt pattern)
        if (!PlayerEx.getLocal().isIdle()) {
            // Only log if there's a HARVESTABLE patch in current region (avoid noise)
            for (HerbPatch patch : HerbPatch.values()) {
                if (HerbPatchHelper.isPatchEnabled(patch, config) &&
                        patch.getRegionId() == playerRegionId &&
                        patchStateCache.get(patch) == PlantState.HARVESTABLE) {
                    Logger.info("[Farming] [" + patch.getName() + "]: Player not idle, skipping harvest");
                    break;
                }
            }
            return;
        }

        // Global action cooldown to prevent multiple actions per tick
        if (currentTick - lastActionTick < PROCESS_COOLDOWN_TICKS) {
            return;
        }

        // Check each enabled patch
        for (HerbPatch patch : HerbPatch.values()) {
            // Skip if patch is not enabled
            if (!HerbPatchHelper.isPatchEnabled(patch, config)) {
                continue;
            }

            // Skip if patch is not in current region
            if (patch.getRegionId() != playerRegionId) {
                continue;
            }

            // Check cooldown
            int lastTick = lastProcessedTick.getOrDefault(patch, 0);
            if (currentTick - lastTick >= PROCESS_COOLDOWN_TICKS) {
                // Try to process the patch
                PlantState state = patchStateCache.get(patch);
                Logger.info("[Farming] [" + patch.getName() + "]: Attempting to process (state: " + state + ")");
                boolean processed = patchHelper.processHerbPatchWithConfig(patch, config);

                if (processed) {
                    // Update last processed tick
                    lastProcessedTick.put(patch, currentTick);
                    lastActionTick = currentTick;  // Track global action
                    break;  // Only process one patch per tick
                }
            }
        }
    }

    /**
     * Gets the current patch state for a given patch from cache.
     * Cache is updated every game tick.
     *
     * @param patch The herb patch to check
     * @return The current plant state from cache
     */
    public PlantState getPatchState(HerbPatch patch) {
        return patchStateCache.getOrDefault(patch, PlantState.UNKNOWN);
    }

    /**
     * Checks if a patch is in the current region.
     *
     * @param patch The herb patch to check
     * @return true if the patch is in the current region, false otherwise
     */
    public boolean isPatchInCurrentRegion(HerbPatch patch) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return false;
        }

        Player player = client.getLocalPlayer();
        if (player == null) {
            return false;
        }

        WorldPoint playerLocation = player.getWorldLocation();
        if (playerLocation == null) {
            return false;
        }

        return patch.getRegionId() == playerLocation.getRegionID();
    }

    /**
     * Manually processes a single herb patch.
     * This can be called externally (e.g., from a script or overlay click handler).
     *
     * @param patch The herb patch to process
     * @return true if an action was performed, false otherwise
     */
    public boolean processPatches(HerbPatch patch) {
        if (patchHelper == null) {
            return false;
        }

        return patchHelper.processHerbPatchWithConfig(patch, config);
    }

    /**
     * Manually processes all enabled herb patches.
     * Useful for manual farming run scripts.
     *
     * @return The number of patches successfully processed
     */
    public int processAllEnabledPatches() {
        if (patchHelper == null) {
            return 0;
        }

        int processedCount = 0;

        for (HerbPatch patch : HerbPatch.values()) {
            if (HerbPatchHelper.isPatchEnabled(patch, config)) {
                boolean processed = patchHelper.processHerbPatchWithConfig(patch, config);
                if (processed) {
                    processedCount++;
                }
            }
        }

        return processedCount;
    }
}
