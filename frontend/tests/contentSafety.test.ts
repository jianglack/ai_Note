import assert from 'node:assert/strict';
import test from 'node:test';

import {
  isSafeAnchorUrl,
  isSafeImageUrl,
  markdownUrlTransform,
  sanitizeFormulaInput,
  sanitizeHtmlFallback,
  shouldShowErrorStack,
} from '../src/security/contentSafety.ts';

test('anchor URLs reject scriptable and local-file protocols', () => {
  assert.equal(isSafeAnchorUrl('javascript:alert(1)'), false);
  assert.equal(isSafeAnchorUrl('JaVaScRiPt:alert(1)'), false);
  assert.equal(isSafeAnchorUrl('data:text/html,<script>alert(1)</script>'), false);
  assert.equal(isSafeAnchorUrl('file:///etc/passwd'), false);
  assert.equal(isSafeAnchorUrl('https://example.com/path?q=1'), true);
  assert.equal(isSafeAnchorUrl('/notes/123'), true);
  assert.equal(isSafeAnchorUrl('#spirit-note-1'), true);
  assert.equal(isSafeAnchorUrl('mailto:user@example.com'), true);
});

test('image URLs allow only http, relative, and safe raster data URLs', () => {
  assert.equal(isSafeImageUrl('javascript:alert(1)'), false);
  assert.equal(isSafeImageUrl('data:text/html,<svg onload=alert(1)>'), false);
  assert.equal(isSafeImageUrl('data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+'), false);
  assert.equal(isSafeImageUrl('data:image/png;base64,iVBORw0KGgo='), true);
  assert.equal(isSafeImageUrl('https://cdn.example.com/a.webp'), true);
  assert.equal(isSafeImageUrl('/api/media/1'), true);
});

test('markdown URL transform strips unsafe links instead of passing them through', () => {
  assert.equal(markdownUrlTransform('javascript:alert(1)', 'href'), '');
  assert.equal(markdownUrlTransform('data:text/html,hi', 'href'), '');
  assert.equal(markdownUrlTransform('https://example.com', 'href'), 'https://example.com');
  assert.equal(markdownUrlTransform('data:image/png;base64,abc', 'src'), 'data:image/png;base64,abc');
  assert.equal(markdownUrlTransform('data:image/svg+xml;base64,abc', 'src'), '');
});

test('formula input rejects html-looking payloads before math insertion', () => {
  assert.equal(sanitizeFormulaInput('<img src=x onerror=alert(1)>'), null);
  assert.equal(sanitizeFormulaInput('x < y'), null);
  assert.equal(sanitizeFormulaInput('E = mc^2'), 'E = mc^2');
});

test('fallback HTML sanitizer strips script tags, handlers, and unsafe URLs', () => {
  const html = '<p onclick="alert(1)">ok<script>alert(1)</script><a href="javascript:alert(1)">bad</a><img src="data:image/svg+xml;base64,abc" onerror="alert(1)"></p>';
  const sanitized = sanitizeHtmlFallback(html);

  assert.equal(sanitized.includes('<script'), false);
  assert.equal(sanitized.includes('onclick'), false);
  assert.equal(sanitized.includes('onerror'), false);
  assert.equal(sanitized.includes('javascript:'), false);
  assert.equal(sanitized.includes('data:image/svg+xml'), false);
});

test('error stacks are hidden in production but available in non-production', () => {
  assert.equal(shouldShowErrorStack('production'), false);
  assert.equal(shouldShowErrorStack('development'), true);
  assert.equal(shouldShowErrorStack('test'), true);
});
