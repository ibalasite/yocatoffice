// tests/perf/lib/summary.js
// Self-contained baseline reporter (no network jslib import — runs offline).
// Writes results/baseline-<scenario>-<ts>.{json,html} + a readable stdout table.
// The baseline report is the comparable artifact for SCALE evaluation (test-plan §5.11).

import { RESULT_DIR, SLO } from './config.js';

function fmt(n) {
  if (n === undefined || n === null || Number.isNaN(n)) return '-';
  return (Math.round(n * 100) / 100).toString();
}

function row(label, m, fail) {
  if (!m || !m.values) return `${label.padEnd(26)} (no samples)`;
  const v = m.values;
  if ('p(95)' in v) {
    return `${label.padEnd(26)} p50=${fmt(v['p(50)'] ?? v.med)}ms p95=${fmt(v['p(95)'])}ms ` +
           `p99=${fmt(v['p(99)'])}ms avg=${fmt(v.avg)}ms max=${fmt(v.max)}ms`;
  }
  if ('rate' in v && ('passes' in v || 'fails' in v)) {
    return `${label.padEnd(26)} rate=${fmt(v.rate * 100)}% (pass=${v.passes ?? 0} fail=${v.fails ?? 0})`;
  }
  if ('count' in v) {
    return `${label.padEnd(26)} count=${v.count} rate=${fmt(v.rate)}/s`;
  }
  return `${label.padEnd(26)} ${JSON.stringify(v)}`;
}

function textSummary(data, scenario) {
  const m = data.metrics || {};
  const lines = [];
  lines.push('════════════════════════════════════════════════════════════════');
  lines.push(` k6 perf baseline — scenario: ${scenario}`);
  lines.push('════════════════════════════════════════════════════════════════');
  lines.push(row('http_req_duration (all)', m['http_req_duration']));
  lines.push(row('http_req_failed (all)', m['http_req_failed']));
  lines.push(row('http_reqs', m['http_reqs']));
  lines.push(row('iterations', m['iterations']));
  lines.push(row('vus_max', m['vus_max']));
  lines.push('— threshold results —');
  for (const [name, m2] of Object.entries(m)) {
    if (!m2.thresholds) continue;
    for (const [expr, t] of Object.entries(m2.thresholds)) {
      const ok = t.ok === undefined ? !t.fails : t.ok;
      lines.push(`  ${ok ? 'PASS' : 'FAIL'}  ${name} ${expr}`);
    }
  }
  lines.push('════════════════════════════════════════════════════════════════');
  return lines.join('\n');
}

function htmlReport(data, scenario, ts) {
  const m = data.metrics || {};
  const thr = [];
  for (const [name, m2] of Object.entries(m)) {
    if (!m2.thresholds) continue;
    for (const [expr, t] of Object.entries(m2.thresholds)) {
      const ok = t.ok === undefined ? !t.fails : t.ok;
      thr.push(`<tr class="${ok ? 'ok' : 'bad'}"><td>${name}</td><td>${expr}</td><td>${ok ? 'PASS' : 'FAIL'}</td></tr>`);
    }
  }
  const dur = m['http_req_duration'] && m['http_req_duration'].values || {};
  const failed = m['http_req_failed'] && m['http_req_failed'].values || {};
  return `<!doctype html><html><head><meta charset="utf-8">
<title>k6 baseline — ${scenario} — ${ts}</title>
<style>body{font:14px system-ui,sans-serif;margin:2rem;color:#111}
h1{font-size:1.2rem}table{border-collapse:collapse;margin:1rem 0}
td,th{border:1px solid #ccc;padding:4px 10px;text-align:left}
.ok{background:#e7f7e7}.bad{background:#fdecec}
.kpi{display:inline-block;margin:0 1.5rem 1rem 0}</style></head><body>
<h1>k6 Performance Baseline — ${scenario}</h1>
<p>Generated ${ts}. SLO source: test-plan §3.4/§10.2, EDD §10.5.</p>
<div class="kpi"><b>P50</b> ${fmt(dur['p(50)'] ?? dur.med)} ms</div>
<div class="kpi"><b>P95</b> ${fmt(dur['p(95)'])} ms</div>
<div class="kpi"><b>P99</b> ${fmt(dur['p(99)'])} ms (SLO ≤ ${SLO.overall.p99})</div>
<div class="kpi"><b>Error rate</b> ${fmt((failed.rate || 0) * 100)} % (SLO ≤ ${SLO.overall.errRate * 100})</div>
<div class="kpi"><b>Requests</b> ${(m['http_reqs'] && m['http_reqs'].values.count) || 0}</div>
<h2>Threshold results</h2>
<table><tr><th>metric</th><th>threshold</th><th>result</th></tr>${thr.join('')}</table>
<p>Full metric payload: baseline-${scenario}-${ts}.json</p>
</body></html>`;
}

// Returns a k6 handleSummary() closure for the given scenario name.
export function baselineSummary(scenario) {
  return function handleSummary(data) {
    const ts = new Date().toISOString().replace(/[:.]/g, '-');
    const base = `${RESULT_DIR}/baseline-${scenario}-${ts}`;
    const out = {};
    out['stdout'] = '\n' + textSummary(data, scenario) + '\n';
    out[`${base}.json`] = JSON.stringify(data, null, 2);
    out[`${base}.html`] = htmlReport(data, scenario, ts);
    return out;
  };
}
