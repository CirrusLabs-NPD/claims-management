import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError, download } from '../api/client';
import type { ClaimReport, ReportPeriodType } from '../api/types';
import { PageHeader } from '../components/Layout';
import {
  Card, DateText, DateTimeText, EmptyState, ErrorAlert, Loading, Money, StatusBadge,
} from '../components/Common';

type ExportFormat = 'csv' | 'xlsx' | 'pdf';

const QUARTERS = [1, 2, 3, 4] as const;

/** The years offered in the picker: this year back through nine prior years. */
function yearOptions(): number[] {
  const now = new Date().getFullYear();
  return Array.from({ length: 10 }, (_, i) => now - i);
}

/** Most recent complete quarter, so the page opens on a period that has data. */
function defaultPeriod(): { year: number; quarter: number } {
  const now = new Date();
  const currentQuarter = Math.floor(now.getMonth() / 3) + 1;
  return currentQuarter === 1
    ? { year: now.getFullYear() - 1, quarter: 4 }
    : { year: now.getFullYear(), quarter: currentQuarter - 1 };
}

export default function ReportsPage() {
  const [params, setParams] = useSearchParams();
  const fallback = useMemo(defaultPeriod, []);

  const period = (params.get('period') as ReportPeriodType | null) ?? 'QUARTER';
  const year = Number(params.get('year') ?? fallback.year);
  const quarter = Number(params.get('quarter') ?? fallback.quarter);

  const [report, setReport] = useState<ClaimReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState<ExportFormat | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);

  const setParam = useCallback((patch: Record<string, string>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => next.set(k, v));
    if (patch.period === 'YEAR') next.delete('quarter');
    setParams(next);
  }, [params, setParams]);

  const query = useMemo(() => ({
    period,
    year,
    ...(period === 'QUARTER' ? { quarter } : {}),
  }), [period, year, quarter]);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setExportError(null);
    api<ClaimReport>('/reports/claims', { query })
      .then((r) => { if (alive) { setReport(r); setError(null); } })
      .catch((err) => {
        if (alive) {
          setReport(null);
          setError(err instanceof ApiError ? err.detail : 'Could not load the report.');
        }
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [query]);

  const runExport = useCallback(async (format: ExportFormat) => {
    setExporting(format);
    setExportError(null);
    try {
      await download(`/reports/claims/export.${format}`, { query });
    } catch (err) {
      setExportError(
        err instanceof ApiError ? err.detail : `Could not export the ${format.toUpperCase()} report.`,
      );
    } finally {
      setExporting(null);
    }
  }, [query]);

  const hasDetail = !!report && report.detail.length > 0;
  const collectionRate = report && report.summary.totalCharged > 0
    ? (report.summary.totalPaid / report.summary.totalCharged) * 100
    : 0;

  return (
    <>
      <PageHeader
        title="Reports"
        subtitle="Quarterly and annual claims report — rolled-up summary with the underlying detail"
        actions={
          <div className="btn-row">
            <button className="btn btn-secondary" disabled={!hasDetail || exporting !== null}
                    onClick={() => runExport('csv')} aria-busy={exporting === 'csv'}>
              {exporting === 'csv' ? 'Exporting…' : 'Export CSV'}
            </button>
            <button className="btn btn-secondary" disabled={!hasDetail || exporting !== null}
                    onClick={() => runExport('xlsx')} aria-busy={exporting === 'xlsx'}>
              {exporting === 'xlsx' ? 'Exporting…' : 'Export Excel'}
            </button>
            <button className="btn btn-secondary" disabled={!hasDetail || exporting !== null}
                    onClick={() => runExport('pdf')} aria-busy={exporting === 'pdf'}>
              {exporting === 'pdf' ? 'Exporting…' : 'Export PDF'}
            </button>
          </div>
        }
      />

      <div className="content">
        <ErrorAlert message={error} />
        <ErrorAlert message={exportError} />

        <div className="card">
          <div className="filters" role="group" aria-label="Report period">
            <div className="field">
              <label htmlFor="period">Period</label>
              <select id="period" value={period}
                      onChange={(e) => setParam({ period: e.target.value })}>
                <option value="QUARTER">Quarter</option>
                <option value="YEAR">Full year</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="year">Year</label>
              <select id="year" value={year} onChange={(e) => setParam({ year: e.target.value })}>
                {yearOptions().map((y) => <option key={y} value={y}>{y}</option>)}
              </select>
            </div>
            {period === 'QUARTER' && (
              <div className="field">
                <label htmlFor="quarter">Quarter</label>
                <select id="quarter" value={quarter}
                        onChange={(e) => setParam({ quarter: e.target.value })}>
                  {QUARTERS.map((q) => <option key={q} value={q}>Q{q}</option>)}
                </select>
              </div>
            )}
            {report && (
              <div className="field">
                <label>&nbsp;</label>
                <span className="pill">
                  {report.period.label} &middot;{' '}
                  <DateText value={report.period.from} /> &ndash; <DateText value={report.period.to} />
                </span>
              </div>
            )}
          </div>

          {loading && <Loading label="Loading report" />}
        </div>

        {!loading && report && (
          <>
            <div className="grid grid-4 mt-16">
              <div className="card stat">
                <div className="stat-label">Total claims</div>
                <div className="stat-value">{report.summary.totalClaims.toLocaleString()}</div>
                <div className="stat-note">in {report.period.label}</div>
              </div>
              <div className="card stat">
                <div className="stat-label">Charged</div>
                <div className="stat-value"><Money value={report.summary.totalCharged} /></div>
                <div className="stat-note">gross billed amount</div>
              </div>
              <div className="card stat">
                <div className="stat-label">Collected</div>
                <div className="stat-value"><Money value={report.summary.totalPaid} /></div>
                <div className="stat-note">{collectionRate.toFixed(1)}% of charges</div>
              </div>
              <div className="card stat">
                <div className="stat-label">Outstanding AR</div>
                <div className="stat-value"><Money value={report.summary.outstandingReceivable} /></div>
                <div className="stat-note">excludes paid and voided</div>
              </div>
            </div>

            <div className="grid grid-2 mt-16">
              <Card title="Summary by status" tight>
                <table>
                  <thead>
                    <tr>
                      <th>Status</th>
                      <th className="num">Claims</th>
                      <th className="num">Charged</th>
                      <th className="num">Paid</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.summary.byStatus.filter((b) => b.count > 0).map((b) => (
                      <tr key={b.status}>
                        <td>
                          <StatusBadge status={b.status} />
                          <div className="faint" style={{ fontSize: 11, marginTop: 3 }}>{b.description}</div>
                        </td>
                        <td className="num">{b.count}</td>
                        <td className="num"><Money value={b.totalCharge} muted /></td>
                        <td className="num"><Money value={b.paidAmount} muted /></td>
                      </tr>
                    ))}
                    {report.summary.byStatus.every((b) => b.count === 0) && (
                      <tr><td colSpan={4} className="faint" style={{ padding: '14px' }}>
                        No claims in this period.
                      </td></tr>
                    )}
                  </tbody>
                </table>
              </Card>

              <Card title="Summary by place of service" tight>
                <table>
                  <thead>
                    <tr>
                      <th>POS code</th>
                      <th className="num">Claims</th>
                      <th className="num">Charged</th>
                      <th className="num">Paid</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.summary.byType.map((b) => (
                      <tr key={b.code || '—'}>
                        <td className="mono">{b.code || '—'}</td>
                        <td className="num">{b.count}</td>
                        <td className="num"><Money value={b.totalCharge} muted /></td>
                        <td className="num"><Money value={b.paidAmount} muted /></td>
                      </tr>
                    ))}
                    {report.summary.byType.length === 0 && (
                      <tr><td colSpan={4} className="faint" style={{ padding: '14px' }}>
                        No claims in this period.
                      </td></tr>
                    )}
                  </tbody>
                </table>
              </Card>
            </div>

            <div className="mt-16">
              <Card title={`Claim detail — ${report.detail.length.toLocaleString()} claim${report.detail.length === 1 ? '' : 's'}`} tight>
                {hasDetail ? (
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
                        <th>Submitted</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.detail.map((c) => (
                        <tr key={c.id}>
                          <td className="mono">{c.claimNumber}</td>
                          <td>
                            {c.patientName}
                            <div className="faint mono" style={{ fontSize: 11 }}>{c.patientMrn}</div>
                          </td>
                          <td>{c.payerName}</td>
                          <td className="muted">{c.providerName}</td>
                          <td>
                            <DateText value={c.serviceDateFrom} />
                            {c.serviceDateTo !== c.serviceDateFrom && (
                              <> &ndash; <DateText value={c.serviceDateTo} /></>
                            )}
                          </td>
                          <td className="num"><Money value={c.totalCharge} /></td>
                          <td className="num"><Money value={c.paidAmount} muted /></td>
                          <td className="num"><Money value={c.outstanding} muted /></td>
                          <td><StatusBadge status={c.status} /></td>
                          <td><DateTimeText value={c.submittedAt} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ) : (
                  <EmptyState
                    title="No claims in this period"
                    hint="Pick another quarter or year, or switch to a full-year report."
                  />
                )}
              </Card>
            </div>
          </>
        )}
      </div>
    </>
  );
}
