-- =====================================================================
-- Demo seed data.
-- Deterministic IDs so the UI, docs and demo script can reference rows.
-- Sequences are re-synced at the bottom.
--
-- Demo logins (BCrypt, cost 10):
--   admin  / admin123   ADMIN
--   biller / biller123  BILLER
--   dana   / biller123  BILLER
--   viewer / viewer123  VIEWER
-- =====================================================================

INSERT INTO app_user (id, username, password_hash, full_name, role) VALUES
 (1, 'admin',  '$2b$10$Jgdqjs.9hkFo3yRnheNQKus/OAoNOd0UjxpIYFLJ5aRhchwcQGc4O', 'Avery Admin',    'ADMIN'),
 (2, 'biller', '$2b$10$/UEXiWeNH.TAFQqRbu2mRO5AcDvKSQcCtpbWFIqxXm8iZHRnRnT1u', 'Bianca Reyes',   'BILLER'),
 (3, 'dana',   '$2b$10$/UEXiWeNH.TAFQqRbu2mRO5AcDvKSQcCtpbWFIqxXm8iZHRnRnT1u', 'Dana Okafor',    'BILLER'),
 (4, 'viewer', '$2b$10$0Qzgsk19gP.bJ4.dKYfKEutwh0oeFsE3pBm8HqGAdVhLhEegeZWDi', 'Val Chen',       'VIEWER');

-- ---------------------------------------------------------------------
INSERT INTO payer (id, payer_code, name, plan_type, claims_address, phone) VALUES
 (1, 'BCBS001',  'Blue Cross Blue Shield of Illinois', 'COMMERCIAL',   'PO Box 805107, Chicago, IL 60680',   '800-555-0110'),
 (2, 'AETNA01',  'Aetna Health Plans',                 'COMMERCIAL',   'PO Box 14079, Lexington, KY 40512',  '800-555-0122'),
 (3, 'MCARE01',  'Medicare Part B - Novitas',          'MEDICARE',     'PO Box 3096, Mechanicsburg, PA 17055','800-555-0133'),
 (4, 'MCAID01',  'Illinois Medicaid (HFS)',            'MEDICAID',     'PO Box 19118, Springfield, IL 62794', '800-555-0144'),
 (5, 'UHC001',   'UnitedHealthcare',                   'COMMERCIAL',   'PO Box 30555, Salt Lake City, UT 84130','800-555-0155');

INSERT INTO provider (id, npi, first_name, last_name, specialty, tax_id) VALUES
 (1, '1043205678', 'Maya',   'Lindqvist', 'Family Medicine',  '36-4455121'),
 (2, '1558829901', 'Omar',   'Haddad',    'Internal Medicine','36-4455122'),
 (3, '1922113344', 'Priya',  'Raman',     'Orthopedics',      '36-4455123'),
 (4, '1730045566', 'Ellis',  'Brannigan', 'Cardiology',       '36-4455124');

INSERT INTO patient (id, mrn, first_name, last_name, date_of_birth, phone, email, address_line1, city, state, postal_code) VALUES
 (1, 'MRN-100001', 'Robert',  'Castellano', '1958-03-14', '312-555-0181', 'r.castellano@example.com', '812 W Cermak Rd',   'Chicago',   'IL', '60608'),
 (2, 'MRN-100002', 'Amara',   'Njoku',      '1991-11-02', '312-555-0182', 'a.njoku@example.com',      '4410 N Sheridan Rd','Chicago',   'IL', '60640'),
 (3, 'MRN-100003', 'Henrietta','Voss',      '1946-07-29', '847-555-0183', 'h.voss@example.com',       '19 Greenbay Rd',    'Evanston',  'IL', '60201'),
 (4, 'MRN-100004', 'Dmitri',  'Sokolov',    '1984-01-19', '773-555-0184', 'd.sokolov@example.com',    '2233 W Division St','Chicago',   'IL', '60622'),
 (5, 'MRN-100005', 'Lucia',   'Ferreira',   '2003-06-08', '773-555-0185', 'l.ferreira@example.com',   '5501 S Kedzie Ave', 'Chicago',   'IL', '60629'),
 (6, 'MRN-100006', 'Theodore','Okonjo',     '1972-09-23', '630-555-0186', 't.okonjo@example.com',     '77 Roosevelt Rd',   'Glen Ellyn','IL', '60137'),
 (7, 'MRN-100007', 'Saoirse', 'Lynch',      '1999-04-30', '312-555-0187', 's.lynch@example.com',      '1301 S Wabash Ave', 'Chicago',   'IL', '60605'),
 (8, 'MRN-100008', 'Bao',     'Tran',       '1966-12-11', '847-555-0188', 'b.tran@example.com',       '900 Skokie Blvd',   'Northbrook','IL', '60062');

INSERT INTO insurance_policy (id, patient_id, payer_id, member_id, group_number, priority, effective_date, termination_date) VALUES
 (1, 1, 3, 'MB1A2B3C4D5', NULL,      'PRIMARY',   '2023-04-01', NULL),
 (2, 1, 1, 'XOF889201',   'GRP-4410','SECONDARY', '2023-04-01', NULL),
 (3, 2, 1, 'XOF773115',   'GRP-2210','PRIMARY',   '2024-01-01', NULL),
 (4, 3, 3, 'MB9Z8Y7X6W',  NULL,      'PRIMARY',   '2011-08-01', NULL),
 (5, 4, 2, 'AET5512093',  'GRP-8871','PRIMARY',   '2025-01-01', NULL),
 (6, 5, 4, 'IL-MCD-44219',NULL,      'PRIMARY',   '2024-09-01', NULL),
 (7, 6, 5, 'UHC90114427', 'GRP-1188','PRIMARY',   '2024-06-01', NULL),
 (8, 7, 1, 'XOF551002',   'GRP-2210','PRIMARY',   '2025-03-01', NULL),
 (9, 8, 2, 'AET7781140',  'GRP-6620','PRIMARY',   '2022-02-01', NULL),
 -- Terminated policy, kept so the "active coverage on the service date"
 -- submit rule has something to fail against during a demo.
 (10,3, 1, 'XOF110045',   'GRP-3390','SECONDARY', '2019-01-01', '2024-12-31');

-- ---------------------------------------------------------------------
-- Claims, spread across every status so the demo has something in each bucket.
-- ---------------------------------------------------------------------
INSERT INTO claim (id, claim_number, patient_id, provider_id, payer_id, policy_id, status,
                   service_date_from, service_date_to, place_of_service,
                   total_charge, allowed_amount, paid_amount, patient_responsibility,
                   notes, submitted_at, created_by, created_at) VALUES
 (1,  'CLM-2026-000001', 1, 2, 3, 1, 'PAID',           '2026-06-02','2026-06-02','11', 385.00, 246.40, 197.12, 49.28, 'Routine follow-up, hypertension + diabetes.', NOW() - INTERVAL '96 days', 'biller', NOW() - INTERVAL '100 days'),
 (2,  'CLM-2026-000002', 2, 1, 1, 3, 'PAID',           '2026-06-14','2026-06-14','11', 210.00, 168.00, 134.40, 33.60, 'Acute pharyngitis.',                          NOW() - INTERVAL '84 days', 'biller', NOW() - INTERVAL '86 days'),
 (3,  'CLM-2026-000003', 3, 4, 3, 4, 'PARTIALLY_PAID', '2026-07-08','2026-07-08','11', 940.00, 612.00, 400.00, 212.00, 'EKG plus office visit. Partial remittance received.', NOW() - INTERVAL '62 days','dana',  NOW() - INTERVAL '65 days'),
 (4,  'CLM-2026-000004', 4, 3, 2, 5, 'DENIED',         '2026-07-21','2026-07-21','11', 1450.00,  0.00,   0.00,   0.00, 'Knee injection. Denied: no prior authorization on file.', NOW() - INTERVAL '50 days','biller', NOW() - INTERVAL '54 days'),
 (5,  'CLM-2026-000005', 5, 1, 4, 6, 'ACCEPTED',       '2026-08-05','2026-08-05','11', 295.00, 176.00,   0.00,   0.00, 'New patient visit with immunization.',        NOW() - INTERVAL '36 days', 'biller', NOW() - INTERVAL '38 days'),
 (6,  'CLM-2026-000006', 6, 2, 5, 7, 'ACCEPTED',       '2026-08-12','2026-08-12','11', 520.00, 358.00,   0.00,   0.00, 'Comprehensive metabolic panel + lipid workup.', NOW() - INTERVAL '31 days','dana',  NOW() - INTERVAL '33 days'),
 (7,  'CLM-2026-000007', 7, 1, 1, 8, 'REJECTED',       '2026-08-19','2026-08-19','11', 180.00,   0.00,   0.00,   0.00, 'Rejected at clearinghouse: subscriber ID mismatch.', NOW() - INTERVAL '25 days','biller', NOW() - INTERVAL '27 days'),
 (8,  'CLM-2026-000008', 8, 4, 2, 9, 'SUBMITTED',      '2026-08-26','2026-08-26','11', 675.00,   0.00,   0.00,   0.00, 'Cardiology consult.',                          NOW() - INTERVAL '18 days','dana',  NOW() - INTERVAL '20 days'),
 (9,  'CLM-2026-000009', 2, 3, 1, 3, 'SUBMITTED',      '2026-09-01','2026-09-01','11', 1120.00,  0.00,   0.00,   0.00, 'Fracture care, left tibia.',                   NOW() - INTERVAL '11 days','biller', NOW() - INTERVAL '13 days'),
 (10, 'CLM-2026-000010', 1, 2, 3, 1, 'DRAFT',          '2026-09-09','2026-09-09','11', 265.00,   0.00,   0.00,   0.00, 'Quarterly diabetic check. Awaiting coding review.', NULL,                  'biller', NOW() - INTERVAL '6 days'),
 (11, 'CLM-2026-000011', 6, 1, 5, 7, 'DRAFT',          '2026-09-11','2026-09-11','11', 150.00,   0.00,   0.00,   0.00, 'Physical therapy re-eval.',                    NULL,                       'dana',   NOW() - INTERVAL '4 days'),
 (12, 'CLM-2026-000012', 4, 3, 2, 5, 'VOID',           '2026-07-21','2026-07-21','11', 1450.00,  0.00,   0.00,   0.00, 'Duplicate of CLM-2026-000004. Voided.',        NULL,                       'admin',  NOW() - INTERVAL '49 days');

INSERT INTO claim_line (claim_id, line_number, cpt_code, modifiers, service_date, units, charge_amount, allowed_amount, paid_amount, description) VALUES
 (1, 1, '99214', NULL,  '2026-06-02', 1, 245.00, 156.40, 125.12, 'Office visit, established patient, moderate complexity'),
 (1, 2, '80053', NULL,  '2026-06-02', 1, 120.00,  75.00,  60.00, 'Comprehensive metabolic panel'),
 (1, 3, '36415', NULL,  '2026-06-02', 1,  20.00,  15.00,  12.00, 'Venipuncture'),
 (2, 1, '99213', NULL,  '2026-06-14', 1, 185.00, 148.00, 118.40, 'Office visit, established patient, low complexity'),
 (2, 2, '87804', NULL,  '2026-06-14', 1,  25.00,  20.00,  16.00, 'Rapid influenza assay'),
 (3, 1, '99215', NULL,  '2026-07-08', 1, 340.00, 232.00, 160.00, 'Office visit, established patient, high complexity'),
 (3, 2, '93000', NULL,  '2026-07-08', 1, 165.00, 110.00,  70.00, 'Electrocardiogram, complete'),
 (3, 3, '71046', NULL,  '2026-07-08', 1, 435.00, 270.00, 170.00, 'Chest X-ray, 2 views'),
 (4, 1, '20610', 'RT',  '2026-07-21', 1, 850.00,   0.00,   0.00, 'Arthrocentesis, major joint'),
 (4, 2, 'J1885', NULL,  '2026-07-21',10, 600.00,   0.00,   0.00, 'Ketorolac tromethamine injection, 15 mg'),
 (5, 1, '99203', NULL,  '2026-08-05', 1, 245.00, 148.00,   0.00, 'Office visit, new patient, low complexity'),
 (5, 2, '90471', NULL,  '2026-08-05', 1,  50.00,  28.00,   0.00, 'Immunization administration'),
 (6, 1, '99214', NULL,  '2026-08-12', 1, 245.00, 156.00,   0.00, 'Office visit, established patient, moderate complexity'),
 (6, 2, '80053', NULL,  '2026-08-12', 1, 120.00,  75.00,   0.00, 'Comprehensive metabolic panel'),
 (6, 3, '80061', NULL,  '2026-08-12', 1, 155.00, 127.00,   0.00, 'Lipid panel'),
 (7, 1, '99212', NULL,  '2026-08-19', 1, 180.00,   0.00,   0.00, 'Office visit, established patient, straightforward'),
 (8, 1, '99244', NULL,  '2026-08-26', 1, 510.00,   0.00,   0.00, 'Office consultation, moderate-high complexity'),
 (8, 2, '93000', NULL,  '2026-08-26', 1, 165.00,   0.00,   0.00, 'Electrocardiogram, complete'),
 (9, 1, '27750', 'LT',  '2026-09-01', 1, 980.00,   0.00,   0.00, 'Closed treatment of tibial shaft fracture'),
 (9, 2, '99213', '25',  '2026-09-01', 1, 140.00,   0.00,   0.00, 'Office visit, separately identifiable'),
 (10,1, '99214', NULL,  '2026-09-09', 1, 245.00,   0.00,   0.00, 'Office visit, established patient, moderate complexity'),
 (10,2, '36415', NULL,  '2026-09-09', 1,  20.00,   0.00,   0.00, 'Venipuncture'),
 (11,1, '97110', NULL,  '2026-09-11', 2, 150.00,   0.00,   0.00, 'Therapeutic exercise, 15 minutes'),
 (12,1, '20610', 'RT',  '2026-07-21', 1, 850.00,   0.00,   0.00, 'Arthrocentesis, major joint'),
 (12,2, 'J1885', NULL,  '2026-07-21',10, 600.00,   0.00,   0.00, 'Ketorolac tromethamine injection, 15 mg');

INSERT INTO claim_diagnosis (claim_id, icd10_code, description, sequence_no) VALUES
 (1, 'E11.9',   'Type 2 diabetes mellitus without complications', 1),
 (1, 'I10',     'Essential (primary) hypertension',               2),
 (2, 'J02.9',   'Acute pharyngitis, unspecified',                 1),
 (3, 'I50.32',  'Chronic diastolic heart failure',                1),
 (3, 'R06.02',  'Shortness of breath',                            2),
 (4, 'M17.11',  'Unilateral primary osteoarthritis, right knee',  1),
 (5, 'Z00.00',  'General adult medical examination',              1),
 (5, 'Z23',     'Encounter for immunization',                     2),
 (6, 'E78.5',   'Hyperlipidemia, unspecified',                    1),
 (6, 'E11.9',   'Type 2 diabetes mellitus without complications', 2),
 (7, 'J06.9',   'Acute upper respiratory infection, unspecified', 1),
 (8, 'R07.9',   'Chest pain, unspecified',                        1),
 (9, 'S82.201A','Unspecified fracture of shaft of left tibia',    1),
 (10,'E11.9',   'Type 2 diabetes mellitus without complications', 1),
 (11,'M54.5',   'Low back pain',                                  1),
 (12,'M17.11',  'Unilateral primary osteoarthritis, right knee',  1);

INSERT INTO claim_status_history (claim_id, from_status, to_status, reason, changed_by, changed_at) VALUES
 (1, NULL,        'DRAFT',          'Claim created',                              'biller', NOW() - INTERVAL '100 days'),
 (1, 'DRAFT',     'SUBMITTED',      'Submitted to Medicare Part B',               'biller', NOW() - INTERVAL '96 days'),
 (1, 'SUBMITTED', 'ACCEPTED',       'Accepted at clearinghouse',                  'biller', NOW() - INTERVAL '94 days'),
 (1, 'ACCEPTED',  'PAID',           'Remittance posted in full',                  'biller', NOW() - INTERVAL '78 days'),
 (2, NULL,        'DRAFT',          'Claim created',                              'biller', NOW() - INTERVAL '86 days'),
 (2, 'DRAFT',     'SUBMITTED',      'Submitted to BCBS IL',                       'biller', NOW() - INTERVAL '84 days'),
 (2, 'SUBMITTED', 'ACCEPTED',       'Accepted',                                   'biller', NOW() - INTERVAL '82 days'),
 (2, 'ACCEPTED',  'PAID',           'Remittance posted in full',                  'biller', NOW() - INTERVAL '70 days'),
 (3, NULL,        'DRAFT',          'Claim created',                              'dana',   NOW() - INTERVAL '65 days'),
 (3, 'DRAFT',     'SUBMITTED',      'Submitted to Medicare Part B',               'dana',   NOW() - INTERVAL '62 days'),
 (3, 'SUBMITTED', 'ACCEPTED',       'Accepted',                                   'dana',   NOW() - INTERVAL '60 days'),
 (3, 'ACCEPTED',  'PARTIALLY_PAID', 'Partial payment: X-ray reduced to fee schedule','dana',NOW() - INTERVAL '44 days'),
 (4, NULL,        'DRAFT',          'Claim created',                              'biller', NOW() - INTERVAL '54 days'),
 (4, 'DRAFT',     'SUBMITTED',      'Submitted to Aetna',                         'biller', NOW() - INTERVAL '50 days'),
 (4, 'SUBMITTED', 'ACCEPTED',       'Accepted',                                   'biller', NOW() - INTERVAL '48 days'),
 (4, 'ACCEPTED',  'DENIED',         'CO-197 precertification absent',             'biller', NOW() - INTERVAL '35 days'),
 (5, NULL,        'DRAFT',          'Claim created',                              'biller', NOW() - INTERVAL '38 days'),
 (5, 'DRAFT',     'SUBMITTED',      'Submitted to IL Medicaid',                   'biller', NOW() - INTERVAL '36 days'),
 (5, 'SUBMITTED', 'ACCEPTED',       'Accepted',                                   'biller', NOW() - INTERVAL '34 days'),
 (6, NULL,        'DRAFT',          'Claim created',                              'dana',   NOW() - INTERVAL '33 days'),
 (6, 'DRAFT',     'SUBMITTED',      'Submitted to UnitedHealthcare',              'dana',   NOW() - INTERVAL '31 days'),
 (6, 'SUBMITTED', 'ACCEPTED',       'Accepted',                                   'dana',   NOW() - INTERVAL '29 days'),
 (7, NULL,        'DRAFT',          'Claim created',                              'biller', NOW() - INTERVAL '27 days'),
 (7, 'DRAFT',     'SUBMITTED',      'Submitted to BCBS IL',                       'biller', NOW() - INTERVAL '25 days'),
 (7, 'SUBMITTED', 'REJECTED',       'Subscriber ID does not match payer records', 'biller', NOW() - INTERVAL '24 days'),
 (8, NULL,        'DRAFT',          'Claim created',                              'dana',   NOW() - INTERVAL '20 days'),
 (8, 'DRAFT',     'SUBMITTED',      'Submitted to Aetna',                         'dana',   NOW() - INTERVAL '18 days'),
 (9, NULL,        'DRAFT',          'Claim created',                              'biller', NOW() - INTERVAL '13 days'),
 (9, 'DRAFT',     'SUBMITTED',      'Submitted to BCBS IL',                       'biller', NOW() - INTERVAL '11 days'),
 (10,NULL,        'DRAFT',          'Claim created',                              'biller', NOW() - INTERVAL '6 days'),
 (11,NULL,        'DRAFT',          'Claim created',                              'dana',   NOW() - INTERVAL '4 days'),
 (12,NULL,        'DRAFT',          'Claim created',                              'admin',  NOW() - INTERVAL '49 days'),
 (12,'DRAFT',     'VOID',           'Duplicate submission, voided before sending', 'admin',  NOW() - INTERVAL '49 days');

-- ---------------------------------------------------------------------
-- Re-sync sequences after explicit-ID inserts.
-- ---------------------------------------------------------------------
SELECT setval('app_user_id_seq',             (SELECT MAX(id) FROM app_user));
SELECT setval('payer_id_seq',                (SELECT MAX(id) FROM payer));
SELECT setval('provider_id_seq',             (SELECT MAX(id) FROM provider));
SELECT setval('patient_id_seq',              (SELECT MAX(id) FROM patient));
SELECT setval('insurance_policy_id_seq',     (SELECT MAX(id) FROM insurance_policy));
SELECT setval('claim_id_seq',                (SELECT MAX(id) FROM claim));
SELECT setval('claim_line_id_seq',           (SELECT MAX(id) FROM claim_line));
SELECT setval('claim_diagnosis_id_seq',      (SELECT MAX(id) FROM claim_diagnosis));
SELECT setval('claim_status_history_id_seq', (SELECT MAX(id) FROM claim_status_history));
