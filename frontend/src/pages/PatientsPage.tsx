import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { PageResponse, PatientSummary } from '../api/types';
import { PageHeader } from '../components/Layout';
import { DateText, EmptyState, ErrorAlert, Loading, Pagination } from '../components/Common';

export default function PatientsPage() {
  const navigate = useNavigate();
  const [data, setData] = useState<PageResponse<PatientSummary> | null>(null);
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    api<PageResponse<PatientSummary>>('/patients', { query: { q, page, size: 20, sort: 'lastName' } })
      .then((d) => { if (alive) { setData(d); setError(null); } })
      .catch((err) => { if (alive) setError(err instanceof ApiError ? err.detail : 'Could not load patients.'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [q, page]);

  return (
    <>
      <PageHeader title="Patients" subtitle="Demographics and coverage on file" />
      <div className="content">
        <ErrorAlert message={error} />
        <div className="card">
          <div className="filters">
            <div className="field grow">
              <label htmlFor="search">Search</label>
              <input id="search" placeholder="Name or MRN"
                     onKeyDown={(e) => {
                       if (e.key === 'Enter') { setPage(0); setQ((e.target as HTMLInputElement).value); }
                     }}
                     onBlur={(e) => { setPage(0); setQ(e.target.value); }} />
            </div>
          </div>

          {loading && <Loading label="Loading patients" />}
          {!loading && data && data.content.length === 0 && <EmptyState title="No patients found" />}

          {!loading && data && data.content.length > 0 && (
            <>
              <table>
                <thead>
                  <tr><th>MRN</th><th>Name</th><th>Date of birth</th></tr>
                </thead>
                <tbody>
                  {data.content.map((p) => (
                    <tr key={p.id} className="clickable" onClick={() => navigate(`/patients/${p.id}`)}>
                      <td className="mono">{p.mrn}</td>
                      <td>{p.displayName}</td>
                      <td><DateText value={p.dateOfBirth} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <Pagination page={data.page} totalPages={data.totalPages}
                          totalElements={data.totalElements} onChange={setPage} />
            </>
          )}
        </div>
      </div>
    </>
  );
}
