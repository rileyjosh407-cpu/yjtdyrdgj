package net.minecraft.client.gl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.util.Identifier;

/**
 * Base Finder's depth-disabled highlight pipeline.
 *
 * Kept in Minecraft's pipeline package so the private vanilla registration
 * helper can register the custom pipeline for shader compilation.
 */
public final class BaseFinderPipelines {
    public static final RenderPipeline THROUGH_WALL_FILL = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.of("basefinder", "pipeline/through_wall_fill"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private BaseFinderPipelines() {
    }
}
