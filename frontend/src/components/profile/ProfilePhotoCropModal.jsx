import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import Button from '../Button.jsx';
import { CloseIcon } from '../Icons.jsx';
import { fadeVariants, modalVariants } from '../../utils/motion';
import { clamp, clampOffset, coverScale, cropToFile, MAX_ZOOM, MIN_ZOOM } from '../../utils/cropImage';

const MIN_SIDE = 64;
const PREVIEW = 64;
const KEY_STEP = 12;

export default function ProfilePhotoCropModal({ file, busy = false, progress = 0, error = '', onConfirm, onCancel }) {
  const open = !!file;
  const stageRef = useRef(null);
  const imgRef = useRef(null);
  const drag = useRef(null);
  const [src, setSrc] = useState(null);
  const [nat, setNat] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [view, setView] = useState(280);
  const [zoom, setZoom] = useState(MIN_ZOOM);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [cropping, setCropping] = useState(false);
  const [cropError, setCropError] = useState('');

  useEffect(() => {
    if (!file) return undefined;
    const url = URL.createObjectURL(file);
    setSrc(url);
    setNat(null);
    setLoadError('');
    setCropError('');
    setZoom(MIN_ZOOM);
    setOffset({ x: 0, y: 0 });
    return () => {
      URL.revokeObjectURL(url);
      setSrc(null);
    };
  }, [file]);

  useEffect(() => {
    if (!open || !stageRef.current || typeof ResizeObserver === 'undefined') return undefined;
    const el = stageRef.current;
    const measure = () => setView(Math.max(160, Math.round(el.getBoundingClientRect().width)));
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [open, nat]);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => e.key === 'Escape' && !busy && !cropping && onCancel();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, busy, cropping, onCancel]);

  const apply = useCallback(
    (nextOffset, nextZoom) => {
      if (!nat) return;
      setOffset(clampOffset(nextOffset, nat.w, nat.h, view, nextZoom));
    },
    [nat, view],
  );

  useEffect(() => {
    if (nat) setOffset((o) => clampOffset(o, nat.w, nat.h, view, zoom));
  }, [view, nat, zoom]);

  const onImageLoad = (e) => {
    const { naturalWidth: w, naturalHeight: h } = e.currentTarget;
    if (!w || !h) {
      setLoadError('That image could not be read.');
      return;
    }
    if (Math.min(w, h) < MIN_SIDE) {
      setLoadError(`The image is too small (at least ${MIN_SIDE}×${MIN_SIDE} pixels).`);
      return;
    }
    setNat({ w, h });
  };

  const changeZoom = (value) => {
    const next = clamp(Number(value), MIN_ZOOM, MAX_ZOOM);
    setZoom(next);
    apply(offset, next);
  };

  const onPointerDown = (e) => {
    if (!nat || busy) return;
    e.currentTarget.setPointerCapture?.(e.pointerId);
    drag.current = { x: e.clientX, y: e.clientY, start: offset };
  };
  const onPointerMove = (e) => {
    if (!drag.current) return;
    apply({ x: drag.current.start.x + e.clientX - drag.current.x, y: drag.current.start.y + e.clientY - drag.current.y }, zoom);
  };
  const endDrag = () => {
    drag.current = null;
  };
  const onWheel = (e) => {
    if (!nat || busy) return;
    changeZoom(zoom - e.deltaY * 0.002);
  };
  const onKeyDown = (e) => {
    const moves = { ArrowLeft: [KEY_STEP, 0], ArrowRight: [-KEY_STEP, 0], ArrowUp: [0, KEY_STEP], ArrowDown: [0, -KEY_STEP] };
    if (moves[e.key]) {
      e.preventDefault();
      apply({ x: offset.x + moves[e.key][0], y: offset.y + moves[e.key][1] }, zoom);
    } else if (e.key === '+' || e.key === '=') changeZoom(zoom + 0.1);
    else if (e.key === '-') changeZoom(zoom - 0.1);
  };

  const confirm = async () => {
    if (!nat || !imgRef.current || busy || cropping) return;
    setCropping(true);
    setCropError('');
    try {
      const cropped = await cropToFile(imgRef.current, view, zoom, offset);
      await onConfirm(cropped);
    } catch (e) {
      setCropError(e?.message || 'The cropped image could not be created');
    } finally {
      setCropping(false);
    }
  };

  const working = busy || cropping;
  const scale = nat ? coverScale(nat.w, nat.h, view, zoom) : 1;
  const imgStyle = (k) =>
    nat
      ? {
          width: nat.w * scale * k,
          height: nat.h * scale * k,
          maxWidth: 'none',
          left: '50%',
          top: '50%',
          transform: `translate(calc(-50% + ${offset.x * k}px), calc(-50% + ${offset.y * k}px))`,
        }
      : { display: 'none' };
  const shownError = loadError || cropError || error;

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          variants={fadeVariants}
          initial="initial"
          animate="animate"
          exit="exit"
          className="pt-safe pb-safe fixed inset-0 z-50 flex items-end justify-center bg-slate-900/60 p-3 min-[400px]:p-4 sm:items-center"
          onMouseDown={(e) => e.target === e.currentTarget && !working && onCancel()}
        >
          <motion.div
            variants={modalVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            role="dialog"
            aria-modal="true"
            aria-label="Crop profile photo"
            className="relative flex max-h-[calc(100dvh-1.5rem)] w-full max-w-sm min-w-0 flex-col overflow-y-auto rounded-2xl border border-slate-200 bg-white p-4 shadow-xl min-[400px]:p-5 dark:border-slate-700 dark:bg-slate-900"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="text-lg font-bold text-slate-900 dark:text-white">Crop your photo</h2>
                <p className="text-sm text-slate-500 dark:text-slate-400">Drag to move, use the slider to zoom.</p>
              </div>
              <button
                type="button"
                onClick={onCancel}
                disabled={working}
                aria-label="Close"
                className="-mr-1 -mt-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-slate-500 transition hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/30 disabled:opacity-50 dark:text-slate-400 dark:hover:bg-slate-800"
              >
                <CloseIcon className="h-5 w-5" />
              </button>
            </div>

            <div
              ref={stageRef}
              tabIndex={nat ? 0 : -1}
              role="application"
              aria-label="Photo crop area. Arrow keys move the photo, plus and minus zoom."
              onPointerDown={onPointerDown}
              onPointerMove={onPointerMove}
              onPointerUp={endDrag}
              onPointerCancel={endDrag}
              onWheel={onWheel}
              onKeyDown={onKeyDown}
              style={{ touchAction: 'none' }}
              className={`relative mx-auto mt-4 aspect-square w-full max-w-[320px] select-none overflow-hidden rounded-xl bg-slate-900 outline-none focus-visible:ring-4 focus-visible:ring-indigo-500/40 ${
                nat && !busy ? 'cursor-grab active:cursor-grabbing' : ''
              }`}
            >
              {src && !loadError && (
                <img ref={imgRef} src={src} alt="" draggable={false} onLoad={onImageLoad} onError={() => setLoadError('That image could not be opened.')} className="pointer-events-none absolute" style={imgStyle(1)} />
              )}
              {!nat && !loadError && (
                <div className="absolute inset-0 flex items-center justify-center text-sm text-slate-300" role="status">
                  Loading image…
                </div>
              )}
              {nat && (
                <div aria-hidden="true" className="pointer-events-none absolute inset-0 rounded-full border-2 border-white/90 shadow-[0_0_0_999px_rgba(15,23,42,0.6)]" />
              )}
            </div>

            <div className="mx-auto mt-4 flex w-full max-w-[320px] items-center gap-3">
              <div
                aria-hidden="true"
                className="relative shrink-0 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700"
                style={{ width: PREVIEW, height: PREVIEW }}
                title="Preview"
              >
                {nat && src && <img src={src} alt="" draggable={false} className="absolute" style={imgStyle(PREVIEW / view)} />}
              </div>
              <label className="flex min-w-0 flex-1 flex-col gap-1 text-xs font-medium text-slate-500 dark:text-slate-400">
                Zoom
                <input
                  type="range"
                  min={MIN_ZOOM}
                  max={MAX_ZOOM}
                  step="0.01"
                  value={zoom}
                  disabled={!nat || working}
                  onChange={(e) => changeZoom(e.target.value)}
                  aria-label="Zoom"
                  className="h-6 w-full accent-indigo-600 disabled:opacity-50"
                />
              </label>
            </div>

            {working && (
              <div
                className="mx-auto mt-3 h-2 w-full max-w-[320px] overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700"
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={busy ? progress : undefined}
                aria-label="Upload progress"
              >
                <div className={`h-full bg-indigo-600 transition-all ${busy ? '' : 'w-1/3 animate-pulse'}`} style={busy ? { width: `${progress}%` } : undefined} />
              </div>
            )}

            {shownError && (
              <p role="alert" className="mx-auto mt-3 w-full max-w-[320px] break-words text-sm text-red-600 dark:text-red-400">
                {shownError}
              </p>
            )}

            <div className="mx-auto mt-4 flex w-full max-w-[320px] flex-col gap-2 min-[360px]:flex-row-reverse">
              <Button onClick={confirm} loading={working} disabled={!nat || !!loadError} className="min-[360px]:flex-1">
                {busy ? `Uploading ${progress}%` : cropping ? 'Cropping…' : 'Crop & Upload'}
              </Button>
              <Button variant="secondary" onClick={onCancel} disabled={working} className="min-[360px]:flex-1">
                Cancel
              </Button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
