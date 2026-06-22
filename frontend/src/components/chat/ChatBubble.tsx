import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import MoziAvatar from './MoziAvatar';
import { markdownUrlTransform, sanitizeAnchorUrl } from '../../security/contentSafety';

interface NoteSource {
  id: string;
  title: string;
}

interface Props {
  role: 'spirit' | 'user';
  content: string;
  sources?: Record<number, NoteSource>;
  degraded?: boolean;
  degradationReason?: string;
  onNoteClick?: (noteId: string) => void;
}

function preprocessContent(content: string) {
  return content.replace(/\[笔记(\d+)\]/g, '[笔记$1](#spirit-note-$1)');
}

export default function ChatBubble({ role, content, sources, degraded, degradationReason, onNoteClick }: Props) {
  const isUser = role === 'user';

  const components = {
    a: ({ href, children }: { href?: string; children?: React.ReactNode }) => {
      const match = href?.match(/^#spirit-note-(\d+)$/);
      if (match && sources) {
        const source = sources[parseInt(match[1])];
        if (source && onNoteClick) {
          return (
            <span
              className="text-accent cursor-pointer hover:underline"
              onClick={() => onNoteClick(source.id)}
              title={`查看：${source.title}`}
            >
              {children}
            </span>
          );
        }
      }
      const safeHref = sanitizeAnchorUrl(href);
      if (!safeHref) {
        return <span className="text-accent">{children}</span>;
      }
      return <a href={safeHref} target="_blank" rel="noopener noreferrer" className="text-accent hover:underline">{children}</a>;
    },
  };

  return (
    <div className={`flex gap-2 ${isUser ? 'flex-row-reverse' : 'flex-row'} wf-fade-in`}>
      {!isUser && <MoziAvatar size="sm" />}

      <div
        className={`max-w-[80%] px-3 py-2 rounded-[var(--radius-md)] text-sm leading-relaxed ${
          isUser
            ? 'bg-chat-bubble-user text-text'
            : 'bg-chat-bubble-ai text-text'
        }`}
      >
        {isUser ? (
          <span>{content}</span>
        ) : (
          <div className="prose-sm prose-neutral [&_p]:my-1 [&_ul]:my-1 [&_ol]:my-1 [&_li]:my-0.5 [&_pre]:my-2 [&_code]:text-xs [&_code]:bg-paper-2 [&_code]:px-1 [&_code]:rounded">
            {degraded && (
              <span
                className="mb-1 inline-flex rounded border border-border bg-paper-1 px-1.5 py-0.5 text-[11px] leading-none text-text-secondary"
                title={degradationReason}
              >
                简化回复
              </span>
            )}
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              urlTransform={markdownUrlTransform}
              components={components as Record<string, unknown>}
            >
              {preprocessContent(content)}
            </ReactMarkdown>
          </div>
        )}
      </div>
    </div>
  );
}
