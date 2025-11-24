package com.tonic.plugins.eqp48.bankcleaner;

import com.google.inject.Provides;
import com.tonic.Logger;
import com.tonic.api.entities.TileObjectAPI;
import com.tonic.api.handlers.BankBuilder;
import com.tonic.api.handlers.GrandExchangeHandler;
import com.tonic.api.threaded.Delays;
import com.tonic.api.widgets.BankAPI;
import com.tonic.api.widgets.GrandExchangeAPI;
import com.tonic.api.widgets.InventoryAPI;
import com.tonic.data.wrappers.TileObjectEx;
import com.tonic.data.wrappers.ItemEx;
import com.tonic.data.GrandExchangeSlot;
import com.tonic.plugins.bankvaluer.BankValuerUtils;
import com.tonic.queries.TileObjectQuery;
import com.tonic.util.VitaPlugin;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

@PluginDescriptor(
        name = "# Bank Cleaner",
        description = "Withdraws the 28 highest value bank items and opens the GE booth.",
        tags = {"bank", "value", "ge"}
)
public class BankCleanerPlugin extends VitaPlugin
{
    private static final int GE_BOOTH_ID = 10061;

    @Inject
    private BankCleanerConfig config;
    private boolean finished;

    @Provides
    BankCleanerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BankCleanerConfig.class);
    }

    @Override
    protected void startUp()
    {
        finished = false;
    }

    @Override
    protected void shutDown()
    {
        finished = true;
        // Stop any in-flight loop task when plugin is toggled off.
        haltLoop(() -> finished = true);
    }

    @Override
    public void loop() throws Exception
    {
        if (finished)
        {
            return;
        }

        // Ensure the bank is open before any actions.
        if (!BankAPI.isOpen())
        {
            BankBuilder.get().open().build().execute();
            humanTick();
        }

        if (!BankAPI.isOpen())
        {
            return;
        }

        // Clear any carried items to make space for withdrawals.
        if (!InventoryAPI.isEmpty())
        {
            BankAPI.depositAll();
            humanTick();
        }

        List<Integer> topItemIds = getTopItemIds();
        if (topItemIds.isEmpty())
        {
            finished = true;
            return;
        }

        // Withdraw all of each top-valued item until inventory is full.
        for (int itemId : topItemIds)
        {
            if (InventoryAPI.isFull())
            {
                break;
            }

            BankAPI.withdraw(itemId, -1, true);
            humanTick();
        }

        if (InventoryAPI.isFull())
        {
            clickExchangeBooth();
            sellInventoryAtGe();
        }
        else
        {
            Logger.warn("[BankCleaner] Unable to fill inventory with top-valued items.");
        }

        finished = true;
    }

    private List<Integer> getTopItemIds()
    {
        Map<Integer, Long> topItems = BankValuerUtils.getTopItems(28, true);
        if (topItems == null || topItems.isEmpty())
        {
            return List.of();
        }

        return topItems.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(28)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private void clickExchangeBooth()
    {
        TileObjectEx booth = new TileObjectQuery()
                .withId(GE_BOOTH_ID)
                .sortNearest()
                .first();

        if (booth == null)
        {
            Logger.warn("[BankCleaner] Unable to find the Grand Exchange booth to click.");
            return;
        }

        // Interact with the booth using the Exchange option.
        TileObjectAPI.interact(booth, "Exchange");
    }

    private void sellInventoryAtGe()
    {
        if (!ensureGrandExchangeOpen())
        {
            Logger.warn("[BankCleaner] Unable to open Grand Exchange to sell items.");
            return;
        }

        List<SellItem> itemsToSell = InventoryAPI.getItems().stream()
                .filter(item -> item != null && item.getId() > 0)
                .filter(item -> item.getId() != net.runelite.api.gameval.ItemID.COINS && item.getId() != net.runelite.api.gameval.ItemID.PLATINUM)
                .map(item -> new SellItem(item.getId(), item.getQuantity(), false))
                .collect(Collectors.toList());

        List<ActiveSale> activeSlots = new ArrayList<>();
        int baseTicks = config.sellUnderPercent().getFivePercentTicks();
        int relistTicks = baseTicks - 2; // Additional 10% under for relists.

        while (!itemsToSell.isEmpty() || !activeSlots.isEmpty())
        {
            if (!ensureGrandExchangeOpen())
            {
                Logger.warn("[BankCleaner] Grand Exchange closed unexpectedly.");
                return;
            }

            // Collect any completed offers first to free slots for new ones.
            Iterator<ActiveSale> iterator = activeSlots.iterator();
            while (iterator.hasNext())
            {
                ActiveSale sale = iterator.next();
                if (sale.slot.isDone())
                {
                    GrandExchangeAPI.collectAll();
                    iterator.remove();
                }
            }

            // Cancel remaining open offers and queue for relist with deeper undercut.
            if (!activeSlots.isEmpty())
            {
                List<SellItem> relistQueue = new ArrayList<>();
                iterator = activeSlots.iterator();
                while (iterator.hasNext())
                {
                    ActiveSale sale = iterator.next();
                    GrandExchangeAPI.cancel(sale.slot);
                    humanTick();
                    relistQueue.add(new SellItem(sale.itemId, sale.quantity, true));
                    iterator.remove();
                    humanTick();
                }
                GrandExchangeAPI.collectAll();
                humanTick();
                itemsToSell.addAll(relistQueue);
            }

            // Fill any free slots with new sell offers, reducing price by 10% (-2 ticks of 5%).
            while (!itemsToSell.isEmpty())
            {
                int slotNumber = GrandExchangeAPI.freeSlot();
                if (slotNumber == -1)
                {
                    break;
                }

                SellItem item = itemsToSell.remove(0);
                GrandExchangeSlot slot = GrandExchangeSlot.getBySlot(slotNumber);
                if (slot == null)
                {
                    continue;
                }

                int fivePercentTicks = item.relisted ? relistTicks : baseTicks;
                GrandExchangeAPI.startSellOfferPercentage(item.itemId, item.quantity, fivePercentTicks, slotNumber);
                activeSlots.add(new ActiveSale(slot, item.itemId, item.quantity));
                humanTick();
            }

            humanTick();
        }
    }

    private boolean ensureGrandExchangeOpen()
    {
        if (GrandExchangeAPI.isOpen())
        {
            return true;
        }

        GrandExchangeHandler.get().open().build().execute();
        humanTick();
        return GrandExchangeAPI.isOpen();
    }

    private void humanTick()
    {
        int delay = randomDelay();
        Delays.tick(delay);
    }

    private int randomDelay()
    {
        int min = Math.max(1, config.minTickDelay());
        int max = Math.max(min, config.maxTickDelay());
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static final class SellItem
    {
        private final int itemId;
        private final int quantity;
        private final boolean relisted;

        private SellItem(int itemId, int quantity, boolean relisted)
        {
            this.itemId = itemId;
            this.quantity = quantity;
            this.relisted = relisted;
        }
    }

    private static final class ActiveSale
    {
        private final GrandExchangeSlot slot;
        private final int itemId;
        private final int quantity;

        private ActiveSale(GrandExchangeSlot slot, int itemId, int quantity)
        {
            this.slot = slot;
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }
}
