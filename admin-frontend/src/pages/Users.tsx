import { useEffect, useState } from 'react'
import { Table, Typography } from 'antd'
import { api, type UserItem } from '../lib/api'

const { Title } = Typography

export default function Users() {
  const [users, setUsers] = useState<UserItem[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.users().then(r => setUsers(r.users)).finally(() => setLoading(false))
  }, [])

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '昵称', dataIndex: 'nickname' },
    { title: '绑定设备数', dataIndex: 'device_count', align: 'right' as const },
    { title: '上传照片数', dataIndex: 'photo_count', align: 'right' as const },
    { title: '注册时间', dataIndex: 'created_at' },
  ]

  return (
    <div>
      <Title level={4} style={{ marginTop: 0 }}>用户管理</Title>
      <Table dataSource={users} columns={columns} rowKey="id" loading={loading} />
    </div>
  )
}
