package com.falkordb.graph_entities;

import java.util.List;
import java.util.Objects;

/**
 * This class represents a (geographical) point in the graph.
 */
public final class Point {

    // FalkorDB stores point coordinates in single precision, so a round-tripped coordinate drifts
    // slightly from the original. Coordinates are quantized onto a 1e-5 grid (see cell()) before
    // comparison, so nearby values usually collapse to the same cell while equals stays reflexive,
    // symmetric and transitive, and consistent with hashCode - unlike a raw epsilon "within
    // tolerance" check, which is non-transitive and can't have a matching hashCode. The trade-off is
    // that two coordinates straddling a cell boundary compare unequal even when close.
    private static final double GRID = 1e-5;

    private final double latitude;
    private final double longitude;

    // Coordinates are guaranteed finite by the constructors, so Math.round never sees NaN/Infinity
    // (Math.round(NaN) == 0 would otherwise let a non-finite coordinate collide with grid cell 0).
    private static long cell(double coordinate) {
        return Math.round(coordinate / GRID);
    }

    private static double requireFinite(double coordinate, String name) {
        if (!Double.isFinite(coordinate)) {
            throw new IllegalArgumentException(name + " must be a finite number but was " + coordinate);
        }
        return coordinate;
    }

    /**
     * @param latitude The latitude in degrees, normally in the range [-90.0, +90.0]. Must be finite.
     * @param longitude The longitude in degrees, normally in the range [-180.0, +180.0]. Must be finite.
     * @throws IllegalArgumentException if either coordinate is NaN or infinite
     */
    public Point(double latitude, double longitude) {
        this.latitude = requireFinite(latitude, "latitude");
        this.longitude = requireFinite(longitude, "longitude");
    }

    /**
     * @param values {@code [latitude, longitude]}
     * @throws IllegalArgumentException if {@code values} is not exactly two finite doubles
     */
    public Point(List<Double> values) {
        if (values == null || values.size() != 2) {
            throw new IllegalArgumentException("Point requires two doubles.");
        }
        Double lat = values.get(0);
        Double lon = values.get(1);
        if (lat == null || lon == null) {
            throw new IllegalArgumentException("Point requires two doubles.");
        }
        this.latitude = requireFinite(lat, "latitude");
        this.longitude = requireFinite(lon, "longitude");
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
