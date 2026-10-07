package io.github.gamePackage.render;

import com.badlogic.gdx.math.Intersector;

/** Geometry on polygons stored as x,y pairs. Works for concave polygons too. */
public final class Polygons {
    private Polygons() {
    }

    /** Whether two polygons touch: an edge of one crosses the other, or one lies inside the other. */
    public static boolean overlap(float[] a, float[] b) {
        if (a.length < 6 || b.length < 6) return false;
        if (!boundsOverlap(a, b)) return false;
        if (Intersector.isPointInPolygon(b, 0, b.length, a[0], a[1])) return true;
        if (Intersector.isPointInPolygon(a, 0, a.length, b[0], b[1])) return true;
        for (int i = 0; i < a.length; i += 2) {
            int ni = (i + 2) % a.length;
            for (int j = 0; j < b.length; j += 2) {
                int nj = (j + 2) % b.length;
                if (Intersector.intersectSegments(a[i], a[i + 1], a[ni], a[ni + 1],
                    b[j], b[j + 1], b[nj], b[nj + 1], null)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean contains(float[] polygon, float x, float y) {
        return polygon.length >= 6 && Intersector.isPointInPolygon(polygon, 0, polygon.length, x, y);
    }

    private static boolean boundsOverlap(float[] a, float[] b) {
        return min(a, 0) <= max(b, 0) && min(b, 0) <= max(a, 0)
            && min(a, 1) <= max(b, 1) && min(b, 1) <= max(a, 1);
    }

    private static float min(float[] points, int axis) {
        float value = Float.MAX_VALUE;
        for (int i = axis; i < points.length; i += 2) value = Math.min(value, points[i]);
        return value;
    }

    private static float max(float[] points, int axis) {
        float value = -Float.MAX_VALUE;
        for (int i = axis; i < points.length; i += 2) value = Math.max(value, points[i]);
        return value;
    }
}
