package com.harddayz.war;

import com.harddayz.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("harddayz")
@PrefixGameTestTemplate(false)
public class SupplyGameTests {
    @GameTest(template = "empty")
    public static void removedBmpRefundsWithoutShiftingCatalog(GameTestHelper h) {
        var faction = new War.Faction();
        faction.id = "bmp-refund-regression";
        int bmpIndex = 12;
        var entry = Supply.CATALOG.get(bmpIndex);
        h.assertTrue(entry.item().get() == com.harddayz.registry.ModItems.BMP2.get()
                && !entry.available(), "BMP catalog slot changed or remains available");
        h.assertTrue(Supply.CATALOG.get(bmpIndex + 1).item().get() == com.harddayz.registry.ModItems.BRADLEY.get()
                && Supply.CATALOG.get(bmpIndex + 1).available(), "Next catalog entry changed");
        faction.budget = 200;
        faction.spent = entry.price();
        faction.orders.add(new Supply.Order(bmpIndex, Long.MAX_VALUE, "offline-buyer"));
        War.factions().add(faction);
        try {
            Supply.tick(h.getLevel().getServer());
            Supply.tick(h.getLevel().getServer());
            h.assertTrue(faction.orders.isEmpty() && faction.budget == 200 + entry.price() && faction.spent == 0,
                    "Removed vehicle was not refunded exactly once");
            var bmp = com.harddayz.registry.ModEntities.LAUNCHER.get().create(h.getLevel());
            bmp.setKind(com.harddayz.entity.Launcher.Kind.BMP2);
            bmp.tick();
            h.assertTrue(bmp.isRemoved(), "Saved BMP remains active");
            h.assertTrue(!((com.harddayz.item.LauncherItem) entry.item().get()).available(), "BMP can be placed");
        } finally {
            War.factions().remove(faction);
            War.save();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void offlineOrderWaitsForDestination(GameTestHelper h) {
        var faction = new War.Faction();
        faction.id = "supply-regression";
        var order = new Supply.Order(0, 0, "offline-buyer");
        faction.orders.add(order);
        War.factions().add(faction);
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        try {
            Supply.tick(h.getLevel().getServer());
            Supply.tick(h.getLevel().getServer());
            h.assertTrue(faction.orders.size() == 1 && faction.orders.get(0) == order,
                    "Paid order disappeared while buyer was offline");
            h.getLevel().setBlockAndUpdate(at, ModBlocks.HQ.get().defaultBlockState());
            faction.hqs.add(new int[]{at.getX(), at.getY(), at.getZ()});
            Supply.tick(h.getLevel().getServer());
            h.assertTrue(faction.orders.isEmpty(), "Order did not resume after a destination appeared");
            var hq = (HqBlockEntity) h.getLevel().getBlockEntity(at);
            h.assertTrue(hq.getItem(0).getCount() == 1
                    && hq.getItem(0).is(Supply.CATALOG.get(0).item().get()), "Order contents lost");
            Supply.tick(h.getLevel().getServer());
            h.assertTrue(hq.getItem(0).getCount() == 1 && hq.getItem(1).isEmpty(), "Order delivered twice");
        } finally {
            War.factions().remove(faction);
            War.save();
            h.getLevel().removeBlock(at, false);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hqRespectsStackLimits(GameTestHelper h) {
        var hq = new HqBlockEntity(BlockPos.ZERO, ModBlocks.HQ.get().defaultBlockState());
        var incoming = new ItemStack(Items.IRON_SWORD, 30);
        var rest = hq.insert(incoming);
        h.assertTrue(incoming.getCount() == 30 && rest.getCount() == 3,
                "Overflow or caller stack was lost");
        for (int i = 0; i < hq.getContainerSize(); i++) {
            h.assertTrue(hq.getItem(i).getCount() == 1, "Unstackable items combined");
        }
        hq.clearContent();
        hq.setItem(0, new ItemStack(Items.STONE, 60));
        rest = hq.insert(new ItemStack(Items.STONE, 70));
        h.assertTrue(rest.isEmpty() && hq.getItem(0).getCount() == 64
                && hq.getItem(1).getCount() == 64 && hq.getItem(2).getCount() == 2,
                "Partial stacks or overflow split incorrectly");
        h.succeed();
    }
}
