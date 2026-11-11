package com.parceltracker.service;

import java.util.List;
import org.springframework.http.HttpStatus;

public final class CoveragePolygon {
    private CoveragePolygon() {}

    public static void validate(List<List<Double>> polygon) {
        if (polygon == null || polygon.size() < 3 || polygon.size() > 100) throw invalid();
        for (List<Double> point : polygon) {
            if (point == null || point.size() != 2 || point.get(0) == null || point.get(1) == null
                    || !Double.isFinite(point.get(0)) || !Double.isFinite(point.get(1))
                    || point.get(0) < -44.0 || point.get(0) > -9.0
                    || point.get(1) < 112.0 || point.get(1) > 154.0) throw invalid();
        }
        double area = 0;
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            List<Double> a = polygon.get(i);
            List<Double> b = polygon.get((i + 1) % n);
            if (a.equals(b)) throw invalid();
            area += a.get(1) * b.get(0) - b.get(1) * a.get(0);
            for (int j = i + 1; j < n; j++) {
                if (j == i + 1 || (i == 0 && j == n - 1)) continue;
                if (intersects(a, b, polygon.get(j), polygon.get((j + 1) % n))) throw invalid();
            }
        }
        if (Math.abs(area) < 1e-10) throw invalid();
    }

    private static double cross(List<Double> a, List<Double> b, List<Double> p) {
        return (b.get(1) - a.get(1)) * (p.get(0) - a.get(0))
                - (b.get(0) - a.get(0)) * (p.get(1) - a.get(1));
    }

    private static boolean onSegment(List<Double> a, List<Double> b, List<Double> p) {
        return Math.abs(cross(a, b, p)) < 1e-10
                && p.get(0) >= Math.min(a.get(0), b.get(0)) - 1e-10
                && p.get(0) <= Math.max(a.get(0), b.get(0)) + 1e-10
                && p.get(1) >= Math.min(a.get(1), b.get(1)) - 1e-10
                && p.get(1) <= Math.max(a.get(1), b.get(1)) + 1e-10;
    }

    private static boolean intersects(List<Double> a, List<Double> b, List<Double> c, List<Double> d) {
        double abC = cross(a, b, c), abD = cross(a, b, d), cdA = cross(c, d, a), cdB = cross(c, d, b);
        return ((abC > 0 && abD < 0 || abC < 0 && abD > 0)
                && (cdA > 0 && cdB < 0 || cdA < 0 && cdB > 0))
                || onSegment(a, b, c) || onSegment(a, b, d) || onSegment(c, d, a) || onSegment(c, d, b);
    }

    public static boolean contains(List<List<Double>> polygon, double latitude, double longitude) {
        List<Double> point = List.of(latitude, longitude);
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            List<Double> a = polygon.get(j), b = polygon.get(i);
            if (onSegment(a, b, point)) return true;
            if ((a.get(0) > latitude) != (b.get(0) > latitude)
                    && longitude < (b.get(1) - a.get(1)) * (latitude - a.get(0))
                    / (b.get(0) - a.get(0)) + a.get(1)) inside = !inside;
        }
        return inside;
    }

    private static ServiceAreaException invalid() {
        return new ServiceAreaException(HttpStatus.BAD_REQUEST,
                "Each custom area must be a non-intersecting polygon with 3–100 points within Australia.");
    }
}
