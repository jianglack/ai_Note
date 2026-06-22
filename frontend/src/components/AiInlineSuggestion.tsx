import { SparklesIcon } from '@heroicons/react/24/outline';
import './AiInlineSuggestion.css';

interface AiInlineSuggestionProps {
  noteTitle: string;
  noteDate?: string;
  description: string;
  onLink: () => void;
  onDismiss: () => void;
}

export default function AiInlineSuggestion({
  noteTitle,
  noteDate,
  description,
  onLink,
  onDismiss,
}: AiInlineSuggestionProps) {
  return (
    <div className="ai-inline-suggestion">
      <SparklesIcon className="ai-inline-icon" />
      <div className="ai-inline-body">
        <div className="ai-inline-label">AI · 相关联想</div>
        <div className="ai-inline-text">
          {description}
          {noteDate && (
            <>
              <span className="ai-inline-link">{noteDate}的《{noteTitle}》</span>
            </>
          )}
          {!noteDate && (
            <span className="ai-inline-link">《{noteTitle}》</span>
          )}
        </div>
        <div className="ai-inline-actions">
          <button className="ai-inline-btn primary" onClick={onLink}>建立关联</button>
          <button className="ai-inline-btn" onClick={onDismiss}>忽略</button>
        </div>
      </div>
    </div>
  );
}
