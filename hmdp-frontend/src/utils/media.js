const IMG_BASE = import.meta.env.VITE_IMG_BASE || '/imgs';

export function resolveImg(path) {
  if (!path) return '';
  if (path.startsWith('data:') || path.startsWith('http://') || path.startsWith('https://')) return path;
  if (path.startsWith(IMG_BASE)) return path;
  if (path.startsWith('/')) return `${IMG_BASE}${path}`;
  return `${IMG_BASE}/${path}`;
}

export function splitImages(images) {
  if (!images) return [];
  const list = Array.isArray(images) ? images : String(images).split(',');
  return list.map(s => s.trim()).filter(Boolean).map(resolveImg);
}

