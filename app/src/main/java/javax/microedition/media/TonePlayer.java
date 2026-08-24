/* Copyright 2026 J2ME-Loader contributors. Licensed under the Apache License, Version 2.0. */
package javax.microedition.media;

import android.media.MediaPlayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.microedition.media.control.ToneControl;
import javax.microedition.media.tone.ToneSequence;
import javax.microedition.util.ContextHolder;

/** Converts J2ME tone sequences to a temporary MIDI file consumed by API 10 MediaPlayer. */
public class TonePlayer extends BasePlayer implements ToneControl {
    private byte[] midiSequence;
    private long duration;
    private MediaPlayer player;
    private File midiFile;

    public TonePlayer() {
        addControl(ToneControl.class.getName(), this);
    }

    @Override
    public void setSequence(byte[] sequence) {
        try {
            ToneSequence tone = new ToneSequence(sequence);
            tone.process();
            midiSequence = tone.getByteArray();
            duration = tone.getDuration();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid tone sequence", e);
        }
    }

    @Override public long doGetDuration() { return duration; }

    @Override
    public void doStart() {
        if (midiSequence == null) return;
        try {
            closePlayer();
            midiFile = File.createTempFile("j2me-tone", ".mid", ContextHolder.getCacheDir());
            FileOutputStream output = new FileOutputStream(midiFile);
            try { output.write(midiSequence); } finally { output.close(); }
            player = new MediaPlayer();
            player.setDataSource(midiFile.getAbsolutePath());
            player.prepare();
            player.start();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to play tone", e);
        }
    }

    @Override public void doStop() { if (player != null) player.pause(); }
    @Override public void doClose() { closePlayer(); }

    private void closePlayer() {
        if (player != null) { player.release(); player = null; }
        if (midiFile != null) { midiFile.delete(); midiFile = null; }
    }
}
