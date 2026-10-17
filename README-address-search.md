# 地址模拟 GPS

维护者：[@zhiqianzheng](https://github.com/zhiqianzheng)。本次功能由维护者提出并验证，开发过程中使用 AI 辅助。修改日期：2026-10-17。

基于 Warren Bank 的 [Mock my GPS](https://github.com/warren-bank/Android-Mock-Location)，保留原项目 GPL-2.0 许可证与作者信息。新增代码同样采用 GPL-2.0。本版本使用独立包名 `com.github.warren_bank.mock_location.address`，可以与原版同时安装。请只运行其中一个应用的模拟服务。

## 手机使用

1. 在开发者选项中将“地址模拟 GPS”选为模拟位置信息应用，允许应用位置权限，并开启系统定位。
2. 输入中文地址或地点名称，城市可选填，点击“搜索地址”。
3. 核对候选结果的名称、详细地址与来源，选择后点击“启动模拟”。
4. 打开地图点击“我的位置”验证。结束时回到本应用点击“停止模拟”。

搜索会先匹配已有书签的完整名称（支持省略“有限公司”等后缀）；这类结果明确显示“已保存的书签”，不冒充新的在线搜索。其他名称通过系统地址服务查询。选择后自动填写、保存书签并启动，无需手工转换或交换经纬度。

## 高德入口与坐标处理

“搜索设置”中的高德 Web 服务 Key 留空时使用系统服务。填写用户自己的有效 Web 服务 Key 后，使用高德 POI 关键字搜索；未找到 POI 时尝试地址地理编码。Key 只保存在应用私有设置中，不写入源码或日志。搜索请求直接发往所选服务。

- 系统地址服务的公司覆盖率和可用性由手机服务提供方决定。只返回城市、区县时，界面会明确标记范围结果。
- 当前这台 Android 12 小米手机的系统服务返回国内地图坐标，已用“北京天安门”在高德蓝点实测验证，因此默认自动转换为 GPS 坐标。其他手机若返回标准 GPS 坐标，可在“搜索设置”中选择“标准 GPS 坐标”。
- 高德结果按 GCJ-02 自动近似反算为 WGS84；海外范围保持坐标不变。
- 手工坐标入口仍按“纬度、经度”输入。无效范围、NaN、无穷值会被拒绝。
- 系统服务可能只返回公司所在城市；这类结果会明确提示范围较粗。已确认的位置可保存为书签，之后按名称调用；仓库不包含个人书签或设备数据。
- 没有有效高德 Key，因此高德成功搜索路径尚未做真实 Key 联网验证。

参考：[Android Geocoder](https://developer.android.com/reference/android/location/Geocoder)、[高德 POI 搜索](https://developer.amap.com/api/webservice/guide/api-advanced/newpoisearch)、[高德坐标系说明](https://developer.amap.com/faq/advisory/others/39838)。

## 构建与验证

使用 JDK 21（本次实际构建版本）、Android SDK Platform 35、Build Tools 35.0.0。Gradle Wrapper 固定为 8.9，AGP 为原项目的 8.7.2。设置 `JAVA_HOME`，并在 `android-studio-project/local.properties` 中配置自己的 `sdk.dir`。

在 `android-studio-project` 目录运行：

```powershell
$variant = 'WithAllLanguageTranslationsWithGooglePlayServicesFusedLocationProviderWithBackupRestoreSAFDebug'
.\gradlew.bat ":Mock-my-GPS:assemble${variant}" ":Mock-my-GPS:test${variant}UnitTest" ":Mock-my-GPS:lint${variant}"
```

测试 APK 位于 `Mock-my-GPS/build/outputs/apk/withAllLanguageTranslationsWithGooglePlayServicesFusedLocationProviderWithBackupRestoreSAF/debug/`。

## 公开 Release 构建

[Releases 下载区](https://github.com/zhiqianzheng/Android-Mock-Location/releases/latest) 提供签名 APK、对应源码和 SHA-256 校验值。公开 APK 使用独立发布密钥，关闭调试。当前仅发布 GMS 版本，需要手机安装并启用 Google Play 服务；不需要安装 Magisk 或取得 root 权限，但必须选择模拟位置应用。系统地址服务不可用时，可以配置自己的高德 Key 或输入坐标。

已有早期 debug 测试包的用户：公开包签名不同，不能直接覆盖安装。请先导出书签、停止模拟服务，卸载旧测试包，再安装公开版本，并重新选择模拟位置应用。此后公开版本使用同一发布密钥更新。请自行保管书签备份。

自行构建签名版本时，在 `android-studio-project/signing.properties` 配置 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`；`storeFile` 可使用本机绝对路径。此文件和 `.jks` / `.keystore` 已加入忽略规则，切勿提交密钥或口令。未配置时仍可构建 debug 包，release 包需要自行签名。

```powershell
$variant = 'WithAllLanguageTranslationsWithGooglePlayServicesFusedLocationProviderWithBackupRestoreSAFRelease'
.\gradlew.bat ":Mock-my-GPS:assemble${variant}" ":Mock-my-GPS:test${variant}UnitTest" ":Mock-my-GPS:lint${variant}"
```

源码归档不包含 SDK、依赖缓存、手机数据、签名私钥或用户 Key。公开安装包不会附带个人书签或预设私人地址。

已执行的功能检查：中文“北京天安门”系统搜索、自动转换、启动 Android 与 Google 融合模拟定位、高德蓝点对齐；已有书签迁移与中文名称匹配；停止与重新启动流程。单元测试覆盖国内坐标往返转换、天安门转换、海外不偏移、经纬度填反和无效数字拦截、保存名称的严格匹配。
