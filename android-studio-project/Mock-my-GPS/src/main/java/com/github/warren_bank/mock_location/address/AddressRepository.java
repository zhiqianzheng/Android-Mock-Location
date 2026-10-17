// Modified by zhiqianzheng on 2026-10-17: address-search fork changes, GPL-2.0; original notices retained.
package com.github.warren_bank.mock_location.address;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import com.github.warren_bank.mock_location.data_model.LocPoint;
import com.github.warren_bank.mock_location.data_model.BookmarkItem;
import com.github.warren_bank.mock_location.data_model.SharedPrefs;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AddressRepository {
    private final Context context;
    public AddressRepository(Context context) { this.context = context.getApplicationContext(); }

    @SuppressWarnings("deprecation")
    public List<AddressResult> search(String query, String city, String key, boolean systemGcj02) throws Exception {
        List<AddressResult> saved = new ArrayList<>();
        for (BookmarkItem bookmark : SharedPrefs.getBookmarkItems(context)) {
            if (AddressResult.matchesSavedName(query, bookmark.title)) {
                try {
                    saved.add(new AddressResult(bookmark.title, "使用此前保存并确认的坐标", "已保存的书签 · GPS 坐标", bookmark.toPoint()));
                } catch (NumberFormatException ignored) { }
            }
        }
        if (!saved.isEmpty()) return saved;
        if (!key.isEmpty()) return amap(query, city, key);
        if (!Geocoder.isPresent()) throw new IOException("这台手机没有系统地址搜索服务，请配置高德 Web 服务 Key。");
        List<AddressResult> results = new ArrayList<>();
        List<Address> addresses = new Geocoder(context, Locale.SIMPLIFIED_CHINESE)
                .getFromLocationName(city.isEmpty() ? query : city + " " + query, 10);
        if (addresses != null) for (Address address : addresses) {
            if (!address.hasLatitude() || !address.hasLongitude()) continue;
            String detail = address.getMaxAddressLineIndex() >= 0 ? address.getAddressLine(0) : query;
            String name = address.getFeatureName();
            boolean areaOnly = name == null || name.equals(address.getCountryName()) || name.equals(address.getAdminArea())
                    || name.equals(address.getSubAdminArea()) || name.equals(address.getLocality()) || name.equals(address.getSubLocality());
            LocPoint gps = systemGcj02
                    ? CoordinateTransform.gcj02ToWgs84(address.getLatitude(), address.getLongitude())
                    : new LocPoint(address.getLatitude(), address.getLongitude());
            results.add(new AddressResult(name == null ? query : name, detail == null ? query : detail,
                    systemGcj02 ? "系统地址搜索 · 坐标已自动转换" : "系统地址搜索 · GPS 坐标", gps, areaOnly));
        }
        return results;
    }

    private List<AddressResult> amap(String query, String city, String key) throws Exception {
        Uri.Builder uri = Uri.parse("https://restapi.amap.com/v5/place/text").buildUpon()
                .appendQueryParameter("key", key).appendQueryParameter("keywords", query)
                .appendQueryParameter("page_size", "10");
        if (!city.isEmpty()) uri.appendQueryParameter("region", city);
        JSONArray pois = request(uri.build()).optJSONArray("pois");
        List<AddressResult> results = new ArrayList<>();
        if (pois != null) for (int i = 0; i < pois.length(); i++) {
            JSONObject poi = pois.getJSONObject(i);
            addAmapResult(results, poi.optString("name", query), text(poi, "pname") + text(poi, "cityname")
                    + text(poi, "adname") + text(poi, "address"), text(poi, "location"));
        }
        if (results.isEmpty()) {
            Uri.Builder geocode = Uri.parse("https://restapi.amap.com/v3/geocode/geo").buildUpon()
                    .appendQueryParameter("key", key).appendQueryParameter("address", query);
            if (!city.isEmpty()) geocode.appendQueryParameter("city", city);
            JSONArray items = request(geocode.build()).optJSONArray("geocodes");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                addAmapResult(results, query, text(item, "formatted_address"), text(item, "location"));
            }
        }
        return results;
    }

    private static void addAmapResult(List<AddressResult> results, String name, String address, String location) {
        String[] parts = location.split(",");
        if (parts.length != 2) return;
        try {
            // AMap returns longitude first in GCJ-02; GPS input is latitude first in WGS84.
            LocPoint gps = CoordinateTransform.gcj02ToWgs84(Double.parseDouble(parts[1]), Double.parseDouble(parts[0]));
            results.add(new AddressResult(name, address, "高德地点搜索 · 坐标已自动转换", gps));
        } catch (NumberFormatException ignored) { }
    }

    private static String text(JSONObject json, String key) {
        Object value = json.opt(key);
        return value instanceof String ? (String)value : "";
    }

    private static JSONObject request(Uri uri) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(uri.toString()).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("高德服务连接失败，请检查网络后重试。");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1024*1024) throw new IOException("搜索响应过大，请缩小搜索范围。");
                    output.write(buffer, 0, count);
                }
            }
            JSONObject json = new JSONObject(output.toString("UTF-8"));
            if (!"1".equals(json.optString("status"))) {
                String code = json.optString("infocode");
                if ("10001".equals(code) || "10009".equals(code))
                    throw new IOException("高德 Key 无效或类型不匹配，请检查 Web 服务 Key。");
                throw new IOException("高德搜索未完成（错误码 " + code + "），请检查 Key 的服务权限或配额。");
            }
            return json;
        } finally { connection.disconnect(); }
    }
}
