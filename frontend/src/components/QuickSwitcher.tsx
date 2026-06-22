import { useState, useEffect, useRef } from 'react';
import './QuickSwitcher.css';

interface Note {
  id: string;
  title: string;
  content: string;
  updatedAt: string;
}

interface QuickSwitcherProps {
  isOpen: boolean;
  onClose: () => void;
  notes: Note[];
  onSelectNote: (noteId: string) => void;
}

export default function QuickSwitcher({ isOpen, onClose, notes, onSelectNote }: QuickSwitcherProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  // 过滤笔记
  const filteredNotes = notes.filter(note => {
    const query = searchQuery.toLowerCase();
    return note.title.toLowerCase().includes(query) ||
           note.content.toLowerCase().includes(query);
  });

  // 重置状态
  useEffect(() => {
    if (isOpen) {
      setSearchQuery('');
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 0);
    }
  }, [isOpen]);

  // 键盘导航
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (!isOpen) return;

      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex(prev =>
          prev < filteredNotes.length - 1 ? prev + 1 : prev
        );
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex(prev => prev > 0 ? prev - 1 : 0);
      } else if (e.key === 'Enter') {
        e.preventDefault();
        if (filteredNotes[selectedIndex]) {
          onSelectNote(filteredNotes[selectedIndex].id);
          onClose();
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, selectedIndex, filteredNotes, onSelectNote, onClose]);

  // 自动滚动到选中项
  useEffect(() => {
    if (listRef.current) {
      const selectedElement = listRef.current.children[selectedIndex] as HTMLElement;
      if (selectedElement) {
        selectedElement.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
      }
    }
  }, [selectedIndex]);

  if (!isOpen) return null;

  const listboxId = 'quick-switcher-results';
  const activeOptionId = filteredNotes[selectedIndex]
    ? `quick-switcher-option-${filteredNotes[selectedIndex].id}`
    : undefined;

  return (
    <div className="quick-switcher-overlay" onClick={onClose}>
      <div
        className="quick-switcher"
        role="dialog"
        aria-modal="true"
        aria-labelledby="quick-switcher-title"
        onClick={e => e.stopPropagation()}
      >
        <h2 id="quick-switcher-title" className="quick-switcher-title">
          快速切换笔记
        </h2>
        <input
          ref={inputRef}
          type="text"
          className="quick-switcher-input"
          placeholder="搜索笔记... (↑↓ 导航, Enter 选择, Esc 关闭)"
          aria-controls={listboxId}
          aria-activedescendant={activeOptionId}
          aria-autocomplete="list"
          value={searchQuery}
          onChange={e => {
            setSearchQuery(e.target.value);
            setSelectedIndex(0);
          }}
        />
        <div
          id={listboxId}
          className="quick-switcher-results"
          role="listbox"
          ref={listRef}
        >
          {filteredNotes.length === 0 ? (
            <div className="quick-switcher-empty" role="status">
              {searchQuery ? '未找到匹配的笔记' : '没有笔记'}
            </div>
          ) : (
            filteredNotes.map((note, index) => (
              <div
                key={note.id}
                id={`quick-switcher-option-${note.id}`}
                className={`quick-switcher-item ${index === selectedIndex ? 'selected' : ''}`}
                role="option"
                aria-selected={index === selectedIndex}
                tabIndex={-1}
                onClick={() => {
                  onSelectNote(note.id);
                  onClose();
                }}
                onMouseEnter={() => setSelectedIndex(index)}
              >
                <div className="quick-switcher-item-title">{note.title || '无标题'}</div>
                <div className="quick-switcher-item-preview">
                  {note.content.substring(0, 100)}
                </div>
                <div className="quick-switcher-item-date">
                  {new Date(note.updatedAt).toLocaleString('zh-CN')}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
