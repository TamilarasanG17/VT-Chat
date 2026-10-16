export const MIN_ZOOM = 1;
export const MAX_ZOOM = 4;
export const OUTPUT_SIZE = 512;
export const OUTPUT_TYPE = 'image/jpeg';
export const OUTPUT_QUALITY = 0.9;

export const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

export const coverScale = (natW, natH, view, zoom) => Math.max(view / natW, view / natH) * zoom;

export function maxOffset(natW, natH, view, zoom) {
  const s = coverScale(natW, natH, view, zoom);
  return { x: Math.max(0, (natW * s - view) / 2), y: Math.max(0, (natH * s - view) / 2) };
}

export function clampOffset(offset, natW, natH, view, zoom) {
  const m = maxOffset(natW, natH, view, zoom);
  return { x: clamp(offset.x, -m.x, m.x), y: clamp(offset.y, -m.y, m.y) };
}

export function sourceRect(natW, natH, view, zoom, offset) {
  const s = coverScale(natW, natH, view, zoom);
  const size = view / s;
  return { sx: natW / 2 - offset.x / s - size / 2, sy: natH / 2 - offset.y / s - size / 2, size };
}

export function cropToFile(img, view, zoom, offset, name = 'profile-photo.jpg') {
  return new Promise((resolve, reject) => {
    try {
      const natW = img.naturalWidth;
      const natH = img.naturalHeight;
      if (!natW || !natH) throw new Error('The image has no size');
      const { sx, sy, size } = sourceRect(natW, natH, view, zoom, clampOffset(offset, natW, natH, view, zoom));
      const out = clamp(Math.floor(size), 128, OUTPUT_SIZE);
      const canvas = document.createElement('canvas');
      canvas.width = out;
      canvas.height = out;
      const ctx = canvas.getContext('2d');
      if (!ctx) throw new Error('Canvas is not available');
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(0, 0, out, out);
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, sx, sy, size, size, 0, 0, out, out);
      canvas.toBlob(
        (blob) => {
          if (!blob || blob.type !== OUTPUT_TYPE) {
            reject(new Error('The cropped image could not be created'));
            return;
          }
          resolve(new File([blob], name, { type: OUTPUT_TYPE }));
        },
        OUTPUT_TYPE,
        OUTPUT_QUALITY,
      );
    } catch (e) {
      reject(e instanceof Error ? e : new Error('The cropped image could not be created'));
    }
  });
}
