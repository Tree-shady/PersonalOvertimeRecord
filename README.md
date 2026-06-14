# PersonalOvertimeRecord - 个人加班记录应用

一个功能丰富、稳定可靠的Android应用，用于记录个人的加班信息和计算加班工资。

![Version](https://img.shields.io/badge/version-1.2.0-blue)
![Min SDK](https://img.shields.io/badge/minSdk-24-green)
![Target SDK](https://img.shields.io/badge/targetSdk-36-orange)

## 功能特点

### 核心功能
- 📅 **日历视图** - 直观的日历界面展示考勤记录
- ⏰ **打卡功能** - 记录上班/下班时间
- 💰 **智能计算** - 自动计算加班时长和加班工资
- 📊 **月度统计** - 实时统计当月加班时长、预计工资
- 📈 **年度报表** - 完整的年度数据统计与图表可视化
- 🎯 **智能区分** - 工作日、周末、节假日加班倍率自动识别

### 数据管理
- 📤 **数据导出** - 支持导出为 PDF 精美报告、CSV/JSON 格式
- 📥 **数据导入** - 从文件恢复历史数据
- 🌐 **云端同步** - 支持 WebDAV 协议双向同步
- 🔄 **自动同步** - 后台定时自动同步数据到云端
- 🔀 **增量同步** - 智能增量同步，只同步有变化的数据

### 用户体验
- 🌙 **深色模式** - 支持跟随系统/浅色/深色三种主题
- 🔔 **打卡提醒** - 可设置上下班打卡提醒
- 🎮 **科幻启动页** - 炫酷的终端风格开机自检动画
- ✨ **流畅动画** - 精心设计的过渡动画效果

### 安全与稳定
- 🔒 **数据加密** - SQLCipher 数据库加密
- 🔐 **隐私存储** - EncryptedSharedPreferences 存储敏感信息
- 🛡️ **健壮性** - 全局异常处理、数据验证机制
- 📡 **网络健壮** - 超时处理、重试机制、SSL 支持

## 技术栈

### 框架与语言
- **语言**: Kotlin 1.9+
- **最低 SDK**: 24 (Android 7.0)
- **目标 SDK**: 36
- **编译 SDK**: 36

### 架构
- **架构模式**: MVVM + Repository
- **依赖注入**: 手动依赖管理
- **异步处理**: Kotlin Coroutines

### 主要依赖库

| 类别 | 库 | 用途 |
|------|-----|------|
| UI | Material Components 3 | Material Design 3 界面 |
| UI | ConstraintLayout | 响应式布局 |
| UI | RecyclerView | 列表展示 |
| Lifecycle | ViewModel + LiveData | 生命周期管理 |
| Database | Room + KSP | 本地数据持久化 |
| Database | SQLCipher | 数据库加密 |
| Security | AndroidX Security | 加密存储 |
| Network | OkHttp | HTTP 客户端 |
| Chart | MPAndroidChart | 数据可视化 |
| JSON | Gson | JSON 序列化 |
| Image | Coil | 图片加载 |

## 项目结构

```
app/
├── src/main/java/com/example/personalovertimerecord/
│   ├── adapter/                  # RecyclerView 适配器
│   ├── data/                     # 数据层
│   │   ├── db/                   # Room 数据库（DAO、Entity）
│   │   └── model/                # 数据模型
│   ├── dialog/                   # 对话框组件
│   ├── repository/               # 数据仓库层
│   ├── utils/                    # 工具类
│   │   ├── AppLogger.kt          # 日志工具
│   │   ├── ChartHelper.kt        # 图表助手
│   │   ├── CsvExporter.kt        # CSV 导出
│   │   ├── DataValidator.kt       # 数据验证
│   │   ├── DateUtils.kt          # 日期工具
│   │   ├── GlobalExceptionHandler.kt # 全局异常处理
│   │   ├── HolidayManager.kt      # 节假日管理 (2024-2028)
│   │   ├── NetworkUtils.kt        # 网络工具
│   │   ├── OvertimeCalculator.kt  # 加班计算
│   │   ├── PdfExporter.kt        # PDF 导出
│   │   ├── ReminderManager.kt     # 打卡提醒
│   │   ├── SettingsManager.kt     # 设置管理
│   │   ├── SyncManager.kt        # 同步管理
│   │   ├── ThemeManager.kt        # 主题管理
│   │   └── WebDAVManager.kt       # WebDAV 客户端
│   ├── view/                     # 自定义视图
│   ├── viewmodel/                # ViewModel 层
│   ├── MainActivity.kt           # 主界面
│   ├── SettingsActivity.kt       # 设置界面
│   ├── DataManagerActivity.kt    # 数据管理界面
│   ├── ReportActivity.kt         # 报表界面
│   ├── SplashActivity.kt         # 启动页（科幻风格）
│   └── OvertimeApplication.kt   # 应用入口
└── res/
    ├── layout/                   # XML 布局文件
    ├── drawable/                 # 可绘制资源
    ├── values/                   # 默认主题资源
    ├── values-night/             # 深色主题资源
    ├── anim/                     # 动画资源
    └── xml/                      # 配置文件
```

## 快速开始

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17+ (推荐 JDK 21+)
- Gradle 9.5.1
- Android SDK 36

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/Tree-shady/PersonalOvertimeRecord.git

# 进入项目目录
cd PersonalOvertimeRecord

# 同步 Gradle 依赖
./gradlew --refresh-dependencies

# 构建 Debug 版本
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

### Gradle 镜像配置

如遇 Gradle 下载困难，可配置国内镜像。在 `gradle/wrapper/gradle-wrapper.properties` 中：

```properties
distributionUrl=https://repo.huaweicloud.com/gradle/gradle-8.6-bin.zip
```

## 功能说明

### 加班计算规则

| 日期类型 | 默认倍率 | 说明 |
|---------|---------|------|
| 工作日 | 1.5x | 周一至周五 |
| 周末 | 2.0x | 周六、周日 |
| 节假日 | 3.0x | 国家法定节假日 |

> 节假日数据已内置 2024-2028 年中国法定节假日

### 设置选项

| 类别 | 选项 | 说明 |
|------|------|------|
| 工资设置 | 基本工资 | 月基本工资金额 |
| 工资设置 | 绩效奖金 | 绩效奖金百分比 |
| 工时设置 | 月工作天数 | 默认 21.75 天 |
| 工时设置 | 日工作时长 | 默认 8 小时 |
| 工时设置 | 工作时间 | 上下班时间 |
| 加班设置 | 各类型倍率 | 工作日/周末/节假日加班倍率 |
| 外观设置 | 主题模式 | 跟随系统/浅色/深色 |
| 提醒设置 | 打卡提醒 | 上班/下班提醒时间 |
| 同步设置 | 自动同步 | 开关、同步间隔、WiFi 限制 |

### 数据同步

支持多种同步模式：

| 模式 | 说明 |
|------|------|
| 智能双向同步 | 增量合并，只同步有变化的记录 |
| 仅上传到云端 | 备份本地数据到 WebDAV |
| 仅从云端下载 | 从 WebDAV 恢复数据 |
| 完全覆盖同步 | 云端和本地完全替换 |

### 数据导出

| 格式 | 说明 |
|------|------|
| PDF | 精美的月度/年度考勤报告，可分享 |
| CSV | Excel 兼容格式，便于数据分析 |
| JSON | 完整数据备份，可用于数据迁移 |

## 软件健壮性

### 异常处理

- 全局未捕获异常捕获
- 自定义异常回调
- 完整的错误日志记录

### 数据验证

- 日期/时间格式验证
- 加班时长范围检查
- WebDAV 配置验证
- 综合考勤记录验证

### 网络健壮

- 自动网络状态检测
- 可配置超时处理
- 自动重试机制（指数退避）
- SSL 证书支持

## 使用说明

1. **首次设置**: 首次启动后，进入设置页面配置基本工资、工作时间等
2. **添加记录**: 点击日历日期添加考勤记录
3. **打卡签到**: 输入上班和下班时间，自动计算加班
4. **查看统计**: 主界面实时显示当月加班统计
5. **编辑记录**: 点击列表项编辑已有的考勤记录
6. **数据导出**: 报表页面可导出 PDF/CSV 报告
7. **云端同步**: 配置 WebDAV 后可自动同步数据

## 更新日志

### v1.3.0 (2026-06-14)
- 🔒 修复云同步加密功能严重安全问题（数据未正确加密）
- 🔒 修复导出文件加密功能实现问题
- 🔒 加密密码不再明文保存，使用加密存储
- ✨ 新增旧版本未加密数据兼容处理
- 🛡️ 增强数据安全性验证
- ⚡ 升级 Gradle 到 9.5.1（最新稳定版）
- ⚡ 升级项目 Java 版本到 17

### v1.2.0 (2026-06-14)
- ✨ 新增科幻风格启动界面
- ✨ 新增深色模式支持
- ✨ 新增 PDF/CSV 数据导出
- ✨ 新增自动同步功能
- ✨ 新增打卡提醒功能
- ✨ 新增法定节假日识别 (2024-2028)
- 🛡️ 增强软件健壮性（全局异常处理、数据验证）
- 📡 增强网络工具（超时处理、重试机制）
- 🎨 UI/UX 优化

### v1.1.0
- 优化加班计算逻辑
- 新增增量同步模式
- 修复若干已知问题

## 许可证

本项目基于 MIT 许可证开源，详见 [LICENSE](LICENSE) 文件。

## 致谢

感谢所有贡献者和用户的使用与反馈！
