import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { Claim, ClaimStatus, StatusHistoryEntry } from '../api/types';
import { PageHeader } from '../components/Layout';
import {
  Card, DateText, DateTimeText, ErrorAlert, Loading, Modal, Money, StatusBadge,
} from '../components/Common';
import { useAuth } from '../auth/AuthContext';

/** Transitions that must carry a reason, mirroring the server-side rules. */
const REASON_REQUIRED: ClaimStatus[] = ['REJECTED', 'DENIED', 'VOID'];
/** Transitions that must carry money. */
const PAYMENT_REQUIRED: ClaimStatus[] = ['PAID', 'PARTIALLY_PAID'];

export default function ClaimDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { canWrite, isAdmin } = useAuth();

  const [claim, setClaim] = useState<Claim | null>(null);
  const [history, setHistory] = useState<StatusHistoryEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const [target, setTarget] = useState<ClaimStatus | null>(null);
  const [reason, setReason] = useState('');
  const [allowed, setAllowed] = useState('');
  const [paid, setPaid] = useState('');
  const [busy, setBusy] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const [c, h] = await Promise.all([
        api<Claim>(`/claims/${id}`),
        api<StatusHistoryEntry[]>(`/claims/${id}/history`),
      ]);
      setClaim(c);
      setHistory(h);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Could not load this claim.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  const openTransition = (status: ClaimStatus) => {
    setTarget(status);
    setReason('');
    setModalError(null);
    setAllowed(claim && claim.allowedAmount > 0 ? String(claim.allowedAmount) : String(claim?.totalCharge ?? ''));
    setPaid('');
  };

  const submitTransition = async () => {
    if (!target || !claim) return;
    setBusy(true);
    setModalError(null);
    try {
      await api<Claim>(`/claims/${claim.id}/transition`, {
        method: 'POST',
        body: {
          targetStatus: target,
          reason: reason || null,
          allowedAmount: PAYMENT_REQUIRED.includes(target) && allowed ? Number(allowed) : null,
          paidAmount: PAYMENT_REQUIRED.includes(target) && paid ? Number(paid) : null,
        },
      });
      setTarget(null);
      await load();
    } catch (err) {
      setModalError(err instanceof ApiError ? err.detail : 'The transition failed.');
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!claim) return;
    try {
      await api<void>(`/claims/${claim.id}`, { method: 'DELETE' });
      navigate('/claims');
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Could not delete the claim.');
    }
  };

  if (loading) return <><PageHeader title="Claim" /><div className="content"><Loading /></div></>;
  if (!claim) return <><PageHeader title="Claim" /><div className="content"><ErrorAlert message={error} /></div></>;

  return (
    <>
      <PageHeader
        title={claim.claimNumber}
        subtitle={<>{claim.patientName} &middot; {claim.payerName} &middot; {claim.statusDescription}</>}
        actions={
          <>
            <Link className="btn btn-secondary" to="/claims">Back to claims</Link>
            {canWrite && claim.editable && (
              <Link className="btn btn-secondary" to={`/claims/${claim.id}/edit`}>Edit</Link>
            )}
            {isAdmin && claim.status === 'DRAFT' && (
              <button className="btn btn-danger" onClick={remove}>Delete</button>
            )}
          </>
        }
      />

      <div className="content">
        <ErrorAlert message={error} />

        {canWrite && claim.allowedTransitions.length > 0 && (
          <div className="card" style={{ marginBottom: 16 }}>
            <div className="card-head">
              <h2>Move this claim forward</h2>
              <StatusBadge status={claim.status} />
            </div>
            <div className="card-body">
              <div className="btn-row">
                {claim.allowedTransitions.map((s) => (
                  <button key={s} className="btn btn-secondary" onClick={() => openTransition(s)}>
                    {s.replace('_', ' ')}
                  </button>
                ))}
              </div>
              {claim.status === 'DENIED' && (
                <div className="alert alert-warn mt-16" style={{ marginBottom: 0 }}>
                  Appealing a denial is a <span className="pill pill-phase2">Phase 2</span> capability.
                  The API returns 501 with a pointer to the written spec.
                </div>
              )}
            </div>
          </div>
        )}

        {!canWrite && (
          <div className="alert alert-info">
            You are signed in as a viewer. Claim actions are hidden here and rejected by the API.
          </div>
        )}

        <div className="grid grid-2">
          <Card title="Claim">
            <dl className="kv">
              <dt>Status</dt><dd><StatusBadge status={claim.status} /></dd>
              <dt>Patient</dt>
              <dd><Link to={`/patients/${claim.patientId}`}>{claim.patientName}</Link>{' '}
                  <span className="faint mono">{claim.patientMrn}</span></dd>
              <dt>Provider</dt>
              <dd>{claim.providerName} <span className="faint mono">NPI {claim.providerNpi}</span></dd>
              <dt>Payer</dt><dd>{claim.payerName}</dd>
              <dt>Member ID</dt>
              <dd>{claim.policyMemberId ?? <span className="faint">no policy attached</span>}</dd>
              <dt>Service dates</dt>
              <dd><DateText value={claim.serviceDateFrom} /> &ndash; <DateText value={claim.serviceDateTo} /></dd>
              <dt>Place of service</dt><dd className="mono">{claim.placeOfService}</dd>
              <dt>Submitted</dt><dd><DateTimeText value={claim.submittedAt} /></dd>
              <dt>Created by</dt><dd>{claim.createdBy} &middot; <DateTimeText value={claim.createdAt} /></dd>
              {claim.notes && <><dt>Notes</dt><dd>{claim.notes}</dd></>}
            </dl>
          </Card>

          <Card title="Financials">
            <dl className="kv">
              <dt>Total charged</dt><dd><Money value={claim.totalCharge} /></dd>
              <dt>Allowed</dt><dd><Money value={claim.allowedAmount} muted /></dd>
              <dt>Paid</dt><dd><Money value={claim.paidAmount} muted /></dd>
              <dt>Patient responsibility</dt><dd><Money value={claim.patientResponsibility} muted /></dd>
              <dt>Outstanding</dt><dd><strong><Money value={claim.outstanding} /></strong></dd>
            </dl>

            <h3 className="mt-24" style={{ marginBottom: 8 }}>Diagnoses</h3>
            <table>
              <thead><tr><th>#</th><th>ICD-10</th><th>Description</th></tr></thead>
              <tbody>
                {claim.diagnoses.map((d) => (
                  <tr key={d.id}>
                    <td className="faint">{d.sequenceNo}</td>
                    <td className="mono">{d.icd10Code}</td>
                    <td className="muted">{d.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        </div>

        <div className="mt-16">
          <Card title="Service lines" tight>
            <table>
              <thead>
                <tr>
                  <th>#</th><th>CPT</th><th>Mods</th><th>Service date</th>
                  <th className="num">Units</th><th className="num">Charge</th>
                  <th className="num">Allowed</th><th className="num">Paid</th><th>Description</th>
                </tr>
              </thead>
              <tbody>
                {claim.lines.map((l) => (
                  <tr key={l.id}>
                    <td className="faint">{l.lineNumber}</td>
                    <td className="mono">{l.cptCode}</td>
                    <td className="mono faint">{l.modifiers ?? '—'}</td>
                    <td><DateText value={l.serviceDate} /></td>
                    <td className="num">{l.units}</td>
                    <td className="num"><Money value={l.chargeAmount} /></td>
                    <td className="num"><Money value={l.allowedAmount} muted /></td>
                    <td className="num"><Money value={l.paidAmount} muted /></td>
                    <td className="muted">{l.description}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={5} className="right"><strong>Total</strong></td>
                  <td className="num"><strong><Money value={claim.totalCharge} /></strong></td>
                  <td className="num"><Money value={claim.allowedAmount} muted /></td>
                  <td className="num"><Money value={claim.paidAmount} muted /></td>
                  <td />
                </tr>
              </tfoot>
            </table>
          </Card>
        </div>

        <div className="mt-16">
          <Card title="Audit trail">
            <ul className="timeline">
              {history.map((h, i) => (
                <li key={h.id} className={i === history.length - 1 ? 'current' : undefined}>
                  <div className="timeline-when"><DateTimeText value={h.changedAt} /> &middot; {h.changedBy}</div>
                  <div className="timeline-what">
                    {h.fromStatus ? <>{h.fromStatus.replace('_', ' ')} &rarr; </> : 'Created as '}
                    <StatusBadge status={h.toStatus} />
                  </div>
                  {h.reason && <div className="timeline-why">{h.reason}</div>}
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </div>

      {target && (
        <Modal
          title={`Move to ${target.replace('_', ' ')}`}
          onClose={() => setTarget(null)}
          footer={
            <>
              <button className="btn btn-secondary" onClick={() => setTarget(null)} disabled={busy}>Cancel</button>
              <button className="btn" onClick={submitTransition} disabled={busy}>
                {busy ? 'Working' : 'Confirm'}
              </button>
            </>
          }
        >
          {modalError && <div className="alert alert-error">{modalError}</div>}

          {PAYMENT_REQUIRED.includes(target) && (
            <>
              <div className="field">
                <label htmlFor="allowed">Allowed amount</label>
                <input id="allowed" type="number" step="0.01" min="0"
                       value={allowed} onChange={(e) => setAllowed(e.target.value)} />
                <div className="field-hint">What the payer contractually allows. Defaults to the charge.</div>
              </div>
              <div className="field">
                <label htmlFor="paid">Paid amount</label>
                <input id="paid" type="number" step="0.01" min="0"
                       value={paid} onChange={(e) => setPaid(e.target.value)} autoFocus />
                <div className="field-hint">
                  {target === 'PAID'
                    ? 'Must not exceed the allowed amount.'
                    : 'Must be greater than zero and less than the allowed amount.'}
                </div>
              </div>
            </>
          )}

          <div className="field">
            <label htmlFor="reason">
              Reason {REASON_REQUIRED.includes(target) ? '' : <span className="faint">(optional)</span>}
            </label>
            <textarea id="reason" value={reason} onChange={(e) => setReason(e.target.value)}
                      placeholder={target === 'DENIED' ? 'e.g. CO-197 precertification absent' : ''} />
            {REASON_REQUIRED.includes(target) && (
              <div className="field-hint">Required. It is written to the permanent audit trail.</div>
            )}
          </div>
        </Modal>
      )}
    </>
  );
}
