export const shippingUnits = ['kiện', 'thùng', 'pallet', 'bao', 'cái', 'hộp', 'kg', 'tấn', 'lít', 'm', 'm³'];
const measuredUnits = new Set(['kg', 'tấn', 'tan', 'lít', 'lit', 'l', 'm', 'm²', 'm2', 'm³', 'm3']);
export const allowsFraction = (unit: string) => measuredUnits.has(unit.trim().toLocaleLowerCase('vi'));
export function validQuantity(quantity: number, unit: string, allowZero = false) {
  return Number.isFinite(quantity) && quantity <= 1_000_000_000 && (allowZero ? quantity >= 0 : quantity > 0)
    && (allowsFraction(unit) || Number.isInteger(quantity));
}
