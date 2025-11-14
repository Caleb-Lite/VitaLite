package com.tonic.plugins.debugger;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import java.util.List;

/**
 * Formats menu action data into formatted query code snippets
 * using VitaLite's query API.
 */
public class QueryFormatter
{
    @Inject
    private Client client;

    /**
     * Formats menu action data into query code based on entity type
     */
    public String format(String type, String name, int id, String option, MenuAction menuAction,
                        WorldPoint worldPoint, Widget widget, List<String> availableActions)
    {
        StringBuilder sb = new StringBuilder();

        // Add header comment
        sb.append("// Clicked: \"").append(option).append("\" on \"").append(name != null ? name : "null").append("\"\n");
        sb.append("// Type: ").append(type);
        if (id != -1)
        {
            sb.append(" (ID: ").append(id).append(")");
        }
        sb.append("\n");

        if (worldPoint != null)
        {
            sb.append("// Location: ").append(formatWorldPoint(worldPoint)).append("\n");
        }

        sb.append("\n");

        // Format query based on type
        switch (type)
        {
            case "Actor":
                if (isPlayerAction(menuAction))
                {
                    sb.append(formatPlayerQuery(name, option, worldPoint));
                }
                else
                {
                    sb.append(formatNpcQuery(name, id, option, worldPoint, availableActions));
                }
                break;

            case "TileObject":
                sb.append(formatTileObjectQuery(name, id, option, worldPoint, availableActions));
                break;

            case "TileItem":
                sb.append(formatTileItemQuery(name, id, option, worldPoint));
                break;

            case "WIDGET":
            case "CC_OP":
                sb.append(formatWidgetQuery(name, widget, option));
                break;

            case "WIDGET_TARGET":
                sb.append(formatWidgetTargetQuery(name, widget, option));
                break;

            default:
                sb.append("// Unsupported type for query generation: ").append(type).append("\n");
                sb.append("// MenuAction: ").append(menuAction.name());
                break;
        }

        return sb.toString();
    }

    /**
     * Formats an NPC query
     */
    private String formatNpcQuery(String name, int id, String action, WorldPoint location, List<String> availableActions)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("NpcEx npc = NpcAPI.search()\n");

        // Add name filter if available
        if (name != null && !name.isEmpty())
        {
            sb.append("    .withName(\"").append(escapeString(name)).append("\")\n");
        }

        // Add ID filter if valid
        if (id != -1)
        {
            sb.append("    .withIds(").append(id).append(")\n");
        }

        // Add action filter if available
        if (action != null && !action.isEmpty() && availableActions != null && availableActions.contains(action))
        {
            sb.append("    .withAction(\"").append(escapeString(action)).append("\")\n");
        }

        sb.append("    .first();\n");

        // Add null check with interaction
        if (action != null && !action.isEmpty())
        {
            sb.append("if (npc != null)\n");
            sb.append("{\n");
            sb.append("    NpcAPI.interact(npc, \"").append(escapeString(action)).append("\");\n");
            sb.append("}");
        }

        return sb.toString();
    }

    /**
     * Formats a Player query
     */
    private String formatPlayerQuery(String name, String action, WorldPoint location)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("PlayerEx player = PlayerAPI.search()\n");

        // Add name filter
        if (name != null && !name.isEmpty())
        {
            sb.append("    .withName(\"").append(escapeString(name)).append("\")\n");
        }

        sb.append("    .first();\n");

        // Add null check with interaction
        if (action != null && !action.isEmpty())
        {
            sb.append("if (player != null)\n");
            sb.append("{\n");
            sb.append("    PlayerAPI.interact(player, \"").append(escapeString(action)).append("\");\n");
            sb.append("}");
        }

        return sb.toString();
    }

    /**
     * Formats a TileObject query
     */
    private String formatTileObjectQuery(String name, int id, String action, WorldPoint location, List<String> availableActions)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("TileObjectEx obj = TileObjectAPI.search()\n");

        // Add name filter if available
        if (name != null && !name.isEmpty())
        {
            sb.append("    .withName(\"").append(escapeString(name)).append("\")\n");
        }

        // Add ID filter if valid
        if (id != -1)
        {
            sb.append("    .withId(").append(id).append(")\n");
        }

        // Add location filter if available
        if (location != null)
        {
            sb.append("    .atLocation(").append(formatWorldPoint(location)).append(")\n");
        }

        // Add action filter if available
        if (action != null && !action.isEmpty() && availableActions != null && availableActions.contains(action))
        {
            sb.append("    .withAction(\"").append(escapeString(action)).append("\")\n");
        }

        sb.append("    .first();\n");

        // Add null check with interaction
        if (action != null && !action.isEmpty())
        {
            sb.append("if (obj != null)\n");
            sb.append("{\n");
            sb.append("    TileObjectAPI.interact(obj, \"").append(escapeString(action)).append("\");\n");
            sb.append("}");
        }

        return sb.toString();
    }

    /**
     * Formats a TileItem query
     */
    private String formatTileItemQuery(String name, int id, String action, WorldPoint location)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("TileItemEx item = TileItemAPI.search()\n");

        // Add name filter if available
        if (name != null && !name.isEmpty())
        {
            sb.append("    .withName(\"").append(escapeString(name)).append("\")\n");
        }

        // Add ID filter if valid
        if (id != -1)
        {
            sb.append("    .withId(").append(id).append(")\n");
        }

        // Add location filter if available
        if (location != null)
        {
            sb.append("    .atLocation(").append(formatWorldPoint(location)).append(")\n");
        }

        sb.append("    .first();\n");

        // Add null check with interaction
        if (action != null && !action.isEmpty())
        {
            sb.append("if (item != null)\n");
            sb.append("{\n");
            sb.append("    TileItemAPI.interact(item, \"").append(escapeString(action)).append("\");\n");
            sb.append("}");
        }

        return sb.toString();
    }

    /**
     * Formats a Widget query
     */
    private String formatWidgetQuery(String name, Widget widget, String action)
    {
        StringBuilder sb = new StringBuilder();

        // Check if this is an inventory/bank item widget
        if (widget != null && isInventoryWidget(widget))
        {
            return formatInventoryQuery(name, widget, action);
        }

        sb.append("Widget w = WidgetAPI.search()\n");
        sb.append("    .isVisible()\n");

        // Determine the best text to use for filtering
        String filterText = null;
        if (widget != null)
        {
            // Prioritize getName() over getText()
            String widgetName = widget.getName();
            String widgetText = widget.getText();

            if (widgetName != null && !widgetName.isEmpty())
            {
                filterText = widgetName;
            }
            else if (widgetText != null && !widgetText.isEmpty())
            {
                filterText = widgetText;
            }
        }

        // Fall back to menu target name if widget has no name/text
        if (filterText == null && name != null && !name.isEmpty())
        {
            filterText = name;
        }

        // Add text filter if we have text to filter by
        if (filterText != null)
        {
            if (filterText.length() > 50)
            {
                // Use contains for long text
                String shortText = filterText.substring(0, 30).trim();
                sb.append("    .withTextContains(\"").append(escapeString(shortText)).append("\")\n");
            }
            else
            {
                sb.append("    .withText(\"").append(escapeString(filterText)).append("\")\n");
            }
        }

        // Add item ID filter if widget has an item
        if (widget != null && widget.getItemId() != -1)
        {
            sb.append("    .withItemId(").append(widget.getItemId()).append(")\n");
        }

        // Add action filter if available and different from the text filter
        // This avoids redundant filtering when action text == widget text
        if (action != null && !action.isEmpty() && !action.equals(filterText))
        {
            sb.append("    .withActions(\"").append(escapeString(action)).append("\")\n");
        }

        sb.append("    .first();\n");

        // Add null check with interaction
        if (action != null && !action.isEmpty())
        {
            sb.append("if (w != null)\n");
            sb.append("{\n");
            sb.append("    WidgetAPI.interact(w, \"").append(escapeString(action)).append("\");\n");
            sb.append("}");
        }

        return sb.toString();
    }

    /**
     * Formats a Widget Target query (item on item, spell on item, etc.)
     */
    private String formatWidgetTargetQuery(String name, Widget widget, String action)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("// Widget Target Action (use item/spell on target)\n");
        sb.append("// Source widget should be selected first, then target\n\n");

        sb.append(formatWidgetQuery(name, widget, action));

        return sb.toString();
    }

    /**
     * Formats an Inventory item query
     */
    private String formatInventoryQuery(String name, Widget widget, String action)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("ItemEx item = InventoryAPI.search()\n");

        // Add name filter if available
        if (name != null && !name.isEmpty())
        {
            sb.append("    .withName(\"").append(escapeString(name)).append("\")\n");
        }

        // Add ID filter if widget has item ID
        if (widget != null && widget.getItemId() != -1)
        {
            sb.append("    .withId(").append(widget.getItemId()).append(")\n");
        }

        sb.append("    .first();\n");

        // Add null check with interaction
        if (action != null && !action.isEmpty())
        {
            sb.append("if (item != null)\n");
            sb.append("{\n");
            sb.append("    InventoryAPI.interact(item, \"").append(escapeString(action)).append("\");\n");
            sb.append("}");
        }

        return sb.toString();
    }

    /**
     * Checks if a widget is an inventory-related widget
     */
    private boolean isInventoryWidget(Widget widget)
    {
        if (widget == null)
        {
            return false;
        }

        int groupId = widget.getId() >> 16; // Extract group ID

        // Common inventory-related interface groups
        // 149 = Inventory, 15 = Bank, 213 = Deposit Box, 467 = GE, etc.
        return groupId == 149 || // Inventory
               groupId == 15 ||  // Bank
               groupId == 213 || // Deposit box
               groupId == 162 || // Bank inventory
               groupId == 12 ||  // Shop (player inventory)
               groupId == 238;   // Equipment inventory
    }

    /**
     * Checks if the menu action is for a player (not NPC)
     */
    private boolean isPlayerAction(MenuAction menuAction)
    {
        return menuAction != null && menuAction.name().contains("PLAYER");
    }

    /**
     * Formats a WorldPoint as code
     */
    private String formatWorldPoint(WorldPoint wp)
    {
        return String.format("new WorldPoint(%d, %d, %d)", wp.getX(), wp.getY(), wp.getPlane());
    }

    /**
     * Escapes special characters in strings for code generation
     */
    private String escapeString(String str)
    {
        if (str == null)
        {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
