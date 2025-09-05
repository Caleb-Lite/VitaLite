package com.tonic.types.magic;

import com.tonic.api.widgets.WidgetAPI;
import net.runelite.api.gameval.InterfaceID;

public interface Spell {
    int getAction();
    int getLevel();
    int getWidget();
    int getAutoCastIndex();
    boolean canCast();

    default void cast()
    {
        WidgetAPI.interact(getAction(), getWidget(), -1, -1);
    }

    default void setAutoCast()
    {
        WidgetAPI.interact(1, InterfaceID.CombatInterface.AUTOCAST_NORMAL, -1, -1);
        WidgetAPI.interact(1, InterfaceID.Autocast.SPELLS, getAutoCastIndex(), -1);
    }

    default void setDefensiveAutoCast()
    {
        WidgetAPI.interact(1, InterfaceID.CombatInterface.AUTOCAST_DEFENSIVE, -1, -1);
        WidgetAPI.interact(1, InterfaceID.Autocast.SPELLS, getAutoCastIndex(), -1);
    }
}