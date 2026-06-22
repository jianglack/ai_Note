import { useState } from 'react';
import { useUiStore } from '../stores/uiStore';
import './SettingsPage.css';

type TabId = 'ai';

const TABS: { id: TabId; label: string; icon: string; accent?: boolean }[] = [
  { id: 'ai', label: 'AI · 墨子', icon: '✦', accent: true },
];

function Toggle({ on, onChange }: { on: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="settings-toggle" data-on={on} onClick={() => onChange(!on)}>
      <div className="settings-toggle-thumb" />
    </div>
  );
}

function SettingGroup({ title, subtitle, children }: {
  title: string; subtitle?: string; children: React.ReactNode;
}) {
  return (
    <div className="setting-group">
      <h2 className="setting-group-title">{title}</h2>
      {subtitle && <p className="setting-group-subtitle">{subtitle}</p>}
      {!subtitle && <div style={{ height: 12 }} />}
      <div className="setting-group-body">{children}</div>
    </div>
  );
}

function SettingRow({ label, value, right, hint }: {
  label: string; value?: string; right?: React.ReactNode; hint?: string;
}) {
  return (
    <div className="setting-row">
      <div style={{ flex: 1 }}>
        <div className="setting-row-label">{label}</div>
        {hint && <div className="setting-row-hint">{hint}</div>}
      </div>
      {value && <span className="setting-row-value">{value} ›</span>}
      {right}
    </div>
  );
}

function AiSettings() {
  const [model, setModel] = useState<'fast' | 'balance' | 'deep'>('balance');
  const [proactive, setProactive] = useState(true);
  const [autoTag, setAutoTag] = useState(true);
  const [morningBrief, setMorningBrief] = useState(false);
  const [readAll, setReadAll] = useState(true);
  const [noTrain, setNoTrain] = useState(true);
  const [localFirst, setLocalFirst] = useState(false);
  const [instructions, setInstructions] = useState(
    '我是一个习惯在清晨写作的人，喜欢日式美学，写作偏散文风格。请在改写我的文字时尽量保留"留白"和"破折号"。'
  );

  const models = [
    { id: 'fast' as const, name: '轻盈', desc: '响应最快 · 日常使用' },
    { id: 'balance' as const, name: '均衡', desc: '速度与深度兼顾' },
    { id: 'deep' as const, name: '深思', desc: '复杂推理 · 较慢' },
  ];

  return (
    <div className="settings-content">
      <h1 className="settings-page-title">墨子 · AI 助理</h1>
      <p className="settings-page-desc">
        墨子读过你所有的笔记，理解你的写作风格。在这里调整它的"性格"。
      </p>

      <SettingGroup title="智能模型">
        <div className="model-grid">
          {models.map(m => (
            <div
              key={m.id}
              className={`model-card ${model === m.id ? 'active' : ''}`}
              onClick={() => setModel(m.id)}
            >
              {model === m.id && <span className="model-current">当前</span>}
              <div className="model-name">{m.name}</div>
              <div className="model-desc">{m.desc}</div>
            </div>
          ))}
        </div>
      </SettingGroup>

      <SettingGroup title="墨子的性格">
        <SettingRow label="语气风格" value="温和 · 文学" hint="影响 AI 回复的语气" />
        <SettingRow label="主动建议" right={<Toggle on={proactive} onChange={setProactive} />} hint="阅读时主动提示相关旧笔记" />
        <SettingRow label="自动标签" right={<Toggle on={autoTag} onChange={setAutoTag} />} hint="新笔记自动生成标签" />
        <SettingRow label="晨间简报" right={<Toggle on={morningBrief} onChange={setMorningBrief} />} hint="每天 8:00 推送昨天的写作小结" />
      </SettingGroup>

      <SettingGroup title="隐私与训练">
        <SettingRow label="允许 AI 阅读全部笔记" right={<Toggle on={readAll} onChange={setReadAll} />} hint="关闭后，墨子仅能读你 @ 它的笔记" />
        <SettingRow label="不参与模型训练" right={<Toggle on={noTrain} onChange={setNoTrain} />} hint="你的笔记永远不会用于训练公开模型" />
        <SettingRow label="本地优先模式" right={<Toggle on={localFirst} onChange={setLocalFirst} />} hint="敏感笔记仅在本地处理（速度较慢）" />
      </SettingGroup>

      <SettingGroup title="自定义指令" subtitle="告诉墨子一些关于你的事，它会更懂你">
        <textarea
          className="settings-textarea"
          value={instructions}
          onChange={e => setInstructions(e.target.value.slice(0, 500))}
          rows={4}
        />
        <div className="settings-char-count">{instructions.length} / 500 字</div>
      </SettingGroup>
    </div>
  );
}

export default function SettingsPage() {
  const [tab, setTab] = useState<TabId>('ai');
  const ui = useUiStore();

  return (
    <div className="settings-overlay">
      <div className="settings-page paper-texture">
        <aside className="settings-sidebar">
          <div className="settings-back" onClick={() => ui.setIsSettingsOpen(false)}>
            ← 返回笔记
          </div>
          <div className="settings-section-label">设置</div>
          {TABS.map(t => {
            const active = t.id === tab;
            return (
              <div
                key={t.id}
                className={`settings-tab ${active ? 'active' : ''} ${t.accent ? 'accent' : ''}`}
                onClick={() => setTab(t.id)}
              >
                <span className="settings-tab-icon" style={t.accent ? { color: 'var(--color-accent)' } : undefined}>
                  {t.icon}
                </span>
                {t.label}
                {t.accent && active && <span className="settings-new-badge">NEW</span>}
              </div>
            );
          })}
        </aside>

        <main className="settings-main">
          {tab === 'ai' && <AiSettings />}
        </main>
      </div>
    </div>
  );
}
