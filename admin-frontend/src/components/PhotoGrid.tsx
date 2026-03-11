import { Button, Col, Image, Popconfirm, Row } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import type { PhotoItem } from '../lib/api'

interface Props {
  photos: PhotoItem[]
  onDelete: (id: number) => void
  renderCaption: (p: PhotoItem) => React.ReactNode
}

export default function PhotoGrid({ photos, onDelete, renderCaption }: Props) {
  return (
    <Row gutter={[12, 12]}>
      {photos.map(p => (
        <Col key={p.id} xs={12} sm={8} md={6} lg={4}>
          <div style={{ position: 'relative', background: '#f5f5f5', borderRadius: 8, overflow: 'hidden' }}>
            <Image src={p.url} alt="" style={{ width: '100%', height: 140, objectFit: 'cover', display: 'block' }} />
            <div style={{ padding: '4px 8px' }}>
              {renderCaption(p)}
            </div>
            <Popconfirm
              title="确认删除这张照片？"
              onConfirm={() => onDelete(p.id)}
              okText="删除"
              okButtonProps={{ danger: true }}
              cancelText="取消"
            >
              <Button
                danger size="small" icon={<DeleteOutlined />}
                style={{ position: 'absolute', top: 4, right: 4, opacity: 0.85 }}
              />
            </Popconfirm>
          </div>
        </Col>
      ))}
    </Row>
  )
}
