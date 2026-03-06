package baffledhedgehog.innofixes.fix;

import baffledhedgehog.innofixes.InnoFixes;
import com.mojang.logging.LogUtils;
import me.fzzyhmstrs.fzzy_config.util.ThreadingUtils;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid = InnoFixes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FzzyConfigShutdownFix {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean STOP_ATTEMPTED = new AtomicBoolean(false);

    private FzzyConfigShutdownFix() {
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        innofixes$shutdownFzzyWorkers("ServerStoppingEvent");
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        innofixes$shutdownFzzyWorkers("ServerStoppedEvent");
    }

    private static void innofixes$shutdownFzzyWorkers(String phase) {
        if (!STOP_ATTEMPTED.compareAndSet(false, true)) {
            return;
        }

        try {
            ThreadingUtils.INSTANCE.stop();
        } catch (Throwable throwable) {
            LOGGER.warn("Fzzy Config stop() threw during {}: {}", phase, throwable.toString());
        }

        try {
            ExecutorService executor = ThreadingUtils.INSTANCE.getEXECUTOR$fzzy_config();
            executor.shutdown();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                int pending = executor.shutdownNow().size();
                LOGGER.warn("Forced shutdown of Fzzy Config executor during {}. Pending tasks: {}", phase, pending);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while stopping Fzzy Config executor during {}", phase);
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to stop Fzzy Config executor during {}: {}", phase, throwable.toString());
        }
    }
}
