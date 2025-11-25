package com.tonic.plugins.eqp48.bankcleaner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bankcleaner")
public interface BankCleanerConfig extends Config
{
    @ConfigItem(
            keyName = "warning",
            name = "",
            description = "CAUTION: This plugin will sell every item in your bank at 1 GP.",
            position = 0
    )
    default String warning()
    {
        return "CAUTION: This plugin will sell every item in your bank at 1 GP.";
    }

    @ConfigItem(
            keyName = "minTickDelay",
            name = "Min tick delay",
            description = "Minimum ticks to wait between actions.",
            position = 1
    )
    default int minTickDelay()
    {
        return 2;
    }

    @ConfigItem(
            keyName = "maxTickDelay",
            name = "Max tick delay",
            description = "Maximum ticks to wait between actions.",
            position = 2
    )
    default int maxTickDelay()
    {
        return 3;
    }
}
