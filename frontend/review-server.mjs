// Review-only mock server: serves the production dist and stubs the two API
// calls ReportsPage needs, so the populated page can be measured at 390px.
// NOT committed — it exists only to reproduce/verify the layout locally.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';

const PORT = process.env.PORT || 5000;
const DIST = join(process.cwd(), 'dist');

const TYPES = {
  '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css',
  '.svg': 'image/svg+xml', '.json': 'application/json', '.ico': 'image/x-icon',
};

const STATUSES = [
  ['DRAFT', 'Draft'], ['SUBMITTED', 'Submitted'], ['ACCEPTED', 'Accepted'],
  ['PAID', 'Paid'], ['DENIED', 'Denied'],
];

function report(period, year, quarter) {
  const byStatus = STATUSES.map(([status, description], i) => ({
    status, description, count: 3 + i, totalCharge: 1200.5 * (i + 1),
    paidAmount: 800.25 * i,
  }));
  const byType = [
    { code: '11', count: 12, totalCharge: 15400.0, paidAmount: 9200.0 },
    { code: '22', count: 5, totalCharge: 8800.0, paidAmount: 5100.0 },
    { code: '', count: 2, totalCharge: 900.0, paidAmount: 0 },
  ];
  const detail = Array.from({ length: 18 }, (_, i) => ({
    id: i + 1,
    claimNumber: `CLM-${year}-${String(1000 + i)}`,
    status: STATUSES[i % STATUSES.length][0],
    patientName: `Patient Familyname ${i + 1}`,
    patientMrn: `MRN-${900000 + i}`,
    payerName: 'United Healthcare of the Mid-Atlantic',
    providerName: `Dr. Providerson Longname ${i + 1}`,
    serviceDateFrom: `${year}-0${(quarter - 1) * 3 + 1 || 1}-05`.slice(0, 10),
    serviceDateTo: `${year}-0${(quarter - 1) * 3 + 1 || 1}-08`.slice(0, 10),
    totalCharge: 1450.75 + i * 100,
    paidAmount: 900.5 + i * 50,
    outstanding: 550.25 + i * 25,
    submittedAt: `${year}-03-10T09:00:00Z`,
    createdAt: `${year}-03-01T09:00:00Z`,
  }));
  return {
    period: {
      type: period, year: Number(year),
      quarter: period === 'QUARTER' ? Number(quarter) : null,
      from: `${year}-01-01`, to: `${year}-12-31`,
      label: period === 'QUARTER' ? `Q${quarter} ${year}` : `FY ${year}`,
    },
    summary: {
      totalClaims: detail.length, totalCharged: 84000.5, totalPaid: 51000.25,
      outstandingReceivable: 33000.25, byStatus, byType,
    },
    detail,
  };
}

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const p = url.pathname;

  if (p === '/api/auth/login') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({
      token: 'mock-token', username: 'admin', fullName: 'Ada Admin',
      role: 'ADMIN', expiresAt: '2099-01-01T00:00:00Z',
    }));
  }
  if (p === '/api/reports/claims') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify(report(
      url.searchParams.get('period') || 'QUARTER',
      url.searchParams.get('year') || '2026',
      url.searchParams.get('quarter') || '3',
    )));
  }
  if (p.startsWith('/api/')) {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end('{}');
  }

  let file = p === '/' ? '/index.html' : p;
  try {
    const body = await readFile(join(DIST, file));
    res.writeHead(200, { 'Content-Type': TYPES[extname(file)] || 'application/octet-stream' });
    res.end(body);
  } catch {
    // SPA fallback
    const body = await readFile(join(DIST, 'index.html'));
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(body);
  }
}).listen(PORT, '0.0.0.0', () => console.log(`review server on ${PORT}`));
