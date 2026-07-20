package com.falkordb.graph_entities;

import java.util.List;
import java.util.Objects;

/**
 * This class represents a (geographical) point in the graph.
 */
public final class Point {

    // FalkorDB stores point coordinates in single precision, so a round-tripped coordinate can differ
    // from the original by up to ~1e-5. Coordinates are therefore compared on a 1e-5 grid: this keeps
    // equals reflexive/symmetric/**transitive** and consistent with hashCode — unlike a raw epsilon
    // "within tolerance" check, which is non-transitive and can't have a matching hashCode.
    private static final double GRID = 1e-5;

    private final double latitude;
    private final double longitude;

    private static long cell(double coordinate) {
        return Math.round(coordinate / GRID);
    }

    /**
     * @param latitude The latitude in degrees. It must be in the range [-90.0, +90.0]
     * @param longitude The longitude in degrees. It must be in the range [-180.0, +180.0]
     */
    public Point(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * @param values {@code [latitude, longitude]}
     */
    public Point(List<Double> values) {
        if (values == null || values.size() != 2) {
            throw new IllegalArgumentException("Point requires two doubles.");
        }
        this.latitude = values.get(0);
        this.longitude = values.get(1);
    }

    /**
     * Get the latitude in degrees
     * @return latitude
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * Get the longitude in degrees
     * @return longitude
     */
    public double getLongitude() {
        return longitude;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Point)) {
            return false;
        }
        Point o = (Point) other;
        return cell(latitude) == cell(o.latitude) && cell(longitude) == cell(o.longitude);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cell(latitude), cell(longitude));
    }

    @Override
    public String toString() {
        return "Point{latitude=" + latitude + ", longitude=" + longitude + "}";
    }
}
