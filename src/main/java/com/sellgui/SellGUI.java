package com.sellgui;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SellGUI extends JavaPlugin implements Listener, CommandExecutor {

    private static final int SIZE = 54;
    private static final int ITEM_SLOTS = 45; // slots 0-44 are for items
    private static final int SELL_SLOT = 49;  // bottom row, middle

    private final Map<Material, Double> prices = new EnumMap<>(Material.class);
    private Economy economy;

    /** Marks our inventory so we can recognise it. */
    private static class SellHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void onEnable() {
        saveResource("prices.yml", false);
        loadPrices();

        if (getEconomy() == null) {
            getLogger().severe("No Vault economy found (is EssentialsX installed?). Disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (getCommand("sell") != null) {
            getCommand("sell").setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void loadPrices() {
        prices.clear();
        File file = new File(getDataFolder(), "prices.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) {
                getLogger().warning("prices.yml: unknown material '" + key + "'");
                continue;
            }
            double price = yml.getDouble(key, -1);
            if (price < 0) {
                getLogger().warning("prices.yml: invalid price for '" + key + "'");
                continue;
            }
            prices.put(mat, price);
        }
        getLogger().info("Loaded " + prices.size() + " sell prices.");
    }

    private Economy getEconomy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp =
                    getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }
        return economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /sell.");
            return true;
        }
        openGui((Player) sender);
        return true;
    }

    private void openGui(Player player) {
        SellHolder holder = new SellHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, ChatColor.DARK_GREEN + "Sell Items");
        holder.inventory = inv;

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(" ");
            filler.setItemMeta(fm);
        }
        for (int i = ITEM_SLOTS; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        ItemStack sell = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sm = sell.getItemMeta();
        if (sm != null) {
            sm.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "SELL");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to sell the items above");
            lore.add(ChatColor.GRAY + "(closing the menu also sells)");
            sm.setLore(lore);
            sell.setItemMeta(sm);
        }
        inv.setItem(SELL_SLOT, sell);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof SellHolder)) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        int raw = e.getRawSlot();
        if (raw == SELL_SLOT) {
            e.setCancelled(true);
            sellContents(player, e.getInventory());
            Bukkit.getScheduler().runTask(this, player::closeInventory);
        } else if (raw >= ITEM_SLOTS && raw < SIZE) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof SellHolder)) return;
        for (int raw : e.getRawSlots()) {
            if (raw >= ITEM_SLOTS && raw < SIZE) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof SellHolder)) return;
        if (!(e.getPlayer() instanceof Player)) return;
        sellContents((Player) e.getPlayer(), e.getInventory());
    }

    private void sellContents(Player player, Inventory inv) {
        Economy eco = getEconomy();
        double total = 0;
        List<ItemStack> returned = new ArrayList<>();

        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            Double price = prices.get(item.getType());
            if (price == null || eco == null) {
                returned.add(item); // not sellable -> give back
            } else {
                total += price * item.getAmount();
            }
            inv.setItem(i, null);
        }

        for (ItemStack item : returned) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        if (total > 0 && eco != null) {
            eco.depositPlayer(player, total);
            player.sendMessage(ChatColor.WHITE + "+" + ChatColor.GREEN + "$"
                    + ChatColor.WHITE + String.format(Locale.US, "%,.2f", total));
        }
    }
}
