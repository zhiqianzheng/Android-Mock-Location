// Modified by zhiqianzheng on 2026-10-17: address-search fork changes, GPL-2.0; original notices retained.
package com.github.warren_bank.mock_location.address;
import com.github.warren_bank.mock_location.data_model.LocPoint;

public final class AddressResult {
    public final String name, address, source;
    public final LocPoint gps;
    public final boolean areaOnly;
    public AddressResult(String name, String address, String source, LocPoint gps) {
        this(name, address, source, gps, false);
    }
    public AddressResult(String name, String address, String source, LocPoint gps, boolean areaOnly) {
        this.name = name;
        this.address = address;
        this.source = source;
        this.gps = gps;
        this.areaOnly = areaOnly;
    }
    public static boolean matchesSavedName(String query, String title) {
        String first = normalize(query), second = normalize(title);
        return !first.isEmpty() && first.equals(second);
    }
    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "")
                .replaceFirst("(股份有限公司|有限责任公司|有限公司|公司)$", "");
    }
}
