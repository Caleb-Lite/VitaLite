package com.tonic.plugins.debugger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("debugger")
public interface DebuggerConfig extends Config
{
    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0
    )
    String generalSection = "general";

    enum OutputFormat
    {
        JSON,
        FORMATTED_QUERY
    }

    @ConfigItem(
            keyName = "outputFormat",
            name = "Output Format",
            description = "Choose between JSON data or formatted query code",
            position = 0,
            section = generalSection
    )
    default OutputFormat outputFormat()
    {
        return OutputFormat.JSON;
    }

    @ConfigItem(
            keyName = "autoCopy",
            name = "Auto copy to clipboard",
            description = "Automatically copy menu action info to clipboard when clicked",
            position = 1,
            section = generalSection
    )
    default boolean autoCopy()
    {
        return true;
    }

    @ConfigItem(
            keyName = "logToConsole",
            name = "Log to console",
            description = "Log menu actions to the console",
            position = 2,
            section = generalSection
    )
    default boolean logToConsole()
    {
        return false;
    }

    @ConfigItem(
            keyName = "ignoreCancel",
            name = "Ignore cancel actions",
            description = "Don't log CANCEL menu actions",
            position = 3,
            section = generalSection
    )
    default boolean ignoreCancel()
    {
        return true;
    }

    @ConfigItem(
            keyName = "ignoreWalk",
            name = "Ignore walk actions",
            description = "Don't log WALK menu actions",
            position = 4,
            section = generalSection
    )
    default boolean ignoreWalk()
    {
        return true;
    }

    @ConfigItem(
            keyName = "consumeClicks",
            name = "Consume clicks",
            description = "Prevent menu actions from executing (logs info but blocks the action)",
            position = 5,
            section = generalSection
    )
    default boolean consumeClicks()
    {
        return false;
    }

    @ConfigSection(
            name = "Data Capture",
            description = "Configure what data to capture",
            position = 1
    )
    String dataCaptureSection = "dataCapture";

    @ConfigItem(
            keyName = "includeInventory",
            name = "Include inventory",
            description = "Include snapshot of current inventory items",
            position = 1,
            section = dataCaptureSection
    )
    default boolean includeInventory()
    {
        return true;
    }

    @ConfigItem(
            keyName = "includeEquipment",
            name = "Include equipment",
            description = "Include snapshot of currently equipped items",
            position = 2,
            section = dataCaptureSection
    )
    default boolean includeEquipment()
    {
        return true;
    }

    @ConfigItem(
            keyName = "includeChatMessages",
            name = "Include chat messages",
            description = "Include recent chat messages (3-second buffer)",
            position = 3,
            section = dataCaptureSection
    )
    default boolean includeChatMessages()
    {
        return true;
    }
}