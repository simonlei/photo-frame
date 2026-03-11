import { lazy, Suspense, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, Link, useLocation } from 'react-router-dom'
import { Layout, Menu, Button, Spin } from 'antd'
import type { MenuProps } from 'antd'
import {
  DashboardOutlined,
  MobileOutlined,
  TeamOutlined,
  PictureOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { isLoggedIn, clearToken } from './lib/api'
import Login from './pages/Login'

const Dashboard    = lazy(() => import('./pages/Dashboard'))
const Devices      = lazy(() => import('./pages/Devices'))
const DevicePhotos = lazy(() => import('./pages/DevicePhotos'))
const Users        = lazy(() => import('./pages/Users'))
const Photos       = lazy(() => import('./pages/Photos'))

const { Header, Sider, Content } = Layout

function AdminLayout() {
  const location = useLocation()

  function handleLogout() {
    clearToken()
    window.location.href = '/admin/'
  }

  const selectedKey = location.pathname === '/' ? 'dashboard'
    : location.pathname.startsWith('/devices') ? 'devices'
    : location.pathname.startsWith('/users') ? 'users'
    : location.pathname.startsWith('/photos') ? 'photos'
    : 'dashboard'

  const menuItems: MenuProps['items'] = [
    { key: 'dashboard', icon: <DashboardOutlined />, label: <Link to="/">概览</Link> },
    { key: 'devices',   icon: <MobileOutlined />,   label: <Link to="/devices">设备管理</Link> },
    { key: 'users',     icon: <TeamOutlined />,      label: <Link to="/users">用户管理</Link> },
    { key: 'photos',    icon: <PictureOutlined />,   label: <Link to="/photos">全部照片</Link> },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
        <div style={{ padding: '16px', fontWeight: 600, fontSize: 16, borderBottom: '1px solid #f0f0f0' }}>
          📷 相框管理
        </div>
        <Menu selectedKeys={[selectedKey]} mode="inline" style={{ border: 'none' }} items={menuItems} />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', borderBottom: '1px solid #f0f0f0' }}>
          <Button icon={<LogoutOutlined />} onClick={handleLogout}>退出</Button>
        </Header>
        <Content style={{ margin: 24 }}>
          <Suspense fallback={<div style={{ padding: 24 }}><Spin /></div>}>
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/devices" element={<Devices />} />
              <Route path="/devices/:id" element={<DevicePhotos />} />
              <Route path="/users" element={<Users />} />
              <Route path="/photos" element={<Photos />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  )
}

export default function App() {
  const [loggedIn, setLoggedIn] = useState(isLoggedIn())

  if (!loggedIn) {
    return <Login onLogin={() => setLoggedIn(true)} />
  }

  return (
    <BrowserRouter basename="/admin">
      <AdminLayout />
    </BrowserRouter>
  )
}
