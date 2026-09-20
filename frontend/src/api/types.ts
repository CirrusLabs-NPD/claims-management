export type ClaimStatus =
  | 'DRAFT' | 'SUBMITTED' | 'ACCEPTED' | 'REJECTED'
  | 'PARTIALLY_PAID' | 'PAID' | 'DENIED' | 'APPEALED' | 'VOID';

export type UserRole = 'ADMIN' | 'BILLER' | 'VIEWER';

export type PlanType =
  | 'COMMERCIAL' | 'MEDICARE' | 'MEDICAID' | 'TRICARE' | 'WORKERS_COMP' | 'SELF_PAY';

export type PolicyPriority = 'PRIMARY' | 'SECONDARY' | 'TERTIARY';

export interface LoginResponse {
  token: string;
  username: string;
  fullName: string;
  role: UserRole;
  expiresAt: string;
}

export interface CurrentUser {
  username: string;
  fullName: string;
  role: UserRole;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface ClaimListItem {
  id: number;
  claimNumber: string;
  status: ClaimStatus;
  patientName: string;
  patientMrn: string;
  payerName: string;
  providerName: string;
  serviceDateFrom: string;
  serviceDateTo: string;
  totalCharge: number;
  paidAmount: number;
  outstanding: number;
  submittedAt: string | null;
  createdAt: string;
}

export interface ClaimLine {
  id: number;
  lineNumber: number;
  cptCode: string;
  modifiers: string | null;
  serviceDate: string;
  units: number;
  chargeAmount: number;
  allowedAmount: number;
  paidAmount: number;
  description: string | null;
}

export interface ClaimDiagnosis {
  id: number;
  icd10Code: string;
  description: string | null;
  sequenceNo: number;
}

export interface Claim {
  id: number;
  claimNumber: string;
  status: ClaimStatus;
  statusDescription: string;
  patientId: number;
  patientName: string;
  patientMrn: string;
  providerId: number;
  providerName: string;
  providerNpi: string;
  payerId: number;
  payerName: string;
  policyId: number | null;
  policyMemberId: string | null;
  serviceDateFrom: string;
  serviceDateTo: string;
  placeOfService: string;
  totalCharge: number;
  allowedAmount: number;
  paidAmount: number;
  patientResponsibility: number;
  outstanding: number;
  notes: string | null;
  submittedAt: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  editable: boolean;
  allowedTransitions: ClaimStatus[];
  lines: ClaimLine[];
  diagnoses: ClaimDiagnosis[];
}

export interface StatusHistoryEntry {
  id: number;
  fromStatus: ClaimStatus | null;
  toStatus: ClaimStatus;
  reason: string | null;
  changedBy: string;
  changedAt: string;
}

export interface StatusBucket {
  status: ClaimStatus;
  description: string;
  count: number;
  totalCharge: number;
  paidAmount: number;
}

export interface ClaimSummary {
  totalClaims: number;
  totalCharged: number;
  totalPaid: number;
  outstandingReceivable: number;
  byStatus: StatusBucket[];
}

// Claims report by quarter or year — mirrors backend ReportDtos.
export type ReportPeriodType = 'QUARTER' | 'YEAR';

export interface ReportPeriod {
  type: ReportPeriodType;
  year: number;
  quarter: number | null;
  from: string;
  to: string;
  label: string;
}

export interface TypeBucket {
  code: string;
  count: number;
  totalCharge: number;
  paidAmount: number;
}

export interface ReportSummary {
  totalClaims: number;
  totalCharged: number;
  totalPaid: number;
  outstandingReceivable: number;
  byStatus: StatusBucket[];
  byType: TypeBucket[];
}

export interface ClaimReport {
  period: ReportPeriod;
  summary: ReportSummary;
  detail: ClaimListItem[];
}

export interface PatientSummary {
  id: number;
  mrn: string;
  displayName: string;
  dateOfBirth: string;
}

export interface Policy {
  id: number;
  patientId: number;
  payerId: number;
  payerName: string;
  memberId: string;
  groupNumber: string | null;
  priority: PolicyPriority;
  effectiveDate: string;
  terminationDate: string | null;
  activeToday: boolean;
}

export interface Patient {
  id: number;
  mrn: string;
  firstName: string;
  lastName: string;
  displayName: string;
  dateOfBirth: string;
  phone: string | null;
  email: string | null;
  addressLine1: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  policies: Policy[];
}

export interface Payer {
  id: number;
  payerCode: string;
  name: string;
  planType: PlanType;
  claimsAddress: string | null;
  phone: string | null;
  active: boolean;
}

export interface Provider {
  id: number;
  npi: string;
  firstName: string;
  lastName: string;
  displayName: string;
  specialty: string | null;
  taxId: string | null;
  active: boolean;
}

export interface ClaimLineInput {
  cptCode: string;
  modifiers?: string | null;
  serviceDate: string;
  units: number;
  chargeAmount: number;
  description?: string | null;
}

export interface ClaimDiagnosisInput {
  icd10Code: string;
  description?: string | null;
  sequenceNo: number;
}

export interface ClaimInput {
  patientId: number;
  providerId: number;
  payerId: number;
  policyId?: number | null;
  serviceDateFrom: string;
  serviceDateTo: string;
  placeOfService: string;
  notes?: string | null;
  lines: ClaimLineInput[];
  diagnoses: ClaimDiagnosisInput[];
}

export interface TransitionInput {
  targetStatus: ClaimStatus;
  reason?: string | null;
  paidAmount?: number | null;
  allowedAmount?: number | null;
}

export interface StatusTransitionMap {
  transitions: Record<string, string[]>;
  descriptions: Record<string, string>;
  terminal: string[];
}
