import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import './GraphView.css';
import {
  getKnowledgeGraph,
  type KnowledgeGraphResponse,
  type Note,
  type Schedule,
} from '../api';

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

  const handleNodeClick = useCallback((node: GraphNode) => {
    if (node.type === 'schedule' && node.scheduleRef) {
      onSelectSchedule(node.scheduleRef);
      onClose();
      return;
    }

    if (node.noteRef) {
      onSelectNote(node.noteRef);
      onClose();
    }
  }, [onClose, onSelectNote, onSelectSchedule]);

  const paintNode = useCallback((node: any, ctx: CanvasRenderingContext2D, globalScale: number) => {
    const isSelected = node.isSelected || node.id === selectedNoteId || node.refId === selectedNoteId;
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
      ctx.fillStyle = '#f59e0b';
      ctx.fill();
      ctx.strokeStyle = '#d97706';
      ctx.lineWidth = 1.5;
      ctx.stroke();
    } else {
      ctx.beginPath();
      ctx.arc(node.x, node.y, type === 'note' ? radius : radius - 1, 0, 2 * Math.PI);
      ctx.fillStyle = isSelected
        ? '#4a90e2'
        : type === 'tag'
          ? '#10b981'
          : type === 'folder'
            ? '#64748b'
            : hasLinks
              ? '#7c3aed'
              : '#94a3b8';
      ctx.fill();

      if (isSelected) {
        ctx.strokeStyle = '#2563eb';
        ctx.lineWidth = 2;
        ctx.stroke();
      }
    }

    const fontSize = Math.max(10 / globalScale, 3);
    ctx.font = `${isSelected ? 'bold ' : ''}${fontSize}px sans-serif`;
    ctx.fillStyle = isSelected ? '#1e40af' : '#374151';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    ctx.fillText(node.label.length > 12 ? `${node.label.slice(0, 12)}...` : node.label, node.x, node.y + radius + 2);
  }, [links, selectedNoteId]);

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
    <div className="graph-overlay">
      <div className="graph-panel">
        <div className="graph-header">
          <div>
            <h2>Knowledge Graph</h2>
            <span className="graph-stats">
              {nodes.length} nodes · {links.length} links · {graphSource}
            </span>
          </div>
          <button type="button" className="graph-close" aria-label="Close graph view" onClick={onClose}>x</button>
        </div>
        <div className="graph-legend">
          <span><span className="legend-dot selected" />Selected note</span>
          <span><span className="legend-dot linked" />Linked note</span>
          <span><span className="legend-dot isolated" />Isolated note</span>
          <span><span className="legend-diamond" />Schedule</span>
          <span className="graph-hint">Click node to open · Wheel to zoom · Drag to pan</span>
        </div>
        <div className="graph-canvas" ref={containerRef}>
          <ForceGraph2D
            ref={fgRef}
            graphData={{ nodes, links }}
            width={width}
            height={height - 110}
            nodeCanvasObject={paintNode}
            nodeCanvasObjectMode={() => 'replace'}
            nodeLabel="label"
            onNodeClick={handleNodeClick}
            linkColor={() => '#cbd5e1'}
            linkWidth={1.5}
            linkDirectionalArrowLength={4}
            linkDirectionalArrowRelPos={1}
            backgroundColor="#f8fafc"
            d3AlphaDecay={0.02}
            d3VelocityDecay={0.3}
            cooldownTicks={100}
          />
        </div>
      </div>
    </div>
  );
}
