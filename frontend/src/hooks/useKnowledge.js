import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { knowledgeService } from '../services/knowledgeService';
import { isBusyStatus, validateKnowledgeFile } from '../utils/ai';

const POLL_MS = 3000;

export function useKnowledge() {
  const [documents, setDocuments] = useState([]);
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState('');
  const [upload, setUpload] = useState(null);
  const [notice, setNotice] = useState(null);
  const [selected, setSelected] = useState([]);
  const [busyIds, setBusyIds] = useState([]);
  const mounted = useRef(true);
  const uploadingRef = useRef(false);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const load = useCallback(async ({ silent = false } = {}) => {
    if (!silent) setStatus('loading');
    try {
      const list = await knowledgeService.list();
      if (!mounted.current) return;
      setDocuments(list);
      setStatus('ready');
      setError('');
    } catch (err) {
      if (!mounted.current || silent) return;
      setError(err.message);
      setStatus('error');
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const anyBusy = documents.some((d) => isBusyStatus(d.status));
  useEffect(() => {
    if (!anyBusy) return undefined;
    const id = setInterval(() => load({ silent: true }), POLL_MS);
    return () => clearInterval(id);
  }, [anyBusy, load]);

  const markBusy = (id, on) => setBusyIds((ids) => (on ? [...ids, id] : ids.filter((x) => x !== id)));

  const uploadFile = useCallback(async (file) => {
    if (uploadingRef.current) return false;
    const problem = validateKnowledgeFile(file);
    if (problem) {
      setNotice({ type: 'error', text: problem });
      return false;
    }
    uploadingRef.current = true;
    setNotice(null);
    setUpload({ name: file.name, progress: 0 });
    try {
      const created = await knowledgeService.upload(file, {
        onProgress: (p) => mounted.current && setUpload({ name: file.name, progress: p }),
      });
      if (!mounted.current) return true;
      setDocuments((list) => [created, ...list.filter((d) => d.id !== created.id)]);
      setNotice({ type: 'info', text: 'Uploaded. It is being processed and will be ready shortly.' });
      return true;
    } catch (err) {
      if (mounted.current) setNotice({ type: 'error', text: err.message });
      return false;
    } finally {
      uploadingRef.current = false;
      if (mounted.current) setUpload(null);
    }
  }, []);

  const remove = useCallback(async (doc) => {
    markBusy(doc.id, true);
    try {
      await knowledgeService.remove(doc.id);
      if (!mounted.current) return true;
      setDocuments((list) => list.filter((d) => d.id !== doc.id));
      setSelected((ids) => ids.filter((x) => x !== doc.id));
      return true;
    } catch (err) {
      if (mounted.current) setNotice({ type: 'error', text: err.message });
      return false;
    } finally {
      if (mounted.current) markBusy(doc.id, false);
    }
  }, []);

  const reprocess = useCallback(
    async (doc) => {
      markBusy(doc.id, true);
      setNotice(null);
      try {
        const updated = await knowledgeService.reprocess(doc.id);
        if (!mounted.current) return;
        setDocuments((list) => list.map((d) => (d.id === doc.id ? updated : d)));
        setSelected((ids) => ids.filter((x) => x !== doc.id));
      } catch (err) {
        if (mounted.current) {
          setNotice({ type: 'error', text: err.message });
          load({ silent: true });
        }
      } finally {
        if (mounted.current) markBusy(doc.id, false);
      }
    },
    [load],
  );

  const open = useCallback(async (id, fileName, page) => {
    try {
      await knowledgeService.open(id, fileName, page);
      return '';
    } catch (err) {
      return err.message;
    }
  }, []);

  const toggleSelected = useCallback((id) => setSelected((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id])), []);
  const clearSelection = useCallback(() => setSelected([]), []);

  const readyDocuments = useMemo(() => documents.filter((d) => d.status === 'READY'), [documents]);
  const selectedReadyIds = useMemo(() => {
    const ready = new Set(readyDocuments.map((d) => d.id));
    return selected.filter((id) => ready.has(id));
  }, [selected, readyDocuments]);

  return {
    documents,
    status,
    error,
    reload: load,
    upload,
    notice,
    dismissNotice: () => setNotice(null),
    busyIds,
    uploadFile,
    remove,
    reprocess,
    open,
    selected: selectedReadyIds,
    toggleSelected,
    clearSelection,
    readyCount: readyDocuments.length,
  };
}
