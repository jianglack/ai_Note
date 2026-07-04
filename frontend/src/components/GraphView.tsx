import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import './GraphView.css';
import {
  getKnowledgeGraph,
  type KnowledgeGraphResponse,
  type Note,
  type Schedule,
} from '../api';
import {
  ToolControlRail,
  ToolDetailPanel,
  ToolWorkbenchShell,
} from './workbench';

interface GraphViewProps {
  notes: Note[];
  schedules: Schedule[];
  selectedNoteId?: string;
  onSelectNote: (note: Note) => void;
  onSelectSchedule: (schedule: Schedule) => void;
  onClose: () => void;
}

type GraphNodeType = 'note' | 'schedule' | 'folder' | 'tag' | string;

interface GraphNode {
  id: string;
  label: string;
  isSelected: boolean;
  type: GraphNodeType;
  refId?: string;
  noteRef?: Note;
  scheduleRef?: Schedule;
}

interface GraphLink {
  source: string;
  target: string;
  type?: string;
  label?: string;
}

function extractWikiLinks(content: string): string[] {
  const matches = content.match(/\[\[([^\]]+)\]\]/g) || [];
  return matches.map((m) => m.slice(2, -2));
}

function buildLocalGraph(notes: Note[], schedules: Schedule[], selectedNoteId?: string) {
  const titleToId = new Map(notes.map((n) => [n.title, n.id]));

  const nodes: GraphNode[] = notes.map((n) => ({
    id: n.id,
    label: n.title || 'Untitled',
    isSelected: n.id === selectedNoteId,
    type: 'note',
    refId: n.id,
    noteRef: n,
  }));

  schedules.forEach((s) => {
    nodes.push({
      id: `schedule-${s.id}`,
      label: s.title,
      isSelected: false,
      type: 'schedule',
      refId: s.id,
      scheduleRef: s,
    });
  });

  const links: GraphLink[] = [];

  notes.forEach((note) => {
    extractWikiLinks(note.content).forEach((title) => {
      const targetId = titleToId.get(title);
      if (targetId && targetId !== note.id) {
        links.push({ source: note.id, target: targetId, type: 'LINKS_TO', label: 'links to' });
      }
    });
  });

  schedules.forEach((schedule) => {
    schedule.notes.forEach((noteRef) => {
      links.push({
        source: `schedule-${schedule.id}`,
        target: noteRef.id,
        type: 'MENTIONS',
        label: 'schedule note',
      });
    });
  });

  return { nodes, links };
}

export default function GraphView({
  notes,
  schedules,
  selectedNoteId,
  onSelectNote,
  onSelectSchedule,
  onClose,
}: GraphViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const fgRef = useRef<any>(null);
  const [remoteGraph, setRemoteGraph] = useState<KnowledgeGraphResponse | null>(null);
  const [graphError, setGraphError] = useState<string | null>(null);
  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);

  useEffect(() => {
    let cancelled = false;

    getKnowledgeGraph()
      .then((graph) => {
        if (!cancelled) {
          setRemoteGraph(graph);
          setGraphError(null);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setRemoteGraph(null);
          setGraphError(err instanceof Error ? err.message : 'Graph API unavailable');
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const { nodes, links } = useMemo(() => {
    const noteById = new Map(notes.map((note) => [note.id, note]));
    const scheduleById = new Map(schedules.map((schedule) => [schedule.id, schedule]));

    if (remoteGraph?.nodes?.length) {
      return {
        nodes: remoteGraph.nodes.map((node) => {
          const refId = node.refId || node.id;
          const noteRef = node.type === 'note' ? noteById.get(refId) : undefined;
          const scheduleRef = node.type === 'schedule' ? scheduleById.get(refId) : undefined;

          return {
            id: node.id,
            label: node.label || node.type,
            isSelected: refId === selectedNoteId || node.id === selectedNoteId,
            type: node.type,
            refId,
            noteRef,
            scheduleRef,
          };
        }),
        links: remoteGraph.links.map((link) => ({
          source: link.source,
          target: link.target,
          type: link.type,
          label: link.label,
        })),
      };
    }

    return buildLocalGraph(notes, schedules, selectedNoteId);
  }, [notes, remoteGraph, schedules, selectedNoteId]);

  useEffect(() => {
    if (fgRef.current && selectedNoteId) {
      setTimeout(() => {
        const node = nodes.find((n) => n.refId === selectedNoteId || n.id === selectedNoteId);
        if (node) {
          fgRef.current.centerAt((node as any).x, (node as any).y, 600);
          fgRef.current.zoom(2.5, 600);
        }
      }, 300);
    }
  }, [selectedNoteId, nodes]);

  useEffect(() => {
    if (!selectedNoteId) {
      return;
    }

    const node = nodes.find((n) => n.refId === selectedNoteId || n.id === selectedNoteId);
    if (node) {
      setSelectedNode(node);
    }
  }, [nodes, selectedNoteId]);

  useEffect(() => {
    if (!selectedNode) {
      return;
    }

    const currentNode = nodes.find((node) => node.id === selectedNode.id);
    if (!currentNode) {
      setSelectedNode(null);
      return;
    }

    if (currentNode !== selectedNode) {
      setSelectedNode(currentNode);
    }
  }, [nodes, selectedNode]);

  const handleNodeClick = useCallback((node: GraphNode) => {
    setSelectedNode(node);
  }, []);

  const openSelectedNode = useCallback(() => {
    if (!selectedNode) {
      return;
    }

    if (selectedNode.type === 'schedule' && selectedNode.scheduleRef) {
      onSelectSchedule(selectedNode.scheduleRef);
      onClose();
      return;
    }

    if (selectedNode.noteRef) {
      onSelectNote(selectedNode.noteRef);
      onClose();
    }
  }, [onClose, onSelectNote, onSelectSchedule, selectedNode]);

  const paintNode = useCallback((node: any, ctx: CanvasRenderingContext2D, globalScale: number) => {
    const isSelected = node.isSelected || node.id === selectedNode?.id || node.refId === selectedNode?.refId;
    const hasLinks = links.some((l) => (
      l.source === node.id ||
      l.target === node.id ||
      (l.source as any).id === node.id ||
      (l.target as any).id === node.id
    ));

    const radius = isSelected ? 8 : hasLinks ? 6 : 4;
    const type = node.type as GraphNodeType;

    if (type === 'schedule') {
      const size = hasLinks ? 8 : 6;
      ctx.beginPath();
      ctx.moveTo(node.x, node.y - size);
      ctx.lineTo(node.x + size, node.y);
      ctx.lineTo(node.x, node.y + size);
      ctx.lineTo(node.x - size, node.y);
      ctx.closePath();
      ctx.fillStyle = '#d99a3e';
      ctx.fill();
      ctx.strokeStyle = '#9f6330';
      ctx.lineWidth = 1.5;
      ctx.stroke();
    } else {
      ctx.beginPath();
      ctx.arc(node.x, node.y, type === 'note' ? radius : radius - 1, 0, 2 * Math.PI);
      ctx.fillStyle = isSelected
        ? '#a35d38'
        : type === 'tag'
          ? '#4f8f77'
          : type === 'folder'
            ? '#786d93'
            : hasLinks
              ? '#5f7d89'
              : '#b4a895';
      ctx.fill();

      if (isSelected) {
        ctx.strokeStyle = '#7f4227';
        ctx.lineWidth = 2;
        ctx.stroke();
      }
    }

    const fontSize = Math.max(10 / globalScale, 3);
    ctx.font = `${isSelected ? 'bold ' : ''}${fontSize}px sans-serif`;
    ctx.fillStyle = isSelected ? '#68331f' : '#4a4033';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    ctx.fillText(node.label.length > 12 ? `${node.label.slice(0, 12)}...` : node.label, node.x, node.y + radius + 2);
  }, [links, selectedNode]);

  const width = containerRef.current?.clientWidth || 800;
  const height = containerRef.current?.clientHeight || 600;
  const graphSource = remoteGraph
    ? remoteGraph.neo4jEnabled
      ? 'Neo4j graph'
      : 'PostgreSQL fallback'
    : graphError
      ? 'Local fallback'
      : 'Loading graph';

  return (
    <ToolWorkbenchShell
      title="关系图谱"
      subtitle="查看笔记、日程和引用之间的连接"
      chips={(
        <span className="graph-stats">
          {nodes.length} nodes · {links.length} links · {graphSource}
        </span>
      )}
      onBack={onClose}
      backLabel="关闭关系图谱"
      closeOnEscape
      leftRail={(
        <ToolControlRail
          sections={[
            {
              key: 'legend',
              title: '图例',
              content: (
                <div className="graph-legend-list">
                  <span><span className="legend-dot selected" />当前选中</span>
                  <span><span className="legend-dot linked" />有关联笔记</span>
                  <span><span className="legend-dot isolated" />孤立笔记</span>
                  <span><span className="legend-diamond" />日程</span>
                </div>
              ),
            },
            {
              key: 'controls',
              title: '操作',
              content: (
                <div className="graph-help">
                  <p>点击节点查看详情。</p>
                  <p>滚轮缩放，拖动画布平移。</p>
                </div>
              ),
            },
            {
              key: 'source',
              title: '数据源',
              content: (
                <div className="graph-source">
                  <strong>{graphSource}</strong>
                  {graphError && <span>{graphError}</span>}
                </div>
              ),
            },
          ]}
        />
      )}
      detailPanel={(
        <ToolDetailPanel
          title="节点详情"
          subtitle={selectedNode?.type === 'schedule' ? '日程' : selectedNode?.type === 'note' ? '笔记' : selectedNode?.type}
          empty={!selectedNode}
          emptyMessage="选择一个节点查看关系和来源。"
          actions={selectedNode?.noteRef || selectedNode?.scheduleRef ? (
            <button type="button" className="twb-action twb-action-primary" onClick={openSelectedNode}>
              {selectedNode.type === 'schedule' ? '编辑日程' : '打开笔记'}
            </button>
          ) : undefined}
        >
          {selectedNode && (
            <div className="graph-detail">
              <div className="graph-detail-title">{selectedNode.label}</div>
              <div className="graph-detail-meta">ID: {selectedNode.refId || selectedNode.id}</div>
            </div>
          )}
        </ToolDetailPanel>
      )}
    >
      <div className="graph-canvas" ref={containerRef}>
        <ForceGraph2D
          ref={fgRef}
          graphData={{ nodes, links }}
          width={width}
          height={height}
          nodeCanvasObject={paintNode}
          nodeCanvasObjectMode={() => 'replace'}
          nodeLabel="label"
          onNodeClick={handleNodeClick}
          linkColor={() => 'rgba(120, 101, 75, 0.42)'}
          linkWidth={1.5}
          linkDirectionalArrowLength={4}
          linkDirectionalArrowRelPos={1}
          backgroundColor="rgba(0,0,0,0)"
          d3AlphaDecay={0.02}
          d3VelocityDecay={0.3}
          cooldownTicks={100}
        />
      </div>
    </ToolWorkbenchShell>
  );
}
