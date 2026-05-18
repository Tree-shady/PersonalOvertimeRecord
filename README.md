# PersonalOvertimeRecord - 个人加班记录应用

一个简单易用的Android应用，用于记录个人的加班信息和计算加班工资。

## 功能特点

- 📅 日历视图展示考勤记录
- ⏰ 打卡功能（上班/下班时间）
- 💰 自动计算加班时长和加班工资
- 📊 月统计功能（总加班时长、预计工资）
- ⚙️ 灵活的设置选项（基本工资、加班倍率等）
- ✏️ 支持手动编辑考勤记录
- 💾 本地数据存储
- 🎯 区分工作日、周末、节假日加班倍率
- 📈 绩效奖金计算
- 🏷️ 预设班次（支持自定义）

## 技术栈

- **语言**: Kotlin
- **架构**: MVVM
- **最低SDK版本**: 24 (Android 7.0)
- **目标SDK版本**: 36
- **主要依赖库**:
  - AndroidX Core KTX
  - AppCompat
  - Material Components
  - ConstraintLayout
  - Lifecycle (ViewModel, LiveData)
  - RecyclerView
  - Kotlin Coroutines
  - ViewBinding

## 项目结构

```
app/
├── src/main/java/com/example/personalovertimerecord/
│   ├── adapter/          # RecyclerView适配器
│   ├── data/             # 数据模型和存储类
│   ├── dialog/           # 对话框组件
│   ├── repository/       # 数据仓库
│   ├── utils/            # 工具类
│   ├── view/             # 自定义视图
│   ├── viewmodel/        # ViewModel层
│   ├── MainActivity.kt   # 主界面
│   └── SettingsActivity.kt # 设置界面
└── res/
    ├── layout/           # 布局文件
    ├── drawable/         # 可绘制资源
    └── values/           # 值资源
```

## 快速开始

### 环境要求

- Android Studio Hedgehog (或更高版本)
- JDK 11
- Android SDK 36

### 构建步骤

1. 克隆项目
```bash
git clone https://github.com/your-username/PersonalOvertimeRecord.git
```

2. 使用Android Studio打开项目

3. 同步Gradle依赖

4. 连接Android设备或启动模拟器

5. 点击运行按钮或使用命令:
```bash
./gradlew installDebug
```

## 功能说明

### 加班计算

应用支持智能加班计算，包含以下特性：

- **工作日加班**: 1.5倍工资（可配置）
- **周末加班**: 2.0倍工资（可配置）
- **节假日加班**: 3.0倍工资（可配置）
- **绩效奖金**: 按基本工资百分比计算
- **月工作天数**: 默认为21.75天
- **日工作时长**: 默认为8小时

### 设置选项

在设置页面可以配置：
- 基本工资
- 绩效奖金百分比
- 月工作天数
- 日工作时长
- 工作开始/结束时间
- 各类型加班倍率

### 数据存储

所有数据本地存储，包括：
- 考勤记录（日期、上下班时间、备注）
- 应用设置

## 使用说明

1. **设置参数**: 进入设置页面，配置基本工资、正常工作时间、加班倍率等信息
2. **添加记录**: 点击日历上的日期添加考勤记录
3. **打卡**: 输入上班和下班时间
4. **查看统计**: 主界面会显示当月总加班时长和预计总工资
5. **编辑记录**: 点击列表项可编辑已有的考勤记录

## 许可证

详见 [LICENSE](LICENSE) 文件
