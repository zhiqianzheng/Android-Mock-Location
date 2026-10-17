// Modified by zhiqianzheng on 2026-10-17: address-search fork changes, GPL-2.0; original notices retained.
package com.github.warren_bank.mock_location.address;
import com.github.warren_bank.mock_location.data_model.LocPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class CoordinateTransformTest {
    @Test public void domesticAmapPointConvertsLatitudeFirst() {
        // Synthetic city-scale point; no personal bookmark data is included.
        LocPoint gps = CoordinateTransform.gcj02ToWgs84(31.23, 121.47);
        assertTrue(Math.abs(31.23 - gps.getLatitude()) > 0.0001);
        assertTrue(Math.abs(121.47 - gps.getLongitude()) > 0.0001);
        LocPoint amap = CoordinateTransform.wgs84ToGcj02(gps.getLatitude(), gps.getLongitude());
        assertEquals(31.23, amap.getLatitude(), 1e-8);
        assertEquals(121.47, amap.getLongitude(), 1e-8);
    }
    @Test public void overseasCoordinatesAreUnchanged() {
        LocPoint berlin = CoordinateTransform.gcj02ToWgs84(52.52, 13.41);
        assertEquals(52.52, berlin.getLatitude(), 0);
        assertEquals(13.41, berlin.getLongitude(), 0);
    }
    @Test public void systemTiananmenResultDoesNotGetInjectedAsGpsDirectly() {
        LocPoint gps = CoordinateTransform.gcj02ToWgs84(39.908715, 116.397389);
        assertTrue(gps.getLongitude() < 116.392);
        LocPoint map = CoordinateTransform.wgs84ToGcj02(gps.getLatitude(), gps.getLongitude());
        assertEquals(39.908715, map.getLatitude(), 1e-8);
        assertEquals(116.397389, map.getLongitude(), 1e-8);
    }
    @Test(expected = NumberFormatException.class) public void reversedChineseCoordinatesAreRejected() {
        new LocPoint("121.47,31.23");
    }
    @Test(expected = NumberFormatException.class) public void nonFiniteCoordinatesAreRejected() {
        new LocPoint(Double.NaN, 120);
    }
    @Test(expected = NumberFormatException.class) public void invalidLongitudeIsRejected() {
        new LocPoint(31, 181);
    }
    @Test public void savedCompanyNamesMatchOnlyTheSameCompany() {
        assertTrue(AddressResult.matchesSavedName("示例科技有限公司", "示例科技"));
        assertFalse(AddressResult.matchesSavedName("另一家科技有限公司", "示例科技"));
        assertFalse(AddressResult.matchesSavedName("科技", "示例科技"));
        assertFalse(AddressResult.matchesSavedName("公司", "公司"));
    }
}
