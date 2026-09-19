import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { ClaimListItem, PageResponse, Patient } from '../api/types';
import { PageHeader } from '../components/Layout';
import {
  Card, DateText, EmptyState, ErrorAlert, Loading, Money, StatusBadge,
} from '../components/Common';

export default function PatientDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [patient, setPatient] = useState<Patient | null>(null);
  const [claims, setClaims] = useState<ClaimListItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    let alive = true;
    Promise.all([
      api<Patient>(`/patients/${id}`),
      api<PageResponse<ClaimListItem>>('/claims', { query: { patientId: id, size: 50 } }),
    ])
      .then(([p, c]) => { if (alive) { setPatient(p); setClaims(c.content); } })
      .catch((err) => { if (alive) setError(err instanceof ApiError ? err.detail : 'Could not load the patient.'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [id]);

  if (loading) return <><PageHeader title="Patient" /><div className="content"><Loading /></div></>;
  if (!patient) return <><PageHeader title="Patient" /><div className="content"><ErrorAlert message={error} /></div></>;

  return (
    <>
      <PageHeader title={patient.displayName} subtitle={`MRN ${patient.mrn}`}
                  actions={<Link className="btn btn-secondary" to="/patients">Back to patients</Link>} />
      <div className="content">
        <div className="grid grid-2">
          <Card title="Demographics">
            <dl className="kv">
              <dt>MRN</dt><dd className="mono">{patient.mrn}</dd>
              <dt>Date of birth</dt><dd><DateText value={patient.dateOfBirth} /></dd>
              <dt>Phone</dt><dd>{patient.phone ?? <span className="faint">&mdash;</span>}</dd>
              <dt>Email</dt><dd>{patient.email ?? <span className="faint">&mdash;</span>}</dd>
              <dt>Address</dt>
              <dd>
                {patient.addressLine1}<br />
                {patient.city}, {patient.state} {patient.postalCode}
              </dd>
            </dl>
          </Card>

          <Card title="Coverage" tight>
            {patient.policies.length === 0
              ? <EmptyState title="No coverage on file" />
              : (
                <table>
                  <thead>
                    <tr><th>Priority</th><th>Payer</th><th>Member ID</th><th>Effective</th><th>Status</th></tr>
                  </thead>
                  <tbody>
                    {patient.policies.map((p) => (
                      <tr key={p.id}>
                        <td>{p.priority}</td>
                        <td>{p.payerName}</td>
                        <td className="mono">{p.memberId}</td>
                        <td><DateText value={p.effectiveDate} /></td>
                        <td>
                          <span className={p.activeToday ? 'badge badge-PAID' : 'badge badge-VOID'}>
                            {p.activeToday ? 'ACTIVE' : 'TERMINATED'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
          </Card>
        </div>

        <div className="mt-16">
          <Card title={`Claims (${claims.length})`} tight>
            {claims.length === 0
              ? <EmptyState title="No claims for this patient yet" />
              : (
                <table>
                  <thead>
                    <tr>
                      <th>Claim number</th><th>Payer</th><th>Service dates</th>
                      <th className="num">Charged</th><th className="num">Paid</th><th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {claims.map((c) => (
                      <tr key={c.id}>
                        <td className="mono"><Link to={`/claims/${c.id}`}>{c.claimNumber}</Link></td>
                        <td>{c.payerName}</td>
                        <td><DateText value={c.serviceDateFrom} /></td>
                        <td className="num"><Money value={c.totalCharge} /></td>
                        <td className="num"><Money value={c.paidAmount} muted /></td>
                        <td><StatusBadge status={c.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
          </Card>
        </div>
      </div>
    </>
  );
}
