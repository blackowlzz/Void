package ac.voidac.utils.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;

import java.util.List;
import java.util.function.UnaryOperator;

public final class PipelineOrder {
    private PipelineOrder() {
    }

    public static boolean moveBehindForeign(ChannelPipeline pipeline, String ownName, String foreignPrefix,
                                            UnaryOperator<ChannelHandler> copier) {
        List<String> names = pipeline.names();
        int ownIndex = names.indexOf(ownName);
        if (ownIndex < 0) return false;

        for (int i = names.size() - 1; i > ownIndex; i--) {
            String name = names.get(i);
            if (!name.startsWith(foreignPrefix)) continue;
            ChannelHandler own = pipeline.remove(ownName);
            pipeline.addAfter(name, ownName, copier.apply(own));
            return true;
        }
        return false;
    }
}
