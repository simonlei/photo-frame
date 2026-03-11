import { useEffect, useState } from 'react'
import { Button, Col, Image, message, Popconfirm, Row, Spin, Typography, Pagination } from 'antd'
import { DeleteOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { api, type PhotoItem } from '../lib/api'

const { Title, Text } = Typography

export default function DevicePhotos() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [photos, setPhotos] = useState<PhotoItem[]>([])
  const [loading, setLoading] = useState(true)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const pageSize = 50

  function load(p = page) {
    if (!id) return
    setLoading(true)
    api.devicePhotos(id, p, pageSize)
      .then(r => { setPhotos(r.photos); setTotal(r.total) })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [id, page])

  async function handleDelete(photoId: number) {
    try {
      await api.deletePhoto(photoId)
      message.success('照片已删除')
      load()
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '删除失败')
    }
  }

  return (
    <div>
      <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/devices')} style={{ marginBottom: 16 }}>
        返回设备列表
      </Button>
      <Title level={4} style={{ marginTop: 0 }}>设备相册（共 {total} 张）</Title>
      <Spin spinning={loading}>
        <Row gutter={[12, 12]}>
          {photos.map(p => (
            <Col key={p.id} xs={12} sm={8} md={6} lg={4}>
              <div style={{ position: 'relative', background: '#f5f5f5', borderRadius: 8, overflow: 'hidden' }}>
                <Image src={p.url} alt="" style={{ width: '100%', height: 140, objectFit: 'cover', display: 'block' }} />
                <div style={{ padding: '4px 8px', fontSize: 11, color: '#666' }}>
                  <Text ellipsis style={{ fontSize: 11 }}>{p.uploader_name}</Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 10 }}>{p.uploaded_at}</Text>
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
