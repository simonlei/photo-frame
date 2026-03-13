import { useEffect, useState } from 'react'
import { Alert, message, Pagination, Spin, Tooltip, Typography } from 'antd'
import { api, type PhotoItem } from '../lib/api'
import PhotoGrid from '../components/PhotoGrid'

const { Title, Text } = Typography

export default function Photos() {
  const [photos, setPhotos] = useState<PhotoItem[]>([])
  const [loading, setLoading] = useState(true)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [error, setError] = useState<string | null>(null)
  const pageSize = 50

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    api.photos(page, pageSize)
      .then(r => { setPhotos(r.photos); setTotal(r.total) })
      .catch((err: unknown) => {
        if ((err as Error).name !== 'AbortError') {
          setError(err instanceof Error ? err.message : '加载失败')
        }
      })
      .finally(() => setLoading(false))
    return () => controller.abort()
  }, [page])

  async function handleDelete(id: number) {
    try {
      await api.deletePhoto(id)
      message.success('照片已删除')
      setLoading(true)
      api.photos(page, pageSize)
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
      <Title level={4} style={{ marginTop: 0 }}>全部照片（共 {total} 张）</Title>
      {error && <Alert message={error} type="error" style={{ marginBottom: 16 }} />}
      <Spin spinning={loading}>
        <PhotoGrid
          photos={photos}
          onDelete={handleDelete}
          renderCaption={p => (
            <Tooltip 
              title={
                <div style={{ fontSize: 12 }}>
                  {p.taken_at && <div>📸 拍摄于 {p.taken_at}</div>}
                  {p.location_address && <div>📍 {p.location_address}</div>}
                  {p.camera_model && <div>📷 {p.camera_model}</div>}
                </div>
              }
              placement="top"
            >
              <div>
                <Text ellipsis style={{ fontSize: 11 }}>{p.device_name}</Text>
                <br />
                {p.location_address && (
                  <>
                    <Text ellipsis type="secondary" style={{ fontSize: 10 }}>
                      📍 {p.location_address.slice(0, 12)}...
                    </Text>
                    <br />
                  </>
                )}
                <Text type="secondary" style={{ fontSize: 10 }}>
                  {p.uploader_name} · {p.taken_at?.slice(0, 10) || p.uploaded_at}
                </Text>
              </div>
            </Tooltip>
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
