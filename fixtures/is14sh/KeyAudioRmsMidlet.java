package fixture;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

/**
 * Source-only acceptance fixture. Build it with a CLDC/MIDP toolchain and keep
 * the resulting JAR outside Git. It exercises keypad, RMS and supported audio
 * locators without relying on a user game.
 */
public final class KeyAudioRmsMidlet extends MIDlet {
    private FixtureCanvas canvas;
    private RecordStore rms;

    protected void startApp() throws Exception {
        rms = RecordStore.openRecordStore("is14sh-fixture", true);
        if (rms.getNumRecords() == 0) {
            rms.addRecord(new byte[] {0}, 0, 1);
        }
        canvas = new FixtureCanvas();
        Display.getDisplay(this).setCurrent(canvas);
    }

    protected void pauseApp() { }

    protected void destroyApp(boolean unconditional) throws Exception {
        if (rms != null) {
            rms.closeRecordStore();
        }
    }

    private static final class FixtureCanvas extends Canvas {
        private int lastKey;

        protected void paint(Graphics graphics) {
            graphics.setColor(0x101820);
            graphics.fillRect(0, 0, getWidth(), getHeight());
            graphics.setColor(0xffffff);
            graphics.drawString("IS14SH keypad/RMS/audio", 4, 4, Graphics.TOP | Graphics.LEFT);
            graphics.drawString("last=" + lastKey, 4, 24, Graphics.TOP | Graphics.LEFT);
            graphics.drawString("0-9 * # / D-pad / Enter", 4, 44, Graphics.TOP | Graphics.LEFT);
        }

        protected void keyPressed(int keyCode) {
            lastKey = keyCode;
            repaint();
            if (keyCode == KEY_NUM5) {
                try {
                    Player player = Manager.createPlayer(new ByteArrayInputStream(new byte[0]), "audio/midi");
                    player.close();
                } catch (Exception ignored) { }
            }
        }
    }
}
