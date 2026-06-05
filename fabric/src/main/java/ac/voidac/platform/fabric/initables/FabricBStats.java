package ac.voidac.platform.fabric.initables;

import ac.voidac.VoidAPI;
import ac.voidac.manager.init.start.StartableInitable;
import ac.voidac.manager.init.stop.StoppableInitable;
import ac.voidac.platform.fabric.utils.metrics.MetricsFabric;
import ac.voidac.utils.anticheat.Constants;

public class FabricBStats implements StartableInitable, StoppableInitable {

    private MetricsFabric metricsFabric;

    @Override
    public void start() {
        try {
            metricsFabric = new MetricsFabric(VoidAPI.INSTANCE.getVoidPlugin(), Constants.BSTATS_PLUGIN_ID);
        } catch (Exception ignored) {}
    }

    @Override
    public void stop() {
        if (metricsFabric != null)
            metricsFabric.shutdown();
    }
}
