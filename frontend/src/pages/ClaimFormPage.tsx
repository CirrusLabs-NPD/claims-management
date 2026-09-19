import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type {
  Claim, ClaimDiagnosisInput, ClaimInput, ClaimLineInput,
  PageResponse, PatientSummary, Payer, Policy, Provider,
} from '../api/types';
import { PageHeader } from '../components/Layout';
import { Card, ErrorAlert, Loading, Money } from '../components/Common';

const today = () => new Date().toISOString().slice(0, 10);

const emptyLine = (date: string): ClaimLineInput => ({
  cptCode: '', modifiers: '', serviceDate: date, units: 1, chargeAmount: 0, description: '',
});

const emptyDx = (seq: number): ClaimDiagnosisInput => ({
  icd10Code: '', description: '', sequenceNo: seq,
});

export default function ClaimFormPage({ mode }: { mode: 'create' | 'edit' }) {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [patients, setPatients] = useState<PatientSummary[]>([]);
  const [payers, setPayers] = useState<Payer[]>([]);
  const [providers, setProviders] = useState<Provider[]>([]);
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [places, setPlaces] = useState<{ code: string; label: string }[]>([]);

  const [patientId, setPatientId] = useState('');
  const [providerId, setProviderId] = useState('');
  const [payerId, setPayerId] = useState('');
  const [policyId, setPolicyId] = useState('');
  const [serviceDateFrom, setServiceDateFrom] = useState(today());
  const [serviceDateTo, setServiceDateTo] = useState(today());
  const [placeOfService, setPlaceOfService] = useState('11');
  const [notes, setNotes] = useState('');
  const [lines, setLines] = useState<ClaimLineInput[]>([emptyLine(today())]);
  const [diagnoses, setDiagnoses] = useState<ClaimDiagnosisInput[]>([emptyDx(1)]);

  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Reference data
  useEffect(() => {
    Promise.all([
      api<PageResponse<PatientSummary>>('/patients', { query: { size: 200, sort: 'lastName' } }),
      api<Payer[]>('/payers'),
      api<Provider[]>('/providers'),
      api<{ code: string; label: string }[]>('/reference/places-of-service'),
    ])
      .then(([pt, pay, prov, pos]) => {
        setPatients(pt.content);
        setPayers(pay);
        setProviders(prov);
        setPlaces(pos);
      })
      .catch(() => setError('Could not load reference data.'))
      .finally(() => { if (mode === 'create') setLoading(false); });
  }, [mode]);

  // Existing claim when editing
  useEffect(() => {
    if (mode !== 'edit' || !id) return;
    api<Claim>(`/claims/${id}`)
      .then((c) => {
        setPatientId(String(c.patientId));
        setProviderId(String(c.providerId));
        setPayerId(String(c.payerId));
        setPolicyId(c.policyId ? String(c.policyId) : '');
        setServiceDateFrom(c.serviceDateFrom);
        setServiceDateTo(c.serviceDateTo);
        setPlaceOfService(c.placeOfService);
        setNotes(c.notes ?? '');
        setLines(c.lines.map((l) => ({
          cptCode: l.cptCode, modifiers: l.modifiers ?? '', serviceDate: l.serviceDate,
          units: l.units, chargeAmount: l.chargeAmount, description: l.description ?? '',
        })));
        setDiagnoses(c.diagnoses.map((d) => ({
          icd10Code: d.icd10Code, description: d.description ?? '', sequenceNo: d.sequenceNo,
        })));
      })
      .catch((err) => setError(err instanceof ApiError ? err.detail : 'Could not load the claim.'))
      .finally(() => setLoading(false));
  }, [mode, id]);

  // Coverage follows the selected patient
  useEffect(() => {
    if (!patientId) { setPolicies([]); return; }
    api<Policy[]>(`/patients/${patientId}/policies`)
      .then(setPolicies)
      .catch(() => setPolicies([]));
  }, [patientId]);

  const total = useMemo(
    () => lines.reduce((sum, l) => sum + (Number(l.chargeAmount) || 0), 0),
    [lines],
  );

  const updateLine = (i: number, patch: Partial<ClaimLineInput>) =>
    setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));

  const updateDx = (i: number, patch: Partial<ClaimDiagnosisInput>) =>
    setDiagnoses((prev) => prev.map((d, idx) => (idx === i ? { ...d, ...patch } : d)));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});

    const payload: ClaimInput = {
      patientId: Number(patientId),
      providerId: Number(providerId),
      payerId: Number(payerId),
      policyId: policyId ? Number(policyId) : null,
      serviceDateFrom,
      serviceDateTo,
      placeOfService,
      notes: notes || null,
      lines: lines.map((l) => ({
        cptCode: l.cptCode.trim().toUpperCase(),
        modifiers: l.modifiers?.trim() ? l.modifiers.trim().toUpperCase() : null,
        serviceDate: l.serviceDate,
        units: Number(l.units),
        chargeAmount: Number(l.chargeAmount),
        description: l.description?.trim() || null,
      })),
      diagnoses: diagnoses.map((d, i) => ({
        icd10Code: d.icd10Code.trim().toUpperCase(),
        description: d.description?.trim() || null,
        sequenceNo: d.sequenceNo ?? i + 1,
      })),
    };

    try {
      const saved = mode === 'create'
        ? await api<Claim>('/claims', { method: 'POST', body: payload })
        : await api<Claim>(`/claims/${id}`, { method: 'PUT', body: payload });
      navigate(`/claims/${saved.id}`);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.detail);
        setFieldErrors(err.fieldErrors);
      } else {
        setError('Could not save the claim.');
      }
    } finally {
      setBusy(false);
    }
  };

  if (loading) return <><PageHeader title="Claim" /><div className="content"><Loading /></div></>;

  return (
    <>
      <PageHeader
        title={mode === 'create' ? 'New claim' : 'Edit claim'}
        subtitle="Only DRAFT claims can be edited. The total is always the sum of the service lines."
        actions={<button className="btn btn-secondary" onClick={() => navigate(-1)}>Cancel</button>}
      />

      <div className="content">
        <ErrorAlert message={error} />
        {Object.keys(fieldErrors).length > 0 && (
          <div className="alert alert-error">
            <strong>Fix these fields:</strong>
            <ul style={{ margin: '6px 0 0', paddingLeft: 18 }}>
              {Object.entries(fieldErrors).map(([k, v]) => <li key={k}><code>{k}</code>: {v}</li>)}
            </ul>
          </div>
        )}

        <form onSubmit={submit}>
          <Card title="Encounter">
            <div className="grid grid-3">
              <div className="field">
                <label htmlFor="patient">Patient</label>
                <select id="patient" value={patientId} required
                        onChange={(e) => { setPatientId(e.target.value); setPolicyId(''); }}>
                  <option value="">Select a patient</option>
                  {patients.map((p) => (
                    <option key={p.id} value={p.id}>{p.displayName} ({p.mrn})</option>
                  ))}
                </select>
              </div>

              <div className="field">
                <label htmlFor="provider">Rendering provider</label>
                <select id="provider" value={providerId} required
                        onChange={(e) => setProviderId(e.target.value)}>
                  <option value="">Select a provider</option>
                  {providers.map((p) => (
                    <option key={p.id} value={p.id}>{p.displayName} &mdash; {p.specialty}</option>
                  ))}
                </select>
              </div>

              <div className="field">
                <label htmlFor="payer">Payer</label>
                <select id="payer" value={payerId} required onChange={(e) => setPayerId(e.target.value)}>
                  <option value="">Select a payer</option>
                  {payers.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
              </div>

              <div className="field">
                <label htmlFor="policy">Coverage</label>
                <select id="policy" value={policyId} onChange={(e) => setPolicyId(e.target.value)}
                        disabled={!patientId}>
                  <option value="">No policy attached</option>
                  {policies.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.priority} &mdash; {p.payerName} &mdash; {p.memberId}
                      {p.activeToday ? '' : ' (terminated)'}
                    </option>
                  ))}
                </select>
                <div className="field-hint">
                  Submission is blocked if the policy is not active on the service date.
                </div>
              </div>

              <div className="field">
                <label htmlFor="from">Service date from</label>
                <input id="from" type="date" value={serviceDateFrom} required
                       onChange={(e) => {
                         setServiceDateFrom(e.target.value);
                         if (serviceDateTo < e.target.value) setServiceDateTo(e.target.value);
                       }} />
              </div>

              <div className="field">
                <label htmlFor="to">Service date to</label>
                <input id="to" type="date" value={serviceDateTo} required
                       onChange={(e) => setServiceDateTo(e.target.value)} />
              </div>

              <div className="field">
                <label htmlFor="pos">Place of service</label>
                <select id="pos" value={placeOfService} onChange={(e) => setPlaceOfService(e.target.value)}>
                  {places.map((p) => <option key={p.code} value={p.code}>{p.code} &mdash; {p.label}</option>)}
                </select>
              </div>
            </div>

            <div className="field">
              <label htmlFor="notes">Notes</label>
              <textarea id="notes" value={notes} maxLength={1000}
                        onChange={(e) => setNotes(e.target.value)} />
            </div>
          </Card>

          <div className="mt-16">
            <Card
              title="Diagnoses"
              actions={
                <button type="button" className="btn btn-secondary btn-sm"
                        disabled={diagnoses.length >= 12}
                        onClick={() => setDiagnoses((d) => [...d, emptyDx(d.length + 1)])}>
                  Add diagnosis
                </button>
              }
              tight
            >
              <table>
                <thead>
                  <tr><th style={{ width: 60 }}>Seq</th><th style={{ width: 160 }}>ICD-10</th>
                      <th>Description</th><th style={{ width: 60 }} /></tr>
                </thead>
                <tbody>
                  {diagnoses.map((d, i) => (
                    <tr key={i}>
                      <td className="faint">{i + 1}</td>
                      <td>
                        <input className="mono" value={d.icd10Code} placeholder="E11.9" required
                               onChange={(e) => updateDx(i, { icd10Code: e.target.value })} />
                      </td>
                      <td>
                        <input value={d.description ?? ''} placeholder="Description"
                               onChange={(e) => updateDx(i, { description: e.target.value })} />
                      </td>
                      <td className="right">
                        <button type="button" className="btn btn-secondary btn-sm"
                                disabled={diagnoses.length === 1}
                                onClick={() => setDiagnoses((prev) =>
                                  prev.filter((_, idx) => idx !== i)
                                      .map((x, idx) => ({ ...x, sequenceNo: idx + 1 })))}>
                          Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          </div>

          <div className="mt-16">
            <Card
              title="Service lines"
              actions={
                <button type="button" className="btn btn-secondary btn-sm"
                        onClick={() => setLines((l) => [...l, emptyLine(serviceDateFrom)])}>
                  Add line
                </button>
              }
              tight
            >
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 50 }}>#</th>
                    <th style={{ width: 120 }}>CPT</th>
                    <th style={{ width: 110 }}>Modifiers</th>
                    <th style={{ width: 160 }}>Service date</th>
                    <th style={{ width: 90 }}>Units</th>
                    <th style={{ width: 130 }}>Charge</th>
                    <th>Description</th>
                    <th style={{ width: 60 }} />
                  </tr>
                </thead>
                <tbody>
                  {lines.map((l, i) => (
                    <tr key={i}>
                      <td className="faint">{i + 1}</td>
                      <td>
                        <input className="mono" value={l.cptCode} placeholder="99213" required
                               onChange={(e) => updateLine(i, { cptCode: e.target.value })} />
                      </td>
                      <td>
                        <input className="mono" value={l.modifiers ?? ''} placeholder="25,LT"
                               onChange={(e) => updateLine(i, { modifiers: e.target.value })} />
                      </td>
                      <td>
                        <input type="date" value={l.serviceDate} required
                               onChange={(e) => updateLine(i, { serviceDate: e.target.value })} />
                      </td>
                      <td>
                        <input type="number" min={1} value={l.units} required
                               onChange={(e) => updateLine(i, { units: Number(e.target.value) })} />
                      </td>
                      <td>
                        <input type="number" min={0} step="0.01" value={l.chargeAmount} required
                               onChange={(e) => updateLine(i, { chargeAmount: Number(e.target.value) })} />
                      </td>
                      <td>
                        <input value={l.description ?? ''} placeholder="Description"
                               onChange={(e) => updateLine(i, { description: e.target.value })} />
                      </td>
                      <td className="right">
                        <button type="button" className="btn btn-secondary btn-sm"
                                disabled={lines.length === 1}
                                onClick={() => setLines((prev) => prev.filter((_, idx) => idx !== i))}>
                          Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={5} className="right"><strong>Total charge</strong></td>
                    <td className="num"><strong><Money value={total} /></strong></td>
                    <td colSpan={2} className="faint">Computed server-side from the lines</td>
                  </tr>
                </tfoot>
              </table>
            </Card>
          </div>

          <div className="mt-16 btn-row">
            <button className="btn" type="submit" disabled={busy}>
              {busy ? 'Saving' : mode === 'create' ? 'Create claim' : 'Save changes'}
            </button>
            <button className="btn btn-secondary" type="button" onClick={() => navigate(-1)}>Cancel</button>
          </div>
        </form>
      </div>
    </>
  );
}
