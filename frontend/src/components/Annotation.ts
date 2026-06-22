import { Mark, mergeAttributes } from '@tiptap/core';

export interface AnnotationOptions {
  HTMLAttributes: Record<string, any>;
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    annotation: {
      setAnnotation: (annotationId: string) => ReturnType;
      unsetAnnotation: () => ReturnType;
    };
  }
}

export const Annotation = Mark.create<AnnotationOptions>({
  name: 'annotation',

  addOptions() {
    return {
      HTMLAttributes: {},
    };
  },

  addAttributes() {
    return {
      annotationId: {
        default: null,
        parseHTML: element => element.getAttribute('data-annotation-id'),
        renderHTML: attributes => {
          if (!attributes.annotationId) {
            return {};
          }
          return {
            'data-annotation-id': attributes.annotationId,
            'class': 'annotation-mark',
          };
        },
      },
    };
  },

  parseHTML() {
    return [
      {
        tag: 'span[data-annotation-id]',
      },
    ];
  },

  renderHTML({ HTMLAttributes }) {
    return ['span', mergeAttributes(this.options.HTMLAttributes, HTMLAttributes), 0];
  },

  addCommands() {
    return {
      setAnnotation: (annotationId: string) => ({ commands }) => {
        return commands.setMark(this.name, { annotationId });
      },
      unsetAnnotation: () => ({ commands }) => {
        return commands.unsetMark(this.name);
      },
    };
  },
});
