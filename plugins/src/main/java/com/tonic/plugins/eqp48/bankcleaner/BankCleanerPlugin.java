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
import com.tonic.data.GrandExchangeSlot;
import com.tonic.data.wrappers.TileObjectEx;
import com.tonic.plugins.bankvaluer.BankValuerUtils;
import com.tonic.queries.TileObjectQuery;
import com.tonic.util.VitaPlugin;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
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
        description = "Sells all tradeable items from your bank at 1 gp.",
        tags = {"bank", "value", "ge"}
)
public class BankCleanerPlugin extends VitaPlugin
{
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

        if (!InventoryAPI.isEmpty())
        {
            clickExchangeBooth();
            sellInventoryAtGe();
        }
        else
        {
            Logger.warn("[BankCleaner] Unable to fill inventory with top-valued items.");
        }
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
                .withId(ObjectID.EXCHANGE_BANK_WALL_EXCHANGE)
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
                .filter(item -> item.getId() != ItemID.COINS)
                .map(item -> new SellItem(item.getId(), item.getQuantity()))
                .collect(Collectors.toList());

        List<ActiveSale> activeSlots = new ArrayList<>();

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
                    humanTick();
                }
            }

            // Fill any free slots with new sell offers at 1 gp.
            while (!itemsToSell.isEmpty() && GrandExchangeAPI.freeSlot() != -1)
            {
                SellItem item = itemsToSell.get(0);
                GrandExchangeSlot slot = GrandExchangeAPI.startSellOffer(item.itemId, item.quantity, 1);
                if (slot == null)
                {
                    break;
                }

                itemsToSell.remove(0);
                activeSlots.add(new ActiveSale(slot, item.itemId, item.quantity));
                humanTick();
            }

            humanTick();
        }

        // If we're sitting in the GE with only gp left, immediately reopen the bank for the next batch.
        if (GrandExchangeAPI.isOpen() && InventoryAPI.getItems().stream()
                .filter(item -> item != null && item.getId() > 0)
                .allMatch(item -> item.getId() == ItemID.COINS))
        {
            BankBuilder.get().open().build().execute();
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

        private SellItem(int itemId, int quantity)
        {
            this.itemId = itemId;
            this.quantity = quantity;
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
