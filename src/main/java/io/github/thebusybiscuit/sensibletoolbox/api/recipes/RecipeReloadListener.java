package io.github.thebusybiscuit.sensibletoolbox.api.recipes;

import io.github.thebusybiscuit.sensibletoolbox.SensibleToolboxPlugin;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Restores STB crafting and furnace recipes after Paper reloads datapacks. */
public final class RecipeReloadListener implements Listener {
    private final SensibleToolboxPlugin plugin;

    public RecipeReloadListener(SensibleToolboxPlugin plugin) {
        this.plugin = plugin;
    }

    /** Run on the next server tick after the recipe manager has been replaced. */
    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, RecipeUtil::restoreBukkitRecipes);
    }
}
