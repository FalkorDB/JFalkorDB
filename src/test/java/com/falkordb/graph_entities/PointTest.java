package com.falkordb.graph_entities;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class PointTest {

    @Test
    public void testConstructorWithLatitudeLongitude() {
        Point point = new Point(40.7128, -74.0060);

        assertEquals(40.7128, point.getLatitude());
        assertEquals(-74.0060, point.getLongitude());
    }

    @Test
    public void testConstructorWithList() {
        Point point = new Point(Arrays.asList(40.7128, -74.0060));

        assertEquals(40.7128, point.getLatitude());
        assertEquals(-74.0060, point.getLongitude());
    }

    @Test
    public void testConstructorWithNullList() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Point(null);
        });
    }

    @Test
    public void testConstructorWithEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Point(Collections.emptyList());
        });
    }

    @Test
    public void testConstructorWithSingleValueList() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Point(Collections.singletonList(40.7128));
        });
    }

    @Test
    public void testConstructorWithThreeValueList() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Point(Arrays.asList(40.7128, -74.0060, 100.0));
        });
    }

    @Test
    public void testGetLatitude() {
        Point point = new Point(51.5074, -0.1278);
        assertEquals(51.5074, point.getLatitude());
    }

    @Test
    public void testGetLongitude() {
        Point point = new Point(51.5074, -0.1278);
        assertEquals(-0.1278, point.getLongitude());
    }

    @Test
    public void testEqualsWithSameObject() {
        Point point = new Point(40.7128, -74.0060);
        assertEquals(point, point);
    }

    @Test
    public void testEqualsWithEqualPoints() {
        Point point1 = new Point(40.7128, -74.0060);
        Point point2 = new Point(40.7128, -74.0060);

        assertEquals(point1, point2);
    }

    @Test
    public void testEqualsWithClosePoints() {
        // Points within EPSILON (1e-5) should be equal
        Point point1 = new Point(40.7128, -74.0060);
        Point point2 = new Point(40.71280001, -74.00600001);

        assertEquals(point1, point2);
    }

    @Test
    public void testEqualsWithDifferentLatitude() {
        Point point1 = new Point(40.7128, -74.0060);
        Point point2 = new Point(51.5074, -74.0060);

        assertNotEquals(point1, point2);
    }

    @Test
    public void testEqualsWithDifferentLongitude() {
        Point point1 = new Point(40.7128, -74.0060);
        Point point2 = new Point(40.7128, -0.1278);

        assertNotEquals(point1, point2);
    }

    @Test
    public void testEqualsWithNull() {
        Point point = new Point(40.7128, -74.0060);
        assertNotEquals(point, null);
    }

    @Test
    public void testEqualsWithDifferentType() {
        Point point = new Point(40.7128, -74.0060);
        assertNotEquals(point, "some string");
    }

    @Test
    public void testHashCode() {
        Point point1 = new Point(40.7128, -74.0060);
        Point point2 = new Point(40.7128, -74.0060);

        assertEquals(point1.hashCode(), point2.hashCode());
    }

    @Test
    public void testPointInequality() {
        Point point1 = new Point(40.7128, -74.0060);
        Point point2 = new Point(51.5074, -0.1278);

        assertNotEquals(point1, point2);
    }

    @Test
    public void testToString() {
        Point point = new Point(40.7128, -74.0060);
        String result = point.toString();

        assertTrue(result.contains("Point{"));
        assertTrue(result.contains("latitude="));
        assertTrue(result.contains("longitude="));
        assertTrue(result.contains("40.7128"));
        assertTrue(result.contains("-74.006"));
    }

    @Test
    public void testPointWithZeroCoordinates() {
        Point point = new Point(0.0, 0.0);

        assertEquals(0.0, point.getLatitude());
        assertEquals(0.0, point.getLongitude());
    }

    @Test
    public void testPointWithNegativeCoordinates() {
        Point point = new Point(-33.8688, -151.2093);

        assertEquals(-33.8688, point.getLatitude());
        assertEquals(-151.2093, point.getLongitude());
    }

    @Test
    public void testPointWithBoundaryValues() {
        // Test max latitude
        Point point1 = new Point(90.0, 0.0);
        assertEquals(90.0, point1.getLatitude());

        // Test min latitude
        Point point2 = new Point(-90.0, 0.0);
        assertEquals(-90.0, point2.getLatitude());

        // Test max longitude
        Point point3 = new Point(0.0, 180.0);
        assertEquals(180.0, point3.getLongitude());

        // Test min longitude
        Point point4 = new Point(0.0, -180.0);
        assertEquals(-180.0, point4.getLongitude());
    }

    @Test
    public void equalsHashCodeContract() {
        EqualsVerifier.forClass(Point.class).verify();
    }

    @Test
    public void toleratesSinglePrecisionRoundTripDrift() {
        // FalkorDB returns points in single precision, so the original and the round-tripped value
        // differ slightly (see GraphAPIIT#testGeoPointLatLon). They must still be equal AND share a
        // hashCode — the regression that the old epsilon-equals/exact-hashCode pair violated.
        Point original = new Point(30.27822306, -97.75134723);
        Point roundTripped = new Point(30.2782230377197, -97.751350402832);
        assertEquals(original, roundTripped);
        assertEquals(original.hashCode(), roundTripped.hashCode());
    }

    @Test
    public void distinctPointsAreNotEqual() {
        assertNotEquals(new Point(1.0, 2.0), new Point(3.0, 4.0));
        assertNotEquals(new Point(1.0, 2.0), new Point(1.0, 9.0));
    }

    @Test
    public void rejectsNonFiniteCoordinates() {
        // Non-finite coordinates have no grid cell (Math.round(NaN) == 0 would collide with 0.0),
        // so both constructors reject them fail-fast.
        assertThrows(IllegalArgumentException.class, () -> new Point(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Point(0.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Point(Double.POSITIVE_INFINITY, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Point(0.0, Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new Point(Arrays.asList(Double.NaN, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> new Point(Arrays.asList(0.0, Double.POSITIVE_INFINITY)));
    }

    @Test
    public void rejectsNullListElements() {
        // A null element unboxes to no coordinate, so the list constructor rejects it fail-fast
        // rather than throwing a NullPointerException.
        assertThrows(IllegalArgumentException.class, () -> new Point(Arrays.<Double>asList(null, 2.0)));
        assertThrows(IllegalArgumentException.class, () -> new Point(Arrays.<Double>asList(1.0, null)));
    }
}
