import { useEffect, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { Payer } from '../api/types';
import { PageHeader } from '../components/Layout';
import { ErrorAlert, Loading } from '../components/Common';

export default function PayersPage() {
  const [payers, setPayers] = useState<Payer[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api<Payer[]>('/payers')
      .then(setPayers)
      .catch((err) => setError(err instanceof ApiError ? err.detail : 'Could not load payers.'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <>
      <PageHeader title="Payers" subtitle="Insurance companies claims are billed to" />
      <div className="content">
        <ErrorAlert message={error} />
        <div className="card">
          {loading ? <Loading /> : (
            <table>
              <thead>
                <tr><th>Code</th><th>Name</th><th>Plan type</th><th>Claims address</th><th>Phone</th><th>Status</th></tr>
              </thead>
              <tbody>
                {payers.map((p) => (
                  <tr key={p.id}>
                    <td className="mono">{p.payerCode}</td>
                    <td>{p.name}</td>
                    <td><span className="pill">{p.planType.replace('_', ' ')}</span></td>
                    <td className="muted">{p.claimsAddress}</td>
                    <td className="muted nowrap">{p.phone}</td>
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
