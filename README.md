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
