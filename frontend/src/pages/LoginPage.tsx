import { useId, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';
import api from '../services/api';
import './auth.css';

/* ═══════════════════════════════════════════════════════════════
   书页线稿插画 — 照搬设计稿 Login.jsx
   ═══════════════════════════════════════════════════════════════ */
function BookIllustration() {
  return (
    <svg viewBox="0 0 400 480" style={{ width: '100%', height: 'auto', display: 'block' }}>
      <defs>
        <filter id="pencil" x="-10%" y="-10%" width="120%" height="120%">
          <feTurbulence baseFrequency="0.9" numOctaves={2} seed={3} />
          <feDisplacementMap in="SourceGraphic" scale={1.2} />
        </filter>
      </defs>
      <ellipse cx="200" cy="420" rx="160" ry="18" fill="#4a4032" opacity="0.08" />
      <g filter="url(#pencil)">
        <path d="M 60 140 Q 60 120, 80 118 L 195 118 Q 200 124, 200 140 L 200 380 Q 200 395, 195 398 L 80 398 Q 60 396, 60 378 Z"
          fill="#fbf7ee" stroke="#4a4032" strokeWidth="1.6" />
        {[0,1,2,3,4,5,6,7,8,9,10].map(i => (
          <line key={`l${i}`} x1="78" y1={160 + i*19} x2={178 - (i%3)*8} y2={160 + i*19}
            stroke="#4a4032" strokeWidth="0.8" opacity="0.55" />
        ))}
        <rect x="78" y="135" width="85" height="3" fill="#b8452e" />
        <rect x="78" y="142" width="60" height="2" fill="#4a4032" opacity="0.7" />
      </g>
      <g filter="url(#pencil)">
        <path d="M 200 140 Q 200 124, 205 118 L 320 118 Q 340 120, 340 140 L 340 378 Q 340 396, 320 398 L 205 398 Q 200 395, 200 380 Z"
          fill="#fbf7ee" stroke="#4a4032" strokeWidth="1.6" />
        {[0,1,2,3,4,5,6].map(i => (
          <line key={`r${i}`} x1="210" y1={170 + i*19} x2={320 - (i%2)*15} y2={170 + i*19}
            stroke="#4a4032" strokeWidth="0.8" opacity="0.55" />
        ))}
        <circle cx="240" cy="320" r="18" fill="none" stroke="#b8452e" strokeWidth="1.5" />
        <path d="M 232 320 L 238 326 L 250 312" fill="none" stroke="#b8452e" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M 270 305 Q 290 295, 320 315" fill="none" stroke="#4a4032" strokeWidth="1" opacity="0.6" />
      </g>
      <line x1="200" y1="120" x2="200" y2="398" stroke="#4a4032" strokeWidth="0.8" opacity="0.3" />
      <g fontFamily="'Ma Shan Zheng', cursive" fill="#b8452e" opacity="0.85">
        <text x="130" y="80" fontSize="28" transform="rotate(-8 130 80)">思</text>
        <text x="240" y="60" fontSize="22" transform="rotate(12 240 60)">考</text>
        <text x="310" y="95" fontSize="30" transform="rotate(-6 310 95)">记</text>
        <text x="50" y="95" fontSize="20" transform="rotate(8 50 95)">录</text>
      </g>
      <circle cx="90" cy="70" r="2" fill="#4a4032" opacity="0.6" />
      <circle cx="340" cy="75" r="1.5" fill="#4a4032" opacity="0.5" />
      <circle cx="370" cy="200" r="3" fill="#b8452e" opacity="0.7" />
      <circle cx="30" cy="250" r="2" fill="#4a4032" opacity="0.5" />
      <g transform="translate(320, 160)">
        <path d="M 0 -12 L 3 -3 L 12 0 L 3 3 L 0 12 L -3 3 L -12 0 L -3 -3 Z"
          fill="#f5d76e" stroke="#b8452e" strokeWidth="1" />
      </g>
      <g transform="translate(70, 300)">
        <path d="M 0 -8 L 2 -2 L 8 0 L 2 2 L 0 8 L -2 2 L -8 0 L -2 -2 Z"
          fill="#f5d76e" stroke="#b8452e" strokeWidth="0.8" />
      </g>
    </svg>
  );
}

/* ═══════════════════════════════════════════════════════════════
   InkField — 底部虚线输入框
   ═══════════════════════════════════════════════════════════════ */
function InkField({ label, placeholder, type = 'text', value, onChange }: {
  label: string; placeholder?: string; type?: string;
  value: string; onChange: (v: string) => void;
}) {
  const [focused, setFocused] = useState(false);
  const inputId = useId();
  return (
    <div className="ink-field">
      <label className="ink-field-label" htmlFor={inputId}>{label}</label>
      <input
        id={inputId}
        type={type}
        placeholder={placeholder}
        value={value}
        onChange={e => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        className="ink-field-input"
        style={{
          borderBottom: `1.5px ${focused ? 'solid' : 'dashed'} ${focused ? 'var(--color-accent, #b8452e)' : 'var(--color-paper-4, #c9b893)'}`,
        }}
      />
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
   主组件 — 合并登录 / 注册 / 忘记密码
   ═══════════════════════════════════════════════════════════════ */
export default function LoginPage() {
  const [mode, setMode] = useState<'login' | 'register' | 'forgot'>('login');
  const [username, setUsername] = useState(() => localStorage.getItem('remembered-username') ?? '');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [remember, setRemember] = useState(() => !!localStorage.getItem('remembered-username'));
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState('');

  const login = useAuthStore((s) => s.login);
  const register = useAuthStore((s) => s.register);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      if (mode === 'login') {
        await login(username, password);
        if (remember) localStorage.setItem('remembered-username', username);
        else localStorage.removeItem('remembered-username');
        navigate('/');
      } else if (mode === 'register') {
        if (!username || !email || !password) { setError('请填写所有字段'); setLoading(false); return; }
        if ([...password].length < 15) { setError('密码长度至少为 15 个字符'); setLoading(false); return; }
        await register(username, email, password);
        navigate('/');
      } else {
        if (password !== confirmPassword) { setError('两次输入的密码不一致'); setLoading(false); return; }
        if ([...password].length < 15) { setError('密码长度至少为 15 个字符'); setLoading(false); return; }
        await api.post('/api/auth/reset-password', { username, email, newPassword: password });
        setSuccess('密码重置成功！');
        setTimeout(() => setMode('login'), 1500);
      }
    } catch (err: any) {
      if (mode === 'login') setError('用户名或密码错误');
      else if (mode === 'register') setError(err.response?.data?.message || '注册失败，请重试');
      else setError('用户名或邮箱不正确');
    } finally {
      setLoading(false);
    }
  };

  const accent = 'var(--color-accent, #b8452e)';

  return (
    <div className="auth-page paper-texture">
      {/* ═══ 左栏：品牌 + 插画 + 引言 ═══ */}
      <div className="auth-left">
        {/* 品牌 */}
        <div className="auth-brand">
          <div className="auth-brand-icon">
            <span>智</span>
          </div>
          <div>
            <div className="auth-brand-name">智记 · Smart Notes</div>
            <div className="auth-brand-tagline">让 AI 与笔为伴</div>
          </div>
        </div>

        {/* 插画 */}
        <div className="auth-illustration">
          <div style={{ width: '100%', maxWidth: 460 }}>
            <BookIllustration />
          </div>
        </div>

        {/* 引言 */}
        <div className="auth-quote">
          <div className="auth-quote-text">
            "我们记录，<br />
            <span style={{ color: accent }}>不是为了记住</span>，<br />
            而是为了看清自己。"
          </div>
          <div className="auth-quote-attr">—— 智记的编辑手记</div>
        </div>
      </div>

      {/* ═══ 右栏：表单 ═══ */}
      <div className="auth-right">
        <div className="auth-stamp">
          <span className="stamp">二零二六 · 春</span>
        </div>

        <div className="auth-form-container">
          {/* 标题 */}
          <div className="auth-heading">
            <div className="auth-welcome-label">欢迎回来 · WELCOME</div>
            <h1 className="auth-title">
              继续你<br />未完的一页
            </h1>
            <div className="auth-accent-bar" />
          </div>

          {/* Tab 切换 */}
          <div className="auth-tabs">
            {(['login', 'register'] as const).map(k => (
              <button
                key={k}
                className={`auth-tab ${mode === k ? 'active' : ''}`}
                onClick={() => { setMode(k); setError(''); setSuccess(''); }}
              >
                {k === 'login' ? '登录' : '注册'}
              </button>
            ))}
          </div>

          {/* 表单 */}
          <form onSubmit={handleSubmit}>
            {mode !== 'forgot' && (
              <>
                <InkField label="账号" placeholder="请输入用户名" value={username} onChange={setUsername} />
                {mode === 'register' && (
                  <InkField label="邮箱" placeholder="your@email.com" type="email" value={email} onChange={setEmail} />
                )}
                <InkField label="密码" type="password" placeholder={mode === 'register' ? '至少 15 个字符' : '请输入密码'} value={password} onChange={setPassword} />
                {mode === 'register' && (
                  <InkField label="确认密码" type="password" placeholder="再输一次" value={confirmPassword} onChange={setConfirmPassword} />
                )}
                {mode === 'login' && (
                  <div className="auth-options">
                    <label className="auth-checkbox">
                      <input
                        className="auth-checkbox-input"
                        type="checkbox"
                        checked={remember}
                        onChange={(event) => setRemember(event.target.checked)}
                      />
                      <span className={`auth-check-box ${remember ? 'checked' : ''}`} aria-hidden="true">
                        {remember && (
                          <svg width="10" height="10" viewBox="0 0 10 10">
                            <path d="M 1 5 L 4 8 L 9 2" fill="none" stroke="#fbf7ee" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                          </svg>
                        )}
                      </span>
                      记住我
                    </label>
                    <a href="#" className="auth-forgot" onClick={e => { e.preventDefault(); setMode('forgot'); setError(''); }}>
                      忘记密码？
                    </a>
                  </div>
                )}
              </>
            )}

            {mode === 'forgot' && (
              <>
                <p className="auth-forgot-hint">
                  请填写注册时的信息来重置密码。
                </p>
                <InkField label="用户名" placeholder="请输入用户名" value={username} onChange={setUsername} />
                <InkField label="邮箱" placeholder="your@email.com" type="email" value={email} onChange={setEmail} />
                <InkField label="新密码" type="password" placeholder="至少 15 个字符" value={password} onChange={setPassword} />
                <InkField label="确认密码" type="password" placeholder="再输一次" value={confirmPassword} onChange={setConfirmPassword} />
                <a href="#" className="auth-back-link" onClick={e => { e.preventDefault(); setMode('login'); setError(''); }}>
                  ← 返回登录
                </a>
              </>
            )}

            {error && <p className="auth-error">{error}</p>}
            {success && <p className="auth-success">{success}</p>}

            <button className="auth-primary-btn" type="submit" disabled={loading}>
              {loading ? '处理中...' : mode === 'login' ? '开始书写' : mode === 'register' ? '创建笔记本' : '重置密码'}
            </button>
          </form>

          <p className="auth-terms">
            继续即表示同意<a href="#">服务条款</a> 与 <a href="#">隐私政策</a>
          </p>
        </div>
      </div>
    </div>
  );
}
