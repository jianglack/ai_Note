import { useState } from 'react';
import { useUiStore } from '../stores/uiStore';
import { useToastStore } from '../stores/toastStore';
import './SettingsPage.css';

type TabId = 'ai';
type ModelMode = 'fast' | 'balance' | 'deep';

type AiSettingsState = {
  model: ModelMode;
  proactive: boolean;
  autoTag: boolean;
  readAll: boolean;
  noTrain: boolean;
  instructions: string;
};

const SETTINGS_KEY = 'ainote.ai-settings.v1';
const DEFAULT_SETTINGS: AiSettingsState = {
  model: 'balance',
  proactive: true,
  autoTag: true,
  readAll: true,
  noTrain: true,
  instructions: '我习惯在清晨写作，喜欢留白和克制的表达。改写文字时尽量保留原有节奏。',
};

const TABS: { id: TabId; label: string; icon: string; accent?: boolean }[] = [
  { id: 'ai', label: 'AI · 墨子', icon: '✦', accent: true },
];

function loadAiSettings(): AiSettingsState {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    return { ...DEFAULT_SETTINGS, ...JSON.parse(raw) };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

function Toggle({
  on,
  onChange,
  disabled,
}: {
  on: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      className="settings-toggle"
      data-on={on}
      disabled={disabled}
      aria-pressed={on}
      onClick={() => onChange(!on)}
    >
      <span className="settings-toggle-thumb" />
    </button>
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

function SettingRow({ label, value, right, hint, disabled }: {
  label: string; value?: string; right?: React.ReactNode; hint?: string; disabled?: boolean;
}) {
  return (
    <div className={`setting-row ${disabled ? 'disabled' : ''}`}>
      <div style={{ flex: 1 }}>
        <div className="setting-row-label">{label}</div>
        {hint && <div className="setting-row-hint">{hint}</div>}
      </div>
      {value && <span className="setting-row-value">{value} ·</span>}
      {right}
    </div>
  );
}

function AiSettings() {
  const toastStore = useToastStore();
  const [settings, setSettings] = useState<AiSettingsState>(() => loadAiSettings());

  const models = [
    { id: 'fast' as const, name: '轻盈', desc: '响应最快 · 日常使用' },
    { id: 'balance' as const, name: '均衡', desc: '速度与深度兼顾' },
    { id: 'deep' as const, name: '深思', desc: '复杂推理 · 较慢' },
  ];

  const updateSetting = <K extends keyof AiSettingsState>(key: K, value: AiSettingsState[K]) => {
    setSettings((current) => ({ ...current, [key]: value }));
  };

  const saveSettings = () => {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
    toastStore.addToast({ type: 'success', title: '设置已保存' });
  };

  const resetSettings = () => {
    setSettings(DEFAULT_SETTINGS);
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(DEFAULT_SETTINGS));
    toastStore.addToast({ type: 'info', title: '设置已恢复默认' });
  };

  return (
    <div className="settings-content">
      <h1 className="settings-page-title">墨子 · AI 助理</h1>
      <p className="settings-page-desc">
        设置保存在本机浏览器。未接入后端配置接口的能力已禁用，避免给出不可生效的开关。
      </p>

      <SettingGroup title="智能模型">
        <div className="model-grid">
          {models.map(m => (
            <button
              key={m.id}
              type="button"
              className={`model-card ${settings.model === m.id ? 'active' : ''}`}
              onClick={() => updateSetting('model', m.id)}
            >
              {settings.model === m.id && <span className="model-current">当前</span>}
              <span className="model-name">{m.name}</span>
              <span className="model-desc">{m.desc}</span>
            </button>
          ))}
        </div>
      </SettingGroup>

      <SettingGroup title="墨子的性格">
        <SettingRow label="语气风格" value="温和 · 文学" hint="影响 AI 回复的语气" />
        <SettingRow
          label="主动建议"
          right={<Toggle on={settings.proactive} onChange={(v) => updateSetting('proactive', v)} />}
          hint="阅读时主动提示相关旧笔记"
        />
        <SettingRow
          label="自动标签"
          right={<Toggle on={settings.autoTag} onChange={(v) => updateSetting('autoTag', v)} />}
          hint="新笔记自动生成标签"
        />
        <SettingRow
          label="晨间简报"
          right={<Toggle on={false} onChange={() => {}} disabled />}
          hint="需要后端定时任务配置接口"
          disabled
        />
      </SettingGroup>

      <SettingGroup title="隐私与训练">
        <SettingRow
          label="允许 AI 阅读全部笔记"
          right={<Toggle on={settings.readAll} onChange={(v) => updateSetting('readAll', v)} />}
          hint="关闭后，墨子仅能读取你明确选择的笔记"
        />
        <SettingRow
          label="不参与模型训练"
          right={<Toggle on={settings.noTrain} onChange={(v) => updateSetting('noTrain', v)} />}
          hint="该偏好会保存在本机设置中"
        />
        <SettingRow
          label="本地优先模式"
          right={<Toggle on={false} onChange={() => {}} disabled />}
          hint="当前部署没有本地推理后端"
          disabled
        />
      </SettingGroup>

      <SettingGroup title="自定义指令" subtitle="告诉墨子一些关于你的事，它会更懂你">
        <textarea
          className="settings-textarea"
          value={settings.instructions}
          onChange={e => updateSetting('instructions', e.target.value.slice(0, 500))}
          rows={4}
        />
        <div className="settings-char-count">{settings.instructions.length} / 500 字</div>
      </SettingGroup>

      <div className="settings-actions">
        <button type="button" className="settings-secondary-btn" onClick={resetSettings}>恢复默认</button>
        <button type="button" className="settings-primary-btn" onClick={saveSettings}>保存设置</button>
      </div>
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
          <button type="button" className="settings-back" onClick={() => ui.setIsSettingsOpen(false)}>
            ← 返回笔记
          </button>
          <div className="settings-section-label">设置</div>
          {TABS.map(t => {
            const active = t.id === tab;
            return (
              <button
                type="button"
                key={t.id}
                className={`settings-tab ${active ? 'active' : ''} ${t.accent ? 'accent' : ''}`}
                onClick={() => setTab(t.id)}
              >
                <span className="settings-tab-icon" style={t.accent ? { color: 'var(--color-accent)' } : undefined}>
                  {t.icon}
                </span>
                {t.label}
                {t.accent && active && <span className="settings-new-badge">NEW</span>}
              </button>
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
