/*
 * Copyright (c) 2012, tuxed
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 	* Redistributions of source code must retain the above copyright
 * 	notice, this list of conditions and the following disclaimer.
 * 	* Redistributions in binary form must reproduce the above copyright
 * 	notice, this list of conditions and the following disclaimer in the
 * 	documentation and/or other materials provided with the distribution.
 * 	* Neither the name of tuxed nor the
 *	names of its contributors may be used to endorse or promote products
 * 	derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL TUXED BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.imaginarycode.plugins.seizeddrugs;

import com.google.common.collect.ImmutableMap;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

/**
 * @author tux
 */
public class SeizedDrugs extends JavaPlugin implements Listener {
    private enum BeatdownResult {
        HIT,
        MISS,
        BEAT
    }

    private enum Mode {
        DRUG_SEIZE,
        BEATDOWN
    }

    private Map<String, Integer> copInfo = new HashMap<>();
    private Map<String, Integer> beatdownInfo = new HashMap<>();
    private Map<String, Mode> copModes = new HashMap<>();
    private Random rnd = new Random();
    private WorldGuardPlugin wgplugin = null;
    private static SeizedDrugsAPI api = null;

    public static SeizedDrugsAPI getApi() {
        return api;
    }

    /**
     * Given a player name, return the current health value of the player in beatdown mode.
     *
     * @param player A player (as a string, not a Player)
     * @return health value as a Integer
     */

    public int getBeatdownHealth(String player) {
        if (!beatdownInfo.containsKey(player)) {
            return getConfig().getInt("beatdown-health", 20);
        }
        return beatdownInfo.get(player);
    }

    /**
     * Set a player's beatdown health. This function can be used to give bluffs, for example.
     * This is not affected by the max beatdown health value.
     *
     * @param player (as a String, not a Player)
     * @param health an Integer
     */
    public void setBeatdownHealth(String player, Integer health) {
        beatdownInfo.put(player, health);
    }

    private boolean canBeatHere(Player c) {
        List<String> regions = getConfig().getStringList("ban-arrest-regions");
        if (wgplugin != null) {
            RegionManager regionManager = wgplugin.getRegionManager(c.getWorld());
            if (regionManager != null) {
                ApplicableRegionSet set = regionManager.getApplicableRegions(c.getLocation());
                for (ProtectedRegion region : set) {
                    if (regions.contains(region.getId())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private BeatdownResult beatdown(Player caught) {
        if (!beatdownInfo.containsKey(caught.getName())) {
            beatdownInfo.put(caught.getName(), getConfig().getInt("beatdown-health", 20));
        }
        if (rnd.nextInt(100) + 1 <= getConfig().getInt("per-beat-miss", 1)) {
            return BeatdownResult.MISS;
        } else {
            Integer s = beatdownInfo.get(caught.getName()) - getConfig().getInt("per-beat-health");
            if (s <= 0) {
                beatdownInfo.remove(caught.getName());
                return BeatdownResult.BEAT;
            } else {
                beatdownInfo.put(caught.getName(), s);
                return BeatdownResult.HIT;
            }
        }
    }

    private boolean badCopHandler(Player policeman, boolean good) {
        // Bad donut eater, bad!
        if (!good) {
            if (!copInfo.containsKey(policeman.getUniqueId().toString())) {
                copInfo.put(policeman.getUniqueId().toString(), 1);
            } else {
                copInfo.put(policeman.getUniqueId().toString(), copInfo.get(policeman.getUniqueId().toString()) + 1);
            }
            return false;
        } else {
            if (copInfo.containsKey(policeman.getUniqueId().toString())) {
                copInfo.remove(policeman.getUniqueId().toString());
            }
        }
        return true;
    }

    /**
     * Seize a player's drugs.
     *
     * @return true if drugs were found and false if not, increasing/resetting cop offense
     */
    @SuppressWarnings("deprecation")
    private boolean seize(Player policeman, Player p) {
        ItemStack[] i = p.getInventory().getContents();
        int seized = 0;

        if (i.length < 1) {
            return badCopHandler(policeman, false);
        }

        // Check if we seized drugs

        for (ItemStack item : i) {
            if (item != null) {
                if (isDrug(item.getTypeId(), item.getDurability())) {
                    seized++;
                    p.getInventory().remove(item);
                    if (!getConfig().getBoolean("destroy-items")) {
                        if (policeman.getInventory().firstEmpty() == -1) {
                            if (!getConfig().getBoolean("destroy-items-if-inv-full")) {
                                p.getWorld().dropItemNaturally(policeman.getLocation(), item);
                            }
                        } else {
                            policeman.getInventory().addItem(item);
                        }
                    }
                }
            }
        }

        if (seized < getConfig().getInt("num-stacks-required-to-arrest", 1)) {
            return badCopHandler(policeman, false);
        }

        p.updateInventory();
        policeman.updateInventory();

        return badCopHandler(policeman, true);
    }

    /**
     * Given a cop's name, return how many incorrectly-performed seizures they have performed.
     * This function could be used to inflict other punishments that are more than the vanilla jailing.
     *
     * @param player The cop's name (as a OfflinePlayer)
     * @return the times they have incorrectly caught people
     */
    public int getCopIncorrectSeizure(OfflinePlayer player) {
        if (copInfo.containsKey(player.getUniqueId().toString())) {
            return copInfo.get(player.getUniqueId().toString());
        } else {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new BeatdownHealRunnable(), 1200L, 1200L);
        getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                saveCopData();
            }
        }, 1200L, 1200L);
        getConfig().options().copyDefaults(true);

        if (!getConfig().contains("drugs")) {
            getLogger().info("Adding default drug configuration.");
            getConfig().set("drugs", Arrays.asList("353", "339", "372", "296", "351:1", "351:2", "351:3", "351:15", "40", "39"));
        }

        this.saveConfig();
        getServer().getPluginManager().registerEvents(this, this);

        Yaml yaml = new Yaml();

        File yamlData = new File(getDataFolder(), "data.yml");
        if (yamlData.exists()) {
            try (InputStream stream = new FileInputStream(yamlData)) {
                Map<String, Object> objectMap = yaml.loadAs(stream, Map.class);
                copInfo = (Map)objectMap.get("cop-data");
            } catch (IOException e) {
                getLogger().info("Could not load data, continuing...");
            }
        } else {
            try {
                yamlData.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not create data file!", e);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        Plugin wgTmpPlugin = getServer().getPluginManager().getPlugin("WorldGuard");
        if (wgTmpPlugin == null) {
            getLogger().info("WorldGuard not found. Region-specific features are disabled.");
        } else {
            wgplugin = (WorldGuardPlugin) wgTmpPlugin;
        }
        api = new SeizedDrugsAPI(this);
        getLogger().info("SeizedDrugs plugin enabled");
    }

    private void saveCopData() {
        try {
            Map<String, Integer> saved = Bukkit.getScheduler().callSyncMethod(this, new Callable<Map<String, Integer>>() {
                @Override
                public Map<String, Integer> call() throws Exception {
                    return ImmutableMap.copyOf(copInfo);
                }
            }).get();
            try (Writer stream = new FileWriter(new File(getDataFolder(), "data.yml"))) {
                new Yaml().dump(ImmutableMap.of("cop-data", saved), stream);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Unable to save cop info", e);
            }
        } catch (InterruptedException | ExecutionException e) {
            getLogger().log(Level.SEVERE, "Unable to save cop info", e);
        }
    }

    @Override
    public void onDisable() {
        saveCopData();
        wgplugin = null;
        copInfo = null;
        copModes = null;
        beatdownInfo = null;
        rnd = null;
    }

    private boolean isDrug(int it, int id) {
        String b;
        if (id == 0) {
            b = Integer.toString(it);
        } else {
            b = it + ":" + id;
        }

        List<String> allowed = getConfig().getStringList("drugs");

        return allowed.contains("drugs." + b) || allowed.contains("drugs." + it + ":*");
    }

    private boolean canUseMode(Player user, Mode m) {
        // Configuration
        if (getConfig().getBoolean("beatdown-only", false) && m == Mode.DRUG_SEIZE) {
            return false;
        }
        if (getConfig().getBoolean("seize-only", false) && m == Mode.BEATDOWN) {
            return false;
        }
        // Permissions
        return !(m == Mode.DRUG_SEIZE && !user.hasPermission("seizeddrugs.use.seize")) && !(m == Mode.BEATDOWN && !user.hasPermission("seizeddrugs.use.beatdown"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Case 1: Police and rogue admins
        if (!sender.hasPermission("seizeddrugs.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("No arguments provided!");
            if (sender.hasPermission("seizeddrugs.admin")) {
                sender.sendMessage("/police check <name>: Check player status");
                sender.sendMessage("/police reload: Reload plugin");
                sender.sendMessage("/police reset: Reset cop data");
                sender.sendMessage("/police beatreset: Reset beatdown health for all players");
                sender.sendMessage("/police setjail: Set the jail for jailed players");
            }
            if (sender instanceof Player) {
                sender.sendMessage("/police mode: Switch from beatdown mode to drug seize mode and vice versa");
            }
            return true;
        }

        if ("mode".equals(args[0]) && sender instanceof Player) {
            if (!copModes.containsKey(sender.getName())) {
                if (getConfig().getBoolean("beatdown-only", false)) {
                    copModes.put(sender.getName(), Mode.BEATDOWN);
                } else {
                    copModes.put(sender.getName(), Mode.DRUG_SEIZE);
                }
            }
            Mode s = copModes.get(sender.getName());
            switch (s) {
                case BEATDOWN:
                    if (canUseMode((Player) sender, Mode.DRUG_SEIZE)) {
                        copModes.remove(sender.getName());
                        copModes.put(sender.getName(), Mode.DRUG_SEIZE);
                        sender.sendMessage(ChatColor.GOLD + "Changed to drug seizing mode.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "You are restricted from changing to another mode.");
                    }
                    break;
                case DRUG_SEIZE:
                    if (canUseMode((Player) sender, Mode.BEATDOWN)) {
                        copModes.remove(sender.getName());
                        copModes.put(sender.getName(), Mode.BEATDOWN);
                        sender.sendMessage(ChatColor.GOLD + "Changed to beatdown mode.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "You are restricted from changing to another mode.");
                    }
                    break;
                default:
                    break;
            }
        }
        // Case 2: Admin
        if (!sender.hasPermission("seizeddrugs.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if ("reload".equals(args[0])) {
            this.reloadConfig();
            sender.sendMessage("Plugin configuration reloaded!");
        }
        if ("check".equals(args[0])) {
            if (args.length < 2) {
                sender.sendMessage("You must specify the player to check.");
                return true;
            }
            sender.sendMessage("Incorrect seizures for " + args[1] + ": " + this.getCopIncorrectSeizure(Bukkit.getOfflinePlayer(args[1])));
        }
        if ("reset".equals(args[0])) {
            copInfo.clear();
            sender.sendMessage("All cop statuses cleared!");
        }
        if ("beatreset".equals(args[0])) {
            this.beatdownInfo.clear();
            sender.sendMessage("Beatdown health restored for all players.");
        }
        if ("setjail".equals(args[0]) && sender instanceof Player) {
            Player s = (Player) sender;
            getConfig().set("jail-location.world", s.getLocation().getWorld().getName());
            getConfig().set("jail-location.x", s.getLocation().getX());
            getConfig().set("jail-location.y", s.getLocation().getY());
            getConfig().set("jail-location.z", s.getLocation().getZ());
            getConfig().set("jail-location.yaw", s.getLocation().getYaw());
            getConfig().set("jail-location.pitch", s.getLocation().getPitch());
            sender.sendMessage("Jail location set to your current location.");
        }
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent evt) {
        if (evt.getPlayer().hasPermission("seizeddrugs.use")) {
            if (!copModes.containsKey(evt.getPlayer().getName())) {
                if (getConfig().getBoolean("beatdown-only", false)) {
                    copModes.put(evt.getPlayer().getName(), Mode.BEATDOWN);
                } else {
                    copModes.put(evt.getPlayer().getName(), Mode.DRUG_SEIZE);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent evt) {
        if (evt.isCancelled() && !getConfig().getBoolean("ignore-pvp-restrictions")) {
            return;
        }
        if (evt.getEntity() instanceof Player && evt.getDamager() instanceof Player && evt.getCause() == DamageCause.ENTITY_ATTACK) {
            Player cop = (Player) evt.getDamager();
            Player caught = (Player) evt.getEntity();
            if (cop.getItemInHand().getTypeId() == getConfig().getInt("police-item-id") && cop.hasPermission("seizeddrugs.use") && !caught.hasPermission("seizeddrugs.exempt") && canBeatHere(caught)) {
                // We are going to be mode-dependent here.
                if (!copModes.containsKey(cop.getName())) {
                    if (getConfig().getBoolean("beatdown-only", false)) {
                        copModes.put(cop.getName(), Mode.BEATDOWN);
                    } else {
                        copModes.put(cop.getName(), Mode.DRUG_SEIZE);
                    }
                }
                switch (copModes.get(cop.getName())) {
                    case BEATDOWN:
                        performBeatdown(cop, caught);
                        break;
                    case DRUG_SEIZE:
                        performSeize(cop, caught);
                        break;
                }
                evt.setCancelled(true);
            }
        }
    }

    private void executeJailServerCommand(Player executor, Player username, String duration) {
        String command = getConfig().getString("jail-command");

        // Future SeizedJailsIntegration
        /*
        if(command.equals("sdjail")) {
        	try {
				jail.jailPlayer(Bukkit.getPlayer(username), Integer.valueOf(duration));
			} catch (Exception e) {
				getLogger().info("Something went wrong when we tried to jail "+username+": "+e.getMessage());
				e.printStackTrace();
			}
        	return;
        }*/

        command = command.replaceAll("%username%", username.getName());
        command = command.replaceAll("%duration%", duration);
        command = command.replaceAll("%cop%", executor.getName());
        // CommandSender may vary.
        // config.yml will dictate if the command is to be executed as the cop or the console
        // The default is the console.
        CommandSender s = getConfig().getBoolean("run-command-as-cop") ? executor : getServer().getConsoleSender();
        getServer().dispatchCommand(s, command);
    }

    private void performSeize(Player cop, Player caught) {
        if (seize(cop, caught)) {
            executeJailServerCommand(cop, caught, getConfig().getString("jail-duration-for-player"));
            caught.sendMessage(formatMessage(getConfig().getString("caught-player"), caught, cop));
            cop.sendMessage(formatMessage(getConfig().getString("cop-congratulation"), caught, cop));
        } else {
            int threshold = getConfig().getInt("cop-threshold");
            if (threshold > 0) {
                // FUCK DA PO-LICE
                int co = getCopIncorrectSeizure(cop);
                cop.sendMessage(formatMessage(getConfig().getString("cop-warning"), caught, cop));
                if (co > threshold) {
                    executeJailServerCommand(cop, cop, getConfig().getString("jail-duration-for-cop"));
                    cop.sendMessage(formatMessage(getConfig().getString("cop-jailed"), cop, cop));
                }
            }
        }
    }

    private void performBeatdown(Player cop, Player caught) {
        switch (beatdown(caught)) {
            case HIT:
                cop.sendMessage(formatMessage(getConfig().getString("beatdown-hit"), caught, cop));
                caught.sendMessage(formatMessage(getConfig().getString("beatdown-player-hit"), caught, cop));
                break;
            case MISS:
                cop.sendMessage(formatMessage(getConfig().getString("beatdown-miss"), caught, cop));
                break;
            case BEAT:
                cop.sendMessage(formatMessage(getConfig().getString("beatdown-beat"), caught, cop));
                caught.sendMessage(getConfig().getString("beatdown-player"));
                executeJailServerCommand(cop, caught, getConfig().getString("jail-duration-for-player"));
                break;
        }
    }

    // General message formatting function(s).
    private String formatMessage(String msg, Player player, Player cop) {
        String m = msg.replace("%health%", Integer.toString(getBeatdownHealth(player.getName())));
        m = m.replace("%max%", Integer.toString(getConfig().getInt("beatdown-health", 20)));
        m = m.replace("%player%", player.getName());
        m = m.replace("%cop%", cop.getName());
        m = m.replace("%times%", Integer.toString(getCopIncorrectSeizure(cop)));
        return ChatColor.translateAlternateColorCodes('&', m);
    }

    public class BeatdownHealRunnable implements Runnable {
        @Override
        public void run() {
            int m = getConfig().getInt("beatdown-health", 20);
            for (Iterator<Map.Entry<String, Integer>> it = beatdownInfo.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Integer> entry = it.next();
                if (entry.getValue() < m) {
                    if (entry.getValue() + 1 == m) {
                        it.remove();
                    } else {
                        entry.setValue(entry.getValue() + 1);
                    }
                }
            }
        }
    }
}
