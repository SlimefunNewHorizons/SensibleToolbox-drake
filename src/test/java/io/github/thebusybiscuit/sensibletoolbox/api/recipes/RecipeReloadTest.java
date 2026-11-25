package io.github.thebusybiscuit.sensibletoolbox.api.recipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.sensibletoolbox.SensibleToolboxPlugin;
import io.github.thebusybiscuit.sensibletoolbox.items.itemroutermodules.BreakerModule;
import io.github.thebusybiscuit.sensibletoolbox.items.itemroutermodules.SenderModule;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class RecipeReloadTest {
    /** A reload restores missing router recipes without replacing an existing keyed recipe. */
    @Test
    void restoresMissingRouterRecipesAndPreservesExistingOnes() {
        ServerMock server = MockBukkit.mock();
        SensibleToolboxPlugin plugin = MockBukkit.load(SensibleToolboxPlugin.class);
        RecipeReloadListener listener = new RecipeReloadListener(plugin);
        server.getScheduler().performTicks(2);

        NamespacedKey breaker = new BreakerModule().getKey();
        NamespacedKey sender = new SenderModule().getKey();
        assertNotNull(Bukkit.getRecipe(breaker));
        assertNotNull(Bukkit.getRecipe(sender));
        assertTrue(Bukkit.removeRecipe(breaker));
        assertTrue(Bukkit.removeRecipe(sender));

        listener.onResourcesReloaded(new ServerResourcesReloadedEvent(ServerResourcesReloadedEvent.Cause.COMMAND));
        server.getScheduler().performTicks(2);
        assertNotNull(Bukkit.getRecipe(breaker));
        assertNotNull(Bukkit.getRecipe(sender));
        assertTrue(Bukkit.removeRecipe(sender));

        ShapelessRecipe existing = new ShapelessRecipe(sender, new ItemStack(Material.DIAMOND));
        existing.addIngredient(Material.DIRT);
        assertTrue(Bukkit.addRecipe(existing));
        listener.onResourcesReloaded(new ServerResourcesReloadedEvent(ServerResourcesReloadedEvent.Cause.PLUGIN));
        server.getScheduler().performTicks(2);
        assertNotNull(Bukkit.getRecipe(breaker));
        assertEquals(Material.DIAMOND, Bukkit.getRecipe(sender).getResult().getType());
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }
}
