import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, ApiError, tokenStore } from '../api/client';
import type { ClaimReport, ReportPeriodType } from '../api/types';
import { PageHeader } from '../components/Layout';
import {
  Card, DateText, EmptyState, ErrorAlert, Loading, Money, StatusBadge,
} from '../components/Common';

type ExportFormat = 'csv' | 'xlsx' | 'pdf';

const EXPORTS: { format: ExportFormat; label: string }[] = [
  { format: 'csv', label: 'CSV' },
  { format: 'xlsx', label: 'Excel' },
  { format: 'pdf', label: 'PDF' },
];

const CURRENT_YEAR = new Date().getFullYear();
// Same window the backend accepts: MIN_YEAR (2000) up to next year.
const YEARS = Array.from({ length: CURRENT_YEAR + 1 - 2000 + 1 }, (_, i) => CURRENT_YEAR + 1 - i);
const QUARTERS = [1, 2, 3, 4] as const;

/** Query the report endpoints attaches for the current period selection. */
function reportQuery(period: ReportPeriodType, year: string, quarter: string) {
  return period === 'QUARTER'
    ? { period, year, quarter }
    : { period, year };
}

/**
 * Downloads a report export in the given format. Exports are binary, so this
 * fetches the blob directly (the JSON `api()` client parses the body as text),
 * reusing the same bearer token, then triggers a browser download.
 */
async function downloadExport(
  format: ExportFormat,
  query: Record<string, string>,
): Promise<void> {
  const token = tokenStore.get();
  const params = new URLSearchParams(query).toString();
  const response = await fetch(`/api/reports/claims/export.${format}?${params}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });

  if (!response.ok) {
    const text = await response.text();
    let detail = `Export failed with status ${response.status}`;
    try { detail = (JSON.parse(text).detail as string) ?? detail; } catch { /* not json */ }
    throw new ApiError(response.status, detail);
  }

  const blob = await response.blob();
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^"]+)"?/.exec(disposition);
  const filename = match?.[1] ?? `claims-report.${format}`;

  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export default function ReportsPage() {
  const [params, setParams] = useSearchParams();

  const period = (params.get('period') as ReportPeriodType | null) ?? 'QUARTER';
  const year = params.get('year') ?? String(CURRENT_YEAR);
  const quarter = params.get('quarter') ?? '1';

  const [report, setReport] = useState<ClaimReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState<ExportFormat | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);

  const setParam = useCallback((key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    // Switching to an annual report drops the quarter it no longer needs.
    if (key === 'period' && value === 'YEAR') next.delete('quarter');
    if (key === 'period' && value === 'QUARTER' && !next.get('quarter')) next.set('quarter', '1');
    setParams(next);
  }, [params, setParams]);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setExportError(null);
    api<ClaimReport>('/reports/claims', { query: reportQuery(period, year, quarter) })
      .then((r) => { if (alive) { setReport(r); setError(null); } })
      .catch((err) => {
        if (alive) {
          setReport(null);
          setError(err instanceof ApiError ? err.detail : 'Could not load the report.');
        }
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [period, year, quarter]);

  const onExport = async (format: ExportFormat) => {
    setExporting(format);
    setExportError(null);
    try {
      await downloadExport(format, reportQuery(period, year, quarter) as Record<string, string>);
    } catch (err) {
      setExportError(err instanceof ApiError ? err.detail : `Could not export the ${format.toUpperCase()} file.`);
    } finally {
      setExporting(null);
    }
  };

  const hasData = !!report && report.summary.totalClaims > 0;
  const canExport = !!report && !loading;

  const exportButtons = (
    <div className="btn-row">
      {EXPORTS.map(({ format, label }) => (
        <button
          key={format}
          className="btn btn-secondary btn-sm"
          disabled={!canExport || exporting !== null}
          onClick={() => onExport(format)}
        >
          {exporting === format ? 'Exporting…' : `Export ${label}`}
        </button>
      ))}
    </div>
  );

  return (
    <>
      <PageHeader
        title="Reports"
        subtitle="Roll up claims by quarter or year, then export the summary and detail"
        actions={exportButtons}
      />

      <div className="content">
        <ErrorAlert message={error} />
        <ErrorAlert message={exportError} />

        <div className="card">
          <div className="filters">
            <div className="field">
              <label htmlFor="period">Period</label>
              <select id="period" value={period} onChange={(e) => setParam('period', e.target.value)}>
                <option value="QUARTER">Quarter</option>
                <option value="YEAR">Full year</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="year">Year</label>
              <select id="year" value={year} onChange={(e) => setParam('year', e.target.value)}>
                {YEARS.map((y) => <option key={y} value={y}>{y}</option>)}
              </select>
            </div>
            {period === 'QUARTER' && (
              <div className="field">
                <label htmlFor="quarter">Quarter</label>
                <select id="quarter" value={quarter} onChange={(e) => setParam('quarter', e.target.value)}>
                  {QUARTERS.map((q) => <option key={q} value={q}>Q{q}</option>)}
                </select>
              </div>
            )}
          </div>

          {loading && <Loading label="Building the report" />}

          {!loading && report && !hasData && (
            <EmptyState
              title={`No claims for ${report.period.label}`}
              hint="Pick another quarter or year — claims are matched on their service dates."
            />
          )}
        </div>

        {!loading && report && hasData && (
          <>
            <div className="grid grid-4 mt-16">
              <div className="card stat">
                <div className="stat-label">Claims</div>
                <div className="stat-value">{report.summary.totalClaims.toLocaleString()}</div>
                <div className="stat-note">{report.period.label}</div>
              </div>
              <div className="card stat">
                <div className="stat-label">Charged</div>
                <div className="stat-value"><Money value={report.summary.totalCharged} /></div>
                <div className="stat-note">gross billed amount</div>
              </div>
              <div className="card stat">
                <div className="stat-label">Paid</div>
                <div className="stat-value"><Money value={report.summary.totalPaid} /></div>
                <div className="stat-note">collected in period</div>
              </div>
              <div className="card stat">
                <div className="stat-label">Outstanding AR</div>
                <div className="stat-value"><Money value={report.summary.outstandingReceivable} /></div>
                <div className="stat-note">excludes paid and voided</div>
              </div>
            </div>

            <div className="grid grid-2 mt-16">
              <Card title="By status" tight>
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
                  </tbody>
                </table>
              </Card>

              <Card title="By place of service" tight>
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
                        <td className="mono">{b.code || <span className="faint">&mdash;</span>}</td>
                        <td className="num">{b.count}</td>
                        <td className="num"><Money value={b.totalCharge} muted /></td>
                        <td className="num"><Money value={b.paidAmount} muted /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Card>
            </div>

            <div className="card mt-16">
              <div className="card-head">
                <h2>Claim detail</h2>
                <span className="faint" style={{ fontSize: 12 }}>
                  {report.detail.length.toLocaleString()} claim{report.detail.length === 1 ? '' : 's'}
                </span>
              </div>
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
                  {report.detail.map((c) => (
                    <tr key={c.id} className="clickable">
                      <td className="mono"><Link to={`/claims/${c.id}`}>{c.claimNumber}</Link></td>
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
            </div>
          </>
        )}
      </div>
    </>
  );
}
