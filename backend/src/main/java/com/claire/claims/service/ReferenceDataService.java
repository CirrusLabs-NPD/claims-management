package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.ClaimDtos.PageResponse;
import com.claire.claims.dto.ReferenceDtos.*;
import com.claire.claims.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD for patients, payers, providers and coverage. */
@Service
public class ReferenceDataService {

    private final PatientRepository patients;
    private final PayerRepository payers;
    private final ProviderRepository providers;
    private final InsurancePolicyRepository policies;
    private final ClaimMapper mapper;

    public ReferenceDataService(PatientRepository patients, PayerRepository payers,
                                ProviderRepository providers, InsurancePolicyRepository policies,
                                ClaimMapper mapper) {
        this.patients = patients;
        this.payers = payers;
        this.providers = providers;
        this.policies = policies;
        this.mapper = mapper;
    }

    // ----- patients -------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<PatientSummary> searchPatients(String q, Pageable pageable) {
        Page<Patient> page;
        if (q == null || q.isBlank()) {
            page = patients.findAll(pageable);
        } else {
            String term = q.trim();
            page = patients
                .findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrMrnContainingIgnoreCase(
                    term, term, term, pageable);
        }
        return new PageResponse<>(page.getContent().stream().map(mapper::toPatientSummary).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatient(Long id) {
        Patient p = patients.findById(id).orElseThrow(() -> new NotFoundException("Patient", id));
        return mapper.toPatient(p, policies.findByPatientIdOrderByPriorityAsc(id));
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> policiesForPatient(Long patientId) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("Patient", patientId);
        }
        return policies.findByPatientIdOrderByPriorityAsc(patientId)
                .stream().map(mapper::toPolicy).toList();
    }

    @Transactional
    public PatientResponse createPatient(PatientRequest r) {
        if (patients.existsByMrn(r.mrn())) {
            throw new ConflictException("A patient with MRN " + r.mrn() + " already exists");
        }
        Patient p = new Patient();
        copy(r, p);
        Patient saved = patients.save(p);
        return mapper.toPatient(saved, List.of());
    }

    @Transactional
    public PatientResponse updatePatient(Long id, PatientRequest r) {
        Patient p = patients.findById(id).orElseThrow(() -> new NotFoundException("Patient", id));
        if (!p.getMrn().equals(r.mrn()) && patients.existsByMrn(r.mrn())) {
            throw new ConflictException("A patient with MRN " + r.mrn() + " already exists");
        }
        copy(r, p);
        Patient saved = patients.save(p);
        return mapper.toPatient(saved, policies.findByPatientIdOrderByPriorityAsc(id));
    }

    private void copy(PatientRequest r, Patient p) {
        p.setMrn(r.mrn());
        p.setFirstName(r.firstName());
        p.setLastName(r.lastName());
        p.setDateOfBirth(r.dateOfBirth());
        p.setPhone(r.phone());
        p.setEmail(r.email());
        p.setAddressLine1(r.addressLine1());
        p.setCity(r.city());
        p.setState(r.state());
        p.setPostalCode(r.postalCode());
    }

    // ----- payers ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PayerResponse> listPayers() {
        return payers.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(mapper::toPayer).toList();
    }

    @Transactional(readOnly = true)
    public PayerResponse getPayer(Long id) {
        return mapper.toPayer(payers.findById(id).orElseThrow(() -> new NotFoundException("Payer", id)));
    }

    @Transactional
    public PayerResponse createPayer(PayerRequest r) {
        if (payers.existsByPayerCode(r.payerCode())) {
            throw new ConflictException("Payer code " + r.payerCode() + " is already in use");
        }
        Payer p = new Payer();
        copy(r, p);
        return mapper.toPayer(payers.save(p));
    }

    @Transactional
    public PayerResponse updatePayer(Long id, PayerRequest r) {
        Payer p = payers.findById(id).orElseThrow(() -> new NotFoundException("Payer", id));
        if (!p.getPayerCode().equals(r.payerCode()) && payers.existsByPayerCode(r.payerCode())) {
            throw new ConflictException("Payer code " + r.payerCode() + " is already in use");
        }
        copy(r, p);
        return mapper.toPayer(payers.save(p));
    }

    private void copy(PayerRequest r, Payer p) {
        p.setPayerCode(r.payerCode());
        p.setName(r.name());
        p.setPlanType(r.planType());
        p.setClaimsAddress(r.claimsAddress());
        p.setPhone(r.phone());
        if (r.active() != null) p.setActive(r.active());
    }

    // ----- providers ------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ProviderResponse> listProviders() {
        return providers.findAll().stream()
                .sorted((a, b) -> a.getLastName().compareToIgnoreCase(b.getLastName()))
                .map(mapper::toProvider).toList();
    }

    @Transactional(readOnly = true)
    public ProviderResponse getProvider(Long id) {
        return mapper.toProvider(providers.findById(id)
                .orElseThrow(() -> new NotFoundException("Provider", id)));
    }

    @Transactional
    public ProviderResponse createProvider(ProviderRequest r) {
        if (providers.existsByNpi(r.npi())) {
            throw new ConflictException("NPI " + r.npi() + " is already registered");
        }
        Provider p = new Provider();
        copy(r, p);
        return mapper.toProvider(providers.save(p));
    }

    @Transactional
    public ProviderResponse updateProvider(Long id, ProviderRequest r) {
        Provider p = providers.findById(id).orElseThrow(() -> new NotFoundException("Provider", id));
        if (!p.getNpi().equals(r.npi()) && providers.existsByNpi(r.npi())) {
            throw new ConflictException("NPI " + r.npi() + " is already registered");
        }
        copy(r, p);
        return mapper.toProvider(providers.save(p));
    }

    private void copy(ProviderRequest r, Provider p) {
        p.setNpi(r.npi());
        p.setFirstName(r.firstName());
        p.setLastName(r.lastName());
        p.setSpecialty(r.specialty());
        p.setTaxId(r.taxId());
        if (r.active() != null) p.setActive(r.active());
    }

    // ----- policies -------------------------------------------------------

    @Transactional
    public PolicyResponse createPolicy(PolicyRequest r) {
        validatePolicyDates(r);
        InsurancePolicy pol = new InsurancePolicy();
        copy(r, pol);
        return mapper.toPolicy(policies.save(pol));
    }

    @Transactional
    public PolicyResponse updatePolicy(Long id, PolicyRequest r) {
        InsurancePolicy pol = policies.findById(id)
                .orElseThrow(() -> new NotFoundException("InsurancePolicy", id));
        validatePolicyDates(r);
        copy(r, pol);
        return mapper.toPolicy(policies.save(pol));
    }

    private void validatePolicyDates(PolicyRequest r) {
        if (r.terminationDate() != null && r.terminationDate().isBefore(r.effectiveDate())) {
            throw new BusinessRuleException("terminationDate cannot be before effectiveDate");
        }
    }

    private void copy(PolicyRequest r, InsurancePolicy pol) {
        Patient patient = patients.findById(r.patientId())
                .orElseThrow(() -> new NotFoundException("Patient", r.patientId()));
        Payer payer = payers.findById(r.payerId())
                .orElseThrow(() -> new NotFoundException("Payer", r.payerId()));
        pol.setPatient(patient);
        pol.setPayer(payer);
        pol.setMemberId(r.memberId());
        pol.setGroupNumber(r.groupNumber());
        pol.setPriority(r.priority());
        pol.setEffectiveDate(r.effectiveDate());
        pol.setTerminationDate(r.terminationDate());
    }
}
