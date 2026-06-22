import { ReactRenderer } from '@tiptap/react';
import tippy, { type Instance as TippyInstance } from 'tippy.js';
import type { SuggestionOptions, SuggestionKeyDownProps } from '@tiptap/suggestion';
import SlashMenu, { type SlashMenuItem, type SlashMenuRef } from './SlashMenu';
import type { Editor } from '@tiptap/core';

function getSlashItems(editor: Editor, onAiAction?: (action: string) => void): SlashMenuItem[] {
  return [
    {
      group: '基础块',
      icon: 'H',
      name: '标题 1',
      desc: '大标题',
      shortcut: '#',
      command: () => editor.chain().focus().toggleHeading({ level: 1 }).run(),
    },
    {
      group: '基础块',
      icon: '¶',
      name: '正文',
      desc: '普通段落',
      command: () => editor.chain().focus().setParagraph().run(),
    },
    {
      group: '基础块',
      icon: '☐',
      name: '待办',
      desc: '可勾选清单',
      shortcut: '[]',
      command: () => editor.chain().focus().toggleTaskList().run(),
    },
    {
      group: '基础块',
      icon: '"',
      name: '引用',
      desc: '强调引文',
      command: () => editor.chain().focus().toggleBlockquote().run(),
    },
    {
      group: 'AI · 墨子',
      icon: '✦',
      name: 'AI 续写',
      desc: '让墨子根据上文续写',
      accent: true,
      hot: true,
      command: () => onAiAction?.('continue'),
    },
    {
      group: 'AI · 墨子',
      icon: '✧',
      name: 'AI 总结本篇',
      desc: '生成300字摘要',
      accent: true,
      command: () => onAiAction?.('summarize'),
    },
    {
      group: 'AI · 墨子',
      icon: '◈',
      name: 'AI 思维导图',
      desc: '提取要点可视化',
      accent: true,
      command: () => onAiAction?.('mindmap'),
    },
    {
      group: 'AI · 墨子',
      icon: '✿',
      name: 'AI 关联笔记',
      desc: '搜索相关旧笔记',
      accent: true,
      command: () => onAiAction?.('related'),
    },
  ];
}

export function createSlashSuggestion(
  onAiAction?: (action: string) => void,
): Partial<SuggestionOptions> {
  return {
    items: ({ query, editor }: { query: string; editor: Editor }) => {
      const all = getSlashItems(editor, onAiAction);
      if (!query) return all;
      const q = query.toLowerCase();
      return all.filter(
        (item) =>
          item.name.toLowerCase().includes(q) ||
          item.desc.toLowerCase().includes(q) ||
          item.group.toLowerCase().includes(q),
      );
    },

    render: () => {
      let component: ReactRenderer<SlashMenuRef> | null = null;
      let popup: TippyInstance[] | null = null;

      return {
        onStart: (props: any) => {
          component = new ReactRenderer(SlashMenu, {
            props: {
              ...props,
              command: (item: SlashMenuItem) => {
                props.command(item);
              },
            },
            editor: props.editor,
          });

          if (!props.clientRect) return;

          popup = tippy('body', {
            getReferenceClientRect: props.clientRect,
            appendTo: () => document.body,
            content: component.element,
            showOnCreate: true,
            interactive: true,
            trigger: 'manual',
            placement: 'bottom-start',
            offset: [0, 4],
          });
        },

        onUpdate: (props: any) => {
          component?.updateProps({
            ...props,
            command: (item: SlashMenuItem) => {
              props.command(item);
            },
          });

          if (popup?.[0] && props.clientRect) {
            popup[0].setProps({
              getReferenceClientRect: props.clientRect,
            });
          }
        },

        onKeyDown: (props: SuggestionKeyDownProps) => {
          if (props.event.key === 'Escape') {
            popup?.[0]?.hide();
            return true;
          }
          return component?.ref?.onKeyDown(props) ?? false;
        },

        onExit: () => {
          popup?.[0]?.destroy();
          component?.destroy();
        },
      };
    },
  };
}
