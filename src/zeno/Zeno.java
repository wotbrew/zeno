package zeno;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.lwjgl.opengl.GL11;

public abstract class Zeno {

    private static Thread glThread;
    private static SpriteBatch spriteBatch;
    private static OrthographicCamera camera;
    private static BitmapFont defaultFont;

    public static void init(){
        spriteBatch = new SpriteBatch();
        camera = new OrthographicCamera();
        defaultFont = new BitmapFont(true);
        glThread = Thread.currentThread();
    }

    public static Thread getGlThread() {
        return glThread;
    }

    private static boolean drawing = false;

    public static void begin() {
        GL11.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        camera.setToOrtho(true);
        spriteBatch.setProjectionMatrix(camera.combined);

        spriteBatch.begin();

        drawing = true;
    }

    public static void end() {
        spriteBatch.end();
        drawing = false;
    }

    public static void drawTextureRegion(TextureRegion r, float x, float y, float w, float h) {
        spriteBatch.draw(r, x, y, w, h);
    }

    private static TextureRegion tRegionProxy = new TextureRegion();

    public static void drawTexture(Texture t, float x, float y, float w, float h) {
        tRegionProxy.setTexture(t);
        tRegionProxy.setRegion(0, 0, t.getWidth(), t.getHeight());
        tRegionProxy.flip(false, true);

        drawTextureRegion(tRegionProxy, x, y, w, h);
    }

    public static void drawString(String s, BitmapFont font, float x, float y, float w, int align) {
        font.draw(spriteBatch, s, x, y, w, align, true);
    }

    public static void drawDefaultString(String s, float x, float y, float w, int align) {
        drawString(s, defaultFont, x, y, w, align);
    }
}
