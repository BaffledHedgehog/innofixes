package baffledhedgehog.innofixes.fix;

import baffledhedgehog.innofixes.InnoFixes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.resource.ResourcePackLoader;

@Mod.EventBusSubscriber(modid = InnoFixes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TopPriorityDataPackFix {
    private static final String TOP_DATA_PACK_ID = InnoFixes.MOD_ID + "_top_data_overrides";
    private static final Component TOP_DATA_PACK_TITLE = Component.literal("InnoFixes Top Data Overrides");

    private TopPriorityDataPackFix() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }

        IModFileInfo modFileInfo = ModList.get().getModFileById(InnoFixes.MOD_ID);
        if (modFileInfo == null) {
            return;
        }

        Pack topDataPack = Pack.readMetaAndCreate(
            TOP_DATA_PACK_ID,
            TOP_DATA_PACK_TITLE,
            true,
            id -> ResourcePackLoader.createPackForMod(modFileInfo),
            PackType.SERVER_DATA,
            Pack.Position.TOP,
            PackSource.BUILT_IN
        );

        if (topDataPack == null) {
            return;
        }

        event.addRepositorySource(consumer -> consumer.accept(topDataPack));
    }
}
