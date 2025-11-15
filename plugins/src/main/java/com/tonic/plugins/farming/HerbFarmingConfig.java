package com.tonic.plugins.farming;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import java.awt.Color;

@ConfigGroup("herbfarming")
public interface HerbFarmingConfig extends Config {

    // ========== SECTIONS ==========
    @ConfigSection(
            name = "Herb Selection",
            description = "Choose which herb to farm",
            position = 1
    )
    String herbSection = "herb";

    @ConfigSection(
            name = "Compost Settings",
            description = "Compost options",
            position = 2
    )
    String compostSection = "compost";

    @ConfigSection(
            name = "Patch Selection",
            description = "Enable/disable specific patches",
            position = 3
    )
    String patchSection = "patches";

    @ConfigSection(
            name = "Overlay Settings",
            description = "Visual overlay options",
            position = 4
    )
    String overlaySection = "overlay";

    // ========== HERB SELECTION ==========

    enum HerbType {
        GUAM,
        MARRENTILL,
        TARROMIN,
        HARRALANDER,
        RANARR,
        TOADFLAX,
        IRIT,
        AVANTOE,
        KWUARM,
        SNAPDRAGON,
        CADANTINE,
        LANTADYME,
        DWARF_WEED,
        TORSTOL
    }

    @ConfigItem(
            keyName = "herbType",
            name = "Herb Type",
            description = "Select which herb to farm",
            position = 0,
            section = herbSection
    )
    default HerbType herbType() {
        return HerbType.RANARR;
    }

    // ========== COMPOST SETTINGS ==========

    enum CompostType {
        NONE,
        COMPOST,
        SUPERCOMPOST,
        ULTRACOMPOST
    }

    @ConfigItem(
            keyName = "compostType",
            name = "Compost Type",
            description = "Select which compost to use",
            position = 0,
            section = compostSection
    )
    default CompostType compostType() {
        return CompostType.ULTRACOMPOST;
    }

    @ConfigItem(
            keyName = "useBottomlessBucket",
            name = "Use Bottomless Bucket",
            description = "Prefer bottomless compost bucket if available in inventory",
            position = 1,
            section = compostSection
    )
    default boolean useBottomlessBucket() {
        return true;
    }

    // ========== PATCH SELECTION ==========

    @ConfigItem(
            keyName = "enableFalador",
            name = "Falador",
            description = "Enable Falador herb patch",
            position = 0,
            section = patchSection
    )
    default boolean enableFalador() {
        return true;
    }

    @ConfigItem(
            keyName = "enableArdougne",
            name = "Ardougne",
            description = "Enable Ardougne herb patch",
            position = 1,
            section = patchSection
    )
    default boolean enableArdougne() {
        return true;
    }

    @ConfigItem(
            keyName = "enableCatherby",
            name = "Catherby",
            description = "Enable Catherby herb patch",
            position = 2,
            section = patchSection
    )
    default boolean enableCatherby() {
        return true;
    }

    @ConfigItem(
            keyName = "enablePortPhasmatys",
            name = "Port Phasmatys",
            description = "Enable Port Phasmatys (Morytania) herb patch",
            position = 3,
            section = patchSection
    )
    default boolean enablePortPhasmatys() {
        return true;
    }

    @ConfigItem(
            keyName = "enableFarmingGuild",
            name = "Farming Guild",
            description = "Enable Farming Guild herb patch",
            position = 4,
            section = patchSection
    )
    default boolean enableFarmingGuild() {
        return true;
    }

    @ConfigItem(
            keyName = "enableHarmony",
            name = "Harmony Island",
            description = "Enable Harmony Island herb patch",
            position = 5,
            section = patchSection
    )
    default boolean enableHarmony() {
        return true;
    }

    @ConfigItem(
            keyName = "enableTrollStronghold",
            name = "Troll Stronghold",
            description = "Enable Troll Stronghold herb patch",
            position = 6,
            section = patchSection
    )
    default boolean enableTrollStronghold() {
        return true;
    }

    @ConfigItem(
            keyName = "enableWeiss",
            name = "Weiss",
            description = "Enable Weiss herb patch",
            position = 7,
            section = patchSection
    )
    default boolean enableWeiss() {
        return true;
    }

    // ========== OVERLAY SETTINGS ==========

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Display patch status overlay",
            position = 0,
            section = overlaySection
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "showPatchStates",
            name = "Show Patch States",
            description = "Display the state of each patch in the overlay",
            position = 1,
            section = overlaySection
    )
    default boolean showPatchStates() {
        return true;
    }

    @ConfigItem(
            keyName = "overlayColor",
            name = "Overlay Text Color",
            description = "Color of the overlay text",
            position = 2,
            section = overlaySection
    )
    default Color overlayColor() {
        return Color.WHITE;
    }
}
