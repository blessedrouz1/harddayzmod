package com.harddayz.client;

import com.harddayz.Compat;
import com.harddayz.item.GunItem;
import com.harddayz.registry.ModItems;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Item;

/**
 * Свойства предметов для моделей обвесов (в новых версиях — custom_model_data в items/*.json).
 * Модели ствола выбираются через overrides: прицел 0/0.25/0.5/0.75, глушитель 0/1, подствольник 0/1.
 */
final class ItemModels {
    private ItemModels() {
    }

    static void register() {
        for (Item gun : new Item[] {ModItems.AK74.get(), ModItems.M4.get(), ModItems.PKM.get(), ModItems.SVD.get()}) {
            ItemProperties.register(gun, Compat.id("sight"), (stack, level, entity, seed) -> switch (GunItem.part(stack, "sight")) {
                case "reddot" -> 0.25f;
                case "scope" -> 0.5f;
                case "thermal" -> 0.75f;
                default -> 0f;
            });
            ItemProperties.register(gun, Compat.id("suppressor"), (stack, level, entity, seed) -> "suppressor".equals(GunItem.part(stack, "muzzle")) ? 1f : 0f);
            ItemProperties.register(gun, Compat.id("underbarrel"), (stack, level, entity, seed) -> "gl".equals(GunItem.part(stack, "under")) ? 1f : 0f);
        }
    }
}
