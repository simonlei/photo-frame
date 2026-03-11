import { useEffect, useState } from 'react'
import { Button, Col, Image, message, Pagination, Popconfirm, Row, Spin, Typography } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import { api, type PhotoItem } from '../lib/api'

const { Title, Text } = Typography

export default function Photos() {
  const [photos, setPhotos] = useState<PhotoItem[]>([])
  const [loading, setLoading] = useState(true)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const pageSize = 50

  function load(p = page) {
    setLoading(true)
    api.photos(p, pageSize)
      .then(r => { setPhotos(r.photos); setTotal(r.total) })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [page])

  async function handleDelete(id: number) {
    try {
      await api.deletePhoto(id)
      message.success('照片已删除')
      load()
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '删除失败')
    }
  }

  return (
    <div>
      <Title level={4} style={{ marginTop: 0 }}>全部照片（共 {total} 张）</Title>
      <Spin spinning={loading}>
        <Row gutter={[12, 12]}>
          {photos.map(p => (
            <Col key={p.id} xs={12} sm={8} md={6} lg={4}>
              <div style={{ position: 'relative', background: '#f5f5f5', borderRadius: 8, overflow: 'hidden' }}>
                <Image src={p.url} alt="" style={{ width: '100%', height: 140, objectFit: 'cover', display: 'block' }} />
                <div style={{ padding: '4px 8px' }}>
                  <Text ellipsis style={{ fontSize: 11 }}>{p.device_name}</Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 10 }}>{p.uploader_name} · {p.uploaded_at}</Text>
                </div>
                <Popconfirm title="确认删除这张照片？" onConfirm={() => handleDelete(p.id)} okText="删除" okButtonProps={{ danger: true }} cancelText="取消">
                  <Button
                    danger size="small" icon={<DeleteOutlined />}
                    style={{ position: 'absolute', top: 4, right: 4, opacity: 0.85 }}
                  />
                </Popconfirm>
              </div>
            </Col>
          ))}
        </Row>
      </Spin>
      {total > pageSize && (
        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Pagination current={page} pageSize={pageSize} total={total} onChange={p => { setPage(p); load(p) }} />
        </div>
      )}
    </div>
  )
}
