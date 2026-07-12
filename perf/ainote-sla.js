import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').trim();
const TOKENS_FILE = (__ENV.AUTH_TOKENS_FILE || '').trim();
const TOKENS = TOKENS_FILE
  ? open(TOKENS_FILE).split(/[\r\n,]+/).map((value) => value.trim()).filter(Boolean)
  : [];
const RUN_ID = (__ENV.SLA_RUN_ID || 'sla-run').trim();
const TARGET_RPS = Math.max(1, Number(__ENV.TARGET_RPS || 5));
const DURATION = (__ENV.DURATION || '30s').trim();
const HARD_GATE = (__ENV.HARD_GATE || 'false').toLowerCase() === 'true';
const MAX_VUS = Math.max(20, Number(__ENV.MAX_VUS || Math.ceil(TARGET_RPS * 4)));
const PREALLOCATED_VUS = Math.min(MAX_VUS, Math.max(10, Number(__ENV.PREALLOCATED_VUS || Math.ceil(TARGET_RPS * 2))));

const MIN_AVAILABILITY = Number(__ENV.SLA_MIN_AVAILABILITY || 0.999);
const MAX_P95_MS = Number(__ENV.SLA_MAX_P95_MS || 500);
const MAX_P99_MS = Number(__ENV.SLA_MAX_P99_MS || 1000);
const APPEND_MAX_P95_MS = Number(__ENV.SLA_APPEND_MAX_P95_MS || 750);
const APPEND_MAX_P99_MS = Number(__ENV.SLA_APPEND_MAX_P99_MS || 1500);
const HISTORY_MAX_P95_MS = Number(__ENV.SLA_HISTORY_MAX_P95_MS || 500);
const HISTORY_MAX_P99_MS = Number(__ENV.SLA_HISTORY_MAX_P99_MS || 1000);

const requestSuccess = new Rate('sla_request_success');
const requestLatency = new Trend('sla_request_latency', true);
const appendLatency = new Trend('sla_append_latency', true);
const historyLatency = new Trend('sla_history_latency', true);
const memoryListLatency = new Trend('sla_memory_list_latency', true);
const notesLatency = new Trend('sla_notes_latency', true);
const appendSuccess = new Counter('sla_append_success');
const operationCount = new Counter('sla_operation_count');
const appendOperations = new Counter('sla_append_operations');
const historyOperations = new Counter('sla_history_operations');
const memoryListOperations = new Counter('sla_memory_list_operations');
const notesOperations = new Counter('sla_notes_operations');

function hardThresholds() {
  if (!HARD_GATE) return {};
  return {
    sla_request_success: [`rate>${MIN_AVAILABILITY - 0.0000001}`],
    checks: [`rate>${MIN_AVAILABILITY - 0.0000001}`],
    sla_request_latency: [`p(95)<${MAX_P95_MS + 0.0001}`, `p(99)<${MAX_P99_MS + 0.0001}`],
    sla_append_latency: [`p(95)<${APPEND_MAX_P95_MS + 0.0001}`, `p(99)<${APPEND_MAX_P99_MS + 0.0001}`],
    sla_history_latency: [`p(95)<${HISTORY_MAX_P95_MS + 0.0001}`, `p(99)<${HISTORY_MAX_P99_MS + 0.0001}`],
    dropped_iterations: ['count==0'],
  };
}

export const options = {
  scenarios: {
    mixed_stateful: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PREALLOCATED_VUS,
      maxVUs: MAX_VUS,
      gracefulStop: '15s',
      tags: { workload: 'stateful-memory-mix' },
    },
  },
  thresholds: hardThresholds(),
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  noConnectionReuse: false,
  userAgent: 'ainote-sla-capacity-v1',
};

export function setup() {
  if (!BASE_URL) throw new Error('BASE_URL is required');
  if (TOKENS.length === 0) throw new Error('AUTH_TOKENS_FILE must contain at least one token');
  const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
  if (health.status !== 200) throw new Error(`backend health failed with status ${health.status}`);
  return { tokenCount: TOKENS.length };
}

function tokenForVu() {
  return TOKENS[(exec.vu.idInTest - 1) % TOKENS.length];
}

function params(token) {
  return {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    timeout: '10s',
    tags: { sla: 'stateful-core' },
  };
}

function record(response, operation, trend, validator = null) {
  const ok = response.status === 200 && (validator === null || validator(response));
  requestSuccess.add(ok, { operation });
  requestLatency.add(response.timings.duration, { operation });
  trend.add(response.timings.duration);
  operationCount.add(1, { operation });
  check(response, { [`${operation} succeeded`]: () => ok });
  return ok;
}

function validJsonObject(response) {
  try {
    const body = response.json();
    return body !== null && typeof body === 'object';
  } catch (_) {
    return false;
  }
}

function appendTurn(token) {
  appendOperations.add(1);
  const id = `${RUN_ID}-${exec.vu.idInTest}-${exec.vu.iterationInScenario}`;
  const response = http.post(
    `${BASE_URL}/api/ai/chat/save`,
    JSON.stringify({
      userMessage: `sla-user-${id}`,
      aiReply: `sla-assistant-${id}`,
    }),
    params(token),
  );
  if (record(response, 'append', appendLatency)) appendSuccess.add(1);
}

function readHistory(token) {
  historyOperations.add(1);
  const response = http.get(`${BASE_URL}/api/ai/chat/history?limit=100`, params(token));
  record(response, 'history', historyLatency, validJsonObject);
}

function listMemories(token) {
  memoryListOperations.add(1);
  const response = http.get(`${BASE_URL}/api/memories`, params(token));
  record(response, 'memory_list', memoryListLatency, validJsonObject);
}

function listNotes(token) {
  notesOperations.add(1);
  const response = http.get(`${BASE_URL}/api/notes?page=0&size=25`, params(token));
  record(response, 'notes', notesLatency, validJsonObject);
}

export default function () {
  const token = tokenForVu();
  const bucket = exec.scenario.iterationInTest % 10;
  if (bucket < 3) {
    appendTurn(token);
  } else if (bucket < 6) {
    readHistory(token);
  } else if (bucket < 8) {
    listMemories(token);
  } else {
    listNotes(token);
  }
}
