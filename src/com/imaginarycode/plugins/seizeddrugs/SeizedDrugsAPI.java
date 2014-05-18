package com.imaginarycode.plugins.seizeddrugs;

public class SeizedDrugsAPI {
    private static SeizedDrugs plugin;

    public SeizedDrugsAPI(SeizedDrugs pl) {
        plugin = pl;
    }

    /**
     * Given a player name, return the current health value of the player in beatdown mode.
     *
     * @param player A player (as a string, not a Player)
     * @return health value as a Integer
     */

    public static Integer getBeatdownHealth(String player) {
        return plugin.getBeatdownHealth(player);
    }

    /**
     * Set a player's beatdown health. This function can be used to give bluffs, for example.
     * This is not affected by the max beatdown health value.
     *
     * @param player (as a String, not a Player)
     * @param health an Integer
     */
    public static void setBeatdownHealth(String player, Integer health) {
        plugin.setBeatdownHealth(player, health);
    }

    /**
     * Given a cop's name, return how many incorrectly-performed seizures they have performed.
     * This function could be used to inflict other punishments that are more than the vanilla jailing.
     *
     * @param co The cop's name (as a String, not a Player)
     * @return the times they have incorrectly caught people
     */
    public Integer getCopIncorrectSeizure(String co) {
        return plugin.getCopIncorrectSeizure(co);
    }
}
