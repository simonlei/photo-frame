import { useState } from 'react'
import { Button, Card, Form, Input, message, Typography } from 'antd'
import { LockOutlined } from '@ant-design/icons'
import { saveToken, verifyToken } from '../lib/api'

const { Title, Text } = Typography

interface Props {
  onLogin: () => void
}

export default function Login({ onLogin }: Props) {
  const [loading, setLoading] = useState(false)

  async function handleSubmit(values: { token: string }) {
    setLoading(true)
    try {
      const ok = await verifyToken(values.token)
      if (!ok) {
        message.error('Token 无效，请检查后重试')
        return
      }
      saveToken(values.token)
      onLogin()
    } catch {
      message.error('Token 无效，请检查后重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: '#f0f2f5' }}>
      <Card style={{ width: 380 }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <Title level={3} style={{ margin: 0 }}>📷 相框管理后台</Title>
          <Text type="secondary">请输入管理员 Token</Text>
        </div>
        <Form onFinish={handleSubmit} layout="vertical">
          <Form.Item name="token" rules={[{ required: true, message: '请输入 Admin Token' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="Admin Token" size="large" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block size="large">
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}
