package com.tonic.api.game;

import com.tonic.Static;
import com.tonic.api.widgets.WidgetAPI;
import com.tonic.types.AttackStyle;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import java.util.Arrays;

/**
 * A collection of combat related methods
 */
public class CombatAPI
{
    private static final int VENOM_THRESHOLD = 1000000;

    /**
     * Checks if the player is currently retaliating
     * @return true if the player is retaliating, false otherwise
     */
    public static boolean isRetaliating()
    {
        return VarAPI.getVarp(VarPlayerID.OPTION_NODEF) == 0;
    }

    /**
     * Toggles the player's auto-retaliate setting
     * @param bool true to enable auto-retaliate, false to disable
     */
    public static void toggleRetaliate(boolean bool)
    {
        if(!isRetaliating() && bool)
        {
            WidgetAPI.interact(1, InterfaceID.CombatInterface.RETALIATE, -1, -1);
        }
        else if(isRetaliating() && !bool)
        {
            WidgetAPI.interact(1, InterfaceID.CombatInterface.RETALIATE, -1, -1);
        }
    }

    /**
     * Checks if the player is currently poisoned
     * @return true if the player is poisoned, false otherwise
     */
    public static boolean isPoisoned()
    {
        return VarAPI.getVarp(VarPlayerID.POISON) > 0;
    }

    /**
     * Checks if the player is currently venomed
     * @return true if the player is venomed, false otherwise
     */
    public static boolean isVenomed()
    {
        return VarAPI.getVarp(VarPlayerID.POISON) >= VENOM_THRESHOLD;
    }

    /**
     * Checks if the player's special attack is enabled
     * @return true if the special attack is enabled, false otherwise
     */
    public static boolean isSpecEnabled()
    {
        return VarAPI.getVarp(VarPlayerID.SA_ATTACK) == 1;
    }

    /**
     * Gets the player's current special attack energy
     * @return the player's special attack energy (0-100)
     */
    public static int getSpecEnergy()
    {
        return VarAPI.getVarp(VarPlayerID.SA_ENERGY) / 10;
    }

    /**
     * Checks if the player has an antifire potion effect active
     * @return true if the player has an antifire potion effect active, false otherwise
     */
    public static boolean isAntifired()
    {
        return VarAPI.getVar(VarbitID.ANTIFIRE_POTION) > 0;
    }

    /**
     * Checks if the player has a super antifire potion effect active
     * @return true if the player has a super antifire potion effect active, false otherwise
     */
    public static boolean isSuperAntifired()
    {
        return VarAPI.getVar(VarbitID.SUPER_ANTIFIRE_POTION) > 0;
    }

    /**
     * Toggles the player's special attack
     */
    public static void toggleSpec()
    {
        if (isSpecEnabled())
        {
            return;
        }

        WidgetAPI.interact(1, InterfaceID.CombatInterface.SP_ATTACKBAR, -1, -1);
    }

    /**
     * Sets the player's attack style
     * @param attackStyle the attack style to set
     */
    public static void setAttackStyle(AttackStyle attackStyle)
    {
        if (attackStyle.getInterfaceId() == -1)
        {
            return;
        }

        Client client = Static.getClient();
        Widget widget = Static.invoke(() -> client.getWidget(attackStyle.getInterfaceId()));
        if (widget != null)
        {
            WidgetAPI.interact(1, attackStyle.getInterfaceId(), -1, -1);
        }
    }

    public static AttackStyle getAttackStyle()
    {
        return AttackStyle.fromIndex(VarAPI.getVarp(43));
    }
}
