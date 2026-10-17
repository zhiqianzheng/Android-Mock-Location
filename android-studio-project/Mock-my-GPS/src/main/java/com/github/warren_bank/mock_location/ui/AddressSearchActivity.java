// Modified by zhiqianzheng on 2026-10-17: address-search fork changes, GPL-2.0; original notices retained.
package com.github.warren_bank.mock_location.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.github.warren_bank.mock_location.R;
import com.github.warren_bank.mock_location.address.AddressRepository;
import com.github.warren_bank.mock_location.address.AddressResult;
import com.github.warren_bank.mock_location.data_model.BookmarkItem;
import com.github.warren_bank.mock_location.data_model.SharedPrefs;
import com.github.warren_bank.mock_location.security_model.RuntimePermissions;
import com.github.warren_bank.mock_location.service.LocationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddressSearchActivity extends Activity implements RuntimePermissions.RuntimePermissionsListener {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AddressResult> results = new ArrayList<>();
    private EditText query, city;
    private TextView status, provider, active;
    private Button search;
    private ArrayAdapter<AddressResult> adapter;
    private SharedPreferences preferences;
    private AddressResult pending;
    private int generation;
    private boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_address_search);
        preferences = getSharedPreferences("ADDRESS_SEARCH", MODE_PRIVATE);
        SharedPrefs.getSharedPreferences(this).edit().putBoolean("fixed_joystick_enabled", false).apply();
        query = findViewById(R.id.address_query);
        city = findViewById(R.id.address_city);
        status = findViewById(R.id.address_status);
        provider = findViewById(R.id.address_provider);
        active = findViewById(R.id.address_active);
        search = findViewById(R.id.address_search);
        query.setText(preferences.getString("query", ""));
        city.setText(preferences.getString("city", ""));
        query.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) { search(); return true; }
            return false;
        });
        search.setOnClickListener(v -> search());
        findViewById(R.id.address_settings).setOnClickListener(v -> settings());
        findViewById(R.id.address_manual).setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.address_stop).setOnClickListener(v -> {
            pending = null;
            if (LocationService.isStarted()) LocationService.doStop(this, true);
            active.setText("模拟已停止");
            status.setText("已释放模拟位置，可以重新选择地址。");
        });
        adapter = new ArrayAdapter<AddressResult>(this, android.R.layout.simple_list_item_2, android.R.id.text1, results) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                AddressResult result = getItem(position);
                ((TextView)row.findViewById(android.R.id.text1)).setText(result.name);
                TextView detail = row.findViewById(android.R.id.text2);
                detail.setText(result.address + "\n" + result.source + (result.areaOnly ? "\n范围结果，非精确地址" : ""));
                detail.setSingleLine(false);
                detail.setMaxLines(4);
                row.setMinimumHeight((int)(88*getResources().getDisplayMetrics().density));
                return row;
            }
        };
        ((ListView)findViewById(R.id.address_results)).setAdapter(adapter);
        ((ListView)findViewById(R.id.address_results)).setOnItemClickListener((parent, view, position, id) -> choose(results.get(position)));
        updateProvider();
    }

    @Override protected void onResume() {
        super.onResume();
        updateActive();
        handler.postDelayed(this::updateActive, 700);
    }

    private void updateActive() {
        if (destroyed) return;
        active.setText(LocationService.isStarted() ? "模拟运行中：" + SharedPrefs.getTripOrigin(this).toString() : "模拟未启动");
    }

    private void search() {
        if (!search.isEnabled()) return;
        String text = query.getText().toString().trim(), region = city.getText().toString().trim();
        if (text.length() < 2) { query.setError("请输入至少两个字的地址或地点名称"); return; }
        hideKeyboard();
        final int request = ++generation;
        final String key = preferences.getString("amap_key", "").trim();
        final boolean systemGcj02 = preferences.getBoolean("system_gcj02", true);
        preferences.edit().putString("query", text).putString("city", region).apply();
        results.clear();
        adapter.notifyDataSetChanged();
        search.setEnabled(false);
        status.setText("正在搜索，请稍候…");
        handler.postDelayed(() -> {
            if (!destroyed && generation == request) {
                generation++;
                search.setEnabled(true);
                status.setText("搜索超时，请检查网络。系统搜索依赖手机的地址服务；也可以在搜索设置中配置高德 Key。");
            }
        }, 30000);
        executor.execute(() -> {
            try {
                List<AddressResult> found = new AddressRepository(this).search(text, region, key, systemGcj02);
                handler.post(() -> {
                    if (destroyed || generation != request) return;
                    generation++;
                    search.setEnabled(true);
                    results.addAll(found);
                    adapter.notifyDataSetChanged();
                    boolean onlyAreas = !found.isEmpty();
                    for (AddressResult item : found) if (!item.areaOnly) onlyAreas = false;
                    status.setText(found.isEmpty() ? "没有找到地点。请补充城市、街道和门牌号；公司名称可尝试配置高德搜索。"
                            : (onlyAreas ? "只找到城市或区县范围，尚未匹配具体地点。请补充详细地址，或配置高德搜索。"
                            : "找到 " + found.size() + " 个地点，请核对地址后选择。坐标将自动填写。"));
                });
            } catch (Exception error) {
                final String message = key.isEmpty() ? "系统地址搜索暂不可用，请检查网络，或在搜索设置中配置高德 Key。"
                        : (error instanceof java.io.IOException && error.getMessage() != null ? error.getMessage() : "搜索结果解析失败，请稍后重试。");
                handler.post(() -> {
                    if (destroyed || generation != request) return;
                    generation++;
                    search.setEnabled(true);
                    status.setText(message);
                });
            }
        });
    }

    private void choose(AddressResult result) {
        new AlertDialog.Builder(this).setTitle(result.name)
                .setMessage((result.areaOnly ? "这是城市或区县范围，尚未精确匹配输入的地点。\n\n" : "")
                        + result.address + "\n\n" + result.source + "\nGPS 坐标（纬度、经度）：\n"
                        + String.format(Locale.US, "%.8f, %.8f", result.gps.getLatitude(), result.gps.getLongitude()))
                .setNegativeButton("取消", null)
                .setPositiveButton("启动模拟", (dialog, which) -> {
                    AppOpsManager ops = (AppOpsManager)getSystemService(APP_OPS_SERVICE);
                    if (ops.checkOpNoThrow("android:mock_location", Process.myUid(), getPackageName()) != AppOpsManager.MODE_ALLOWED) {
                        new AlertDialog.Builder(this).setTitle("选择模拟位置应用")
                                .setMessage("请在开发者选项的“选择模拟位置信息应用”中选择“地址模拟 GPS”，再回来点击启动。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("开发者选项", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))).show();
                        return;
                    }
                    if (Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE, 0) == 0) {
                        new AlertDialog.Builder(this).setTitle("开启系统定位")
                                .setMessage("请先开启手机定位，再回来点击启动。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("定位设置", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).show();
                        return;
                    }
                    pending = result;
                    RuntimePermissions.requestPermissions(this, this);
                }).show();
    }

    private void save(AddressResult result) {
        SharedPrefs.putTripOrigin(this, result.gps);
        ArrayList<BookmarkItem> bookmarks = SharedPrefs.getBookmarkItems(this);
        int index = BookmarkItem.indexOf(bookmarks, result.gps.getLatitude(), result.gps.getLongitude());
        BookmarkItem bookmark = new BookmarkItem(result.name, result.gps.getLatitude(), result.gps.getLongitude());
        if (index < 0) bookmarks.add(bookmark); else bookmarks.set(index, bookmark);
        SharedPrefs.putBookmarkItems(this, bookmarks);
    }

    @Override public void onPermissionsGranted() {
        if (pending == null) return;
        try {
            AddressResult result = pending;
            save(result);
            LocationService.doStart(this, true, result.gps, null, 0);
            preferences.edit().putString("active_name", result.name).apply();
            active.setText("模拟运行中：" + result.name);
            handler.postDelayed(this::updateActive, 700);
            status.setText("已填写 GPS 坐标并启动模拟，可以打开地图点击“我的位置”验证。");
        } catch (Exception error) {
            new AlertDialog.Builder(this).setTitle("无法启动模拟")
                    .setMessage("请在开发者选项中，将“地址模拟 GPS”设为模拟位置信息应用，并允许位置权限。")
                    .setPositiveButton("知道了", null).show();
        } finally { pending = null; }
    }

    @Override public void onPermissionsDenied(String[] permissions) {
        pending = null;
        Toast.makeText(this, "启动模拟需要位置权限，请在应用设置中允许。", Toast.LENGTH_LONG).show();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        RuntimePermissions.onRequestPermissionsResult(this, this, request, permissions, grants);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        RuntimePermissions.onActivityResult(this, this, request, result, data);
    }

    private void settings() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(20*getResources().getDisplayMetrics().density);
        form.setPadding(padding, 0, padding, 0);
        EditText key = new EditText(this);
        key.setHint("高德 Web 服务 Key（可留空）");
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(preferences.getString("amap_key", ""));
        form.addView(key);
        TextView coordinateLabel = new TextView(this);
        coordinateLabel.setText("系统搜索的坐标类型（仅影响系统搜索）");
        coordinateLabel.setPadding(0, padding, 0, 0);
        form.addView(coordinateLabel);
        Spinner coordinates = new Spinner(this);
        ArrayAdapter<String> coordinateChoices = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"国内地图坐标（本机默认）", "标准 GPS 坐标"});
        coordinateChoices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        coordinates.setAdapter(coordinateChoices);
        coordinates.setSelection(preferences.getBoolean("system_gcj02", true) ? 0 : 1);
        form.addView(coordinates);
        new AlertDialog.Builder(this).setTitle("搜索设置")
                .setMessage("留空使用系统地址搜索，无需 Key。填写高德 Web 服务 Key 后，可搜索国内公司和地点，并自动转换坐标。")
                .setView(form).setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    generation++;
                    search.setEnabled(true);
                    preferences.edit().putString("amap_key", key.getText().toString().trim())
                            .putBoolean("system_gcj02", coordinates.getSelectedItemPosition() == 0).apply();
                    results.clear();
                    adapter.notifyDataSetChanged();
                    status.setText(R.string.address_empty);
                    updateProvider();
                }).show();
    }

    private void updateProvider() {
        provider.setText(preferences.getString("amap_key", "").trim().isEmpty()
                ? (preferences.getBoolean("system_gcj02", true) ? "当前：系统地址搜索（无需 Key，自动转换坐标）" : "当前：系统地址搜索（无需 Key，GPS 坐标）")
                : "当前：高德地点搜索（自动转换坐标）");
    }

    private void hideKeyboard() {
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(), 0);
        query.clearFocus();
        city.clearFocus();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        generation++;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }
}
