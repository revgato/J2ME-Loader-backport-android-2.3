/* Copyright 2026 J2ME-Loader contributors. Licensed under the Apache License, Version 2.0. */
package javax.microedition.location;

/** Location is outside the local-only IS14SH MVP and fails explicitly. */
public abstract class LocationProvider {
    public static final int AVAILABLE = 1;
    public static final int TEMPORARILY_UNAVAILABLE = 2;
    public static final int OUT_OF_SERVICE = 3;

    public static LocationProvider getInstance(Criteria criteria) throws LocationException {
        throw new LocationException("Location is unsupported on the IS14SH legacy build");
    }

    public abstract Location getLocation(int timeout) throws LocationException, InterruptedException;
    public abstract void setLocationListener(LocationListener listener, int interval, int timeout, int maxAge);
    public static Location getLastKnownLocation() { return null; }
    public abstract int getState();
    public abstract void reset();

    public static void addProximityListener(ProximityListener listener, Coordinates coordinates,
                                             float proximityRadius) throws LocationException {
        throw new LocationException("Location is unsupported on the IS14SH legacy build");
    }

    public static void removeProximityListener(ProximityListener listener) { }
}
