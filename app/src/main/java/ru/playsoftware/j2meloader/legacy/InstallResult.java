/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import java.io.File;

/** Result returned by the synchronous legacy installer. */
public final class InstallResult {
    public enum Status {
        INSTALLED,
        UPDATED,
        REJECTED,
        FAILED
    }

    private final Status status;
    private final File appDirectory;
    private final String name;
    private final String vendor;
    private final String version;
    private final String message;

    private InstallResult(Status status, File appDirectory, String name, String vendor,
                          String version, String message) {
        this.status = status;
        this.appDirectory = appDirectory;
        this.name = name;
        this.vendor = vendor;
        this.version = version;
        this.message = message;
    }

    public static InstallResult success(Status status, File appDirectory, String name,
                                        String vendor, String version) {
        if (status != Status.INSTALLED && status != Status.UPDATED) {
            throw new IllegalArgumentException("Not a success status: " + status);
        }
        return new InstallResult(status, appDirectory, name, vendor, version, null);
    }

    public static InstallResult rejected(String message) {
        return new InstallResult(Status.REJECTED, null, null, null, null, message);
    }

    public static InstallResult failed(String message) {
        return new InstallResult(Status.FAILED, null, null, null, null, message);
    }

    public Status getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status == Status.INSTALLED || status == Status.UPDATED;
    }

    public File getAppDirectory() {
        return appDirectory;
    }

    public String getName() {
        return name;
    }

    public String getVendor() {
        return vendor;
    }

    public String getVersion() {
        return version;
    }

    public String getMessage() {
        return message;
    }
}
