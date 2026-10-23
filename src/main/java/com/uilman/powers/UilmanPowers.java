package com.uilman.powers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class UilmanPowers extends JavaPlugin implements Listener {
    private final Set<UUID> allowed = new HashSet<>();
    private final Map<UUID, UUID> carrying = new HashMap<>();
    private final Map<UUID, Boolean> oldAllowFlight = new HashMap<>();
    private File dataFile;

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "players.yml");
        loadAllowed();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        for (Player p : Bukkit.getOnlinePlayers()) applyFlight(p);
    }
    @Override public void onDisable() {
        for (UUID id : new ArrayList<>(carrying.keySet())) release(id, false);
        for (Player p : Bukkit.getOnlinePlayers()) restoreFlight(p);
        saveAllowed();
    }
    private void loadAllowed() {
        if (!dataFile.exists()) return;
        for (String value : YamlConfiguration.loadConfiguration(dataFile).getStringList("allowed")) {
            try { allowed.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) {}
        }
    }
    private void saveAllowed() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("allowed", allowed.stream().map(UUID::toString).sorted().toList());
        try { yaml.save(dataFile); } catch (IOException e) { getLogger().severe("Could not save players.yml: " + e.getMessage()); }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(ChatColor.RED + "Only the server console can use this command."); return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage("Uilman abilities: " + allowed.size() + " UUID(s): " + allowed); return true;
        }
        if (args.length != 2 || (!args[0].equalsIgnoreCase("give") && !args[0].equalsIgnoreCase("remove"))) {
            sender.sendMessage("Usage: uilman <give|remove|list> [player]"); return true;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayer(args[1]);
        UUID id = target.getUniqueId();
        if (args[0].equalsIgnoreCase("give")) {
            allowed.add(id);
            if (online != null) { applyFlight(online); online.sendMessage(ChatColor.LIGHT_PURPLE + "Uilman powers unlocked! Right-click to grab, left-click to throw."); }
            sender.sendMessage("Granted Uilman powers to " + args[1] + " (" + id + ")");
        } else {
            allowed.remove(id);
            release(id, false);
            for (UUID holder : new ArrayList<>(carrying.keySet())) if (id.equals(carrying.get(holder))) release(holder, false);
            if (online != null) restoreFlight(online);
            sender.sendMessage("Removed Uilman powers from " + args[1]);
        }
        saveAllowed(); return true;
    }
    private void applyFlight(Player p) {
        if (!allowed.contains(p.getUniqueId())) return;
        oldAllowFlight.putIfAbsent(p.getUniqueId(), p.getAllowFlight());
        p.setAllowFlight(true);
    }
    private void restoreFlight(Player p) {
        Boolean prior = oldAllowFlight.remove(p.getUniqueId());
        if (prior == null) return;
        if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            if (!prior && p.isFlying()) p.setFlying(false);
            p.setAllowFlight(prior);
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) { applyFlight(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        release(id, false);
        for (UUID holder : new ArrayList<>(carrying.keySet())) if (id.equals(carrying.get(holder))) release(holder, false);
        oldAllowFlight.remove(id);
    }
    @EventHandler public void onWorld(PlayerChangedWorldEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        release(id, false);
        for (UUID holder : new ArrayList<>(carrying.keySet())) if (id.equals(carrying.get(holder))) release(holder, false);
    }
    @EventHandler public void onTeleport(PlayerTeleportEvent e) {
        // A grabbed player's movement is controlled by the plugin; disallow external teleport while held.
        if (carrying.containsValue(e.getPlayer().getUniqueId()) && e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) e.setCancelled(true);
    }
    @EventHandler public void onGrab(PlayerInteractEntityEvent e) {
        Player holder = e.getPlayer();
        if (!allowed.contains(holder.getUniqueId()) || !(e.getRightClicked() instanceof Player victim)) return;
        e.setCancelled(true);
        if (holder.equals(victim) || carrying.containsKey(holder.getUniqueId()) || carrying.containsValue(victim.getUniqueId()) || carrying.containsKey(victim.getUniqueId())) return;
        carrying.put(holder.getUniqueId(), victim.getUniqueId());
        victim.setVelocity(new Vector(0, 0, 0));
        holder.sendMessage(ChatColor.LIGHT_PURPLE + "Grabbed " + victim.getName() + "! Left-click to throw, sneak to drop.");
        victim.sendMessage(ChatColor.RED + "You have been grabbed!");
    }
    @EventHandler public void onClick(PlayerInteractEvent e) {
        Player holder = e.getPlayer();
        if (!carrying.containsKey(holder.getUniqueId())) return;
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            release(holder.getUniqueId(), true);
        }
    }
    private void release(UUID holderId, boolean throwVictim) {
        UUID victimId = carrying.remove(holderId);
        if (victimId == null) return;
        Player victim = Bukkit.getPlayer(victimId);
        Player holder = Bukkit.getPlayer(holderId);
        if (victim == null) return;
        if (throwVictim && holder != null && holder.isOnline()) {
            Vector direction = holder.getEyeLocation().getDirection().normalize();
            victim.setVelocity(direction.multiply(2.7).add(new Vector(0, 0.35, 0)));
            holder.sendMessage(ChatColor.LIGHT_PURPLE + "Thrown " + victim.getName() + "!");
        } else victim.setVelocity(new Vector(0, 0, 0));
    }
    private void tick() {
        for (UUID holderId : new ArrayList<>(carrying.keySet())) {
            Player holder = Bukkit.getPlayer(holderId);
            Player victim = Bukkit.getPlayer(carrying.get(holderId));
            if (holder == null || victim == null || !holder.isOnline() || !victim.isOnline() || holder.isDead() || victim.isDead() || !holder.getWorld().equals(victim.getWorld()) || !allowed.contains(holderId)) {
                release(holderId, false); continue;
            }
            if (holder.isSneaking()) { release(holderId, false); continue; }
            Location pos = holder.getEyeLocation().add(holder.getEyeLocation().getDirection().normalize().multiply(2.5));
            pos.setYaw(victim.getLocation().getYaw()); pos.setPitch(victim.getLocation().getPitch());
            victim.teleport(pos);
            victim.setVelocity(new Vector(0, 0, 0));
            victim.setFallDistance(0);
            victim.getWorld().spawnParticle(Particle.PORTAL, pos.clone().add(0, 0.8, 0), 5, 0.4, 0.8, 0.4, 0.03);
        }
    }
}
