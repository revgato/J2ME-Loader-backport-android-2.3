/* Copyright 2012-2026 J2ME-Loader contributors. Licensed under the Apache License, Version 2.0. */
package javax.microedition.media;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.microedition.media.protocol.DataSource;
import javax.microedition.media.protocol.SourceStream;
import javax.microedition.util.ContextHolder;

/** Copies a local stream to a MediaPlayer-readable file; no FFmpeg/native conversion is used. */
public class InternalDataSource extends DataSource {
    private final File mediaFile;
    private final String type;

    public InternalDataSource(InputStream stream, String type) throws IllegalArgumentException, IOException {
        super(null);
        if (stream == null) throw new IllegalArgumentException("stream");
        this.type = type;
        mediaFile = File.createTempFile("j2me", extensionFor(type), ContextHolder.getCacheDir());
        BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(mediaFile));
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) output.write(buffer, 0, read);
        } finally {
            output.close();
            stream.close();
        }
    }

    private static String extensionFor(String type) {
        if (type == null) return ".bin";
        if (type.equalsIgnoreCase("audio/midi") || type.equalsIgnoreCase("audio/x-midi")) return ".mid";
        if (type.equalsIgnoreCase("audio/wav") || type.equalsIgnoreCase("audio/x-wav")) return ".wav";
        if (type.equalsIgnoreCase("audio/mpeg") || type.equalsIgnoreCase("audio/mp3")) return ".mp3";
        return ".bin";
    }

    @Override public String getLocator() { return mediaFile.getAbsolutePath(); }
    @Override public String getContentType() { return type; }
    @Override public void connect() throws IOException { }
    @Override public void disconnect() { mediaFile.delete(); }
    @Override public void start() throws IOException { }
    @Override public void stop() throws IOException { }
    @Override public SourceStream[] getStreams() { return new SourceStream[0]; }
    @Override public Control[] getControls() { return new Control[0]; }
    @Override public Control getControl(String control) { return null; }
}
