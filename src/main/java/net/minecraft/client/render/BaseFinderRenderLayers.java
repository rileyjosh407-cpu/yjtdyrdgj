package net.minecraft.client.render;

import net.minecraft.client.gl.BaseFinderPipelines;

/**
 * Render layers used by Base Finder.
 */
public final class BaseFinderRenderLayers {
    public static final RenderLayer THROUGH_WALL_FILL = RenderLayer.of(
            "basefinder:through_wall_fill",
            RenderSetup.builder(BaseFinderPipelines.THROUGH_WALL_FILL).build()
    );

    private BaseFinderRenderLayers() {
    }
}
