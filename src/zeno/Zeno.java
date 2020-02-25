package zeno;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public abstract class Zeno {

    private static Thread glThread;
    private static SpriteBatch spriteBatch;
    private static OrthographicCamera camera;

    private static BitmapFont yDownFont;
    private static BitmapFont yUpFont;

    public static void init(){
        spriteBatch = new SpriteBatch();
        camera = new OrthographicCamera();

        yDownFont = new BitmapFont(true);
        yUpFont = new BitmapFont();

        glThread = Thread.currentThread();
    }

    public static Thread getGlThread() {
        return glThread;
    }

    public static SpriteBatch getSpriteBatch() {
        return spriteBatch;
    }

    public static OrthographicCamera getCamera() {
        return camera;
    }

    public static BitmapFont getUpFont() {
        return yUpFont;
    }

    public static BitmapFont getDownFont(){
        return yDownFont;
    }



}
