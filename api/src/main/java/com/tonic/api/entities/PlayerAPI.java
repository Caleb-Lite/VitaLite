package com.tonic.api.entities;

import com.tonic.Static;
import com.tonic.api.TClient;
import net.runelite.api.Client;
import net.runelite.api.Player;

/**
 * Player API
 */
public class PlayerAPI
{
    /**
     * interact with a player by option number
     * @param player player
     * @param option option number
     */
    public static void interact(Player player, int option)
    {
        interact(player.getId(), option);
    }

    /**
     * interactShop with a player by option string
     * @param player player
     * @param option option string
     */
    public static void interact(Player player, String option)
    {
        Client client = Static.getClient();
        String[] actions = Static.invoke(client::getPlayerOptions);
        for(int i = 0; i < actions.length; i++)
        {
            if(actions[i].equalsIgnoreCase(option.toLowerCase()))
            {
                interact(player, i);
            }
        }
    }

    /**
     * interact with a player by option number
     * @param index player index
     * @param option option number
     */
    public static void interact(int index, int option)
    {
        TClient client = Static.getClient();
        Static.invoke(() ->
        {
            client.getPacketWriter().clickPacket(0, -1, -1);
            client.getPacketWriter().playerActionPacket(option, index, false);
        });
    }

    /**
     * check if a player is idle
     * @param player player
     * @return true if idle
     */
    public static boolean isIdle(Player player)
    {
        return (player.getIdlePoseAnimation() == player.getPoseAnimation() && player.getAnimation() == -1);
    }
}
