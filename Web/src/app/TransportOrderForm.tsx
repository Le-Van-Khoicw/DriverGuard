import { FormEvent, useState } from 'react';
import { Item } from '../api/dispatch';

export default function TransportOrderForm({ busy, error, save }: {
  busy: boolean; error: string; save: (data: Record<string, unknown>) => void;
}) {
  const [items, setItems] = useState<Item[]>([{ name: '', quantity: 1, unit: 'kiện' }]);
  const [sameContact, setSameContact] = useState(true);
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget));
    save({ ...f, sender: sameContact ? f.customer : f.sender, senderPhone: sameContact ? f.customerPhone : f.senderPhone,
      start: new Date(String(f.start)).getTime(), end: new Date(String(f.end)).getTime(), weightKg: Number(f.weightKg), items });
  }
  const field = (name: string, label: string, example: string, type = 'text', required = true) => <label>{label}{required && <span className="field-required" aria-hidden="true"> *</span>}<input name={name} type={type} placeholder={example} required={required} min={type === 'number' ? '0.001' : undefined} step={type === 'number' ? 'any' : undefined} /></label>;
  return <form className="compact-order-form" onSubmit={submit}>
    <p className="form-intro">Đơn chở hàng cho khách doanh nghiệp. Các ô có dấu * cần được điền.</p>
    <fieldset><legend>1. Khách thuê vận chuyển</legend><div className="log-form-grid">{field('customer', 'Doanh nghiệp / tên khách', 'VD: Công ty TNHH Minh An')}{field('customerPhone', 'SĐT liên hệ bên khách', 'VD: 0901234567', 'tel')}</div></fieldset>
    <div className="log-form-grid order-endpoints">
      <fieldset><legend>2. Điểm lấy hàng</legend>{field('pickupAddress', 'Địa chỉ / kho lấy hàng', 'VD: Kho A, 12 đường số 3, KCN Sóng Thần')}<label className="order-copy-contact"><input type="checkbox" checked={sameContact} onChange={e => setSameContact(e.target.checked)} />Dùng tên và số liên hệ của khách để lấy hàng</label>{!sameContact && <>{field('sender', 'Người liên hệ lấy hàng', 'VD: Anh Bình — thủ kho')}{field('senderPhone', 'SĐT liên hệ lấy hàng', 'VD: 0912345678', 'tel')}</>}</fieldset>
      <fieldset><legend>3. Điểm giao hàng</legend>{field('deliveryAddress', 'Địa chỉ / kho giao hàng', 'VD: Kho đại lý An Phú, Nha Trang')}{field('receiver', 'Người liên hệ nhận hàng', 'VD: Chị Hương — phụ trách kho')}{field('receiverPhone', 'SĐT liên hệ nhận hàng', 'VD: 0987654321', 'tel')}</fieldset>
    </div>
    <fieldset><legend>4. Hàng hóa và thời gian</legend><div className="order-items-head"><span>Tên hàng</span><span>Số lượng</span><span>Đơn vị</span><span /></div>{items.map((item, index) => <div className="log-item" key={index}><input aria-label={`Tên hàng ${index + 1}`} placeholder="VD: Thùng nước đóng chai" value={item.name} required maxLength={200} onChange={e => setItems(v => v.map((x, i) => i === index ? { ...x, name: e.target.value } : x))} /><input aria-label={`Số lượng ${index + 1}`} type="number" min="0.001" step="any" placeholder="VD: 100" value={item.quantity} required onChange={e => setItems(v => v.map((x, i) => i === index ? { ...x, quantity: Number(e.target.value) } : x))} /><input aria-label={`Đơn vị ${index + 1}`} placeholder="VD: thùng, pallet" value={item.unit} required maxLength={30} onChange={e => setItems(v => v.map((x, i) => i === index ? { ...x, unit: e.target.value } : x))} /><button type="button" disabled={items.length === 1 || busy} aria-label={`Xóa mặt hàng ${index + 1}`} onClick={() => setItems(v => v.filter((_, i) => i !== index))}>×</button></div>)}<button type="button" className="order-add-item" disabled={items.length >= 30 || busy} onClick={() => setItems(v => [...v, { name: '', quantity: 1, unit: 'kiện' }])}>+ Thêm mặt hàng</button><div className="order-schedule">{field('weightKg', 'Tổng khối lượng (kg)', 'VD: 2500', 'number')}{field('start', 'Sẵn sàng lấy hàng lúc', '', 'datetime-local')}{field('end', 'Hạn giao mong muốn', '', 'datetime-local')}</div>{field('note', 'Lưu ý xử lý hàng', 'VD: Giữ khô ráo, không chồng quá 3 lớp', 'text', false)}</fieldset>
    {error && <p className="log-error" role="alert">{error}</p>}
    <div className="order-form-footer"><span>Sau khi lưu, điều phối chọn đơn để lập chuyến.</span><button className="log-submit" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu đơn vận chuyển'}</button></div>
  </form>;
}
