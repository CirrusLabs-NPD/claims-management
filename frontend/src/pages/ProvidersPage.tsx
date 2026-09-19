import { useEffect, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { Provider } from '../api/types';
import { PageHeader } from '../components/Layout';
import { ErrorAlert, Loading } from '../components/Common';

export default function ProvidersPage() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api<Provider[]>('/providers')
      .then(setProviders)
      .catch((err) => setError(err instanceof ApiError ? err.detail : 'Could not load providers.'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <>
      <PageHeader title="Providers" subtitle="Rendering providers identified by NPI" />
      <div className="content">
        <ErrorAlert message={error} />
        <div className="card">
          {loading ? <Loading /> : (
            <table>
              <thead>
                <tr><th>NPI</th><th>Name</th><th>Specialty</th><th>Tax ID</th><th>Status</th></tr>
              </thead>
              <tbody>
                {providers.map((p) => (
                  <tr key={p.id}>
                    <td className="mono">{p.npi}</td>
                    <td>{p.displayName}</td>
                    <td className="muted">{p.specialty}</td>
                    <td className="mono muted">{p.taxId}</td>
                    <td>
                      <span className={p.active ? 'badge badge-PAID' : 'badge badge-VOID'}>
                        {p.active ? 'ACTIVE' : 'INACTIVE'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  );
}
