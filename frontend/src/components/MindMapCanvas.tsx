import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import ReactFlow, {
  Controls,
  MiniMap,
  Background,
  useNodesState,
  useEdgesState,
  addEdge,
  Connection,
  Node,
  Edge,
  NodeProps,
  Handle,
  Position,
  BackgroundVariant,
} from 'reactflow';
import dagre from 'dagre';
import 'reactflow/dist/style.css';
import './MindMapCanvas.css';

// ── Types ──

export interface MindMapNodeData {
  label: string;
  level: number;
  color: string;
  collapsed?: boolean;
}

export interface MindMapFlowData {
  nodes: Node<MindMapNodeData>[];
  edges: Edge[];
}

// ── Layout ──

function layoutMindMap(nodes: Node[], edges: Edge[]): { nodes: Node[]; edges: Edge[] } {
  const g = new dagre.graphlib.Graph();
  g.setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: 'LR', ranksep: 100, nodesep: 50 });

  nodes.forEach((node) => {
    g.setNode(node.id, { width: 160, height: 44 });
  });
  edges.forEach((edge) => {
    g.setEdge(edge.source, edge.target);
  });

  dagre.layout(g);

  return {
    nodes: nodes.map((node) => {
      const pos = g.node(node.id);
      return { ...node, position: { x: pos.x - 80, y: pos.y - 22 } };
    }),
    edges,
  };
}

// ── Custom Node ──

const LEVEL_COLORS = ['#6366f1', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#3b82f6'];

function MindMapNode({ data, id }: NodeProps<MindMapNodeData>) {
  const [editing, setEditing] = useState(false);
  const [label, setLabel] = useState(data.label);
  const inputRef = useRef<HTMLInputElement>(null);
  const color = data.color || LEVEL_COLORS[data.level % LEVEL_COLORS.length];

  useEffect(() => {
    if (editing) inputRef.current?.focus();
  }, [editing]);

  const handleDoubleClick = () => setEditing(true);

  const handleBlur = () => {
    setEditing(false);
    data.label = label;
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      setEditing(false);
      data.label = label;
    }
  };

  return (
    <div
      className={`mindmap-flow-node level-${data.level}`}
      style={{
        borderColor: color,
        background: `${color}15`,
        boxShadow: `0 2px 8px ${color}25`,
      }}
      onDoubleClick={handleDoubleClick}
    >
      <Handle type="target" position={Position.Left} style={{ background: color }} />
      {editing ? (
        <input
          ref={inputRef}
          className="mindmap-node-input"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          onBlur={handleBlur}
          onKeyDown={handleKeyDown}
        />
      ) : (
        <span className="mindmap-node-label" style={{ color }}>{label}</span>
      )}
      <Handle type="source" position={Position.Right} style={{ background: color }} />
    </div>
  );
}

const nodeTypes = { mindMapNode: MindMapNode };

// ── Main Component ──

interface MindMapCanvasProps {
  initialData?: MindMapFlowData;
  title?: string;
  mindMapId?: string;
  editable?: boolean;
  onSave?: (data: MindMapFlowData) => void;
  onClose?: () => void;
  onRefresh?: () => void;
}

export default function MindMapCanvas({
  initialData,
  title = '思维导图',
  editable = true,
  onSave,
  onClose,
  onRefresh,
}: MindMapCanvasProps) {
  const defaultNodes = initialData?.nodes || [];
  const defaultEdges = initialData?.edges || [];

  const { nodes: layoutedNodes, edges: layoutedEdges } = useMemo(
    () => layoutMindMap(defaultNodes, defaultEdges),
    [defaultNodes, defaultEdges]
  );

  const [nodes, setNodes, onNodesChange] = useNodesState(layoutedNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(layoutedEdges);
  const idCounter = useRef(defaultNodes.length + 1);

  useEffect(() => {
    if (initialData) {
      const { nodes: ln, edges: le } = layoutMindMap(initialData.nodes, initialData.edges);
      setNodes(ln);
      setEdges(le);
      idCounter.current = initialData.nodes.length + 1;
    }
  }, [initialData]);

  const onConnect = useCallback(
    (params: Connection) => setEdges((eds) => addEdge({ ...params, type: 'smoothstep' }, eds)),
    [setEdges]
  );

  const addChildNode = useCallback(
    (parentId: string) => {
      const parent = nodes.find((n) => n.id === parentId);
      if (!parent) return;
      const parentData = parent.data as MindMapNodeData;
      const newId = `n${idCounter.current++}`;
      const level = parentData.level + 1;
      const color = LEVEL_COLORS[level % LEVEL_COLORS.length];

      const newNode: Node<MindMapNodeData> = {
        id: newId,
        type: 'mindMapNode',
        data: { label: 'New Node', level, color },
        position: { x: parent.position.x + 200, y: parent.position.y },
      };
      const newEdge: Edge = {
        id: `e-${parentId}-${newId}`,
        source: parentId,
        target: newId,
        type: 'smoothstep',
      };

      const allNodes = [...nodes, newNode];
      const allEdges = [...edges, newEdge];
      const { nodes: ln, edges: le } = layoutMindMap(allNodes, allEdges);
      setNodes(ln);
      setEdges(le);
    },
    [nodes, edges, setNodes, setEdges]
  );

  const deleteNode = useCallback(
    (nodeId: string) => {
      if (nodeId === 'root') return;
      // Remove node and all its descendant edges
      const newEdges = edges.filter((e) => e.source !== nodeId && e.target !== nodeId);
      const newNodes = nodes.filter((n) => n.id !== nodeId);
      const { nodes: ln, edges: le } = layoutMindMap(newNodes, newEdges);
      setNodes(ln);
      setEdges(le);
    },
    [nodes, edges, setNodes, setEdges]
  );

  const autoLayout = useCallback(() => {
    const { nodes: ln, edges: le } = layoutMindMap(nodes, edges);
    setNodes(ln);
    setEdges(le);
  }, [nodes, edges, setNodes, setEdges]);

  const handleSave = useCallback(() => {
    if (onSave) {
      onSave({ nodes, edges });
    }
  }, [nodes, edges, onSave]);

  // Context menu
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; nodeId: string } | null>(null);

  const onNodeContextMenu = useCallback(
    (event: React.MouseEvent, node: Node) => {
      event.preventDefault();
      setContextMenu({ x: event.clientX, y: event.clientY, nodeId: node.id });
    },
    []
  );

  const closeContextMenu = () => setContextMenu(null);

  // Keyboard shortcuts
  const onKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      const selected = nodes.find((n) => n.selected);
      if (!selected) return;

      if (event.key === 'Tab') {
        event.preventDefault();
        addChildNode(selected.id);
      } else if (event.key === 'Delete' || event.key === 'Backspace') {
        deleteNode(selected.id);
      }
    },
    [nodes, addChildNode, deleteNode]
  );

  return (
    <div className="mindmap-canvas-overlay" onClick={onClose}>
      <div className="mindmap-canvas-container" onClick={(e) => e.stopPropagation()} onKeyDown={onKeyDown} tabIndex={0}>
        {/* Header */}
        <div className="mindmap-canvas-header">
          <div className="mindmap-canvas-header-left">
            <span className="mindmap-canvas-title">{title}</span>
            <span className="mindmap-canvas-info">{nodes.length} nodes</span>
          </div>
          <div className="mindmap-canvas-actions">
            <button className="mindmap-canvas-btn" onClick={autoLayout} title="Auto Layout">Layout</button>
            {editable && <button className="mindmap-canvas-btn" onClick={handleSave} title="Save">Save</button>}
            {onRefresh && <button className="mindmap-canvas-btn" onClick={onRefresh} title="Refresh">Refresh</button>}
            {onClose && <button type="button" className="mindmap-canvas-btn close" aria-label="Close mind map" onClick={onClose} title="Close">X</button>}
          </div>
        </div>

        {/* ReactFlow Canvas */}
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={editable ? onConnect : undefined}
          onNodeContextMenu={editable ? onNodeContextMenu : undefined}
          nodeTypes={nodeTypes}
          fitView
          attributionPosition="bottom-left"
          onClick={closeContextMenu}
        >
          <Controls />
          <MiniMap
            nodeColor={(n) => (n.data as MindMapNodeData).color || '#6366f1'}
            maskColor="rgba(0,0,0,0.1)"
          />
          <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
        </ReactFlow>

        {/* Context Menu */}
        {contextMenu && (
          <div
            className="mindmap-context-menu"
            style={{ left: contextMenu.x, top: contextMenu.y }}
          >
            <button onClick={() => { addChildNode(contextMenu.nodeId); closeContextMenu(); }}>
              Add Child
            </button>
            {contextMenu.nodeId !== 'root' && (
              <button onClick={() => { deleteNode(contextMenu.nodeId); closeContextMenu(); }}>
                Delete
              </button>
            )}
            <button onClick={closeContextMenu}>Cancel</button>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Helper: Convert old MindMapBranch format to ReactFlow ──

export interface MindMapBranch {
  label: string;
  color: string;
  children: string[];
}

export function branchesToFlowData(title: string, branches: MindMapBranch[]): MindMapFlowData {
  const nodes: Node<MindMapNodeData>[] = [];
  const edges: Edge[] = [];

  nodes.push({
    id: 'root',
    type: 'mindMapNode',
    data: { label: title, level: 0, color: '#6366f1' },
    position: { x: 0, y: 0 },
  });

  let nodeIdx = 1;
  branches.forEach((branch, bi) => {
    const branchId = `b${bi}`;
    nodes.push({
      id: branchId,
      type: 'mindMapNode',
      data: { label: branch.label, level: 1, color: branch.color },
      position: { x: 0, y: 0 },
    });
    edges.push({
      id: `e-root-${branchId}`,
      source: 'root',
      target: branchId,
      type: 'smoothstep',
    });

    branch.children.forEach((child, ci) => {
      const childId = `c${nodeIdx++}`;
      nodes.push({
        id: childId,
        type: 'mindMapNode',
        data: { label: child, level: 2, color: branch.color },
        position: { x: 0, y: 0 },
      });
      edges.push({
        id: `e-${branchId}-${childId}`,
        source: branchId,
        target: childId,
        type: 'smoothstep',
      });
    });
  });

  return { nodes, edges };
}
