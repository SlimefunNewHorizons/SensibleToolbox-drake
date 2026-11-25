package integration;

import io.github.thebusybiscuit.sensibletoolbox.SensibleToolboxPlugin;
import io.github.thebusybiscuit.sensibletoolbox.api.recipes.RecipeReloadListener;
import io.github.thebusybiscuit.sensibletoolbox.api.recipes.RecipeUtil;
import io.github.thebusybiscuit.sensibletoolbox.items.itemroutermodules.BlankModule;
import io.github.thebusybiscuit.sensibletoolbox.items.itemroutermodules.BreakerModule;
import io.github.thebusybiscuit.sensibletoolbox.items.itemroutermodules.SenderModule;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/** Only for the disposable, loopback-only fixture created by run.py. */
public final class RecipeReloadProbe extends JavaPlugin implements Listener {
    private SensibleToolboxPlugin stb;
    private Listener reloadListener;
    private Set<NamespacedKey> initialKeys;
    private int phase;
    private int events;
    private boolean finished;

    @Override
    public void onEnable() {
        // An accidentally installed test must never reload or stop a real server.
        if (!Files.isRegularFile(Path.of("isolated-recipe-reload-test"))
                || !"127.0.0.1".equals(Bukkit.getIp()) || Bukkit.getOnlinePlayers().size() != 0) {
            throw new IllegalStateException("Disposable loopback fixture required");
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(this::begin), 40);
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
            throw new AssertionError("Resource reload test timed out");
        }), 2400);
    }

    private void begin() {
        require(Bukkit.getPluginManager().getPlugin("SensibleToolbox") instanceof SensibleToolboxPlugin,
                "STB plugin present");
        stb = (SensibleToolboxPlugin) Bukkit.getPluginManager().getPlugin("SensibleToolbox");
        require(stb.isEnabled(), "STB enabled");
        var registered = HandlerList.getRegisteredListeners(stb).stream()
                .filter(r -> r.getListener() instanceof RecipeReloadListener).toList();
        require(registered.size() == 1, "One actual STB reload handler registered");
        reloadListener = registered.getFirst().getListener();
        initialKeys = keys();
        verifyCrafts("startup");
        // Negative control: the same server loses both recipes without the handler.
        HandlerList.unregisterAll(reloadListener);
        phase = 1;
        Bukkit.reloadData();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReload(ServerResourcesReloadedEvent event) {
        events++;
        getLogger().info("PAPER_RELOAD_EVENT phase=" + phase + " cause=" + event.getCause());
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(this::afterReload), 5);
    }

    private void afterReload() throws Exception {
        if (phase == 1) {
            require(Bukkit.getRecipe(new BreakerModule().getKey()) == null, "Control loses Breaker");
            require(Bukkit.getRecipe(new SenderModule().getKey()) == null, "Control loses Sender");
            getLogger().info("PAPER_NEGATIVE_CONTROL_PASS breaker,sender missing");
            Bukkit.getPluginManager().registerEvents(reloadListener, stb);
            phase = 2;
            Bukkit.reloadData();
        } else if (phase == 2) {
            verifyCrafts("restored");
            require(initialKeys.equals(keys()), "All STB recipe keys restored");
            phase = 3;
            Bukkit.reloadData();
        } else if (phase == 3) {
            verifyCrafts("second-reload");
            require(initialKeys.equals(keys()), "Recipe keys stable after second reload");
            // Preserve another provider's existing recipe and prove repeatability.
            NamespacedKey sender = new SenderModule().getKey();
            require(Bukkit.removeRecipe(sender), "Remove test Sender recipe");
            ShapelessRecipe replacement = new ShapelessRecipe(sender, new ItemStack(Material.DIAMOND));
            replacement.addIngredient(Material.DIRT);
            require(Bukkit.addRecipe(replacement), "Install test replacement");
            RecipeUtil.restoreBukkitRecipes();
            RecipeUtil.restoreBukkitRecipes();
            require(Bukkit.getRecipe(sender).getResult().getType() == Material.DIAMOND,
                    "Existing keyed recipe preserved");
            require(initialKeys.equals(keys()), "Repeated restore adds no keys");
            require(events == 3, "Three real Paper reload events received");
            Files.writeString(Path.of("result.txt"), "PASS events=" + events
                    + " recipes=" + initialKeys.size() + " players=0\n");
            getLogger().info("PAPER_RECIPE_RELOAD_PASS");
            finished = true;
            Bukkit.shutdown();
        } else {
            throw new AssertionError("Unexpected reload phase " + phase);
        }
    }

    private void verifyCrafts(String label) {
        verifyCraft(new BreakerModule().getKey(), new BreakerModule().toItemStack(),
                Material.DIAMOND_PICKAXE, Material.HOPPER);
        verifyCraft(new SenderModule().getKey(), new SenderModule().toItemStack(), Material.ARROW);
        getLogger().info("PAPER_CRAFT_PASS " + label + " breaker,sender");
    }

    private void verifyCraft(NamespacedKey key, ItemStack expected, Material... ingredients) {
        require(Bukkit.getRecipe(key) != null, "Registered recipe " + key);
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = new BlankModule().toItemStack();
        for (int i = 0; i < ingredients.length; i++) {
            matrix[i + 1] = new ItemStack(ingredients[i]);
        }
        Recipe matched = Bukkit.getCraftingRecipe(matrix, Bukkit.getWorlds().getFirst());
        require(matched instanceof Keyed keyed && keyed.getKey().equals(key), "Matrix matches " + key);
        ItemStack crafted = Bukkit.craftItem(matrix, Bukkit.getWorlds().getFirst());
        require(expected.equals(crafted), "Crafted item and metadata match " + key);
    }

    private Set<NamespacedKey> keys() {
        Set<NamespacedKey> result = new HashSet<>();
        Bukkit.recipeIterator().forEachRemaining(recipe -> {
            if (recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals("sensibletoolbox")) {
                result.add(keyed.getKey());
            }
        });
        require(!result.isEmpty(), "STB recipes exist");
        return result;
    }

    private void guarded(Checked action) {
        if (finished) return;
        try {
            require(Bukkit.getOnlinePlayers().isEmpty(), "No players in fixture");
            action.run();
        } catch (Throwable error) {
            finished = true;
            getLogger().log(Level.SEVERE, "PAPER_RECIPE_RELOAD_FAIL", error);
            Bukkit.shutdown();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Checked {
        void run() throws Exception;
    }
}
