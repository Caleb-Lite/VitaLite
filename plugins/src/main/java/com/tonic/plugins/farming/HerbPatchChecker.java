package com.tonic.plugins.farming;

import com.tonic.plugins.farming.enums.Herb;
import com.tonic.plugins.farming.enums.HerbPatch;
import com.tonic.plugins.farming.enums.PlantState;
import net.runelite.api.Client;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class to check the state of herb patches based on varbit values.
 */
public class HerbPatchChecker {

    // Special state varbit values
    private static final List<Integer> WEEDS = Arrays.asList(0, 1, 2);
    private static final List<Integer> DEAD = Arrays.asList(170, 171, 172);
    private static final int PLANT = 3;

    // Aggregate all growing varbit values from all herb types
    private static final List<Integer> GROWING = Stream.of(Herb.values())
            .flatMap(herb -> herb.getGrowing().stream())
            .collect(Collectors.toList());

    // Aggregate all diseased varbit values from all herb types
    private static final List<Integer> DISEASED = Stream.of(Herb.values())
            .flatMap(herb -> herb.getDiseased().stream())
            .collect(Collectors.toList());

    // Aggregate all harvest varbit values from all herb types
    private static final List<Integer> HARVEST = Stream.of(Herb.values())
            .flatMap(herb -> herb.getHarvest().stream())
            .collect(Collectors.toList());

    /**
     * Checks the current state of a herb patch based on its varbit value.
     *
     * @param client The game client
     * @param varbitId The varbit ID of the herb patch to check
     * @return The current PlantState of the patch
     */
    public static PlantState checkHerbPatch(Client client, int varbitId) {
        int varbitValue = client.getVarbitValue(varbitId);

        if (GROWING.contains(varbitValue)) {
            return PlantState.GROWING;
        } else if (DISEASED.contains(varbitValue)) {
            return PlantState.DISEASED;
        } else if (HARVEST.contains(varbitValue)) {
            return PlantState.HARVESTABLE;
        } else if (WEEDS.contains(varbitValue)) {
            return PlantState.WEEDS;
        } else if (DEAD.contains(varbitValue)) {
            return PlantState.DEAD;
        } else if (varbitValue == PLANT) {
            return PlantState.PLANT;
        } else {
            return PlantState.UNKNOWN;
        }
    }

    /**
     * Checks the current state of a specific herb patch location.
     *
     * @param client The game client
     * @param herbPatch The herb patch location to check
     * @return The current PlantState of the patch
     */
    public static PlantState checkHerbPatch(Client client, HerbPatch herbPatch) {
        return checkHerbPatch(client, herbPatch.getVarbit());
    }
}
