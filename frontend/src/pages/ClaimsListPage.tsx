import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { ClaimListItem, ClaimStatus, PageResponse, Payer } from '../api/types';
import { PageHeader } from '../components/Layout';
import {
  DateText, EmptyState, ErrorAlert, Loading, Money, Pagination, StatusBadge,
} from '../components/Common';
import { useAuth } from '../auth/AuthContext';

const STATUSES: ClaimStatus[] = [
  'DRAFT', 'SUBMITTED', 'ACCEPTED', 'REJECTED',
  'PARTIALLY_PAID', 'PAID', 'DENIED', 'APPEALED', 'VOID',
];

export default function ClaimsListPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const { canWrite } = useAuth();

  const [data, setData] = useState<PageResponse<ClaimListItem> | null>(null);
  const [payers, setPayers] = useState<Payer[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const status = params.get('status') ?? '';
  const payerId = params.get('payerId') ?? '';
  const q = params.get('q') ?? '';
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  const page = Number(params.get('page') ?? '0');

  const setParam = useCallback((key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next);
  }, [params, setParams]);

  useEffect(() => {
    api<Payer[]>('/payers').then(setPayers).catch(() => setPayers([]));
  }, []);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    api<PageResponse<ClaimListItem>>('/claims', {
      query: { status, payerId, q, from, to, page, size: 20, sort: 'createdAt,desc' },
    })
      .then((d) => { if (alive) { setData(d); setError(null); } })
      .catch((err) => {
        if (alive) setError(err instanceof ApiError ? err.detail : 'Could not load claims.');
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [status, payerId, q, from, to, page]);

  return (
    <>
      <PageHeader
        title="Claims"
        subtitle="Every claim in the system, filterable by status, payer and service date"
        actions={canWrite ? <Link className="btn" to="/claims/new">New claim</Link> : undefined}
      />

      <div className="content">
        <ErrorAlert message={error} />

        <div className="card">
          <div className="filters">
            <div className="field grow">
              <label htmlFor="q">Search</label>
              <input id="q" placeholder="Claim number, patient name or MRN"
                     defaultValue={q}
                     onKeyDown={(e) => {
                       if (e.key === 'Enter') setParam('q', (e.target as HTMLInputElement).value);
                     }}
                     onBlur={(e) => setParam('q', e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="status">Status</label>
              <select id="status" value={status} onChange={(e) => setParam('status', e.target.value)}>
                <option value="">All statuses</option>
                {STATUSES.map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="payer">Payer</label>
              <select id="payer" value={payerId} onChange={(e) => setParam('payerId', e.target.value)}>
                <option value="">All payers</option>
                {payers.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="from">Service from</label>
              <input id="from" type="date" value={from} onChange={(e) => setParam('from', e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="to">Service to</label>
              <input id="to" type="date" value={to} onChange={(e) => setParam('to', e.target.value)} />
            </div>
            <div className="field">
              <label>&nbsp;</label>
              <button className="btn btn-secondary" onClick={() => setParams(new URLSearchParams())}>
                Clear
              </button>
            </div>
          </div>

          {loading && <Loading label="Loading claims" />}

          {!loading && data && data.content.length === 0 && (
            <EmptyState title="No claims match these filters"
                        hint="Widen the date range or clear the filters." />
          )}

          {!loading && data && data.content.length > 0 && (
            <>
              <table>
                <thead>
                  <tr>
                    <th>Claim number</th>
                    <th>Patient</th>
                    <th>Payer</th>
                    <th>Provider</th>
                    <th>Service dates</th>
                    <th className="num">Charged</th>
                    <th className="num">Paid</th>
                    <th className="num">Outstanding</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((c) => (
                    <tr key={c.id} className="clickable" onClick={() => navigate(`/claims/${c.id}`)}>
                      <td className="mono">{c.claimNumber}</td>
                      <td>
                        {c.patientName}
                        <div className="faint mono" style={{ fontSize: 11 }}>{c.patientMrn}</div>
                      </td>
                      <td>{c.payerName}</td>
                      <td className="muted">{c.providerName}</td>
                      <td>
                        <DateText value={c.serviceDateFrom} />
                        {c.serviceDateTo !== c.serviceDateFrom && <> &ndash; <DateText value={c.serviceDateTo} /></>}
                      </td>
                      <td className="num"><Money value={c.totalCharge} /></td>
                      <td className="num"><Money value={c.paidAmount} muted /></td>
                      <td className="num"><Money value={c.outstanding} muted /></td>
                      <td><StatusBadge status={c.status} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <Pagination page={data.page} totalPages={data.totalPages}
                          totalElements={data.totalElements}
                          onChange={(p) => setParam('page', String(p))} />
            </>
          )}
        </div>
      </div>
    </>
  );
}
