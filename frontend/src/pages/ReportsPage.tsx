import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError, downloadExport } from '../api/client';
import type { ClaimReport, ReportPeriodType } from '../api/types';
import { PageHeader } from '../components/Layout';
import {
  Card, DateText, EmptyState, ErrorAlert, Loading, Money, StatusBadge,
} from '../components/Common';

type ExportFormat = 'csv' | 'xlsx' | 'pdf';

const CURRENT_YEAR = new Date().getFullYear();
const YEARS = Array.from({ length: 8 }, (_, i) => CURRENT_YEAR - i);
const QUARTERS = [1, 2, 3, 4];
const EXPORTS: { format: ExportFormat; label: string }[] = [
  { format: 'csv', label: 'CSV' },
  { format: 'xlsx', label: 'Excel' },
  { format: 'pdf', label: 'PDF' },
];

/** Query params for the report + export endpoints, from the current picker state. */
function reportQuery(period: ReportPeriodType, year: number, quarter: number) {
  return period === 'QUARTER'
    ? { period, year, quarter }
    : { period, year };
}

export default function ReportsPage() {
  const [params, setParams] = useSearchParams();

  const period = (params.get('period') as ReportPeriodType) === 'YEAR' ? 'YEAR' : 'QUARTER';
  const year = Number(params.get('year')) || CURRENT_YEAR;
  const quarter = Math.min(4, Math.max(1, Number(params.get('quarter')) || 1));

  const [report, setReport] = useState<ClaimReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState<ExportFormat | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);

  const setParam = useCallback((key: string, value: string) => {
    const next = new URLSearchParams(params);
    next.set(key, value);
    setParams(next, { replace: true });
  }, [params, setParams]);

  const query = useMemo(() => reportQuery(period, year, quarter), [period, year, quarter]);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setExportError(null);
    api<ClaimReport>('/reports/claims', { query })
      .then((d) => { if (alive) { setReport(d); setError(null); } })
      .catch((err) => {
        if (alive) {
          setReport(null);
          setError(err instanceof ApiError ? err.detail : 'Could not load the report.');
        }
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [query]);

  const runExport = async (format: ExportFormat) => {
    setExporting(format);
    setExportError(null);
    const stamp = period === 'QUARTER' ? `${year}-Q${quarter}` : `${year}`;
    try {
      await downloadExport('/reports/claims', { ...query, format }, `claims-report-${stamp}.${format}`);
    } catch (err) {
      setExportError(err instanceof ApiError ? err.detail : `Could not export the ${format.toUpperCase()} report.`);
    } finally {
      setExporting(null);
    }
  };

  const collectionRate = report && report.summary.totalCharged > 0
    ? (report.summary.totalPaid / report.summary.totalCharged) * 100
    : 0;

  const hasDetail = !!report && report.detail.length > 0;

  return (
    <>
      <PageHeader
        title="Reports"
        subtitle="Quarterly and annual claims report — rolled-up summary and the underlying detail"
        actions={
          <div className="btn-row" role="group" aria-label="Export report">
            {EXPORTS.map(({ format, label }) => (
              <button
                key={format}
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={!hasDetail || exporting !== null}
                aria-busy={exporting === format}
                onClick={() => runExport(format)}
              >
                {exporting === format ? 'Exporting…' : `Export ${label}`}
              </button>
            ))}
          </div>
        }
      />

      <div className="content">
        <ErrorAlert message={error} />
        <ErrorAlert message={exportError} />

        <div className="card">
          <div className="filters">
            <div className="field">
              <label htmlFor="period">Period</label>
              <select
                id="period"
                value={period}
                onChange={(e) => setParam('period', e.target.value)}
              >
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
            {report && (
              <div className="field grow" style={{ alignSelf: 'flex-end' }}>
                <span className="faint">
                  Covering <strong>{report.period.label}</strong>{' '}
                  (<DateText value={report.period.from} /> &ndash; <DateText value={report.period.to} />)
                </span>
              </div>
            )}
          </div>

          {loading && <Loading label="Building report" />}

          {!loading && report && (
            <div className="card-body">
              <div className="grid grid-4">
                <div className="card stat">
                  <div className="stat-label">Total claims</div>
                  <div className="stat-value">{report.summary.totalClaims.toLocaleString()}</div>
                  <div className="stat-note">with a service date in period</div>
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
                <Card title="By status" tight>
                  {report.summary.byStatus.filter((b) => b.count > 0).length === 0 ? (
                    <EmptyState title="No claims in this period" />
                  ) : (
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
                            <td><StatusBadge status={b.status} /></td>
                            <td className="num">{b.count}</td>
                            <td className="num"><Money value={b.totalCharge} muted /></td>
                            <td className="num"><Money value={b.paidAmount} muted /></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </Card>

                <Card title="By place of service" tight>
                  {report.summary.byType.length === 0 ? (
                    <EmptyState title="No claims in this period" />
                  ) : (
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
                      </tbody>
                    </table>
                  )}
                </Card>
              </div>

              <div className="mt-24">
                <Card
                  title="Claim detail"
                  actions={<span className="faint">{report.detail.length.toLocaleString()} claim{report.detail.length === 1 ? '' : 's'}</span>}
                  tight
                >
                  {!hasDetail ? (
                    <EmptyState
                      title="No claims for this period"
                      hint="Pick another quarter or year, or switch to a full-year report."
                    />
                  ) : (
                    <div className="table-scroll">
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
                  )}
                </Card>
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
