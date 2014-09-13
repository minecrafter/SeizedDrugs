package com.imaginarycode.plugins.seizeddrugs;

import org.bukkit.OfflinePlayer;

public class SeizedDrugsAPI {
    private static SeizedDrugs plugin;

    public SeizedDrugsAPI(SeizedDrugs pl) {
        plugin = pl;
    }

    /**
     * Given a player name, return the current health value of the player in beatdown mode.
     *
     * @param player the player
     * @return health value as a Integer
     */

    public int getBeatdownHealth(OfflinePlayer player) {
        return plugin.getBeatdownHealth(player.getName());
    }

    /**
     * Set a player's beatdown health. This function can be used to give bluffs, for example.
     * This is not affected by the max beatdown health value.
     *
     * @param player the player
     * @param health an Integer
     */
    public void setBeatdownHealth(OfflinePlayer player, int health) {
        plugin.setBeatdownHealth(player.getName(), health);
    }

    /**
     * Given a cop's name, return how many incorrectly-performed seizures they have performed.
     * This function could be used to inflict other punishments that are more than the vanilla jailing.
     *
     * @param player the cop
     * @return the times they have incorrectly caught people
     */
    public int getCopIncorrectSeizure(OfflinePlayer player) {
        return plugin.getCopIncorrectSeizure(player);
    }
}
