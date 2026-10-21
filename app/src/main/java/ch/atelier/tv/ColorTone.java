package ch.atelier.tv;

/** Small colour calculations shared by the text scrims and the listening palette. */
final class ColorTone {
    private static final double[] LINEAR = new double[256];
    static { for (int i = 0; i < LINEAR.length; i++) LINEAR[i] = linear(i / 255d); }
    static double luminance(int color) {
        return .2126 * LINEAR[color >> 16 & 255] + .7152 * LINEAR[color >> 8 & 255] + .0722 * LINEAR[color & 255];
    }
    private static double linear(double value) { return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4); }
    static int mix(int first, int second, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        return 0xFF000000 | Math.round((first >> 16 & 255) * (1 - t) + (second >> 16 & 255) * t) << 16 |
            Math.round((first >> 8 & 255) * (1 - t) + (second >> 8 & 255) * t) << 8 |
            Math.round((first & 255) * (1 - t) + (second & 255) * t);
    }
    static float shade(int background, int foreground) {
        double limit = (luminance(foreground) + .05) / 4.5 - .05;
        if (luminance(background) <= limit) return 0;
        float low = 0, high = .8f;
        for (int i = 0; i < 9; i++) {
            float middle = (low + high) / 2;
            if (luminance(mix(background, 0xFF000000, middle)) > limit) low = middle; else high = middle;
        }
        return high;
    }
}
