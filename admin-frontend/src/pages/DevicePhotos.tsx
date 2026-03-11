import { useEffect, useState } from 'react'
import { Alert, Button, message, Spin, Typography, Pagination } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { api, type PhotoItem } from '../lib/api'
import PhotoGrid from '../components/PhotoGrid'

const { Title, Text } = Typography

export default function DevicePhotos() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [photos, setPhotos] = useState<PhotoItem[]>([])
  const [loading, setLoading] = useState(true)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [error, setError] = useState<string | null>(null)
  const pageSize = 50

  useEffect(() => {
    if (!id) return
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    api.devicePhotos(id, page, pageSize)
      .then(r => { setPhotos(r.photos); setTotal(r.total) })
      .catch((err: unknown) => {
        if ((err as Error).name !== 'AbortError') {
          setError(err instanceof Error ? err.message : '加载失败')
        }
      })
      .finally(() => setLoading(false))
    return () => controller.abort()
  }, [id, page])

  async function handleDelete(photoId: number) {
    try {
      await api.deletePhoto(photoId)
      message.success('照片已删除')
      if (!id) return
      setLoading(true)
      api.devicePhotos(id, page, pageSize)
        .then(r => { setPhotos(r.photos); setTotal(r.total) })
        .catch((err: unknown) => {
          setError(err instanceof Error ? err.message : '加载失败')
        })
        .finally(() => setLoading(false))
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
      {error && <Alert message={error} type="error" style={{ marginBottom: 16 }} />}
      <Spin spinning={loading}>
        <PhotoGrid
          photos={photos}
          onDelete={handleDelete}
          renderCaption={p => (
            <div style={{ fontSize: 11, color: '#666' }}>
              <Text ellipsis style={{ fontSize: 11 }}>{p.uploader_name}</Text>
              <br />
              <Text type="secondary" style={{ fontSize: 10 }}>{p.uploaded_at}</Text>
            </div>
          )}
        />
      </Spin>
      {total > pageSize && (
        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Pagination current={page} pageSize={pageSize} total={total} onChange={p => setPage(p)} />
        </div>
      )}
    </div>
  )
}
