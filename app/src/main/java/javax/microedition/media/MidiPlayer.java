/* Copyright 2026 J2ME-Loader contributors. Licensed under the Apache License, Version 2.0. */
package javax.microedition.media;

import android.media.MediaPlayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.microedition.media.control.MIDIControl;
import javax.microedition.util.ContextHolder;

/** MIDIControl backed by Android's API 10 MediaPlayer, not the removed native MidiDriver. */
public class MidiPlayer extends BasePlayer implements MIDIControl {
    private MediaPlayer player;
    private File midiFile;

    public MidiPlayer() {
        addControl(MIDIControl.class.getName(), this);
    }

    @Override public int[] getBankList(boolean custom) { return new int[0]; }
    @Override public int getChannelVolume(int channel) { return -1; }
    @Override public String getKeyName(int bank, int prog, int key) { return null; }
    @Override public int[] getProgram(int channel) { return new int[0]; }
    @Override public int[] getProgramList(int bank) { return new int[0]; }
    @Override public String getProgramName(int bank, int prog) { return ""; }
    @Override public boolean isBankQuerySupported() { return false; }

    @Override
    public int longMidiEvent(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 0 || offset > data.length - length) return -1;
        try {
            play(data, offset, length);
            return length;
        } catch (IOException e) {
            return -1;
        }
    }

    @Override public void setChannelVolume(int channel, int volume) { }
    @Override public void setProgram(int channel, int bank, int program) { }
    @Override public void shortMidiEvent(int type, int data1, int data2) { }

    private void play(byte[] data, int offset, int length) throws IOException {
        closePlayer();
        midiFile = File.createTempFile("j2me-midi", ".mid", ContextHolder.getCacheDir());
        FileOutputStream output = new FileOutputStream(midiFile);
        try { output.write(data, offset, length); } finally { output.close(); }
        player = new MediaPlayer();
        player.setDataSource(midiFile.getAbsolutePath());
        player.prepare();
        player.start();
    }

    @Override public void doStop() { if (player != null) player.pause(); }
    @Override public void doClose() { closePlayer(); }

    private void closePlayer() {
        if (player != null) { player.release(); player = null; }
        if (midiFile != null) { midiFile.delete(); midiFile = null; }
    }
}
