import { useEffect, useState } from 'react'
import { Button, message, Popconfirm, Space, Table, Typography } from 'antd'
import { EyeOutlined, DeleteOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { api, type DeviceItem } from '../lib/api'

const { Title } = Typography

export default function Devices() {
  const [devices, setDevices] = useState<DeviceItem[]>([])
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  function load() {
    setLoading(true)
    api.devices().then(r => setDevices(r.devices)).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  async function handleDelete(id: string) {
    try {
      await api.deleteDevice(id)
      message.success('设备已删除')
      load()
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '删除失败')
    }
  }

  const columns = [
    { title: '设备名称', dataIndex: 'name' },
    { title: '设备 ID', dataIndex: 'id', render: (v: string) => <code style={{ fontSize: 12 }}>{v}</code> },
    { title: '绑定用户数', dataIndex: 'user_count', align: 'right' as const },
    { title: '照片数', dataIndex: 'photo_count', align: 'right' as const },
    { title: '创建时间', dataIndex: 'created_at' },
    {
      title: '操作',
      render: (_: unknown, record: DeviceItem) => (
        <Space>
          <Button size="small" icon={<EyeOutlined />} onClick={() => navigate(`/devices/${record.id}`)}>
            查看相册
          </Button>
          <Popconfirm
            title="确认删除该设备及其所有照片？"
            onConfirm={() => handleDelete(record.id)}
            okText="删除"
            okButtonProps={{ danger: true }}
            cancelText="取消"
          >
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Title level={4} style={{ marginTop: 0 }}>设备管理</Title>
      <Table dataSource={devices} columns={columns} rowKey="id" loading={loading} />
    </div>
  )
}
