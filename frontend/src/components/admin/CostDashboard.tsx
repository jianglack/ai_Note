import { useEffect, useState } from 'react';
import api from '../../services/api';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend
} from 'recharts';

type DayCost = { date: string; totalCost: number; callCount: number; totalTokens: number };
type ModelCost = { model: string; totalCost: number; callCount: number; totalTokens: number };
type TopRequest = {
  traceId: string; userId: string; model: string; callType: string;
  inputTokens: number; outputTokens: number; estimatedCostYuan: number;
  latencyMs: number; createdAt: string;
};

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899'];

export default function CostDashboard() {
  const [days, setDays] = useState(30);
  const [dayCosts, setDayCosts] = useState<DayCost[]>([]);
  const [modelCosts, setModelCosts] = useState<ModelCost[]>([]);
  const [topRequests, setTopRequests] = useState<TopRequest[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      api.get(`/api/admin/cost-stats?days=${days}`),
      api.get(`/api/admin/cost-by-model?days=${days}`),
      api.get(`/api/admin/cost-top?limit=10&days=${days}`),
    ]).then(([dayRes, modelRes, topRes]) => {
      setDayCosts(dayRes.data.reverse());
      setModelCosts(modelRes.data);
      setTopRequests(topRes.data);
    }).catch(console.error)
      .finally(() => setLoading(false));
  }, [days]);

  const totalCost = dayCosts.reduce((s, d) => s + d.totalCost, 0);
  const totalCalls = dayCosts.reduce((s, d) => s + d.callCount, 0);

  if (loading) return <div style={{ padding: 40, textAlign: 'center' }}>Loading...</div>;

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700 }}>API 成本追踪</h1>
        <select value={days} onChange={e => setDays(Number(e.target.value))}
          style={{ padding: '6px 12px', borderRadius: 6, border: '1px solid #d1d5db' }}>
          <option value={7}>最近 7 天</option>
          <option value={30}>最近 30 天</option>
          <option value={90}>最近 90 天</option>
        </select>
      </div>

      {/* 概览卡片 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16, marginBottom: 24 }}>
        <StatCard label="总费用" value={`¥${totalCost.toFixed(4)}`} />
        <StatCard label="总调用次数" value={totalCalls.toLocaleString()} />
        <StatCard label="日均费用" value={`¥${(totalCost / Math.max(days, 1)).toFixed(4)}`} />
      </div>

      {/* 图表 */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 24, marginBottom: 24 }}>
        {/* 费用趋势 */}
        <div style={{ background: '#fff', borderRadius: 12, padding: 20, border: '1px solid #e5e7eb' }}>
          <h3 style={{ fontSize: 16, fontWeight: 600, marginBottom: 16 }}>费用趋势</h3>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={dayCosts}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" tick={{ fontSize: 12 }} />
              <YAxis tick={{ fontSize: 12 }} />
              <Tooltip formatter={(v) => `¥${Number(v).toFixed(6)}`} />
              <Line type="monotone" dataKey="totalCost" stroke="#3b82f6" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* 模型占比 */}
        <div style={{ background: '#fff', borderRadius: 12, padding: 20, border: '1px solid #e5e7eb' }}>
          <h3 style={{ fontSize: 16, fontWeight: 600, marginBottom: 16 }}>模型占比</h3>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie data={modelCosts} dataKey="totalCost" nameKey="model"
                cx="50%" cy="50%" outerRadius={80} label={({ name }) => name}>
                {modelCosts.map((_, i) => (
                  <Cell key={i} fill={COLORS[i % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip formatter={(v) => `¥${Number(v).toFixed(6)}`} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Top 10 最贵请求 */}
      <div style={{ background: '#fff', borderRadius: 12, padding: 20, border: '1px solid #e5e7eb' }}>
        <h3 style={{ fontSize: 16, fontWeight: 600, marginBottom: 16 }}>Top 10 最贵请求</h3>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr style={{ borderBottom: '2px solid #e5e7eb', textAlign: 'left' }}>
              <th style={{ padding: 8 }}>TraceId</th>
              <th style={{ padding: 8 }}>模型</th>
              <th style={{ padding: 8 }}>类型</th>
              <th style={{ padding: 8 }}>Input</th>
              <th style={{ padding: 8 }}>Output</th>
              <th style={{ padding: 8 }}>费用</th>
              <th style={{ padding: 8 }}>延迟</th>
              <th style={{ padding: 8 }}>时间</th>
            </tr>
          </thead>
          <tbody>
            {topRequests.map((r, i) => (
              <tr key={i} style={{ borderBottom: '1px solid #f3f4f6' }}>
                <td style={{ padding: 8, fontFamily: 'monospace', fontSize: 11 }}>{r.traceId?.substring(0, 8)}</td>
                <td style={{ padding: 8 }}>{r.model}</td>
                <td style={{ padding: 8 }}>{r.callType}</td>
                <td style={{ padding: 8 }}>{r.inputTokens}</td>
                <td style={{ padding: 8 }}>{r.outputTokens}</td>
                <td style={{ padding: 8, fontWeight: 600 }}>¥{r.estimatedCostYuan?.toFixed(6)}</td>
                <td style={{ padding: 8 }}>{r.latencyMs}ms</td>
                <td style={{ padding: 8, fontSize: 11 }}>{r.createdAt?.substring(0, 16)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ background: '#fff', borderRadius: 12, padding: 20, border: '1px solid #e5e7eb' }}>
      <div style={{ fontSize: 13, color: '#6b7280', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 24, fontWeight: 700 }}>{value}</div>
    </div>
  );
}
