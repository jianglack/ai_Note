import { useState } from 'react';
import type { SuggestionButton } from './types';
import './ai-cards.css';

interface Props {
  kind: 'review' | 'expired' | 'merge';
  text: string;
  buttons: SuggestionButton[];
  onAction: (action: string) => void;
}

const kindConfig = {
  review: { icon: '📖', cls: '' },
  expired: { icon: '⏰', cls: 'warn' },
  merge: { icon: '🔗', cls: 'merge' },
};

export default function SuggestionMini({ kind, text, buttons, onAction }: Props) {
  const [done, setDone] = useState(false);
  const cfg = kindConfig[kind];

  const handleClick = (action: string) => {
    setDone(true);
    onAction(action);
  };

  return (
    <div className={`sc-mini ${cfg.cls} ${done ? 'collapsed' : ''}`}>
      <div className="sc-mini-line">
        <span className="sc-mini-ico">{cfg.icon}</span>
        <span>{text}</span>
      </div>
      {!done && (
        <div className="sc-mini-actions">
          {buttons.map((b, i) => (
            <button
              key={i}
              className={`sc-mini-btn ${b.variant === 'primary' ? 'primary' : ''} ${b.variant === 'subtle' ? 'subtle' : ''}`}
              onClick={() => handleClick(b.action)}
            >
              {b.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
