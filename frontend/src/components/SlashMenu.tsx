import { useState, useEffect, useCallback, useRef, forwardRef, useImperativeHandle } from 'react';
import './SlashMenu.css';

export interface SlashMenuItem {
  group: string;
  icon: string;
  name: string;
  desc: string;
  shortcut?: string;
  accent?: boolean;
  hot?: boolean;
  command: () => void;
}

interface SlashMenuProps {
  items: SlashMenuItem[];
  command: (item: SlashMenuItem) => void;
}

export interface SlashMenuRef {
  onKeyDown: (props: { event: KeyboardEvent }) => boolean;
}

const SlashMenu = forwardRef<SlashMenuRef, SlashMenuProps>(({ items, command }, ref) => {
  const [selectedIndex, setSelectedIndex] = useState(0);
  const menuRef = useRef<HTMLDivElement>(null);

  // Reset selection when items change
  useEffect(() => { setSelectedIndex(0); }, [items]);

  // Scroll selected item into view
  useEffect(() => {
    const el = menuRef.current?.querySelector('.slash-item.selected');
    el?.scrollIntoView({ block: 'nearest' });
  }, [selectedIndex]);

  const selectItem = useCallback((index: number) => {
    const item = items[index];
    if (item) command(item);
  }, [items, command]);

  useImperativeHandle(ref, () => ({
    onKeyDown: ({ event }: { event: KeyboardEvent }) => {
      if (event.key === 'ArrowUp') {
        setSelectedIndex((i) => (i + items.length - 1) % items.length);
        return true;
      }
      if (event.key === 'ArrowDown') {
        setSelectedIndex((i) => (i + 1) % items.length);
        return true;
      }
      if (event.key === 'Enter') {
        selectItem(selectedIndex);
        return true;
      }
      return false;
    },
  }));

  if (items.length === 0) return null;

  // Group items while preserving flat index
  const grouped: { group: string; items: { item: SlashMenuItem; flatIndex: number }[] }[] = [];
  let flatIndex = 0;
  items.forEach((item) => {
    let g = grouped.find((x) => x.group === item.group);
    if (!g) {
      g = { group: item.group, items: [] };
      grouped.push(g);
    }
    g.items.push({ item, flatIndex });
    flatIndex++;
  });

  return (
    <div className="slash-menu" ref={menuRef}>
      {/* Input hint */}
      <div className="slash-hint">
        <span className="slash-hint-tag">/ai</span>
        <span>过滤命令 · ↑↓ 选择 · ⏎ 确认</span>
      </div>

      {grouped.map(({ group, items: groupItems }) => (
        <div key={group}>
          <div className="slash-group-label">{group}</div>
          {groupItems.map(({ item, flatIndex: fi }) => (
            <div
              key={item.name}
              className={`slash-item ${fi === selectedIndex ? 'selected' : ''} ${item.accent ? 'accent' : ''}`}
              onClick={() => selectItem(fi)}
              onMouseEnter={() => setSelectedIndex(fi)}
            >
              <span className={`slash-icon ${item.accent ? 'accent' : ''}`}>
                {item.icon}
              </span>
              <div className="slash-item-text">
                <div className="slash-item-name">
                  {item.name}
                  {item.hot && <span className="slash-hot-badge">HOT</span>}
                </div>
                <div className="slash-item-desc">{item.desc}</div>
              </div>
              {item.shortcut && (
                <span className="slash-shortcut">{item.shortcut}</span>
              )}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
});

SlashMenu.displayName = 'SlashMenu';
export default SlashMenu;
