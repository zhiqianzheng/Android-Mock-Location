// Modified by zhiqianzheng on 2026-10-17: address-search fork changes, GPL-2.0; original notices retained.
package com.github.warren_bank.mock_location.data_model;

// copied from:
//   https://github.com/xiangtailiang/FakeGPS/blob/V1.1/app/src/main/java/com/github/fakegps/model/LocPoint.java

public class LocPoint {
    private double mLatitude;
    private double mLongitude;

    public LocPoint(LocPoint locPoint) {
        mLatitude = locPoint.getLatitude();
        mLongitude = locPoint.getLongitude();
    }

    public LocPoint(double latitude, double longitude) {
        validate(latitude, longitude);
        mLatitude = latitude;
        mLongitude = longitude;
    }

    public LocPoint(String text) throws NumberFormatException {
        String[] parts = text.split(",");
        if (parts.length == 2) {
            mLatitude  = Double.parseDouble(parts[0].trim());
            mLongitude = Double.parseDouble(parts[1].trim());
            validate(mLatitude, mLongitude);
        }
        else {
            throw new NumberFormatException("expected: latitude,longitude");
        }
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public void setLatitude(double latitude) {
        validate(latitude, mLongitude);
        mLatitude = latitude;
    }

    public void setLongitude(double longitude) {
        validate(mLatitude, longitude);
        mLongitude = longitude;
    }

    public static void validate(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude) || Math.abs(latitude) > 90
                || Double.isNaN(longitude) || Double.isInfinite(longitude) || Math.abs(longitude) > 180) {
            throw new NumberFormatException("坐标无效：请按纬度、经度输入；纬度为 -90～90，经度为 -180～180。");
        }
    }

    @Override
    public String toString() {
        return String.format(
            "%1$s, %2$s",
            mLatitude,
            mLongitude
        );
    }

    public boolean equals(LocPoint locPoint) {
        double threshold = 1e-4;
        return equals(locPoint, threshold);
    }

    public boolean equals(LocPoint locPoint, double threshold) {
        double that_lat = locPoint.getLatitude();
        double that_lon = locPoint.getLongitude();

        return (Math.abs(that_lat - mLatitude) < threshold) && (Math.abs(that_lon - mLongitude) < threshold); // todo: fix that longitude difference doesn't work if straddling opposite sides of the 180th meridian (ex: 179.99999 and -179.99999)
    }

}
