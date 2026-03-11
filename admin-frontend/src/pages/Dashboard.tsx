import { useEffect, useState } from 'react'
import { Card, Col, Row, Statistic, Table, Typography } from 'antd'
import { TeamOutlined, MobileOutlined, PictureOutlined } from '@ant-design/icons'
import { api, type Stats } from '../lib/api'

const { Title } = Typography

export default function Dashboard() {
  const [stats, setStats] = useState<Stats | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.stats().then(setStats).finally(() => setLoading(false))
  }, [])

  const columns = [
    { title: '排名', render: (_: unknown, __: unknown, idx: number) => idx + 1, width: 60 },
    { title: '设备名称', dataIndex: 'device_name' },
    { title: '照片数', dataIndex: 'photo_count', align: 'right' as const },
  ]

  return (
    <div>
      <Title level={4} style={{ marginTop: 0 }}>概览</Title>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={8}>
          <Card loading={loading}>
            <Statistic title="总用户数" value={stats?.user_count ?? 0} prefix={<TeamOutlined />} />
          </Card>
        </Col>
        <Col span={8}>
          <Card loading={loading}>
            <Statistic title="总设备数" value={stats?.device_count ?? 0} prefix={<MobileOutlined />} />
          </Card>
        </Col>
        <Col span={8}>
          <Card loading={loading}>
            <Statistic title="总照片数" value={stats?.photo_count ?? 0} prefix={<PictureOutlined />} />
          </Card>
        </Col>
      </Row>
      <Card title="设备照片数 Top 10" loading={loading}>
        <Table
          dataSource={stats?.top_devices ?? []}
          columns={columns}
          rowKey="device_id"
          pagination={false}
          size="small"
        />
      </Card>
    </div>
  )
}
