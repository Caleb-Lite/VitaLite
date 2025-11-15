package com.tonic.plugins.farming;

import com.tonic.plugins.farming.enums.HerbPatch;
import com.tonic.plugins.farming.enums.PlantState;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class HerbFarmingOverlay extends Overlay {

    private final HerbFarmingPlugin plugin;
    private final HerbFarmingConfig config;
    private final Client client;
    private final HerbPatchHelper patchHelper;
    private final PanelComponent panelComponent = new PanelComponent();

    public HerbFarmingOverlay(HerbFarmingPlugin plugin, HerbFarmingConfig config, Client client, HerbPatchHelper patchHelper) {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        this.patchHelper = patchHelper;

        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // Don't show overlay if disabled
        if (!config.showOverlay()) {
            return null;
        }

        panelComponent.getChildren().clear();

        // Add title
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Herb Farming")
                .color(config.overlayColor())
                .build());

        // Add configured herb type
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Herb:")
                .right(formatHerbType(config.herbType()))
                .rightColor(Color.YELLOW)
                .build());

        // Add configured compost type
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Compost:")
                .right(formatCompostType(config.compostType()))
                .rightColor(Color.CYAN)
                .build());

        // Add separator
        panelComponent.getChildren().add(LineComponent.builder()
                .left("---------------")
                .build());

        // Show patch states if enabled
        if (config.showPatchStates()) {
            int enabledPatchCount = 0;

            for (HerbPatch patch : HerbPatch.values()) {
                // Only show enabled patches
                if (!HerbPatchHelper.isPatchEnabled(patch, config)) {
                    continue;
                }

                enabledPatchCount++;

                PlantState state = plugin.getPatchState(patch);
                Color stateColor = getStateColor(state);

                // Check if patch is in current region
                boolean inCurrentRegion = plugin.isPatchInCurrentRegion(patch);
                String patchName = inCurrentRegion ? "► " + patch.getName() : patch.getName();
                Color nameColor = inCurrentRegion ? Color.WHITE : Color.GRAY;

                panelComponent.getChildren().add(LineComponent.builder()
                        .left(patchName)
                        .leftColor(nameColor)
                        .right(formatState(state))
                        .rightColor(stateColor)
                        .build());
            }

            // Show message if no patches are enabled
            if (enabledPatchCount == 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("No patches enabled")
                        .leftColor(Color.GRAY)
                        .build());
            }
        }

        // Add auto-process status

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Auto-Process: ON")
                .leftColor(Color.GREEN)
                .build());

        return panelComponent.render(graphics);
    }

    /**
     * Gets the color for a given plant state.
     *
     * @param state The plant state
     * @return The color to display
     */
    private Color getStateColor(PlantState state) {
        switch (state) {
            case WEEDS:
                return new Color(139, 69, 19); // Brown
            case PLANT:
                return new Color(205, 133, 63); // Peru/tan
            case GROWING:
                return new Color(34, 139, 34); // Forest green
            case DISEASED:
                return new Color(255, 69, 0); // Red-orange
            case DEAD:
                return Color.RED;
            case HARVESTABLE:
                return new Color(50, 205, 50); // Lime green
            case UNKNOWN:
            default:
                return Color.GRAY;
        }
    }

    /**
     * Formats a plant state for display.
     *
     * @param state The plant state
     * @return Formatted string
     */
    private String formatState(PlantState state) {
        switch (state) {
            case WEEDS:
                return "Weeds";
            case PLANT:
                return "Ready";
            case GROWING:
                return "Growing";
            case DISEASED:
                return "Diseased!";
            case DEAD:
                return "Dead!";
            case HARVESTABLE:
                return "Harvest!";
            case UNKNOWN:
            default:
                return "Unknown";
        }
    }

    /**
     * Formats an herb type for display.
     *
     * @param herbType The herb type
     * @return Formatted string
     */
    private String formatHerbType(HerbFarmingConfig.HerbType herbType) {
        String name = herbType.name();
        // Convert DWARF_WEED to "Dwarf Weed"
        return name.substring(0, 1).toUpperCase() +
                name.substring(1).toLowerCase().replace('_', ' ');
    }

    /**
     * Formats a compost type for display.
     *
     * @param compostType The compost type
     * @return Formatted string
     */
    private String formatCompostType(HerbFarmingConfig.CompostType compostType) {
        if (compostType == HerbFarmingConfig.CompostType.NONE) {
            return "None";
        }
        String name = compostType.name();
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }
}
