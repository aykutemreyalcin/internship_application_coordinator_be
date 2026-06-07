package com.internship.coordinator.service;

import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.CaseStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class CaseSpecifications {

    private CaseSpecifications() {
    }

    static Specification<ApplicationCase> withFilters(CaseStatus status, String search) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("studentName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("studentId")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("companyName")), pattern)));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
