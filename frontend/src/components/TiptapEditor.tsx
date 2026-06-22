import { useEditor, EditorContent, BubbleMenu } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import Link from '@tiptap/extension-link';
import Image from '@tiptap/extension-image';
import Table from '@tiptap/extension-table';
import TableRow from '@tiptap/extension-table-row';
import TableCell from '@tiptap/extension-table-cell';
import TableHeader from '@tiptap/extension-table-header';
import TaskList from '@tiptap/extension-task-list';
import TaskItem from '@tiptap/extension-task-item';
import Underline from '@tiptap/extension-underline';
import Highlight from '@tiptap/extension-highlight';
import Superscript from '@tiptap/extension-superscript';
import Subscript from '@tiptap/extension-subscript';
import CodeBlockLowlight from '@tiptap/extension-code-block-lowlight';
import Mathematics from '@tiptap/extension-mathematics';
import TextStyle from '@tiptap/extension-text-style';
import Color from '@tiptap/extension-color';
import WikiLink from './WikiLink';
import { Annotation as AnnotationMark } from './Annotation';
import SlashCommand from './SlashCommand';
import { createSlashSuggestion } from './slashSuggestion';
import { createLowlight, common } from 'lowlight';
import { useEffect, useRef, useState, useMemo } from 'react';
import { marked } from 'marked';
import TurndownService from 'turndown';
import { getAnnotations, createAnnotation, updateAnnotation, deleteAnnotation, uploadImage as uploadImageApi, type Annotation } from '../api';
import { isSafeAnchorUrl, sanitizeAnchorUrl, sanitizeFormulaInput, sanitizeImageUrl } from '../security/contentSafety';
import { API_BASE_URL } from '../services/apiBase';
import { askConfirm, askPrompt, showAlert } from '../services/dialogService';
import 'katex/dist/katex.min.css';
import './TiptapEditor.css';

interface TiptapEditorProps {
  content: string;
  onChange: (content: string) => void;
  onAiAction?: (action: 'summarize' | 'improve' | 'expand' | 'simplify', selectedText: string) => void;
  onWikiLinkClick?: (title: string) => void;
  allNotes?: { id: string; title: string }[];
  noteId?: string; // 添加 noteId 用于加载注释
  onAddTags?: (tags: string[]) => void; // 添加标签回调，用于同步到笔记
  onSlashAiAction?: (action: string) => void; // 斜杠菜单 AI 命令回调
  onEditorReady?: (editor: ReturnType<typeof useEditor>) => void; // 暴露 editor 实例
  hideToolbar?: boolean; // 隐藏内置工具栏（外部渲染时用）
}

// 初始化 Markdown 转换器
const turndownService = new TurndownService({
  headingStyle: 'atx',
  codeBlockStyle: 'fenced',
});

// 让 turndown 把 <span data-wiki-link data-title="X"> 转成 [[X]]
turndownService.addRule('wikiLink', {
  filter: (node: HTMLElement) => {
    return node.nodeName === 'SPAN' && node.hasAttribute('data-wiki-link');
  },
  replacement: (_content: string, node: HTMLElement) => {
    const title = node.getAttribute('data-title') || '';
    return `[[${title}]]`;
  },
});

// 让 marked 把 [[X]] 转成 <span data-wiki-link data-title="X">
const wikiLinkExtension = {
  name: 'wikiLink',
  level: 'inline' as const,
  start: (src: string) => src.indexOf('[['),
  tokenizer(src: string) {
    const match = src.match(/^\[\[([^\]]+)\]\]/);
    if (match) {
      return { type: 'wikiLink', raw: match[0], title: match[1] };
    }
  },
  renderer(token: { title: string }) {
    return `<span data-wiki-link="" data-title="${token.title}" class="wiki-link">[[${token.title}]]</span>`;
  },
};
marked.use({ extensions: [wikiLinkExtension] });

// 初始化 lowlight 实例
const lowlight = createLowlight(common);

// 检测内容是否为 Markdown
const isMarkdown = (text: string): boolean => {
  if (!text || text.trim().startsWith('<')) return false;
  const markdownPatterns = [
    /^#{1,6}\s/m,           // 标题
    /^\*\*.*\*\*/m,         // 粗体
    /^\*.*\*/m,             // 斜体
    /^\[.*\]\(.*\)/m,       // 链接
    /^```/m,                // 代码块
    /^[-*+]\s/m,            // 列表
    /^\d+\.\s/m,            // 有序列表
  ];
  return markdownPatterns.some(pattern => pattern.test(text));
};

export default function TiptapEditor({ content, onChange, onAiAction, onWikiLinkClick, allNotes = [], noteId, onAddTags, onSlashAiAction, onEditorReady, hideToolbar }: TiptapEditorProps) {
  const [showAiMenu, setShowAiMenu] = useState(false);
  const [initialContent, setInitialContent] = useState('');
  const [showWikiSuggest, setShowWikiSuggest] = useState(false);
  const [wikiFilter, setWikiFilter] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const editorRef = useRef<ReturnType<typeof useEditor>>(null);

  // 图片上传处理
  const handleImageUpload = async (file: File) => {
    if (!noteId) return;
    try {
      const result = await uploadImageApi(noteId, file);
      const url = sanitizeImageUrl(API_BASE_URL + result.url);
      if (url) {
        editorRef.current?.chain().focus().setImage({ src: url, alt: file.name }).run();
      }
    } catch (err) {
      console.error('Image upload failed:', err);
    }
  };
  
  // 斜杠菜单建议配置（memoize to avoid re-creating on every render）
  const slashSuggestion = useMemo(() => createSlashSuggestion(onSlashAiAction), [onSlashAiAction]);

  // 注释相关状态
  const [annotations, setAnnotations] = useState<Annotation[]>([]);
  const [showAnnotationDialog, setShowAnnotationDialog] = useState(false);
  const [annotationComment, setAnnotationComment] = useState('');
  const [annotationTags, setAnnotationTags] = useState<string[]>([]);
  const [annotationTagInput, setAnnotationTagInput] = useState('');
  const [selectedText, setSelectedText] = useState('');
  const [selectedRange, setSelectedRange] = useState<{ from: number; to: number } | null>(null);
  const [showAnnotationSidebar, setShowAnnotationSidebar] = useState(true); // 注释侧边栏显示状态
  const [activeAnnotationId, setActiveAnnotationId] = useState<string | null>(null); // 当前选中的注释

  // 将 Markdown 转换为 HTML
  useEffect(() => {
    if (content && isMarkdown(content)) {
      const html = marked.parse(content) as string;
      setInitialContent(html);
    } else {
      setInitialContent(content);
    }
  }, []);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        codeBlock: false, // 禁用默认代码块，使用 CodeBlockLowlight 替代
      }),
      Placeholder.configure({
        placeholder: '开始记录...',
      }),
      CodeBlockLowlight.configure({
        lowlight,
        defaultLanguage: 'plaintext',
        HTMLAttributes: {
          class: 'code-block-lowlight',
        },
      }),
      Mathematics.configure({
        katexOptions: {
          throwOnError: false,
          strict: false,
          trust: false,
        },
      }),
      TextStyle,
      Color,
      WikiLink,
      AnnotationMark, // 添加注释扩展
      Link.configure({
        openOnClick: true,
        isAllowedUri: (url) => isSafeAnchorUrl(url),
        shouldAutoLink: (url) => isSafeAnchorUrl(url),
        HTMLAttributes: {
          class: 'editor-link',
          target: '_blank',
          rel: 'noopener noreferrer',
        },
      }),
      Image.configure({
        allowBase64: true,
        HTMLAttributes: {
          class: 'editor-image',
        },
      }),
      Table.configure({
        resizable: true,
      }),
      TableRow,
      TableCell,
      TableHeader,
      TaskList,
      TaskItem.configure({
        nested: true,
      }),
      Underline,
      Highlight.configure({
        multicolor: true,
      }),
      Superscript,
      Subscript,
      SlashCommand.configure({
        suggestion: slashSuggestion,
      }),
    ],
    content: initialContent,
    onUpdate: ({ editor }) => {
      // 将 HTML 转换回 Markdown
      const html = editor.getHTML();
      const markdown = turndownService.turndown(html);
      onChange(markdown);
    },
    editorProps: {
      attributes: {
        class: 'tiptap-editor',
      },
      handlePaste: (_view, event) => {
        const items = event.clipboardData?.items;
        if (items) {
          for (const item of Array.from(items)) {
            if (item.type.startsWith('image/')) {
              event.preventDefault();
              const file = item.getAsFile();
              if (file && noteId) {
                handleImageUpload(file);
              }
              return true;
            }
          }
        }
        return false;
      },
      handleDrop: (_view, event) => {
        const files = event.dataTransfer?.files;
        if (files && files.length > 0) {
          for (const file of Array.from(files)) {
            if (file.type.startsWith('image/')) {
              event.preventDefault();
              if (noteId) handleImageUpload(file);
              return true;
            }
          }
        }
        return false;
      },
    },
  });

  // 保存 editor 引用供图片上传使用
  editorRef.current = editor;

  // 暴露 editor 实例给父组件
  useEffect(() => {
    if (editor && onEditorReady) onEditorReady(editor);
  }, [editor, onEditorReady]);

  // 同步外部内容变化
  useEffect(() => {
    if (!editor) return;

    const currentMarkdown = turndownService.turndown(editor.getHTML());

    // 如果外部内容与编辑器内容不同，更新编辑器
    if (content !== currentMarkdown) {
      if (content && isMarkdown(content)) {
        const html = marked.parse(content) as string;
        editor.commands.setContent(html);
      } else {
        editor.commands.setContent(content || '');
      }
    }
  }, [content, editor]);
  
  // 加载注释
  useEffect(() => {
    if (!noteId) return;
    
    const loadAnnotations = async () => {
      try {
        const data = await getAnnotations(noteId);
        setAnnotations(data);
      } catch (err) {
        console.error('加载注释失败:', err);
      }
    };
    
    loadAnnotations();
  }, [noteId]);

  if (!editor) {
    return null;
  }

  const handleAiAction = (action: 'summarize' | 'improve' | 'expand' | 'simplify') => {
    const { from, to } = editor.state.selection;
    const selectedText = editor.state.doc.textBetween(from, to, ' ');

    if (selectedText && onAiAction) {
      onAiAction(action, selectedText);
    }
    setShowAiMenu(false);
  };
  
  // 注释处理函数
  const handleAddAnnotation = async () => {
    if (!editor || !noteId) return;
    
    const { from, to } = editor.state.selection;
    const text = editor.state.doc.textBetween(from, to, ' ');
    
    if (!text) {
      await showAlert({
        title: '无法创建注释',
        message: '请先选中要注释的文字',
      });
      return;
    }
    
    setSelectedText(text);
    setSelectedRange({ from, to });
    setAnnotationComment('');
    setAnnotationTags([]);
    setShowAnnotationDialog(true);
  };
  
  const handleSaveAnnotation = async () => {
    if (!noteId || !selectedRange) return;

    try {
      const annotation = await createAnnotation({
        noteId,
        textContent: selectedText,
        comment: annotationComment,
        startOffset: selectedRange.from,
        endOffset: selectedRange.to,
        tags: annotationTags,
      });

      // 在编辑器中标记这段文字
      editor?.chain()
        .focus()
        .setTextSelection(selectedRange)
        .setAnnotation(annotation.id)
        .run();

      setAnnotations([...annotations, annotation]);

      // 同步标签到笔记
      if (annotationTags.length > 0 && onAddTags) {
        onAddTags(annotationTags);
      }

      setShowAnnotationDialog(false);
      setAnnotationComment('');
      setAnnotationTags([]);
    } catch (err) {
      console.error('创建注释失败:', err);
      await showAlert({
        title: '创建注释失败',
        message: '请稍后重试',
      });
    }
  };
  
  const handleAddAnnotationTag = () => {
    if (!annotationTagInput.trim()) return;
    if (annotationTags.includes(annotationTagInput.trim())) return;
    
    setAnnotationTags([...annotationTags, annotationTagInput.trim()]);
    setAnnotationTagInput('');
  };
  
  const handleRemoveAnnotationTag = (tag: string) => {
    setAnnotationTags(annotationTags.filter(t => t !== tag));
  };

  // 点击注释定位到对应位置
  const handleAnnotationClick = (annotation: Annotation) => {
    if (!editor) return;
    setActiveAnnotationId(annotation.id);
    // 选中对应的文本范围
    editor.chain()
      .focus()
      .setTextSelection({ from: annotation.startOffset, to: annotation.endOffset })
      .run();
    // 滚动到可视区域
    const { view } = editor;
    const coords = view.coordsAtPos(annotation.startOffset);
    const editorElement = view.dom.closest('.tiptap-editor-wrapper');
    if (editorElement && coords) {
      editorElement.scrollTo({
        top: coords.top - editorElement.getBoundingClientRect().top + editorElement.scrollTop - 100,
        behavior: 'smooth'
      });
    }
  };

  // 删除注释
  const handleDeleteAnnotation = async (annotationId: string) => {
    const confirmed = await askConfirm({
      title: '删除注释',
      message: '确定要删除这条注释吗？',
      confirmLabel: '删除',
      danger: true,
    });
    if (!confirmed) return;
    try {
      await deleteAnnotation(annotationId);
      setAnnotations(annotations.filter(a => a.id !== annotationId));
      // 移除编辑器中的标记
      if (editor) {
        editor.chain().focus().unsetAnnotation().run();
      }
    } catch (err) {
      console.error('删除注释失败:', err);
      await showAlert({
        title: '删除注释失败',
        message: '请稍后重试',
      });
    }
  };

  const addLink = async () => {
    const url = await askPrompt({
      title: '插入链接',
      message: '输入链接地址:',
      placeholder: 'https://example.com',
      confirmLabel: '插入',
    });
    const href = sanitizeAnchorUrl(url);
    if (href) {
      editor.chain().focus().setLink({ href }).run();
    }
  };

  const addImage = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (noteId) {
      // 上传到服务器
      handleImageUpload(file);
    } else {
      // 没有 noteId 时回退为 base64 内联
      const reader = new FileReader();
      reader.onload = () => {
        const src = sanitizeImageUrl(reader.result as string);
        if (src) {
          editor?.chain().focus().setImage({ src, alt: file.name }).run();
        }
      };
      reader.readAsDataURL(file);
    }
    // 清空 value，允许重复选同一文件
    e.target.value = '';
  };

  const insertTable = () => {
    editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run();
  };

  const insertMath = async () => {
    const formula = await askPrompt({
      title: '插入公式',
      message: '输入数学公式 (LaTeX 格式):',
      confirmLabel: '插入',
    });
    const safeFormula = sanitizeFormulaInput(formula);
    if (safeFormula) {
      editor.chain().focus().insertContent({
        type: 'mathematics',
        attrs: {
          formula: safeFormula,
        },
      }).run();
    }
  };

  const filteredNotes = allNotes.filter(n =>
    n.title.toLowerCase().includes(wikiFilter.toLowerCase())
  ).slice(0, 8);

  return (
    <div className="tiptap-with-sidebar">
      <div
        className={`tiptap-container ${showAnnotationSidebar && annotations.length > 0 ? 'with-sidebar' : ''}`}
        onClick={(e) => {
          const target = e.target as HTMLElement;
          if (target.hasAttribute('data-wiki-link')) {
            const title = target.getAttribute('data-title');
            if (title) onWikiLinkClick?.(title);
          }
        }}
      >
      {/* 隐藏的文件选择器 */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />
      {/* Minimal toolbar — grouped with dividers */}
      {!hideToolbar && <div className="paper-toolbar" role="toolbar" aria-label="Editor formatting toolbar">
        {/* Text format */}
        <button className={`tb-btn${editor.isActive('bold') ? ' is-active' : ''}`}
          aria-label="Toggle bold"
          onClick={() => editor.chain().focus().toggleBold().run()} title="加粗 ⌘B">
          <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, fontWeight: 700 }}>B</span>
        </button>
        <button className={`tb-btn${editor.isActive('italic') ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleItalic().run()} title="斜体 ⌘I">
          <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, fontStyle: 'italic' }}>I</span>
        </button>
        <button className={`tb-btn${editor.isActive('strike') ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleStrike().run()} title="删除线">
          <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, textDecoration: 'line-through' }}>S</span>
        </button>
        <button className={`tb-btn${editor.isActive('underline') ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleUnderline().run()} title="下划线 ⌘U">
          <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, textDecoration: 'underline', textUnderlineOffset: 2 }}>U</span>
        </button>

        <span className="tb-divider" />

        {/* Color */}
        <button className={`tb-btn${editor.isActive('highlight') ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleHighlight().run()} title="高亮">
          <span style={{ position: 'relative', display: 'inline-flex' }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>
            </svg>
            <span style={{ position: 'absolute', left: 0, right: 0, bottom: -2, height: 2, background: '#f0d878', borderRadius: 1 }} />
          </span>
        </button>
        <button className="tb-btn" title="文字颜色" style={{ position: 'relative' }}>
          <input
            type="color"
            style={{ position: 'absolute', inset: 0, opacity: 0, cursor: 'pointer', width: '100%', height: '100%' }}
            value={editor.getAttributes('textStyle').color || '#000000'}
            onChange={(e) => editor.chain().focus().setColor(e.target.value).run()}
          />
          <span style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center' }}>
            <span style={{ fontSize: 13, fontWeight: 700, lineHeight: 1 }}>A</span>
            <span style={{ width: 12, height: 2, background: 'var(--color-accent)', borderRadius: 1, marginTop: 1 }} />
          </span>
        </button>

        <span className="tb-divider" />

        {/* Insert */}
        <button className={`tb-btn${editor.isActive('link') ? ' is-active' : ''}`}
          aria-label="Insert link"
          onClick={addLink} title="链接">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <path d="M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1"/><path d="M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1"/>
          </svg>
        </button>
        <button className="tb-btn" aria-label="Insert image" onClick={addImage} title="图片">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 11h18"/>
          </svg>
        </button>

        <span className="tb-divider" />

        {/* Headings */}
        <button className={`tb-btn${editor.isActive('heading', { level: 1 }) ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()} title="一级标题">
          <span style={{ fontFamily: 'var(--font-serif)', fontSize: 12, fontWeight: 600 }}>H1</span>
        </button>
        <button className={`tb-btn${editor.isActive('heading', { level: 2 }) ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} title="二级标题">
          <span style={{ fontFamily: 'var(--font-serif)', fontSize: 12, fontWeight: 600 }}>H2</span>
        </button>
        <button className={`tb-btn${editor.isActive('heading', { level: 3 }) ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()} title="三级标题">
          <span style={{ fontFamily: 'var(--font-serif)', fontSize: 12, fontWeight: 600 }}>H3</span>
        </button>

        <span className="tb-divider" />

        {/* Lists */}
        <button className={`tb-btn${editor.isActive('bulletList') ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleBulletList().run()} title="无序列表">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
            <circle cx="5" cy="6" r="1.4" fill="currentColor"/><circle cx="5" cy="12" r="1.4" fill="currentColor"/><circle cx="5" cy="18" r="1.4" fill="currentColor"/>
            <path d="M10 6h11M10 12h11M10 18h11"/>
          </svg>
        </button>
        <button className={`tb-btn${editor.isActive('orderedList') ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleOrderedList().run()} title="有序列表">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
            <path d="M10 6h11M10 12h11M10 18h11"/><path d="M3 5h2v3M3 8h2M3 11h2.5M3 14.5h2A1.5 1.5 0 1 1 4 17H3M3 20h2"/>
          </svg>
        </button>
        <button className={`tb-btn${editor.isActive('taskList') ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleTaskList().run()} title="待办列表">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="5" width="6" height="6" rx="1"/><path d="m4.5 8 1.2 1.2L7.5 7.4"/><rect x="3" y="14" width="6" height="6" rx="1"/><path d="M12 8h10M12 17h10"/>
          </svg>
        </button>

        <span className="tb-divider" />

        {/* Advanced */}
        <button className={`tb-btn${editor.isActive('codeBlock') ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleCodeBlock().run()} title="代码块">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <path d="m8 8-4 4 4 4M16 8l4 4-4 4M14 5l-4 14"/>
          </svg>
        </button>
        <button className={`tb-btn${editor.isActive('mathematics') ? ' is-active' : ''}`}
          onClick={insertMath} title="数学公式">
          <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, fontStyle: 'italic' }}>Σ</span>
        </button>
        <button className={`tb-btn${editor.isActive('blockquote') ? ' is-active' : ''}`}
          onClick={() => editor.chain().focus().toggleBlockquote().run()} title="引用">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
            <path d="M5 6h4l-1 6H5zM14 6h4l-1 6h-3z"/>
          </svg>
        </button>
        <button className="tb-btn"
          onClick={() => editor.chain().focus().setHorizontalRule().run()} title="分割线">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
            <path d="M3 12h18M3 6h12M3 18h12"/>
          </svg>
        </button>
        <button className="tb-btn"
          aria-label="Insert table"
          onClick={insertTable} title="表格">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M3 15h18M9 4v16M15 4v16"/>
          </svg>
        </button>
        <div className="wiki-link-wrapper" style={{ display: 'inline-flex' }}>
          <button className="tb-btn"
            onClick={() => { setShowWikiSuggest(v => !v); setWikiFilter(''); }} title="双向链接">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
              <path d="M5 12h14M5 12 9 8M5 12l4 4M19 12l-4-4M19 12l-4 4"/>
            </svg>
          </button>
          {showWikiSuggest && (
            <div className="wiki-suggest-popup">
              <input
                className="wiki-suggest-input"
                placeholder="搜索笔记..."
                value={wikiFilter}
                autoFocus
                onChange={e => setWikiFilter(e.target.value)}
                onKeyDown={e => { if (e.key === 'Escape') setShowWikiSuggest(false); }}
              />
              <div className="wiki-suggest-list">
                {filteredNotes.length === 0 ? (
                  <div className="wiki-suggest-empty">无匹配笔记</div>
                ) : (
                  filteredNotes.map(note => (
                    <div
                      key={note.id}
                      className="wiki-suggest-item"
                      onMouseDown={e => {
                        e.preventDefault();
                        editor.chain().focus().insertWikiLink(note.title || '无标题').run();
                        setShowWikiSuggest(false);
                      }}
                    >
                      {note.title || '无标题'}
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>
      </div>}

      {/* 选中文本浮动工具栏 */}
      {editor && (
        <BubbleMenu
          editor={editor}
          tippyOptions={{ duration: 100 }}
          className="bubble-menu"
        >
          {/* 注释 */}
          <button className="bubble-fmt" aria-label="Add annotation" onClick={() => handleAddAnnotation()} title="添加注释">💬</button>
          <div className="bubble-divider" />
          {/* 格式按钮 */}
          <button className="bubble-fmt" onClick={() => editor.chain().focus().toggleBold().run()}
            title="加粗" style={{ fontWeight: 800 }}>B</button>
          <button className="bubble-fmt" onClick={() => editor.chain().focus().toggleItalic().run()}
            title="斜体" style={{ fontStyle: 'italic' }}>I</button>
          <button className="bubble-fmt" onClick={() => editor.chain().focus().toggleUnderline().run()}
            title="下划线" style={{ textDecoration: 'underline' }}>U</button>
          <button className="bubble-fmt" onClick={() => editor.chain().focus().toggleStrike().run()}
            title="删除线" style={{ textDecoration: 'line-through' }}>S</button>
          <div className="bubble-divider" />
          {/* 高亮 */}
          <button className="bubble-fmt bubble-highlight"
            onClick={() => editor.chain().focus().toggleHighlight({ color: '#f5d76e' }).run()}
            title="高亮">
            <span className="highlight-dot" />
          </button>
          <div className="bubble-divider" />
          {/* AI 操作组 */}
          <div className="bubble-ai-group">
            <button onClick={() => handleAiAction('improve')} title="AI 改写">✦ 改写</button>
            <button onClick={() => handleAiAction('expand')} title="AI 翻译">⇄ 翻译</button>
            <button onClick={() => handleAiAction('summarize')} title="AI 解释">? 解释</button>
            <button onClick={() => handleAiAction('simplify')} title="AI 续写">→ 续写</button>
          </div>
        </BubbleMenu>
      )}

      {/* 编辑器内容 */}
      <EditorContent editor={editor} />
      
      {/* 注释对话框 */}
      {showAnnotationDialog && (
        <div className="annotation-dialog-overlay" onClick={() => setShowAnnotationDialog(false)}>
          <div className="annotation-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="annotation-dialog-header">
              <h3>添加注释</h3>
              <button className="close-btn" aria-label="Close annotation dialog" onClick={() => setShowAnnotationDialog(false)}>×</button>
            </div>
            <div className="annotation-dialog-body">
              <div className="annotation-selected-text">
                <strong>选中的文字：</strong>
                <p>{selectedText}</p>
              </div>
              <textarea
                className="annotation-comment-input"
                placeholder="输入注释内容..."
                value={annotationComment}
                onChange={(e) => setAnnotationComment(e.target.value)}
                rows={4}
              />
              <div className="annotation-tags-section">
                <label>标签（会同时添加到笔记）：</label>
                <div className="annotation-tag-input-wrapper">
                  <input
                    className="annotation-tag-input"
                    placeholder="输入标签后按回车..."
                    value={annotationTagInput}
                    onChange={(e) => setAnnotationTagInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        handleAddAnnotationTag();
                      }
                    }}
                  />
                </div>
                {annotationTags.length > 0 && (
                  <div className="annotation-tag-list">
                    {annotationTags.map(tag => (
                      <span key={tag} className="annotation-tag">
                        {tag}
                        <button aria-label={`Remove annotation tag ${tag}`} onClick={() => handleRemoveAnnotationTag(tag)}>×</button>
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
            <div className="annotation-dialog-footer">
              <button className="annotation-cancel-btn" onClick={() => setShowAnnotationDialog(false)}>
                取消
              </button>
              <button className="annotation-save-btn" onClick={handleSaveAnnotation}>
                保存注释
              </button>
            </div>
          </div>
        </div>
      )}
      </div>

      {/* 注释侧边栏 */}
      {showAnnotationSidebar && annotations.length > 0 && (
        <div className="annotation-sidebar">
          <div className="annotation-sidebar-header">
            <h4>注释 ({annotations.length})</h4>
            <button
              className="annotation-sidebar-toggle"
              onClick={() => setShowAnnotationSidebar(false)}
              title="收起侧边栏"
            >
              ×
            </button>
          </div>
          <div className="annotation-sidebar-list">
            {annotations.map(annotation => (
              <div
                key={annotation.id}
                className={`annotation-sidebar-item ${activeAnnotationId === annotation.id ? 'active' : ''}`}
                onClick={() => handleAnnotationClick(annotation)}
              >
                <div className="annotation-item-text">
                  "{annotation.textContent.length > 30
                    ? annotation.textContent.slice(0, 30) + '...'
                    : annotation.textContent}"
                </div>
                {annotation.comment && (
                  <div className="annotation-item-comment">
                    {annotation.comment.length > 50
                      ? annotation.comment.slice(0, 50) + '...'
                      : annotation.comment}
                  </div>
                )}
                {annotation.tags && annotation.tags.length > 0 && (
                  <div className="annotation-item-tags">
                    {annotation.tags.map(tag => (
                      <span key={tag.id} className="annotation-item-tag">{tag.name}</span>
                    ))}
                  </div>
                )}
                <button
                  className="annotation-item-delete"
                  aria-label="Delete annotation"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDeleteAnnotation(annotation.id);
                  }}
                  title="删除注释"
                >
                  🗑️
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 收起时的展开按钮 */}
      {!showAnnotationSidebar && annotations.length > 0 && (
        <button
          className="annotation-sidebar-expand"
          aria-label="Show annotations"
          onClick={() => setShowAnnotationSidebar(true)}
          title="展开注释列表"
        >
          📝 {annotations.length}
        </button>
      )}
    </div>
  );
}
