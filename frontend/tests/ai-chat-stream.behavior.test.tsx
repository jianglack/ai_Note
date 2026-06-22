import { afterEach, describe, expect, it, vi } from 'vitest';

import { aiChatStream } from '../src/api';

const apiMock = vi.hoisted(() => ({
  defaults: { baseURL: 'http://backend.test' },
  post: vi.fn(),
  get: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('../src/services/api', () => ({
  default: apiMock,
}));

vi.mock('../src/services/apiBase', () => ({
  getAuthToken: () => 'jwt-token',
}));

describe('aiChatStream behavior', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('parses token, progress, and complete SSE events from fetch', async () => {
    const encoder = new TextEncoder();
    const chunks = [
      'event: token\ndata: Hello\n\n',
      'event: progress\ndata: {"step":"search","detail":"Looking"}\n\n',
      'event: complete\ndata: {"content":"Done","sources":{}}\n\n',
    ].map((chunk) => encoder.encode(chunk));

    const read = vi.fn()
      .mockResolvedValueOnce({ done: false, value: chunks[0] })
      .mockResolvedValueOnce({ done: false, value: chunks[1] })
      .mockResolvedValueOnce({ done: false, value: chunks[2] })
      .mockResolvedValueOnce({ done: true, value: undefined });

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      body: {
        getReader: () => ({ read }),
      },
    }));

    const onToken = vi.fn();
    const onComplete = vi.fn();
    const onError = vi.fn();
    const onProgress = vi.fn();

    await aiChatStream('hi', 'all', undefined, onToken, onComplete, onError, onProgress);

    expect(fetch).toHaveBeenCalledWith('http://backend.test/api/ai/chat/stream', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        Authorization: 'Bearer jwt-token',
        Accept: 'text/event-stream',
      }),
    }));
    expect(onToken).toHaveBeenCalledWith('Hello');
    expect(onProgress).toHaveBeenCalledWith('search', 'Looking');
    expect(onComplete).toHaveBeenCalledWith({ content: 'Done', sources: {} });
    expect(onError).not.toHaveBeenCalled();
  });
});
