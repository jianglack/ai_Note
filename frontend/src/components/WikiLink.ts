import { Node, mergeAttributes } from '@tiptap/core';

export interface WikiLinkOptions {
  onLinkClick?: (title: string) => void;
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    wikiLink: {
      insertWikiLink: (title: string) => ReturnType;
    };
  }
}

const WikiLink = Node.create<WikiLinkOptions>({
  name: 'wikiLink',
  group: 'inline',
  inline: true,
  selectable: true,
  atom: true,

  addOptions() {
    return {
      onLinkClick: undefined,
    };
  },

  addAttributes() {
    return {
      title: {
        default: null,
      },
    };
  },

  parseHTML() {
    return [
      {
        tag: 'span[data-wiki-link]',
        getAttrs: el => ({
          title: (el as HTMLElement).getAttribute('data-title'),
        }),
      },
    ];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'span',
      mergeAttributes({
        'data-wiki-link': '',
        'data-title': HTMLAttributes.title,
        class: 'wiki-link',
      }),
      `[[${HTMLAttributes.title}]]`,
    ];
  },

  addCommands() {
    return {
      insertWikiLink: (title) => ({ commands }) => {
        return commands.insertContent({
          type: this.name,
          attrs: { title },
        });
      },
    };
  },
});

export default WikiLink;
