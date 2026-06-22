export type SseEvent = {
  event: string;
  data: string;
};

export async function parseSseStream(
  response: Response,
  onEvent: (event: SseEvent) => void,
  options: { defaultEventType?: string } = {}
): Promise<void> {
  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error('No response body');
  }

  const decoder = new TextDecoder();
  const defaultEventType = options.defaultEventType ?? 'message';
  let currentEventType = defaultEventType;
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      if (line.startsWith('event:')) {
        currentEventType = line.substring(6).trim() || defaultEventType;
        continue;
      }

      if (!line.startsWith('data:')) continue;

      onEvent({
        event: currentEventType,
        data: line.substring(5).replace(/^ /, ''),
      });
      currentEventType = defaultEventType;
    }
  }
}
