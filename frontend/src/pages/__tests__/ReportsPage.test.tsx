import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import type { ClaimReport } from '../../api/types';
import ReportsPage from '../ReportsPage';

// ---------------------------------------------------------------------------
// Acceptance cases for S-5 (CLM-REPORT-UI). Derived from the story criteria:
//   1. Choosing a quarter or a year loads and shows the matching summary on
//      top and detail rows below.
//   2. The three export buttons each trigger a download of the corresponding
//      format for the selected period.
//   3. Loading, empty, and error states render without breaking the layout.
// The page fetches GET /api/reports/claims and GET
// /api/reports/claims/export.{csv,xlsx,pdf}; both go through the global
// fetch, so we stub fetch and localStorage and assert on what renders and on
// the URLs the page requests.
// ---------------------------------------------------------------------------

const YEAR = new Date().getFullYear();

function quarterReport(): ClaimReport {
  return {
    period: {
      type: 'QUARTER',
      year: YEAR,
      quarter: 1,
      from: `${YEAR}-01-01`,
      to: `${YEAR}-03-31`,
      label: `Q1 ${YEAR}`,
    },
    summary: {
      totalClaims: 2,
      totalCharged: 1500,
      totalPaid: 900,
      outstandingReceivable: 600,
      byStatus: [
        { status: 'PAID', description: 'Paid in full', count: 1, totalCharge: 500, paidAmount: 500 },
        { status: 'SUBMITTED', description: 'Submitted to payer', count: 1, totalCharge: 1000, paidAmount: 400 },
      ],
      byType: [
        { code: '11', count: 1, totalCharge: 500, paidAmount: 500 },
        { code: '22', count: 1, totalCharge: 1000, paidAmount: 400 },
      ],
    },
    detail: [
      {
        id: 101,
        claimNumber: 'CLM-Q1-0001',
        status: 'PAID',
        patientName: 'Ada Lovelace',
        patientMrn: 'MRN-1001',
        payerName: 'Aetna',
        providerName: 'Dr. Grace Hopper',
        serviceDateFrom: `${YEAR}-01-10`,
        serviceDateTo: `${YEAR}-01-10`,
        totalCharge: 500,
        paidAmount: 500,
        outstanding: 0,
        submittedAt: `${YEAR}-01-12T00:00:00`,
        createdAt: `${YEAR}-01-11T00:00:00`,
      },
      {
        id: 102,
        claimNumber: 'CLM-Q1-0002',
        status: 'SUBMITTED',
        patientName: 'Alan Turing',
        patientMrn: 'MRN-1002',
        payerName: 'Cigna',
        providerName: 'Dr. Katherine Johnson',
        serviceDateFrom: `${YEAR}-02-05`,
        serviceDateTo: `${YEAR}-02-06`,
        totalCharge: 1000,
        paidAmount: 400,
        outstanding: 600,
        submittedAt: `${YEAR}-02-07T00:00:00`,
        createdAt: `${YEAR}-02-06T00:00:00`,
      },
    ],
  };
}

function yearReport(): ClaimReport {
  const r = quarterReport();
  return {
    ...r,
    period: {
      type: 'YEAR',
      year: YEAR,
      quarter: null,
      from: `${YEAR}-01-01`,
      to: `${YEAR}-12-31`,
      label: `${YEAR}`,
    },
    detail: [
      {
        ...r.detail[0],
        id: 201,
        claimNumber: 'CLM-YR-0001',
        patientName: 'Edsger Dijkstra',
      },
    ],
    summary: { ...r.summary, totalClaims: 1 },
  };
}

function emptyReport(): ClaimReport {
  return {
    period: {
      type: 'QUARTER', year: YEAR, quarter: 3,
      from: `${YEAR}-07-01`, to: `${YEAR}-09-30`, label: `Q3 ${YEAR}`,
    },
    summary: {
      totalClaims: 0, totalCharged: 0, totalPaid: 0, outstandingReceivable: 0,
      byStatus: [], byType: [],
    },
    detail: [],
  };
}

/** A Response-like object good enough for the page's report + export paths. */
function jsonResponse(body: unknown, ok = true, status = 200): Response {
  const text = JSON.stringify(body);
  return {
    ok,
    status,
    headers: new Headers({ 'Content-Type': 'application/json' }),
    text: async () => text,
    json: async () => JSON.parse(text),
    blob: async () => new Blob([text], { type: 'application/json' }),
  } as unknown as Response;
}

function binaryResponse(bytes: string, filename: string, contentType: string): Response {
  return {
    ok: true,
    status: 200,
    headers: new Headers({
      'Content-Type': contentType,
      'Content-Disposition': `attachment; filename="${filename}"`,
    }),
    text: async () => bytes,
    blob: async () => new Blob([bytes], { type: contentType }),
  } as unknown as Response;
}

function renderReports(initialEntry = '/reports') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/claims/:id" element={<div>claim detail</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  window.localStorage.setItem('cms.token', 'test-token');
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
  // jsdom does not implement these; the export path uses them.
  if (!('createObjectURL' in URL)) {
    Object.defineProperty(URL, 'createObjectURL', { value: vi.fn(() => 'blob:mock'), configurable: true });
  } else {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock');
  }
  if (!('revokeObjectURL' in URL)) {
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), configurable: true });
  } else {
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
  }
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.localStorage.clear();
});

function reportUrls() {
  return fetchMock.mock.calls.map((c) => String(c[0]));
}

describe('S-5 AC1 — period selection loads summary + detail', () => {
  it('quarter: requests the quarter period and renders summary on top and detail below', async () => {
    fetchMock.mockResolvedValue(jsonResponse(quarterReport()));
    renderReports('/reports?period=QUARTER&year=' + YEAR + '&quarter=1');

    // Summary rollup renders (the four KPI stat cards).
    await waitFor(() => expect(document.querySelectorAll('.stat-label').length).toBe(4));
    const statLabels = Array.from(document.querySelectorAll('.stat-label')).map((n) => n.textContent);
    expect(statLabels).toEqual(['Claims', 'Charged', 'Paid', 'Outstanding AR']);
    // Summary breakdown tables.
    expect(screen.getByText('By status')).toBeInTheDocument();
    expect(screen.getByText('By place of service')).toBeInTheDocument();

    // Detail rows render below with the claim numbers.
    expect(screen.getByText('Claim detail')).toBeInTheDocument();
    expect(screen.getByText('CLM-Q1-0001')).toBeInTheDocument();
    expect(screen.getByText('CLM-Q1-0002')).toBeInTheDocument();

    // The report request carried the quarter period selection.
    const url = reportUrls().find((u) => u.includes('/api/reports/claims') && !u.includes('export'));
    expect(url).toBeDefined();
    expect(url).toContain('period=QUARTER');
    expect(url).toContain('year=' + YEAR);
    expect(url).toContain('quarter=1');
  });

  it('annual: switching to Full year drops the quarter param and reloads', async () => {
    fetchMock.mockImplementation((input: string) => {
      const u = String(input);
      if (u.includes('period=YEAR')) return Promise.resolve(jsonResponse(yearReport()));
      return Promise.resolve(jsonResponse(quarterReport()));
    });
    const user = userEvent.setup();
    renderReports('/reports?period=QUARTER&year=' + YEAR + '&quarter=2');

    await screen.findByText('CLM-Q1-0001');

    await user.selectOptions(screen.getByLabelText('Period'), 'YEAR');

    // Annual detail rows appear.
    expect(await screen.findByText('CLM-YR-0001')).toBeInTheDocument();

    // The annual request has no quarter param.
    const yearUrl = reportUrls().find((u) => u.includes('period=YEAR'));
    expect(yearUrl).toBeDefined();
    expect(yearUrl).not.toContain('quarter=');
    // The quarter selector is not shown for an annual report.
    expect(screen.queryByLabelText('Quarter')).not.toBeInTheDocument();
  });

  it('changing the quarter selection re-fetches with the new quarter', async () => {
    fetchMock.mockResolvedValue(jsonResponse(quarterReport()));
    const user = userEvent.setup();
    renderReports('/reports?period=QUARTER&year=' + YEAR + '&quarter=1');

    await screen.findByText('CLM-Q1-0001');
    await user.selectOptions(screen.getByLabelText('Quarter'), '3');

    await waitFor(() => {
      expect(reportUrls().some((u) => u.includes('quarter=3'))).toBe(true);
    });
  });
});

describe('S-5 AC2 — the three export buttons trigger a download per format', () => {
  const cases: { label: string; ext: string; contentType: string }[] = [
    { label: 'Export CSV', ext: 'csv', contentType: 'text/csv' },
    { label: 'Export Excel', ext: 'xlsx', contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
    { label: 'Export PDF', ext: 'pdf', contentType: 'application/pdf' },
  ];

  for (const { label, ext, contentType } of cases) {
    it(`${label} requests export.${ext} for the selected period and downloads`, async () => {
      fetchMock.mockImplementation((input: string) => {
        const u = String(input);
        if (u.includes(`export.${ext}`)) {
          return Promise.resolve(binaryResponse('BYTES', `claims-report.${ext}`, contentType));
        }
        return Promise.resolve(jsonResponse(quarterReport()));
      });
      const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
      const user = userEvent.setup();
      renderReports('/reports?period=QUARTER&year=' + YEAR + '&quarter=1');

      await screen.findByText('CLM-Q1-0001');
      await user.click(screen.getByRole('button', { name: label }));

      await waitFor(() => {
        const exportUrl = reportUrls().find((u) => u.includes(`export.${ext}`));
        expect(exportUrl).toBeDefined();
        expect(exportUrl).toContain('period=QUARTER');
        expect(exportUrl).toContain('quarter=1');
      });
      // A browser download was triggered (anchor click + object URL created).
      expect(URL.createObjectURL).toHaveBeenCalled();
      expect(clickSpy).toHaveBeenCalled();
    });
  }

  it('surfaces an export error without breaking the page', async () => {
    fetchMock.mockImplementation((input: string) => {
      const u = String(input);
      if (u.includes('export.csv')) {
        return Promise.resolve(jsonResponse({ detail: 'Export temporarily unavailable' }, false, 500));
      }
      return Promise.resolve(jsonResponse(quarterReport()));
    });
    const user = userEvent.setup();
    renderReports('/reports?period=QUARTER&year=' + YEAR + '&quarter=1');

    await screen.findByText('CLM-Q1-0001');
    await user.click(screen.getByRole('button', { name: 'Export CSV' }));

    expect(await screen.findByText('Export temporarily unavailable')).toBeInTheDocument();
    // The report (summary + detail) is still on screen.
    expect(screen.getByText('CLM-Q1-0001')).toBeInTheDocument();
  });
});

describe('S-5 AC3 — loading, empty and error states', () => {
  it('shows a loading state while the report is in flight', async () => {
    let resolve!: (r: Response) => void;
    fetchMock.mockReturnValue(new Promise<Response>((r) => { resolve = r; }));
    renderReports('/reports?period=QUARTER&year=' + YEAR + '&quarter=1');

    expect(screen.getByText(/Building the report/i)).toBeInTheDocument();

    resolve(jsonResponse(quarterReport()));
    await screen.findByText('CLM-Q1-0001');
    expect(screen.queryByText(/Building the report/i)).not.toBeInTheDocument();
  });

  it('shows an empty state for a period with no claims and no detail table', async () => {
    fetchMock.mockResolvedValue(jsonResponse(emptyReport()));
    renderReports('/reports?period=QUARTER&year=' + YEAR + '&quarter=3');

    expect(await screen.findByText(`No claims for Q3 ${YEAR}`)).toBeInTheDocument();
    // No detail table for an empty period.
    expect(screen.queryByText('Claim detail')).not.toBeInTheDocument();
  });

  it('shows an error alert when the report request fails', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ detail: 'Report service is down' }, false, 500));
    renderReports('/reports?period=QUARTER&year=' + YEAR + '&quarter=1');

    expect(await screen.findByText('Report service is down')).toBeInTheDocument();
    // No summary/detail on an errored load.
    expect(screen.queryByText('Claim detail')).not.toBeInTheDocument();
  });
});
