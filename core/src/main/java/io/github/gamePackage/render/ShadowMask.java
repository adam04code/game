package io.github.gamePackage.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ShortArray;

/**
 * Limits drawing to a set of shadow polygons using the stencil buffer. Used to darken the part of a sprite that a
 * shadow falls on: draw the sprite, then {@link #begin} with the shadows, draw it again tinted dark, {@link #end}.
 * Needs a stencil buffer (see the desktop launcher's back buffer config); without one it draws nothing.
 */
public class ShadowMask {
    private final EarClippingTriangulator triangulator = new EarClippingTriangulator();
    private boolean active;

    /** Whether the window has a stencil buffer to mask with. */
    public static boolean isSupported() {
        return Gdx.graphics.getBufferFormat().stencil > 0;
    }

    /**
     * Marks the polygons (world x,y pairs) in the stencil buffer. Until {@link #end()}, draws only land inside them.
     * {@code shapes} must have the world projection set and must not be drawing.
     */
    public void begin(ShapeRenderer shapes, Array<float[]> polygons) {
        GL20 gl = Gdx.gl;
        gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
        gl.glEnable(GL20.GL_STENCIL_TEST);
        gl.glColorMask(false, false, false, false);
        gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xFF);
        gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (float[] polygon : polygons) {
            if (polygon.length < 6) continue;
            ShortArray triangles = triangulator.computeTriangles(polygon);
            for (int i = 0; i + 2 < triangles.size; i += 3) {
                int a = triangles.get(i) * 2;
                int b = triangles.get(i + 1) * 2;
                int c = triangles.get(i + 2) * 2;
                shapes.triangle(polygon[a], polygon[a + 1], polygon[b], polygon[b + 1], polygon[c], polygon[c + 1]);
            }
        }
        shapes.end();
        gl.glColorMask(true, true, true, true);
        gl.glStencilFunc(GL20.GL_EQUAL, 1, 0xFF);
        gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
        active = true;
    }

    public void end() {
        if (!active) return;
        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
        active = false;
    }
}
