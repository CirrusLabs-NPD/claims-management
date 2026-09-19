import { useState } from 'react';
import { api, ApiError } from '../api/client';
import { PageHeader } from '../components/Layout';
import { Card } from '../components/Common';

interface Item {
  ref: string;
  title: string;
  summary: string;
  endpoint: string;
  method: 'GET' | 'POST';
}

const PHASE_2: Item[] = [
  {
    ref: '2.1',
    title: 'EDI 837P claim export',
    summary: 'Generate an X12 837 Professional file for a batch of submitted claims, with control numbers, loops 2000A/2000B/2300/2400 and a batch manifest.',
    endpoint: '/edi/837p/export',
    method: 'POST',
  },
  {
    ref: '2.2',
    title: 'ERA 835 remittance ingest',
    summary: 'Parse an 835 remittance advice, match CLP segments to claims, post payments and adjustments to the line level, and drive the resulting status transitions automatically.',
    endpoint: '/edi/835/import',
    method: 'POST',
  },
  {
    ref: '2.3',
    title: 'Real-time eligibility (270/271)',
    summary: 'Verify coverage with the payer before the encounter: active status, copay, deductible remaining, and plan dates written back onto the policy.',
    endpoint: '/eligibility/check',
    method: 'POST',
  },
  {
    ref: '2.4',
    title: 'Denial management and appeals',
    summary: 'CARC/RARC reason codes on denials, a denial worklist, appeal letters, and the DENIED to APPEALED transition that Phase 1 deliberately blocks.',
    endpoint: '/denials',
    method: 'GET',
  },
  {
    ref: '2.5',
    title: 'Document attachments',
    summary: 'Upload, store and retrieve supporting documentation against a claim: operative notes, referrals, prior authorisations, itemised bills.',
    endpoint: '/claims/1/attachments',
    method: 'GET',
  },
  {
    ref: '2.6',
    title: 'Analytics and reporting',
    summary: 'AR aging buckets, denial rate by payer, days in AR, clean-claim rate, and payer performance comparisons.',
    endpoint: '/analytics/ar-aging',
    method: 'GET',
  },
];

export default function RoadmapPage() {
  const [results, setResults] = useState<Record<string, string>>({});

  const probe = async (item: Item) => {
    setResults((r) => ({ ...r, [item.ref]: 'Calling...' }));
    try {
      await api(item.endpoint, { method: item.method, body: item.method === 'POST' ? {} : undefined });
      setResults((r) => ({ ...r, [item.ref]: 'Unexpectedly succeeded - this feature appears to be implemented.' }));
    } catch (err) {
      if (err instanceof ApiError) {
        setResults((r) => ({ ...r, [item.ref]: `${err.status} - ${err.detail}` }));
      } else {
        setResults((r) => ({ ...r, [item.ref]: 'Request failed.' }));
      }
    }
  };

  return (
    <>
      <PageHeader
        title="Phase 2 roadmap"
        subtitle="Specified, wired into the API, and deliberately not implemented"
      />
      <div className="content">
        <div className="alert alert-info">
          Phase 1 is complete and demoable on its own. The six capabilities below are the
          handoff to the next agent. Each already has a route, an authorisation rule and a
          written specification in <code>docs/PHASE2_HANDOFF.md</code>; each returns{' '}
          <strong>501 Not Implemented</strong> with an <code>X-Phase: 2</code> header. Nothing
          is silently missing. Press <em>Probe endpoint</em> to see it for yourself.
        </div>

        <div className="grid grid-2">
          {PHASE_2.map((item) => (
            <Card
              key={item.ref}
              title={<>{item.title} <span className="pill pill-phase2">{item.ref}</span></>}
              actions={
                <button className="btn btn-secondary btn-sm" onClick={() => probe(item)}>
                  Probe endpoint
                </button>
              }
            >
              <p className="muted" style={{ marginTop: 0 }}>{item.summary}</p>
              <div className="mono faint" style={{ fontSize: 12 }}>
                {item.method} /api{item.endpoint}
              </div>
              {results[item.ref] && (
                <div className="alert alert-warn" style={{ marginTop: 12, marginBottom: 0 }}>
                  {results[item.ref]}
                </div>
              )}
            </Card>
          ))}
        </div>

        <div className="mt-16">
          <Card title="Phase 3 - hardening">
            <ul className="muted" style={{ margin: 0, paddingLeft: 18 }}>
              <li>Testcontainers integration tests across the full claim lifecycle</li>
              <li>Unit tests for every edge of the status state machine</li>
              <li>GitHub Actions CI: build, test, image publish</li>
              <li>Refresh tokens with rotation, and rate limiting on the login endpoint</li>
              <li>Structured JSON logging with request correlation IDs, Micrometer metrics</li>
              <li>PHI access audit log and secrets supplied entirely from the environment</li>
            </ul>
          </Card>
        </div>
      </div>
    </>
  );
}
