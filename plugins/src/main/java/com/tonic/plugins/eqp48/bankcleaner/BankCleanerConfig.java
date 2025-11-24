package com.tonic.plugins.eqp48.bankcleaner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bankcleaner")
public interface BankCleanerConfig extends Config
{
    @ConfigItem(
            keyName = "minTickDelay",
            name = "Min tick delay",
            description = "Minimum ticks to wait between actions.",
            position = 1
    )
    default int minTickDelay()
    {
        return 1;
    }

    @ConfigItem(
            keyName = "maxTickDelay",
            name = "Max tick delay",
            description = "Maximum ticks to wait between actions.",
            position = 2
    )
    default int maxTickDelay()
    {
        return 2;
    }

    @ConfigItem(
            keyName = "sellUnderPercent",
            name = "Sell under (%)",
            description = "Percent under guide price to list sell offers (5% increments up to 25%).",
            position = 3
    )
    default SellUnderPercent sellUnderPercent()
    {
        return SellUnderPercent.FIVE;
    }


    enum SellUnderPercent
    {
        FIVE(-1, "5%"),
        TEN(-2, "10%"),
        FIFTEEN(-3, "15%"),
        TWENTY(-4, "20%"),
        TWENTY_FIVE(-5, "25%");

        private final int fivePercentTicks;
        private final String displayName;

        SellUnderPercent(int fivePercentTicks, String displayName)
        {
            this.fivePercentTicks = fivePercentTicks;
            this.displayName = displayName;
        }

        public int getFivePercentTicks()
        {
            return fivePercentTicks;
        }

        @Override
        public String toString()
        {
            return displayName;
        }
    }
}
