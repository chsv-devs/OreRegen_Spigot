package com.hancho.oregenerator;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class OreGenerator extends JavaPlugin implements Listener {
    private final ConcurrentHashMap<Block, Long> regenQueue = new ConcurrentHashMap<>();
    private final String worldName = "mineWorld";

    public LinkedHashMap<String, Integer> targetBlockMap;
    public int total = 0;
    public int refreshPeriod = 30;
    boolean isEnabled = true;

    @Override
    public void onEnable() {
        BukkitRunnable bRunnable = new BukkitRunnable() {
            @Override
            public void run() {
                resetMine(false);
            }
        };

        bRunnable.runTaskTimer(this, 30, 5);
        this.getServer().getPluginManager().registerEvents(this, this);

        reloadConfiguration();
    }

    @Override
    public void onDisable() {
        resetMine(true);
    }

    public void reloadConfiguration() {
        total = 0;

        this.reloadConfig();
        FileConfiguration config = this.getConfig();
        targetBlockMap = new LinkedHashMap<>();
        Object ob = config.get("blocks");

        if (ob instanceof MemorySection) {
            MemorySection ms = (MemorySection) ob;

            ms.getValues(false).forEach((k, v) -> {
                targetBlockMap.put(k, (int) v);
            });
        } else {
            targetBlockMap.put(Material.STONE.name(), 100);
            config.set("refreshPeriod", 30);
            config.set("blocks", targetBlockMap);

            try {
                config.save(this.getDataFolder().getAbsolutePath() + "/config.yml");
            } catch (IOException e) {
                this.getLogger().info(e.getMessage());
            }
        }

        this.refreshPeriod = config.getInt("refreshPeriod");
        targetBlockMap.forEach((k, v) -> {
            total += v;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent ev) {
        if (!isEnabled) return;
        Player player = ev.getPlayer();
        World world = player.getWorld();
        Block block = ev.getBlock();

        if (world.getName().equals(worldName) && block.getType().equals(Material.STONE)) {
            ev.getBlock().setType(Material.getMaterial(getRandomBlock()));
            ev.setCancelled(false);
            this.regenQueue.put(block, System.currentTimeMillis() / 1000);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player)) return;
        if (ev.getEntity().getWorld().getName().equals(worldName)) return;
        if (ev.getCause().equals(EntityDamageEvent.DamageCause.SUFFOCATION)) ev.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            if (args[0].equals("reload")) {
                reloadConfiguration();
                sender.sendMessage("리로드 완료");
            } else if (args[0].equals("toggle")) {
                this.isEnabled = !this.isEnabled;
                String msg = isEnabled
                        ? "이제 수정된 블럭이 기록되어 리젠됩니다."
                        : "이제 수정된 블럭이 기록되지 않아 리젠하지 않습니다";
                sender.sendMessage(msg);
            } else if (args[0].equals("status")) {
                String msg = isEnabled
                        ? "상태 : 켜짐"
                        : "상태 : 꺼짐";
                sender.sendMessage(msg + "\n 리젠 대기중인 블럭 : " + regenQueue.size() + "개");
            }
        } else {
            sender.sendMessage("/og reload\n/og toggle\n/og status");
        }

        return true;
    }

    public String getRandomBlock() {
        int r = ThreadLocalRandom.current().nextInt(total);
        Iterator i = this.targetBlockMap.keySet().iterator();

        int start = 0;
        int end = 0;
        while (true) {
            if (i.hasNext()) {
                String key = (String) i.next();
                int p = this.targetBlockMap.get(key);
                end += p;
                if (start <= r && r < end) {
                    return key;
                }
                start += p;
            } else {
                break;
            }
        }

        throw new RuntimeException("엥?");
    }

    public void resetMine(boolean resetAll) {
		long current = System.currentTimeMillis() / 1000;

		if (resetAll) {
			for (Block block : regenQueue.keySet()) {
			    block.setType(Material.STONE, false);
			}

			this.regenQueue.clear();
		} else {
			Iterator<Block> it = this.regenQueue.keySet().iterator();

			while (it.hasNext()) {
                Block block = it.next();
				if ((current - this.regenQueue.get(block)) < this.refreshPeriod) {
					continue;
				}

                block.setType(Material.STONE, false);
				it.remove();
			}
		}
	}
}
