# photo-frame

智能家庭电子相框系统。将老 Android 平板变成展示家庭照片的相框，通过微信小程序上传照片。

## 项目结构

```
photo-frame/
├── backend/        # Go + Gin 后端 API（MySQL 8 + 腾讯云 COS）
├── miniprogram/    # 微信小程序（uni-app + Vue3）
├── android/        # Android 相框 App（Kotlin）
├── docker-compose.yml
└── nginx.conf
```

## 快速启动（后端开发）

```bash
# 1. 配置环境变量
cp backend/.env.example backend/.env
# 编辑 .env 填入真实配置

# 2. 启动 MySQL（使用 Docker）
docker-compose up mysql -d

# 3. 启动后端
cd backend && go run .
```

## 部署

```bash
# 配置好 .env 后
docker-compose up -d
```

## Android App 发布

### 修改版本号

编辑 `android/app/build.gradle`：

```groovy
defaultConfig {
    versionCode 2        // 每次发布必须递增（整数）
    versionName "1.1.0"  // 显示给用户的版本号
}
```

两个字段都要更新：`versionCode` 每次 +1，`versionName` 按语义化版本（major.minor.patch）递增。

### 构建 APK

```bash
cd android
./gradlew assembleRelease
# 输出：android/app/build/outputs/apk/release/app-release.apk
```

### 发布更新（自动，推荐）

推送 release tag 即可触发 GitHub Actions 自动完成构建和发布：

```bash
git tag v1.1.0 -m "修复了若干问题"
git push origin v1.1.0
```

Action 会自动：
1. 将 tag 版本号写入 `build.gradle`
2. 构建 release APK
3. 上传到 GitHub Release 附件
4. 计算 SHA-256
5. 将版本信息写入生产数据库（`versions` 表）

**首次使用需在 GitHub 仓库 Settings → Secrets 中配置：**

| Secret | 默认值 | 说明 |
|--------|--------|------|
| `DB_HOST` | `127.0.0.1` | 生产数据库主机 |
| `DB_PORT` | `3306` | 数据库端口 |
| `DB_NAME` | `photoframe` | 数据库名 |
| `DB_USER` | `photoframe` | 数据库用户名 |
| `DB_PASSWORD` | 必填 | 数据库密码 |
| `APK_BASE_URL` | 必填 | APK 下载基础 URL，如 `https://github.com/<user>/photo-frame/releases/download` |

相框 App 下次启动时会自动检测新版本并弹窗提示升级；用户也可在设置页手动点击「检查更新」。

## API 概览

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/wx-login` | POST | 微信登录换 token |
| `/api/device/register` | POST | 相框设备注册 |
| `/api/bind` | POST | 扫码绑定相框 |
| `/api/my/frames` | GET | 我绑定的相框列表 |
| `/api/upload` | POST | 上传照片 |
| `/api/photos` | GET | 获取相框照片列表 |
| `/api/photos/:id` | DELETE | 删除照片 |
| `/api/version/latest` | GET | 获取最新 APK 版本 |
