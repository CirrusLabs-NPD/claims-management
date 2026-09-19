package com.claire.claims.web;

import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.domain.PlanType;
import com.claire.claims.domain.PolicyPriority;
import com.claire.claims.dto.ClaimDtos.StatusTransitionMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes enums and the workflow as data.
 *
 * The React client reads the state machine from here rather than duplicating
 * it, so the button set on a claim can never disagree with what the API will
 * actually accept.
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    @GetMapping("/status-transitions")
    public StatusTransitionMap statusTransitions() {
        Map<String, List<String>> transitions = new LinkedHashMap<>();
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (ClaimStatus s : ClaimStatus.values()) {
            transitions.put(s.name(), s.allowedNext().stream().map(Enum::name).toList());
            descriptions.put(s.name(), s.getDescription());
        }
        List<String> terminal = Arrays.stream(ClaimStatus.values())
                .filter(ClaimStatus::isTerminal).map(Enum::name).toList();
        return new StatusTransitionMap(transitions, descriptions, terminal);
    }

    @GetMapping("/claim-statuses")
    public List<Map<String, Object>> claimStatuses() {
        return Arrays.stream(ClaimStatus.values())
                .map(s -> Map.<String, Object>of(
                        "value", s.name(),
                        "description", s.getDescription(),
                        "editable", s.isEditable(),
                        "terminal", s.isTerminal(),
                        "open", s.isOpen()))
                .toList();
    }

    @GetMapping("/plan-types")
    public List<String> planTypes() {
        return Arrays.stream(PlanType.values()).map(Enum::name).toList();
    }

    @GetMapping("/policy-priorities")
    public List<String> policyPriorities() {
        return Arrays.stream(PolicyPriority.values()).map(Enum::name).toList();
    }

    /** CMS place-of-service codes, trimmed to the ones an outpatient practice uses. */
    @GetMapping("/places-of-service")
    public List<Map<String, String>> placesOfService() {
        return List.of(
                Map.of("code", "11", "label", "Office"),
                Map.of("code", "02", "label", "Telehealth"),
                Map.of("code", "19", "label", "Off-campus outpatient hospital"),
                Map.of("code", "21", "label", "Inpatient hospital"),
                Map.of("code", "22", "label", "On-campus outpatient hospital"),
                Map.of("code", "23", "label", "Emergency room"),
                Map.of("code", "31", "label", "Skilled nursing facility"),
                Map.of("code", "81", "label", "Independent laboratory"));
    }
}
