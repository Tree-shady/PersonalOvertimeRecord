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

## 使用说明

1. **设置参数**: 进入设置页面，配置基本工资、正常工作时间、加班倍率等信息
2. **添加记录**: 点击日历上的日期添加考勤记录
3. **打卡**: 输入上班和下班时间
4. **查看统计**: 主界面会显示当月总加班时长和预计总工资

## 许可证

详见 [LICENSE](LICENSE) 文件
