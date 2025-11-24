package com.tonic.plugins.eqp48.debugger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PluginDescriptor(
        name = "# Debugger",
        description = "Logs actions and copies them to clipboard automatically",
        tags = {"debugger", "developer"}
)
@Slf4j
public class DebuggerPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private DebuggerConfig config;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final List<ChatMessageData> recentChatMessages = new ArrayList<>();
    private static final long CHAT_MESSAGE_BUFFER_MS = 3000; // Keep messages for 3 seconds

    @Provides
    DebuggerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DebuggerConfig.class);
    }

    @Override
    protected void startUp()
    {
        log.info("[Menu Debugger] Plugin started");
    }

    @Override
    protected void shutDown()
    {
        log.info("[Menu Debugger] Plugin stopped");
        recentChatMessages.clear();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        // Store chat message with timestamp
        ChatMessageData chatData = new ChatMessageData(
                event.getType(),
                event.getMessage(),
                System.currentTimeMillis()
        );

        synchronized (recentChatMessages)
        {
            recentChatMessages.add(chatData);

            // Clean up old messages
            long now = System.currentTimeMillis();
            recentChatMessages.removeIf(msg -> now - msg.timestamp > CHAT_MESSAGE_BUFFER_MS);
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        MenuAction menuAction = event.getMenuAction();

        // Filter out actions based on config
        if (config.ignoreCancel() && menuAction == MenuAction.CANCEL)
        {
            return;
        }

        if (config.ignoreWalk() && menuAction == MenuAction.WALK)
        {
            return;
        }

        String type = extractType(menuAction);
        String target = event.getMenuTarget();
        String name = cleanTarget(target);
        int rawId = event.getId();
        int id = resolveEntityId(menuAction, rawId);
        String option = event.getMenuOption();

        // Get widget information if available
        Widget widget = resolveWidget(event);
        Widget selectedWidget = null;

        // For WIDGET_TARGET actions, get the selected widget (source item)
        if (menuAction.name().contains("WIDGET_TARGET"))
        {
            selectedWidget = client.getSelectedWidget();
        }

        // Gather simplified data
        MenuActionData data = new MenuActionData();
        data.type = type;
        data.name = name;
        data.id = id;
        data.option = option;
        if (menuAction.name().contains("NPC") || menuAction.name().contains("PLAYER"))
        {
            data.actorIndex = rawId;
        }
        data.menuAction = menuAction;
        data.widget = widget;
        data.selectedWidget = selectedWidget;
        data.param0 = event.getParam0();
        data.param1 = event.getParam1();

        // Location data - always include for world interactions
        data.worldPoint = getClickedLocation(event, menuAction);
        if (data.worldPoint != null)
        {
            data.regionId = data.worldPoint.getRegionID();
        }

        // Available actions for Actors and TileObjects
        if (type.equals("Actor") || type.equals("TileObject") || type.equals("TileItem"))
        {
            data.availableOptions = getAllAvailableActions(event, menuAction);
        }

        // Current inventory (optional)
        if (config.includeInventory())
        {
            data.inventory = getInventorySnapshot();
        }

        // Current equipment (optional)
        if (config.includeEquipment())
        {
            data.equipment = getEquipmentSnapshot();
        }

        // Chat messages (optional)
        if (config.includeChatMessages())
        {
            synchronized (recentChatMessages)
            {
                if (!recentChatMessages.isEmpty())
                {
                    data.chatMessages = new ArrayList<>(recentChatMessages);
                }
            }
        }

        // Format the output as JSON
        String output = formatAsJson(data);

        // Log to console if enabled
        if (config.logToConsole())
        {
            log.info("\n{}", output);
        }

        // Copy to clipboard if enabled
        if (config.autoCopy())
        {
            copyToClipboard(output);
        }

        // Consume the click to prevent the action from executing
        if (config.consumeClicks())
        {
            event.consume();
        }
    }

    /**
     * Extracts the simplified type from the MenuAction enum.
     * - NPCs and Players -> Actor
     * - Game objects, wall objects, decorative objects, ground objects -> TileObject
     * - Ground items -> TileItem
     */
    private String extractType(MenuAction menuAction)
    {
        String actionName = menuAction.name();

        // Actor: NPCs and Players
        if (actionName.contains("NPC") || actionName.contains("PLAYER"))
        {
            return "Actor";
        }
        // TileObject: All tile-based objects
        else if (actionName.contains("GAME_OBJECT"))
        {
            return "TileObject";
        }
        // TileItem: Ground items
        else if (actionName.contains("GROUND_ITEM"))
        {
            return "TileItem";
        }
        // Keep other types as-is for widgets, walk, etc.
        else if (actionName.contains("WIDGET") && !actionName.contains("TARGET"))
        {
            return "WIDGET";
        }
        else if (actionName.contains("WIDGET_TARGET"))
        {
            return "WIDGET_TARGET";
        }
        else if (actionName.equals("WALK"))
        {
            return "WALK";
        }
        else if (actionName.equals("CANCEL"))
        {
            return "CANCEL";
        }
        else if (actionName.contains("EXAMINE"))
        {
            return "EXAMINE";
        }
        else if (actionName.contains("CC_OP"))
        {
            return "CC_OP";
        }
        else if (actionName.contains("RUNELITE"))
        {
            return "RUNELITE";
        }

        // Default to the full action name if no pattern matches
        return actionName;
    }

    /**
     * Cleans the target string by removing color tags and other formatting.
     */
    private String cleanTarget(String target)
    {
        if (target == null || target.isEmpty())
        {
            return "";
        }

        // Remove color tags (e.g., <col=ffffff>Name</col>)
        String cleaned = target.replaceAll("<col=[0-9a-fA-F]+>", "").replaceAll("</col>", "");

        // Remove other common tags
        cleaned = cleaned.replaceAll("<.*?>", "");

        return cleaned.trim();
    }


    /**
     * Gets all available actions for Actors (NPCs/Players), TileObjects, and TileItems
     */
    private List<String> getAllAvailableActions(MenuOptionClicked event, MenuAction menuAction)
    {
        List<String> actions = new ArrayList<>();
        int id = event.getId();

        try
        {
            String actionName = menuAction.name();

            // Actor: NPCs
            if (actionName.contains("NPC"))
            {
                // Find NPC by index
                NPC npc = findNpcByIndex(id);
                if (npc != null && npc.getComposition() != null)
                {
                    String[] npcActions = npc.getComposition().getActions();
                    if (npcActions != null)
                    {
                        for (String action : npcActions)
                        {
                            if (action != null && !action.isEmpty())
                            {
                                actions.add(action);
                            }
                        }
                    }
                }
            }
            // Actor: Players
            else if (actionName.contains("PLAYER"))
            {
                // Players have standard actions - could be expanded if needed
                // For now, just return empty list or add standard player actions
            }
            // TileObject: Game objects
            else if (actionName.contains("GAME_OBJECT"))
            {
                ObjectComposition comp = client.getObjectDefinition(id);
                if (comp != null)
                {
                    String[] objActions = comp.getActions();
                    if (objActions != null)
                    {
                        for (String action : objActions)
                        {
                            if (action != null && !action.isEmpty())
                            {
                                actions.add(action);
                            }
                        }
                    }
                }
            }
            // TileItem: Ground items
            else if (actionName.contains("GROUND_ITEM"))
            {
                ItemComposition itemComp = client.getItemDefinition(id);
                if (itemComp != null)
                {
                    String[] itemActions = itemComp.getInventoryActions();
                    if (itemActions != null)
                    {
                        for (String action : itemActions)
                        {
                            if (action != null && !action.isEmpty())
                            {
                                actions.add(action);
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            // Silent fail - this is expected in some cases
        }

        return actions;
    }

    /**
     * Gets the WorldPoint of the clicked object/NPC/tile
     */
    private WorldPoint getClickedLocation(MenuOptionClicked event, MenuAction menuAction)
    {
        try
        {
            String actionName = menuAction.name();

            // For NPCs, get the NPC's location
            if (actionName.contains("NPC"))
            {
                int id = event.getId();
                NPC npc = findNpcByIndex(id);
                if (npc != null)
                {
                    return npc.getWorldLocation();
                }
            }
            // For game objects, wall objects, decorative objects, ground objects - use scene coordinates
            else if (actionName.contains("GAME_OBJECT"))
            {
                int sceneX = event.getParam0();
                int sceneY = event.getParam1();
                return WorldPoint.fromScene(client, sceneX, sceneY, client.getPlane());
            }
            // For ground items
            else if (actionName.contains("GROUND_ITEM"))
            {
                int sceneX = event.getParam0();
                int sceneY = event.getParam1();
                return WorldPoint.fromScene(client, sceneX, sceneY, client.getPlane());
            }
            // For walk actions
            else if (actionName.equals("WALK"))
            {
                int sceneX = event.getParam0();
                int sceneY = event.getParam1();
                return WorldPoint.fromScene(client, sceneX, sceneY, client.getPlane());
            }
        }
        catch (Exception e)
        {
            // Silent fail - this is expected in some cases
        }

        return null;
    }

    /**
     * Gets a snapshot of the current inventory
     */
    private List<InventoryItem> getInventorySnapshot()
    {
        List<InventoryItem> items = new ArrayList<>();

        try
        {
            ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
            if (inventory != null)
            {
                Item[] inventoryItems = inventory.getItems();
                if (inventoryItems != null)
                {
                    for (int i = 0; i < inventoryItems.length; i++)
                    {
                        Item item = inventoryItems[i];
                        if (item != null && item.getId() != -1)
                        {
                            ItemComposition itemComp = client.getItemDefinition(item.getId());
                            String itemName = itemComp != null ? itemComp.getName() : "Unknown";
                            items.add(new InventoryItem(i, item.getId(), itemName, item.getQuantity()));
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            // Silent fail - this is expected in some cases
        }

        return items;
    }

    /**
     * Gets a snapshot of the current equipment
     */
    private List<EquipmentItem> getEquipmentSnapshot()
    {
        List<EquipmentItem> items = new ArrayList<>();

        try
        {
            ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
            if (equipment != null)
            {
                Item[] equipmentItems = equipment.getItems();
                if (equipmentItems != null)
                {
                    // Equipment slots are indexed by KitType
                    for (int i = 0; i < equipmentItems.length; i++)
                    {
                        Item item = equipmentItems[i];
                        if (item != null && item.getId() != -1)
                        {
                            ItemComposition itemComp = client.getItemDefinition(item.getId());
                            String itemName = itemComp != null ? itemComp.getName() : "Unknown";
                            String slotName = getEquipmentSlotName(i);
                            items.add(new EquipmentItem(i, slotName, item.getId(), itemName, item.getQuantity()));
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            // Silent fail - this is expected in some cases
        }

        return items;
    }

    /**
     * Gets the equipment slot name from the slot index
     */
    private String getEquipmentSlotName(int slot)
    {
        switch (slot)
        {
            case 0: return "Head";
            case 1: return "Cape";
            case 2: return "Neck";
            case 3: return "Weapon";
            case 4: return "Body";
            case 5: return "Shield";
            case 7: return "Legs";
            case 9: return "Hands";
            case 10: return "Feet";
            case 12: return "Ring";
            case 13: return "Ammo";
            default: return "Slot " + slot;
        }
    }


    /**
     * Formats the menu action information as JSON using simplified schema
     */
    private String formatAsJson(MenuActionData data)
    {
        Map<String, Object> json = new LinkedHashMap<>();

        json.put("type", data.type);
        if (data.actorIndex != null)
        {
            json.put("actorIndex", data.actorIndex);
        }

        // Basic info
        if (data.widget != null)
        {
            if (data.selectedWidget != null && data.menuAction.name().contains("WIDGET_TARGET"))
            {
                json.put("name", data.name); // Full menu entry like "Use Coins -> Varrock teleport"
                json.put("id", data.widget.getItemId()); // Use target item ID for WIDGET_TARGET
                json.put("sourceName", cleanTarget(data.selectedWidget.getName()));
                json.put("sourceItemId", data.selectedWidget.getItemId());
                json.put("targetName", cleanTarget(data.widget.getName()));
                json.put("targetItemId", data.widget.getItemId());
            }
            else
            {
                String widgetName = cleanTarget(data.widget.getName());
                json.put("name", widgetName.isEmpty() ? data.name : widgetName);
                int itemId = data.widget.getItemId();
                if (itemId > 0)
                {
                    json.put("itemId", itemId);
                }
                else
                {
                    json.put("id", data.id);
                }
            }
        }
        else
        {
            json.put("name", data.name);
            json.put("id", data.id);
        }

        json.put("option", data.option);
        json.put("param0", data.param0);
        json.put("param1", data.param1);

        // Widget details (always include for widget actions)
        if (data.widget != null)
        {
            Map<String, Object> widgetDetails = createWidgetDetailsMap(data.widget);
            for (Map.Entry<String, Object> entry : widgetDetails.entrySet())
            {
                // Don't overwrite already-set fields (like name and id for WIDGET_TARGET)
                if (!json.containsKey(entry.getKey()))
                {
                    json.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // Location data
        if (data.worldPoint != null)
        {
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("worldPoint", String.format("(%d, %d, %d)",
                    data.worldPoint.getX(), data.worldPoint.getY(), data.worldPoint.getPlane()));
            location.put("x", data.worldPoint.getX());
            location.put("y", data.worldPoint.getY());
            location.put("plane", data.worldPoint.getPlane());
            location.put("regionId", data.regionId);
            json.put("location", location);
        }

        // Available options (for Actors, TileObjects, TileItems)
        if (data.availableOptions != null && !data.availableOptions.isEmpty())
        {
            json.put("availableOptions", data.availableOptions);
        }

        // Inventory
        if (data.inventory != null && !data.inventory.isEmpty())
        {
            json.put("inventory", data.inventory);
        }

        // Equipment
        if (data.equipment != null && !data.equipment.isEmpty())
        {
            json.put("equipment", data.equipment);
        }

        // Chat messages
        if (data.chatMessages != null && !data.chatMessages.isEmpty())
        {
            List<Map<String, Object>> chatList = new ArrayList<>();
            for (ChatMessageData chatMsg : data.chatMessages)
            {
                Map<String, Object> msgJson = new LinkedHashMap<>();
                msgJson.put("type", chatMsg.type.name());
                msgJson.put("message", chatMsg.message);
                chatList.add(msgJson);
            }
            json.put("chatMessages", chatList);
        }

        return gson.toJson(json);
    }

    /**
     * Creates a map with widget details
     */
    private Map<String, Object> createWidgetDetailsMap(Widget widget)
    {
        Map<String, Object> details = new LinkedHashMap<>();

        int widgetId = widget.getId();
        int interfaceId = WidgetInfo.TO_GROUP(widgetId);
        int childId = WidgetInfo.TO_CHILD(widgetId);

        details.put("widget", interfaceId + ":" + childId);
        details.put("widgetId", widgetId);
        details.put("interfaceId", interfaceId);
        details.put("childId", childId);
        details.put("index", widget.getIndex());
        details.put("parentId", widget.getParentId());
        details.put("widgetType", widget.getType());
        details.put("contentType", widget.getContentType());

        String widgetName = widget.getName();
        if (widgetName != null && !widgetName.isEmpty())
        {
            details.put("name", widgetName);
        }

        String widgetText = widget.getText();
        if (widgetText != null && !widgetText.isEmpty())
        {
            details.put("text", widgetText);
        }

        int itemId = widget.getItemId();
        if (itemId > 0)
        {
            details.put("itemId", itemId);
        }

        int itemQuantity = widget.getItemQuantity();
        if (itemQuantity > 0)
        {
            details.put("itemQuantity", itemQuantity);
        }

        String[] actions = widget.getActions();
        if (actions != null)
        {
            List<String> optionsList = new ArrayList<>();
            for (String action : actions)
            {
                if (action != null && !action.isEmpty())
                {
                    optionsList.add(action);
                }
            }
            if (!optionsList.isEmpty())
            {
                details.put("options", optionsList);
            }
        }

        Widget[] children = widget.getChildren();
        if (children != null && children.length > 0)
        {
            List<Map<String, Object>> childSummaries = new ArrayList<>();
            for (Widget child : children)
            {
                if (child == null)
                {
                    continue;
                }

                Map<String, Object> childInfo = new LinkedHashMap<>();
                int childWidgetId = child.getId();
                childInfo.put("widget", WidgetInfo.TO_GROUP(childWidgetId) + ":" + WidgetInfo.TO_CHILD(childWidgetId));
                childInfo.put("widgetId", childWidgetId);
                childInfo.put("index", child.getIndex());

                String childName = child.getName();
                if (childName != null && !childName.isEmpty())
                {
                    childInfo.put("name", childName);
                }

                String childText = child.getText();
                if (childText != null && !childText.isEmpty())
                {
                    childInfo.put("text", childText);
                }

                if (child.getItemId() > 0)
                {
                    childInfo.put("itemId", child.getItemId());
                }

                String[] childActions = child.getActions();
                if (childActions != null)
                {
                    List<String> childOptions = new ArrayList<>();
                    for (String action : childActions)
                    {
                        if (action != null && !action.isEmpty())
                        {
                            childOptions.add(action);
                        }
                    }
                    if (!childOptions.isEmpty())
                    {
                        childInfo.put("options", childOptions);
                    }
                }

                childSummaries.add(childInfo);
            }

            if (!childSummaries.isEmpty())
            {
                details.put("children", childSummaries);
            }
        }

        return details;
    }


    /**
     * Copies the given text to the system clipboard.
     */
    private void copyToClipboard(String text)
    {
        SwingUtilities.invokeLater(() ->
        {
            try
            {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection selection = new StringSelection(text);
                clipboard.setContents(selection, selection);
            }
            catch (Exception e)
            {
                log.error("[Menu Debugger] Failed to copy to clipboard", e);
            }
        });
    }

    /**
     * Data class to hold simplified menu action information
     */
    private static class MenuActionData
    {
        String type;
        String name;
        int id;
        Integer actorIndex;
        String option;
        MenuAction menuAction;
        Widget widget;
        Widget selectedWidget;
        WorldPoint worldPoint;
        Integer regionId;
        List<String> availableOptions;
        List<InventoryItem> inventory;
        List<EquipmentItem> equipment;
        List<ChatMessageData> chatMessages;
        int param0;
        int param1;
    }


    /**
     * Data class for chat messages
     */
    private static class ChatMessageData
    {
        final ChatMessageType type;
        final String message;
        final long timestamp;

        ChatMessageData(ChatMessageType type, String message, long timestamp)
        {
            this.type = type;
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    private int resolveEntityId(MenuAction menuAction, int rawId)
    {
        String actionName = menuAction.name();

        if (actionName.contains("NPC"))
        {
            NPC npc = findNpcByIndex(rawId);
            if (npc != null)
            {
                NPCComposition composition = npc.getTransformedComposition();
                if (composition == null)
                {
                    composition = npc.getComposition();
                }
                if (composition != null)
                {
                    return composition.getId();
                }
            }
        }

        return rawId;
    }

    private NPC findNpcByIndex(int index)
    {
        if (client.getTopLevelWorldView() != null && client.getTopLevelWorldView().npcs() != null)
        {
            NPC npc = client.getTopLevelWorldView().npcs().byIndex(index);
            if (npc != null)
            {
                return npc;
            }
        }

        for (NPC npc : client.getNpcs())
        {
            if (npc != null && npc.getIndex() == index)
            {
                return npc;
            }
        }

        return null;
    }

    private Widget resolveWidget(MenuOptionClicked event)
    {
        Widget widget = event.getWidget();
        if (widget != null)
        {
            return widget;
        }

        int widgetId = event.getParam1();
        if (widgetId <= 0)
        {
            return null;
        }

        int groupId = WidgetInfo.TO_GROUP(widgetId);
        int childId = WidgetInfo.TO_CHILD(widgetId);

        Widget resolved = client.getWidget(groupId, childId);
        if (resolved != null)
        {
            return resolved;
        }

        Widget groupRoot = client.getWidget(groupId, 0);
        if (groupRoot != null && groupRoot.getChildren() != null)
        {
            for (Widget child : groupRoot.getChildren())
            {
                if (child != null && child.getId() == widgetId)
                {
                    return child;
                }
            }
        }

        return null;
    }

    /**
     * Data class for inventory items
     */
    private static class InventoryItem
    {
        final int slot;
        final int id;
        final String name;
        final int quantity;

        InventoryItem(int slot, int id, String name, int quantity)
        {
            this.slot = slot;
            this.id = id;
            this.name = name;
            this.quantity = quantity;
        }
    }

    /**
     * Data class for equipment items
     */
    private static class EquipmentItem
    {
        final int slot;
        final String slotName;
        final int id;
        final String name;
        final int quantity;

        EquipmentItem(int slot, String slotName, int id, String name, int quantity)
        {
            this.slot = slot;
            this.slotName = slotName;
            this.id = id;
            this.name = name;
            this.quantity = quantity;
        }
    }

}
