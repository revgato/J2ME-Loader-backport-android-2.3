package javax.microedition.media;

final class LegacyUnsupported {
    private LegacyUnsupported() { }

    static UnsupportedOperationException camera() {
        return new UnsupportedOperationException("Camera is unsupported on the IS14SH legacy build");
    }
}
