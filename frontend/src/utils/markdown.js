const FENCE = /^\s{0,3}```\s*([\w+#.-]*)\s*$/;
const HEADING = /^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$/;
const RULE = /^\s{0,3}([-*_])(?:\s*\1){2,}\s*$/;
const BULLET = /^\s*[-*+]\s+(.*)$/;
const ORDERED = /^\s*(\d{1,9})[.)]\s+(.*)$/;
const QUOTE = /^\s{0,3}>\s?(.*)$/;
const TABLE_SEPARATOR = /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/;

const isSpecialStart = (line) =>
  FENCE.test(line) || HEADING.test(line) || RULE.test(line) || BULLET.test(line) || ORDERED.test(line) || QUOTE.test(line);

const splitRow = (line) =>
  line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((c) => c.trim());

export function parseBlocks(source) {
  const lines = String(source ?? '').replace(/\r\n?/g, '\n').split('\n');
  const blocks = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    if (line.trim() === '') {
      i += 1;
      continue;
    }

    const fence = line.match(FENCE);
    if (fence) {
      const code = [];
      i += 1;
      while (i < lines.length && !/^\s{0,3}```\s*$/.test(lines[i])) {
        code.push(lines[i]);
        i += 1;
      }
      i += 1;
      blocks.push({ type: 'code', lang: fence[1] || '', text: code.join('\n') });
      continue;
    }

    const heading = line.match(HEADING);
    if (heading) {
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2] });
      i += 1;
      continue;
    }

    if (RULE.test(line)) {
      blocks.push({ type: 'hr' });
      i += 1;
      continue;
    }

    if (BULLET.test(line) || ORDERED.test(line)) {
      const ordered = ORDERED.test(line);
      const start = ordered ? Number(line.match(ORDERED)[1]) : 1;
      const items = [];
      while (i < lines.length) {
        const m = ordered ? lines[i].match(ORDERED) : lines[i].match(BULLET);
        if (m) {
          items.push(ordered ? m[2] : m[1]);
          i += 1;
        } else if (/^\s+\S/.test(lines[i]) && items.length && !BULLET.test(lines[i]) && !ORDERED.test(lines[i])) {
          items[items.length - 1] += `\n${lines[i].trim()}`;
          i += 1;
        } else if (/^\s+[-*+]\s|^\s+\d+[.)]\s/.test(lines[i]) && items.length) {
          items[items.length - 1] += `\n${lines[i].trim()}`;
          i += 1;
        } else {
          break;
        }
      }
      blocks.push({ type: ordered ? 'ol' : 'ul', start, items });
      continue;
    }

    if (QUOTE.test(line)) {
      const quote = [];
      while (i < lines.length && QUOTE.test(lines[i])) {
        quote.push(lines[i].match(QUOTE)[1]);
        i += 1;
      }
      blocks.push({ type: 'quote', text: quote.join('\n') });
      continue;
    }

    if (line.includes('|') && i + 1 < lines.length && TABLE_SEPARATOR.test(lines[i + 1]) && lines[i + 1].includes('-')) {
      const head = splitRow(line);
      const rows = [];
      i += 2;
      while (i < lines.length && lines[i].trim() !== '' && lines[i].includes('|')) {
        rows.push(splitRow(lines[i]));
        i += 1;
      }
      blocks.push({ type: 'table', head, rows });
      continue;
    }

    const para = [line];
    i += 1;
    while (i < lines.length && lines[i].trim() !== '' && !isSpecialStart(lines[i])) {
      para.push(lines[i]);
      i += 1;
    }
    blocks.push({ type: 'p', text: para.join('\n') });
  }
  return blocks;
}

const INLINE_SOURCE = /`([^`\n]+)`|\*\*(\S(?:[\s\S]*?\S)?)\*\*|\*([^*\s](?:[^*\n]*?[^*\s])?)\*|\[([^\]\n]+)\]\(([^)\s]+)\)/;

export function safeUrl(raw) {
  try {
    const url = new URL(raw);
    return ['http:', 'https:', 'mailto:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

export function parseInline(text, depth = 0) {
  const nodes = [];
  const source = String(text ?? '');
  let last = 0;
  let m;
  const INLINE = new RegExp(INLINE_SOURCE.source, 'g');
  while ((m = INLINE.exec(source)) !== null) {
    if (m.index > last) nodes.push({ type: 'text', text: source.slice(last, m.index) });
    if (m[1] !== undefined) {
      nodes.push({ type: 'code', text: m[1] });
    } else if (m[2] !== undefined) {
      nodes.push({ type: 'bold', children: depth < 3 ? parseInline(m[2], depth + 1) : [{ type: 'text', text: m[2] }] });
    } else if (m[3] !== undefined) {
      nodes.push({ type: 'italic', children: depth < 3 ? parseInline(m[3], depth + 1) : [{ type: 'text', text: m[3] }] });
    } else {
      const href = safeUrl(m[5]);
      if (href) nodes.push({ type: 'link', href, children: [{ type: 'text', text: m[4] }] });
      else nodes.push({ type: 'text', text: m[0] });
    }
    last = m.index + m[0].length;
  }
  if (last < source.length) nodes.push({ type: 'text', text: source.slice(last) });
  return nodes;
}
