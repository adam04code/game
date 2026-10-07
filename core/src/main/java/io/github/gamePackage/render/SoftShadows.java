package io.github.gamePackage.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ShortArray;

/**
 * Soft shadows: shadow polygons are drawn into a low-resolution mask, blurred, and then used to darken whatever
 * is drawn next, by screen position. Two uses:
 * <ul>
 *   <li>{@link #drawGroundShadow}: a soft dark layer over the ground.</li>
 *   <li>{@link #beginReceiver}/{@link #endReceiver}: sprites drawn in between are darkened where the mask is set,
 *   for an object standing in another object's shadow.</li>
 * </ul>
 * Call {@link #setViewport} each frame with the area being rendered.
 */
public class SoftShadows implements Disposable {
    private static final String VERTEX = "attribute vec4 a_position;\n"
        + "attribute vec4 a_color;\n"
        + "attribute vec2 a_texCoord0;\n"
        + "uniform mat4 u_projTrans;\n"
        + "varying vec4 v_color;\n"
        + "varying vec2 v_texCoords;\n"
        + "void main() {\n"
        + "    v_color = a_color;\n"
        + "    v_color.a = v_color.a * (255.0 / 254.0);\n"
        + "    v_texCoords = a_texCoord0;\n"
        + "    gl_Position = u_projTrans * a_position;\n"
        + "}\n";

    /** Separable gaussian (9 taps folded into 5 linear samples) along u_dir. */
    private static final String BLUR = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
        + "varying vec4 v_color;\n"
        + "varying vec2 v_texCoords;\n"
        + "uniform sampler2D u_texture;\n"
        + "uniform vec2 u_dir;\n"
        + "void main() {\n"
        + "    float s = texture2D(u_texture, v_texCoords).r * 0.2270270270;\n"
        + "    s += texture2D(u_texture, v_texCoords + u_dir * 1.3846153846).r * 0.3162162162;\n"
        + "    s += texture2D(u_texture, v_texCoords - u_dir * 1.3846153846).r * 0.3162162162;\n"
        + "    s += texture2D(u_texture, v_texCoords + u_dir * 3.2307692308).r * 0.0702702703;\n"
        + "    s += texture2D(u_texture, v_texCoords - u_dir * 3.2307692308).r * 0.0702702703;\n"
        + "    gl_FragColor = vec4(s, s, s, 1.0);\n"
        + "}\n";

    /** Sprite shader that darkens by the mask at this pixel's screen position. */
    private static final String RECEIVER = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
        + "varying vec4 v_color;\n"
        + "varying vec2 v_texCoords;\n"
        + "uniform sampler2D u_texture;\n"
        + "uniform sampler2D u_mask;\n"
        + "uniform vec4 u_viewport;\n"
        + "uniform float u_darkness;\n"
        + "void main() {\n"
        + "    vec4 c = v_color * texture2D(u_texture, v_texCoords);\n"
        + "    vec2 uv = (gl_FragCoord.xy - u_viewport.xy) / u_viewport.zw;\n"
        + "    float shade = texture2D(u_mask, uv).r * u_darkness;\n"
        + "    gl_FragColor = vec4(c.rgb * (1.0 - shade), c.a);\n"
        + "}\n";

    /** Black with the mask as opacity, for shadows on the ground. */
    private static final String GROUND = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
        + "varying vec4 v_color;\n"
        + "varying vec2 v_texCoords;\n"
        + "uniform sampler2D u_texture;\n"
        + "uniform sampler2D u_mask;\n"
        + "uniform vec4 u_viewport;\n"
        + "uniform float u_darkness;\n"
        + "void main() {\n"
        + "    vec2 uv = (gl_FragCoord.xy - u_viewport.xy) / u_viewport.zw;\n"
        + "    float coverage = texture2D(u_texture, v_texCoords).a;\n"
        + "    gl_FragColor = vec4(0.0, 0.0, 0.0, texture2D(u_mask, uv).r * u_darkness * coverage);\n"
        + "}\n";

    private final ShaderProgram blurShader = compile(BLUR);
    private final ShaderProgram receiverShader = compile(RECEIVER);
    private final ShaderProgram groundShader = compile(GROUND);
    private final SpriteBatch passBatch = new SpriteBatch(4);
    private final EarClippingTriangulator triangulator = new EarClippingTriangulator();
    private final Matrix4 passProjection = new Matrix4();
    private final Texture white;
    private FrameBuffer mask;
    private FrameBuffer scratch;

    /** Viewport in back-buffer pixels. */
    private int viewX;
    private int viewY;
    private int viewWidth = 1;
    private int viewHeight = 1;
    private int quality = 2;
    private float darkness = 0.4f;
    private boolean receiving;

    public SoftShadows() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        white = new Texture(pixmap);
        pixmap.dispose();
    }

    /**
     * The on-screen area being rendered, in logical screen coordinates (as a Viewport's screen bounds), plus the
     * mask resolution divisor (1, 2 or 4) and ground shadow darkness.
     */
    public void setViewport(int screenX, int screenY, int screenWidth, int screenHeight, int quality,
                            float darkness) {
        float scale = Gdx.graphics.getBackBufferWidth() / (float) Math.max(1, Gdx.graphics.getWidth());
        viewX = Math.round(screenX * scale);
        viewY = Math.round(screenY * scale);
        viewWidth = Math.max(1, Math.round(screenWidth * scale));
        viewHeight = Math.max(1, Math.round(screenHeight * scale));
        this.quality = Math.max(1, quality);
        this.darkness = darkness;
        int width = Math.max(1, viewWidth / this.quality);
        int height = Math.max(1, viewHeight / this.quality);
        if (mask == null || mask.getWidth() != width || mask.getHeight() != height) {
            if (mask != null) mask.dispose();
            if (scratch != null) scratch.dispose();
            mask = createBuffer(width, height);
            scratch = createBuffer(width, height);
        }
    }

    /**
     * Draws the polygons (world x,y pairs) into the mask with {@code projection} (the world camera) and blurs it by
     * {@code blurScreenPixels}. Restores the GL viewport to the rendered area afterwards.
     */
    public void buildMask(ShapeRenderer shapes, Matrix4 projection, Array<float[]> polygons, float blurScreenPixels,
                          int passes) {
        mask.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.WHITE);
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
        mask.end();

        // Each pass blurs horizontally into scratch and vertically back; the spread splits the radius across passes.
        float spread = blurScreenPixels / quality / 3.23f / (float) Math.sqrt(Math.max(1, passes));
        if (spread > 0.05f) {
            for (int pass = 0; pass < passes; pass++) {
                blurInto(mask, scratch, spread / mask.getWidth(), 0f);
                blurInto(scratch, mask, 0f, spread / mask.getHeight());
            }
        }
        Gdx.gl.glViewport(viewX, viewY, viewWidth, viewHeight);
    }

    /** Draws the mask as a soft black layer over the area the camera sees ({@code batch} must not be drawing). */
    public void drawGroundShadow(SpriteBatch batch, float worldX, float worldY, float worldWidth, float worldHeight) {
        mask.getColorBufferTexture().bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        batch.setShader(groundShader);
        batch.begin();
        setMaskUniforms(groundShader, darkness);
        batch.draw(white, worldX, worldY, worldWidth, worldHeight);
        batch.end();
        batch.setShader(null);
    }

    /** Sprites drawn with {@code batch} until {@link #endReceiver} are darkened by the mask, up to the darkness. */
    public void beginReceiver(SpriteBatch batch, float receiverDarkness) {
        mask.getColorBufferTexture().bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        batch.setShader(receiverShader);
        batch.begin();
        setMaskUniforms(receiverShader, receiverDarkness);
        receiving = true;
    }

    public void endReceiver(SpriteBatch batch) {
        if (!receiving) return;
        batch.end();
        batch.setShader(null);
        receiving = false;
    }

    private void setMaskUniforms(ShaderProgram shader, float shadeDarkness) {
        shader.setUniformi("u_mask", 1);
        shader.setUniformf("u_viewport", viewX, viewY, viewWidth, viewHeight);
        shader.setUniformf("u_darkness", shadeDarkness);
    }

    private void blurInto(FrameBuffer source, FrameBuffer target, float dirX, float dirY) {
        target.begin();
        passProjection.setToOrtho2D(0, 0, target.getWidth(), target.getHeight());
        passBatch.setProjectionMatrix(passProjection);
        passBatch.setShader(blurShader);
        passBatch.disableBlending();
        passBatch.begin();
        blurShader.setUniformf("u_dir", dirX, dirY);
        passBatch.draw(source.getColorBufferTexture(), 0, 0, target.getWidth(), target.getHeight());
        passBatch.end();
        target.end();
    }

    private static FrameBuffer createBuffer(int width, int height) {
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        buffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return buffer;
    }

    private static ShaderProgram compile(String fragment) {
        ShaderProgram program = new ShaderProgram(VERTEX, fragment);
        if (!program.isCompiled()) throw new IllegalStateException("Shadow shader failed: " + program.getLog());
        return program;
    }

    @Override
    public void dispose() {
        blurShader.dispose();
        receiverShader.dispose();
        groundShader.dispose();
        passBatch.dispose();
        white.dispose();
        if (mask != null) mask.dispose();
        if (scratch != null) scratch.dispose();
    }
}
