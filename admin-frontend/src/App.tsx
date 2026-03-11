import { useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, Link, useLocation } from 'react-router-dom'
import { Layout, Menu, Button } from 'antd'
import {
  DashboardOutlined,
  MobileOutlined,
  TeamOutlined,
  PictureOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { isLoggedIn, clearToken } from './lib/api'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Devices from './pages/Devices'
import DevicePhotos from './pages/DevicePhotos'
import Users from './pages/Users'
import Photos from './pages/Photos'

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

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
        <div style={{ padding: '16px', fontWeight: 600, fontSize: 16, borderBottom: '1px solid #f0f0f0' }}>
          📷 相框管理
        </div>
        <Menu selectedKeys={[selectedKey]} mode="inline" style={{ border: 'none' }}>
          <Menu.Item key="dashboard" icon={<DashboardOutlined />}>
            <Link to="/">概览</Link>
          </Menu.Item>
          <Menu.Item key="devices" icon={<MobileOutlined />}>
            <Link to="/devices">设备管理</Link>
          </Menu.Item>
          <Menu.Item key="users" icon={<TeamOutlined />}>
            <Link to="/users">用户管理</Link>
          </Menu.Item>
          <Menu.Item key="photos" icon={<PictureOutlined />}>
            <Link to="/photos">全部照片</Link>
          </Menu.Item>
        </Menu>
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', borderBottom: '1px solid #f0f0f0' }}>
          <Button icon={<LogoutOutlined />} onClick={handleLogout}>退出</Button>
        </Header>
        <Content style={{ margin: 24 }}>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/devices" element={<Devices />} />
            <Route path="/devices/:id" element={<DevicePhotos />} />
            <Route path="/users" element={<Users />} />
            <Route path="/photos" element={<Photos />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
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
