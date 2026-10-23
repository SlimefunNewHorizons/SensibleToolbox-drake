package io.github.thebusybiscuit.sensibletoolbox.blocks.machines;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import com.github.drakescraft_labs.slimefun4.libraries.dough.protection.ProtectionManager;
import io.github.thebusybiscuit.sensibletoolbox.SensibleToolboxPlugin;
import io.github.thebusybiscuit.sensibletoolbox.api.SensibleToolbox;
import io.github.thebusybiscuit.sensibletoolbox.api.items.BaseSTBBlock;
import me.desht.dhutils.blocks.PersistableLocation;
import me.desht.dhutils.cuboid.Cuboid;

class AutoBuilderTest {

    private ServerMock server;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(SensibleToolboxPlugin.class);
        // the real one is created in a delayed task that also builds every recipe;
        // the API caches the first plugin ever loaded, so patch that one, not the fresh load
        SensibleToolboxPlugin plugin = SensibleToolbox.getPluginInstance();
        setField(SensibleToolboxPlugin.class, plugin, "protectionManager", new ProtectionManager(plugin));
        world = server.addSimpleWorld("test_world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object enumConstant(String fieldName, String constant) {
        try {
            Class type = AutoBuilder.class.getDeclaredField(fieldName).getType();
            return Enum.valueOf(type, constant);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testClearModeBreaksStoneButSkipsBedrock() {
        Location machineLoc = new Location(world, 5, 64, 0);
        AutoBuilder builder = new AutoBuilder();
        setField(BaseSTBBlock.class, builder, "persistableLocation", new PersistableLocation(machineLoc));
        builder.setOwner(UUID.randomUUID());
        builder.onBlockRegistered(machineLoc, true);
        builder.setCharge(1000.0);

        Block stone = world.getBlockAt(0, 64, 0);
        stone.setType(Material.STONE);
        Block bedrock = world.getBlockAt(1, 64, 0);
        bedrock.setType(Material.BEDROCK);

        setField(AutoBuilder.class, builder, "workArea", new Cuboid(stone.getLocation(), bedrock.getLocation()));
        setField(AutoBuilder.class, builder, "baseScuPerOp", 10);
        setField(AutoBuilder.class, builder, "buildX", 0);
        setField(AutoBuilder.class, builder, "buildY", 64);
        setField(AutoBuilder.class, builder, "buildZ", 0);
        setField(AutoBuilder.class, builder, "status", enumConstant("status", "RUNNING"));

        builder.onServerTick();
        assertEquals(Material.AIR, stone.getType(), "Stone should be cleared");
        double afterStone = builder.getCharge();
        assertEquals(1000.0 - 10 * Material.STONE.getHardness(), afterStone, 0.01, "Clearing stone costs scu_per_op * hardness");

        builder.onServerTick();
        assertEquals(Material.BEDROCK, bedrock.getType(), "Unbreakable blocks must be skipped");
        assertEquals(afterStone, builder.getCharge(), 0.01, "Skipping an unbreakable block must not change the charge");
        assertEquals(enumConstant("status", "FINISHED"), builder.getStatus(), "Builder should finish after the last block");
    }
}
