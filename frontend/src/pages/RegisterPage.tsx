import { useRef, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';
import './auth.css';

function AINoteIcon() {
  return (
    <div className="auth-logo">
      <svg viewBox="0 0 26 26" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="4" y="3" width="14" height="18" rx="2" fill="white" opacity="0.9"/>
        <rect x="7" y="9" width="8" height="1.5" rx="0.75" fill="#8b5cf6"/>
        <rect x="7" y="12.5" width="8" height="1.5" rx="0.75" fill="#8b5cf6"/>
        <rect x="7" y="16" width="5" height="1.5" rx="0.75" fill="#8b5cf6"/>
        <circle cx="20" cy="6" r="4" fill="url(#sparkle-reg)"/>
        <defs>
          <radialGradient id="sparkle-reg" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#f9a8d4"/>
            <stop offset="100%" stopColor="#c084fc"/>
          </radialGradient>
        </defs>
      </svg>
    </div>
  );
}

export default function RegisterPage() {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const submittingRef = useRef(false);
  const register = useAuthStore((s) => s.register);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submittingRef.current) return;

    setError('');
    submittingRef.current = true;
    setLoading(true);
    try {
      await register(username, email, password);
      navigate('/');
    } catch (err: any) {
      submittingRef.current = false;
      setLoading(false);
      setError(err.response?.data?.message || '注册失败，请重试');
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        {/* 品牌 */}
        <div className="auth-brand">
          <AINoteIcon />
          <div className="auth-brand-text">
            <span className="auth-brand-name">AI Note</span>
            <span className="auth-brand-tagline">智能笔记，AI 驱动</span>
          </div>
        </div>

        {/* 标题 */}
        <div className="auth-header">
          <h1 className="auth-title">创建账户</h1>
          <p className="auth-subtitle">加入 AI Note，开启智能笔记之旅</p>
        </div>

        {/* 表单 */}
        <form onSubmit={handleSubmit} className="auth-form">
          <div className="auth-field">
            <label className="auth-label">用户名</label>
            <div className="auth-input-wrapper">
              <svg className="auth-input-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                <circle cx="8" cy="5" r="3"/>
                <path d="M2 14c0-3.314 2.686-6 6-6s6 2.686 6 6" strokeLinecap="round"/>
              </svg>
              <input
                className="auth-input"
                type="text"
                placeholder="请输入用户名"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="auth-field">
            <label className="auth-label">邮箱</label>
            <div className="auth-input-wrapper">
              <svg className="auth-input-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                <rect x="2" y="4" width="12" height="9" rx="1.5"/>
                <path d="M2 5l6 5 6-5" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              <input
                className="auth-input"
                type="email"
                placeholder="请输入邮箱"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="auth-field">
            <label className="auth-label">密码</label>
            <div className="auth-input-wrapper">
              <svg className="auth-input-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                <rect x="3" y="7" width="10" height="8" rx="1.5"/>
                <path d="M5 7V5a3 3 0 016 0v2" strokeLinecap="round"/>
              </svg>
              <input
                className="auth-input"
                type="password"
                placeholder="请输入密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
          </div>

          {error && <p className="auth-error">{error}</p>}

          <button className="auth-button" type="submit" disabled={loading}>
            {loading ? '注册中...' : '创建账户'}
          </button>
        </form>

        {/* 底部 */}
        <div className="auth-footer">
          <span>已有账户？</span>
          <Link to="/login">立即登录</Link>
        </div>
      </div>
    </div>
  );
}
