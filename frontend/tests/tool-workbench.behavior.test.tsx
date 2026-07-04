import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import {
  ToolControlRail,
  ToolDetailPanel,
  ToolWorkbenchShell,
} from '../src/components/workbench';

describe('ToolWorkbenchShell', () => {
  it('renders warm workbench regions and actions', async () => {
    const user = userEvent.setup();
    const onBack = vi.fn();
    const onRefresh = vi.fn();
    const onPrimary = vi.fn();

    render(
      <ToolWorkbenchShell
        title="关系图谱"
        subtitle="当前空间 · 2 篇笔记"
        chips={<span>2 nodes</span>}
        onBack={onBack}
        backLabel="关闭关系图谱"
        secondaryActions={[{ key: 'refresh', label: '刷新', onClick: onRefresh }]}
        primaryActions={[{ key: 'focus', label: '聚焦当前笔记', variant: 'primary', onClick: onPrimary }]}
        leftRail={<ToolControlRail sections={[{ key: 'scope', title: '范围', content: <button type="button">当前空间</button> }]} />}
        detailPanel={<ToolDetailPanel title="节点详情">Alpha</ToolDetailPanel>}
      >
        <div>Graph canvas</div>
      </ToolWorkbenchShell>,
    );

    expect(screen.getByRole('dialog', { name: '关系图谱' })).toBeInTheDocument();
    expect(screen.getByText('当前空间 · 2 篇笔记')).toBeInTheDocument();
    expect(screen.getByText('范围')).toBeInTheDocument();
    expect(screen.getByText('Graph canvas')).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: '节点详情' })).toHaveTextContent('Alpha');

    await user.click(screen.getByRole('button', { name: '刷新' }));
    await user.click(screen.getByRole('button', { name: '聚焦当前笔记' }));
    await user.click(screen.getByRole('button', { name: '关闭关系图谱' }));

    expect(onRefresh).toHaveBeenCalledTimes(1);
    expect(onPrimary).toHaveBeenCalledTimes(1);
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('closes on Escape when enabled', () => {
    const onBack = vi.fn();
    render(
      <ToolWorkbenchShell title="调用追踪" onBack={onBack} closeOnEscape>
        Trace body
      </ToolWorkbenchShell>,
    );

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('renders an empty detail panel state', () => {
    render(<ToolDetailPanel title="详情" empty emptyMessage="请选择一项" />);
    expect(screen.getByRole('complementary', { name: '详情' })).toHaveTextContent('请选择一项');
  });
});
