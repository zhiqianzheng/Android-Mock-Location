// Modified by zhiqianzheng on 2026-10-17: address-search fork changes, GPL-2.0; original notices retained.
package com.github.warren_bank.mock_location.address;

import com.github.warren_bank.mock_location.data_model.LocPoint;

/** Approximate GCJ-02 inverse for GPS input. Arguments are always latitude first. */
public final class CoordinateTransform {
    private static final double A = 6378245.0, EE = 0.00669342162296594323;
    private CoordinateTransform() {}

    public static LocPoint gcj02ToWgs84(double lat, double lon) {
        LocPoint.validate(lat, lon);
        if (outsideChina(lat, lon)) return new LocPoint(lat, lon);
        double wLat = lat, wLon = lon;
        for (int i = 0; i < 10; i++) {
            LocPoint projected = wgs84ToGcj02(wLat, wLon);
            double latError = projected.getLatitude() - lat, lonError = projected.getLongitude() - lon;
            wLat -= latError;
            wLon -= lonError;
            if (Math.abs(latError) + Math.abs(lonError) < 1e-10) break;
        }
        return new LocPoint(wLat, wLon);
    }

    public static LocPoint wgs84ToGcj02(double lat, double lon) {
        LocPoint.validate(lat, lon);
        if (outsideChina(lat, lon)) return new LocPoint(lat, lon);
        double x = lon - 105, y = lat - 35, pi = Math.PI;
        double dLat = -100 + 2*x + 3*y + .2*y*y + .1*x*y + .2*Math.sqrt(Math.abs(x));
        dLat += (20*Math.sin(6*x*pi) + 20*Math.sin(2*x*pi))*2/3;
        dLat += (20*Math.sin(y*pi) + 40*Math.sin(y*pi/3))*2/3;
        dLat += (160*Math.sin(y*pi/12) + 320*Math.sin(y*pi/30))*2/3;
        double dLon = 300 + x + 2*y + .1*x*x + .1*x*y + .1*Math.sqrt(Math.abs(x));
        dLon += (20*Math.sin(6*x*pi) + 20*Math.sin(2*x*pi))*2/3;
        dLon += (20*Math.sin(x*pi) + 40*Math.sin(x*pi/3))*2/3;
        dLon += (150*Math.sin(x*pi/12) + 300*Math.sin(x*pi/30))*2/3;
        double rad = lat/180*pi, magic = 1 - EE*Math.sin(rad)*Math.sin(rad);
        dLat = dLat*180/(A*(1-EE)/(magic*Math.sqrt(magic))*pi);
        dLon = dLon*180/(A/Math.sqrt(magic)*Math.cos(rad)*pi);
        return new LocPoint(lat + dLat, lon + dLon);
    }

    private static boolean outsideChina(double lat, double lon) {
        return lon < 72.004 || lon > 137.8347 || lat < .8293 || lat > 55.8271;
    }
}
