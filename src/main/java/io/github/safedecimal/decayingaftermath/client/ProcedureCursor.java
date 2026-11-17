package io.github.safedecimal.decayingaftermath.client;

import org.lwjgl.sdl.SDLMouse;

/** Owns only cursor visibility, never mouse capture or absolute pointer coordinates. */
public final class ProcedureCursor {
    public static final int VISIBLE=1,HIDDEN=0;
    public interface ModeDriver {
        int get(long window);
        void set(long window,int mode);
    }
    private final ModeDriver driver;
    private long ownedWindow;
    private int previousMode;

    public ProcedureCursor() {
        this(new ModeDriver() {
            public int get(long window) {return SDLMouse.SDL_CursorVisible()?VISIBLE:HIDDEN;}
            public void set(long window,int mode) {
                if(mode==VISIBLE)SDLMouse.SDL_ShowCursor();else SDLMouse.SDL_HideCursor();
            }
        });
    }
    public ProcedureCursor(ModeDriver driver) {this.driver=driver;}
    public void hide(long window) {
        if(window==0)return;
        if(ownedWindow!=window) {
            restore();previousMode=driver.get(window);ownedWindow=window;
        }
        if(driver.get(window)!=HIDDEN)driver.set(window,HIDDEN);
    }
    public void restore() {
        if(ownedWindow!=0) {
            long window=ownedWindow;ownedWindow=0;driver.set(window,previousMode);
        }
    }
}
