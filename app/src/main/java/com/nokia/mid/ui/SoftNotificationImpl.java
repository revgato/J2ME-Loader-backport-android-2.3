/* Copyright 2026 J2ME-Loader contributors. Licensed under the Apache License, Version 2.0. */
package com.nokia.mid.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.Hashtable;

/** Notifications are outside the legacy MVP; retain callbacks without API 26 notification code. */
public class SoftNotificationImpl extends SoftNotification {
    final static int EVENT_ACCEPT = 1;
    final static int EVENT_DISMISS = 2;
    static Hashtable<Integer, SoftNotificationImpl> instanceMap = new Hashtable<Integer, SoftNotificationImpl>();
    private static int ids = 1;
    private SoftNotificationListener listener;
    private int id;
    private boolean posted;
    private Bitmap bitmap;

    public SoftNotificationImpl(int notificationId) { id = notificationId; }
    public SoftNotificationImpl() { this(-1); }
    void notificationCallback(int eventArg) {
        if (listener == null) return;
        if (eventArg == EVENT_ACCEPT) listener.notificationSelected(this);
        if (eventArg == EVENT_DISMISS) listener.notificationDismissed(this);
    }
    public int getId() { return posted ? id : -1; }
    public void post() throws SoftNotificationException {
        if (id == -1) id = ids++;
        posted = true;
        instanceMap.put(id, this);
    }
    public void remove() throws SoftNotificationException {
        if (!posted) throw new SoftNotificationException("not posted");
        posted = false;
        instanceMap.remove(id);
    }
    public void setListener(SoftNotificationListener listener) { this.listener = listener; }
    public void setText(String text, String groupText) throws SoftNotificationException { }
    public void setSoftkeyLabels(String softkey1Label, String softkey2Label) throws SoftNotificationException { }
    public void setImage(byte[] imageData) throws SoftNotificationException {
        bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
        if (bitmap == null) throw new SoftNotificationException("Can't decode image");
    }
}
