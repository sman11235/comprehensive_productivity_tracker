package saket.consumer.services;

import java.util.List;
import java.util.Optional;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

import lombok.NoArgsConstructor;
import saket.consumer.exceptions.IllegalLatitudeException;
import saket.consumer.exceptions.IllegalLongitudeException;

/**
 * A util class that is in charge of centralizing boundary checking logic and point formatting.
 */
@NoArgsConstructor
@Service
public final class PointUtil {
    private static final int SRID_WGS84 = 4326;
    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), SRID_WGS84);
    private static final double R = 6_371_000.0;

    /**
     * A function that returns a point in wg84 format.
     * @param lat the latitude of the point
     * @param lon the longitude of the point
     * @return a point in wg84 format (SRID 4326).
     */
    public static Point wgs84FromLatLon(double lat, double lon) {
        if (lat < -90 || lat > 90) 
            throw new IllegalLatitudeException("Latitude is either greater than 90 or less than -90, and thus invalid.");
        if (lon < -180 || lon > 180) 
            throw new IllegalLongitudeException("Longitude is either greater than 90 or less than -90, and thus invalid.");
        Point p = GF.createPoint(new Coordinate(lon, lat));
        p.setSRID(SRID_WGS84);
        return p;
    }

    /**
     * Gets the meter distance between to (lon, lat) Points.
     * @param a A (lon, lat) point
     * @param b A (lon, lat) point
     * @return the distance between a and b in meters.
     */
    public static double distanceInMeters(Point a, Point b) {
        // JTS convention: x = lon, y = lat
        double lat1 = Math.toRadians(a.getY());
        double lon1 = Math.toRadians(a.getX());
        double lat2 = Math.toRadians(b.getY());
        double lon2 = Math.toRadians(b.getX());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double sinDLat = Math.sin(dLat / 2.0);
        double sinDLon = Math.sin(dLon / 2.0);

        double h = sinDLat * sinDLat
                 + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;

        double c = 2.0 * Math.asin(Math.min(1.0, Math.sqrt(h)));
        return R * c;
    }

    /**
     * Gets the centroid from a list of points.
     * @param points the list of points.
     * @return the centroid of the list points.
     */
    public static Optional<Point> centroid(List<Point> points) {
        if (points.isEmpty()) {
            return Optional.empty();
        }
        double latSum = 0.0, lonSum = 0.0;
        int n = points.size();
        for (Point point : points) {
            lonSum += point.getX();
            latSum += point.getY();
        }

        return Optional.of(wgs84FromLatLon(latSum / n, lonSum / n));
    }

}