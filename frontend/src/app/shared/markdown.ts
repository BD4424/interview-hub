/**
 * Tiny, safe Markdown renderer for study notes. Everything is HTML-escaped first, then a small subset is converted:
 * # headings, **bold**, *italic*, `code`, ``` fenced code ```, - / 1. lists, > quotes and http(s) links.
 * Angular's sanitizer runs on top of this when bound with [innerHTML].
 */
export function renderMarkdown(src: string | null | undefined): string {
  if (!src) return '';
  const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const inline = (s: string) => esc(s)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');

  const lines = src.replace(/\r\n/g, '\n').split('\n');
  const out: string[] = [];
  let list: 'ul' | 'ol' | null = null;
  let para: string[] = [];
  const flushPara = () => { if (para.length) { out.push('<p>' + para.map(inline).join('<br>') + '</p>'); para = []; } };
  const closeList = () => { if (list) { out.push(`</${list}>`); list = null; } };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/^```/.test(line.trim())) {
      flushPara(); closeList();
      const code: string[] = [];
      i++;
      while (i < lines.length && !/^```/.test(lines[i].trim())) code.push(lines[i++]);
      out.push('<pre><code>' + esc(code.join('\n')) + '</code></pre>');
      continue;
    }
    const h = /^(#{1,4})\s+(.*)$/.exec(line);
    if (h) { flushPara(); closeList(); const n = h[1].length + 1; out.push(`<h${n}>${inline(h[2])}</h${n}>`); continue; }
    const ul = /^\s*[-*+]\s+(.*)$/.exec(line), ol = /^\s*\d+[.)]\s+(.*)$/.exec(line);
    if (ul || ol) {
      flushPara();
      const kind = ul ? 'ul' : 'ol';
      if (list !== kind) { closeList(); out.push(`<${kind}>`); list = kind; }
      out.push('<li>' + inline((ul ?? ol)![1]) + '</li>');
      continue;
    }
    const q = /^>\s?(.*)$/.exec(line);
    if (q) { flushPara(); closeList(); out.push('<blockquote>' + inline(q[1]) + '</blockquote>'); continue; }
    if (!line.trim()) { flushPara(); closeList(); continue; }
    closeList();
    para.push(line);
  }
  flushPara(); closeList();
  return out.join('\n');
}
