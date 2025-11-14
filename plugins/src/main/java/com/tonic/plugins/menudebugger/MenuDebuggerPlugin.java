package com.tonic.plugins.menudebugger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Provides;
import com.tonic.Logger;
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
import java.util.*;
import java.util.List;

@PluginDescriptor(
        name = "# Menu Debugger",
        description = "Logs menu actions and copies them to clipboard automatically",
        tags = {"menu", "debug", "developer"}
)
public class MenuDebuggerPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private MenuDebuggerConfig config;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final List<ChatMessageData> recentChatMessages = new ArrayList<>();
    private static final long CHAT_MESSAGE_BUFFER_MS = 3000; // Keep messages for 3 seconds

    @Provides
    MenuDebuggerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(MenuDebuggerConfig.class);
    }

    @Override
    protected void startUp()
    {
        Logger.info("[Menu Debugger] Plugin started");
    }

    @Override
    protected void shutDown()
    {
        Logger.info("[Menu Debugger] Plugin stopped");
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
        int id = event.getId();
        String option = event.getMenuOption();

        // Get widget information if available
        Widget widget = event.getWidget();
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
        data.menuAction = menuAction;
        data.widget = widget;
        data.selectedWidget = selectedWidget;

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

        // Format the output (always use JSON)
        String output = formatAsJson(data);

        // Log to console if enabled
        if (config.logToConsole())
        {
            Logger.info("\n" + output);
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
                for (NPC npc : client.getNpcs())
                {
                    if (npc != null && npc.getIndex() == id && npc.getComposition() != null)
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
                        break;
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
                // Find NPC by index
                for (NPC npc : client.getNpcs())
                {
                    if (npc != null && npc.getIndex() == id)
                    {
                        return npc.getWorldLocation();
                    }
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

        return details;
    }


    /**
     * Formats the menu action information as text using simplified schema
     */
    private String formatAsText(MenuActionData data)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[MenuAction]\n");
        sb.append("Type: ").append(data.type).append("\n");

        // Handle widget-specific information
        if (data.widget != null)
        {
            // For WIDGET_TARGET actions (e.g., using item on item)
            if (data.selectedWidget != null && data.menuAction.name().contains("WIDGET_TARGET"))
            {
                String sourceItemName = cleanTarget(data.selectedWidget.getName());
                int sourceItemId = data.selectedWidget.getItemId();
                String targetItemName = cleanTarget(data.widget.getName());
                int targetItemId = data.widget.getItemId();

                sb.append("Name: ").append(sourceItemName).append(" -> ").append(targetItemName).append("\n");
                sb.append("Item ID: ").append(sourceItemId).append(" -> ").append(targetItemId).append("\n");
                sb.append("Action/Option: ").append(data.option.isEmpty() ? "Use" : data.option);
            }
            else
            {
                // Regular widget action
                String widgetName = cleanTarget(data.widget.getName());
                int itemId = data.widget.getItemId();

                sb.append("Name: ").append(widgetName.isEmpty() ? (data.name.isEmpty() ? "N/A" : data.name) : widgetName).append("\n");

                // Add Item ID if this is an item widget
                if (itemId > 0)
                {
                    sb.append("Item ID: ").append(itemId).append("\n");
                }
                else
                {
                    sb.append("ID: ").append(data.id).append("\n");
                }

                sb.append("Action/Option: ").append(data.option.isEmpty() ? "N/A" : data.option);
            }
        }
        else
        {
            // Non-widget action (Actor, TileObject, TileItem, etc.)
            sb.append("Name: ").append(data.name.isEmpty() ? "N/A" : data.name).append("\n");
            sb.append("ID: ").append(data.id).append("\n");
            sb.append("Action/Option: ").append(data.option.isEmpty() ? "N/A" : data.option);
        }

        // Location data
        if (data.worldPoint != null)
        {
            sb.append("\n\n[Location]");
            sb.append("\nWorld Point: (").append(data.worldPoint.getX()).append(", ").append(data.worldPoint.getY()).append(", ").append(data.worldPoint.getPlane()).append(")");
            sb.append("\nRegion ID: ").append(data.regionId);
        }

        // Available options
        if (data.availableOptions != null && !data.availableOptions.isEmpty())
        {
            sb.append("\n\n[Available Options]");
            sb.append("\n").append(data.availableOptions);
        }

        // Inventory
        if (data.inventory != null && !data.inventory.isEmpty())
        {
            sb.append("\n\n[Inventory Snapshot]");
            for (InventoryItem item : data.inventory)
            {
                sb.append("\nSlot ").append(item.slot).append(": ").append(item.name)
                        .append(" (ID: ").append(item.id).append(", Qty: ").append(item.quantity).append(")");
            }
        }

        // Equipment
        if (data.equipment != null && !data.equipment.isEmpty())
        {
            sb.append("\n\n[Equipment Snapshot]");
            for (EquipmentItem item : data.equipment)
            {
                sb.append("\n").append(item.slotName).append(": ").append(item.name)
                        .append(" (ID: ").append(item.id).append(", Qty: ").append(item.quantity).append(")");
            }
        }

        return sb.toString();
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
                Logger.error(e, "[Menu Debugger] Failed to copy to clipboard: %e");
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