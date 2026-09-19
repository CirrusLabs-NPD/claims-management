import type { ReactNode } from 'react';
import type { ClaimStatus } from '../api/types';

export function StatusBadge({ status }: { status: ClaimStatus }) {
  return <span className={`badge badge-${status}`}>{status.replace('_', ' ')}</span>;
}

export function Money({ value, muted }: { value: number | null | undefined; muted?: boolean }) {
  const n = value ?? 0;
  return (
    <span className={muted && n === 0 ? 'faint' : undefined}>
      {n.toLocaleString('en-US', { style: 'currency', currency: 'USD' })}
    </span>
  );
}

export function DateText({ value }: { value: string | null | undefined }) {
  if (!value) return <span className="faint">&mdash;</span>;
  const d = new Date(value.length === 10 ? `${value}T00:00:00` : value);
  if (Number.isNaN(d.getTime())) return <span className="faint">&mdash;</span>;
  return <span className="nowrap">{d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })}</span>;
}

export function DateTimeText({ value }: { value: string | null | undefined }) {
  if (!value) return <span className="faint">&mdash;</span>;
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return <span className="faint">&mdash;</span>;
  return <span className="nowrap">{d.toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' })}</span>;
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="empty">
      <div className="empty-title">{title}</div>
      {hint && <div>{hint}</div>}
    </div>
  );
}

export function Loading({ label = 'Loading' }: { label?: string }) {
  return <div className="loading">{label}&hellip;</div>;
}

export function ErrorAlert({ message }: { message: string | null }) {
  if (!message) return null;
  return <div className="alert alert-error">{message}</div>;
}

export function Card({ title, actions, children, tight }:
  { title?: ReactNode; actions?: ReactNode; children: ReactNode; tight?: boolean }) {
  return (
    <div className="card">
      {(title || actions) && (
        <div className="card-head">
          <h2>{title}</h2>
          {actions}
        </div>
      )}
      <div className={tight ? 'card-body tight' : 'card-body'}>{children}</div>
    </div>
  );
}

export function Modal({ title, children, footer, onClose }:
  { title: string; children: ReactNode; footer: ReactNode; onClose: () => void }) {
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head"><h2>{title}</h2></div>
        <div className="modal-body">{children}</div>
        <div className="modal-foot">{footer}</div>
      </div>
    </div>
  );
}

export function Pagination({ page, totalPages, totalElements, onChange }:
  { page: number; totalPages: number; totalElements: number; onChange: (p: number) => void }) {
  return (
    <div className="pagination">
      <span>
        {totalElements.toLocaleString()} result{totalElements === 1 ? '' : 's'}
        {totalPages > 0 && <> &middot; page {page + 1} of {totalPages}</>}
      </span>
      <div className="btn-row">
        <button className="btn btn-secondary btn-sm" disabled={page <= 0}
                onClick={() => onChange(page - 1)}>Previous</button>
        <button className="btn btn-secondary btn-sm" disabled={page + 1 >= totalPages}
                onClick={() => onChange(page + 1)}>Next</button>
      </div>
    </div>
  );
}
