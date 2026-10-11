package ac.voidac.utils.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineOrderTest {
    private static final String PREFIX = "pe-decoder-";

    private static EmbeddedChannel pipelineOf(String... names) {
        EmbeddedChannel channel = new EmbeddedChannel();
        for (String name : names) channel.pipeline().addLast(name, new ChannelInboundHandlerAdapter());
        return channel;
    }

    private static List<String> handlerNames(EmbeddedChannel channel) {
        return channel.pipeline().names().stream().filter(name -> !name.contains("Context")).toList();
    }

    @Test
    void movesBehindTheLastForeignHandler() {
        EmbeddedChannel channel = pipelineOf("splitter", "pe-decoder-void", "pe-decoder-packetevents", "decoder");

        boolean moved = PipelineOrder.moveBehindForeign(channel.pipeline(), "pe-decoder-void", PREFIX,
                handler -> new ChannelInboundHandlerAdapter());

        assertTrue(moved);
        assertEquals(List.of("splitter", "pe-decoder-packetevents", "pe-decoder-void", "decoder"), handlerNames(channel));
    }

    @Test
    void movesBehindEveryForeignHandlerNotOnlyTheNearest() {
        EmbeddedChannel channel = pipelineOf("pe-decoder-void", "pe-decoder-a", "x", "pe-decoder-b", "decoder");

        PipelineOrder.moveBehindForeign(channel.pipeline(), "pe-decoder-void", PREFIX, handler -> new ChannelInboundHandlerAdapter());

        assertEquals(List.of("pe-decoder-a", "x", "pe-decoder-b", "pe-decoder-void", "decoder"), handlerNames(channel));
    }

    @Test
    void leavesPipelineAloneWhenAlreadyBehindForeignHandlers() {
        EmbeddedChannel channel = pipelineOf("pe-decoder-packetevents", "pe-decoder-void", "decoder");
        ChannelHandler own = channel.pipeline().get("pe-decoder-void");

        boolean moved = PipelineOrder.moveBehindForeign(channel.pipeline(), "pe-decoder-void", PREFIX,
                handler -> new ChannelInboundHandlerAdapter());

        assertFalse(moved);
        assertSame(own, channel.pipeline().get("pe-decoder-void"));
        assertEquals(List.of("pe-decoder-packetevents", "pe-decoder-void", "decoder"), handlerNames(channel));
    }

    @Test
    void leavesPipelineAloneWithoutForeignHandlers() {
        EmbeddedChannel channel = pipelineOf("splitter", "pe-decoder-void", "decoder");

        assertFalse(PipelineOrder.moveBehindForeign(channel.pipeline(), "pe-decoder-void", PREFIX,
                handler -> new ChannelInboundHandlerAdapter()));
        assertEquals(List.of("splitter", "pe-decoder-void", "decoder"), handlerNames(channel));
    }

    @Test
    void ignoresPreViaHandlersBecauseTheirNamesDoNotMatchThePrefix() {
        EmbeddedChannel channel = pipelineOf("pre-pe-decoder-void", "via-decoder", "pe-decoder-void", "decoder");

        assertFalse(PipelineOrder.moveBehindForeign(channel.pipeline(), "pe-decoder-void", PREFIX,
                handler -> new ChannelInboundHandlerAdapter()));
    }

    @Test
    void missingOwnHandlerIsANoOp() {
        EmbeddedChannel channel = pipelineOf("pe-decoder-packetevents", "decoder");

        assertFalse(PipelineOrder.moveBehindForeign(channel.pipeline(), "pe-decoder-void", PREFIX,
                handler -> new ChannelInboundHandlerAdapter()));
    }

    @Test
    void worksForOutboundHandlersToo() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast("pe-encoder-void", new ChannelOutboundHandlerAdapter());
        channel.pipeline().addLast("pe-encoder-packetevents", new ChannelOutboundHandlerAdapter());

        boolean moved = PipelineOrder.moveBehindForeign(channel.pipeline(), "pe-encoder-void", "pe-encoder-",
                handler -> new ChannelOutboundHandlerAdapter());

        assertTrue(moved);
        assertEquals(List.of("pe-encoder-packetevents", "pe-encoder-void"), handlerNames(channel));
    }
}
