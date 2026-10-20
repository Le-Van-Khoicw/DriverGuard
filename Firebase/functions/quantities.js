const measuredUnits = new Set(['kg', 'tấn', 'tan', 'lít', 'lit', 'l', 'm', 'm²', 'm2', 'm³', 'm3']);
export function validQuantity(quantity, unit, allowZero = false) {
  return Number.isFinite(quantity) && quantity <= 1_000_000_000 && (allowZero ? quantity >= 0 : quantity > 0)
    && (measuredUnits.has(String(unit).trim().toLocaleLowerCase('vi')) || Number.isInteger(quantity));
}
