import DOMPurify from 'dompurify';

const SAFE_ANCHOR_PROTOCOLS = new Set(['http:', 'https:', 'mailto:']);
const SAFE_IMAGE_PROTOCOLS = new Set(['http:', 'https:']);
const SCRIPTABLE_PROTOCOL_RE = /^(?:javascript|data|vbscript|file|filesystem|blob):/i;
const SCHEME_RE = /^[a-zA-Z][a-zA-Z0-9+.-]*:/;
const SAFE_RASTER_DATA_IMAGE_RE = /^data:image\/(?:png|jpe?g|gif|webp);base64,[a-z0-9+/=\s]+$/i;

const EDITOR_ALLOWED_TAGS = [
  'a', 'blockquote', 'br', 'code', 'del', 'div', 'em', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'hr', 'img', 'input', 'li', 'mark', 'ol', 'p', 'pre', 's', 'span', 'strong', 'sub',
  'sup', 'table', 'tbody', 'td', 'th', 'thead', 'tr', 'u', 'ul',
];

const EDITOR_ALLOWED_ATTR = [
  'alt', 'checked', 'class', 'colspan', 'disabled', 'href', 'rel', 'rowspan', 'src',
  'target', 'title', 'type',
];

export function isSafeAnchorUrl(value: string | null | undefined): boolean {
  const url = normalizeUrlCandidate(value);
  if (!url) return false;
  if (url.startsWith('#')) return true;
  if (isSafeRelativeUrl(url)) return true;
  if (isProtocolRelative(url)) return false;
  if (hasScriptableProtocol(url)) return false;

  return isSafeProtocol(url, SAFE_ANCHOR_PROTOCOLS);
}

export function isSafeImageUrl(value: string | null | undefined): boolean {
  const url = normalizeUrlCandidate(value);
  if (!url) return false;
  if (SAFE_RASTER_DATA_IMAGE_RE.test(url)) return true;
  if (isSafeRelativeUrl(url)) return true;
  if (isProtocolRelative(url)) return false;
  if (hasScriptableProtocol(url)) return false;

  return isSafeProtocol(url, SAFE_IMAGE_PROTOCOLS);
}

export function sanitizeAnchorUrl(value: string | null | undefined): string | undefined {
  const url = normalizeUrlCandidate(value);
  return url && isSafeAnchorUrl(url) ? url : undefined;
}

export function sanitizeImageUrl(value: string | null | undefined): string | undefined {
  const url = normalizeUrlCandidate(value);
  return url && isSafeImageUrl(url) ? url : undefined;
}

export function markdownUrlTransform(value: string, key: string): string {
  if (key === 'src') {
    return sanitizeImageUrl(value) ?? '';
  }
  if (key === 'href') {
    return sanitizeAnchorUrl(value) ?? '';
  }
  return value;
}

export function sanitizeFormulaInput(value: string | null | undefined): string | null {
  const formula = value?.trim();
  if (!formula || /[<>]/.test(formula)) {
    return null;
  }
  return formula;
}

export function sanitizeEditorHtml(html: string | null | undefined): string {
  const input = html ?? '';
  if (typeof window === 'undefined') {
    return sanitizeHtmlFallback(input);
  }

  const sanitized = DOMPurify.sanitize(input, {
    ALLOWED_TAGS: EDITOR_ALLOWED_TAGS,
    ALLOWED_ATTR: EDITOR_ALLOWED_ATTR,
    ALLOW_DATA_ATTR: true,
    FORBID_ATTR: ['style'],
    FORBID_TAGS: ['base', 'embed', 'iframe', 'link', 'meta', 'object', 'script', 'style', 'svg'],
  });

  return sanitizeHtmlFallback(sanitized);
}

export function sanitizeHtmlFallback(html: string): string {
  return html
    .replace(/<\s*(script|style|iframe|object|embed|link|meta|base|svg|math)\b[^>]*>[\s\S]*?<\s*\/\s*\1\s*>/gi, '')
    .replace(/<\s*(script|style|iframe|object|embed|link|meta|base|svg|math)\b[^>]*\/?>/gi, '')
    .replace(/\sstyle\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/\son[a-z0-9_-]+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/\s(href|src)\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, (_match, attr: string, rawValue: string) => {
      const unquoted = stripQuotes(rawValue);
      const safe = attr.toLowerCase() === 'src'
        ? sanitizeImageUrl(unquoted)
        : sanitizeAnchorUrl(unquoted);
      return safe ? ` ${attr.toLowerCase()}="${escapeHtmlAttribute(safe)}"` : '';
    });
}

export function shouldShowErrorStack(mode: string): boolean {
  return mode !== 'production';
}

function normalizeUrlCandidate(value: string | null | undefined): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function isSafeRelativeUrl(url: string): boolean {
  return (
    (url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\'))
    || url.startsWith('./')
    || url.startsWith('../')
  );
}

function isProtocolRelative(url: string): boolean {
  return url.startsWith('//') || url.startsWith('\\\\');
}

function hasScriptableProtocol(url: string): boolean {
  const compact = url.replace(/[\u0000-\u001f\u007f\s]+/g, '');
  return SCRIPTABLE_PROTOCOL_RE.test(compact);
}

function isSafeProtocol(url: string, protocols: Set<string>): boolean {
  if (!SCHEME_RE.test(url)) {
    return true;
  }

  try {
    return protocols.has(new URL(url).protocol);
  } catch {
    return false;
  }
}

function stripQuotes(value: string): string {
  if (
    (value.startsWith('"') && value.endsWith('"'))
    || (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1);
  }
  return value;
}

function escapeHtmlAttribute(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}
