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

推送 release tag 即可触发 GitHub Actions 自动完成构建和发布，**无需手动修改任何文件**：

```bash
git tag v1.1.0 -m "修复了若干问题"
git push origin v1.1.0
```

Action 会自动：
1. 从 tag 名提取版本号，写入 `build.gradle`（`versionCode` 和 `versionName`）
2. 构建 release APK
3. 上传到 GitHub Release 附件

发布完成后，相框 App 下次启动时会自动检测新版本并弹窗提示升级。

> **版本号规则**：tag 格式为 `vMAJOR.MINOR.PATCH`，`versionCode` 自动计算为 `MAJOR*10000 + MINOR*100 + PATCH`（如 `v1.2.3` → `10203`）。

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
