package net.minecraft.client.gl;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.util.Identifier;

/**
 * Base Finder's depth-disabled highlight pipeline.
 *
 * The 1.21.11 Yarn mappings do not expose vanilla's filled-box snippet, so
 * this pipeline explicitly declares the same position/color shader setup.
 */
public final class BaseFinderPipelines {
    public static final RenderPipeline THROUGH_WALL_FILL = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("basefinder", "pipeline/through_wall_fill"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private BaseFinderPipelines() {
    }
}
