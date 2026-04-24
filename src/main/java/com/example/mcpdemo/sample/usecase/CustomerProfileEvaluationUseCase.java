package com.example.mcpdemo.sample.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CustomerProfileEvaluationUseCase {

    public ProfileEvaluationResponse evaluate(ProfileEvaluationRequest request, JsonNode originalPayload) {
        Persona persona = request.persona();
        FinancialProfile financialProfile = request.financialProfile();

        double monthlyIncome = financialProfile != null && financialProfile.monthlyIncome() != null ? financialProfile.monthlyIncome() : 0.0;
        double debtRatio = financialProfile != null && financialProfile.debtRatio() != null ? financialProfile.debtRatio() : 0.0;
        Integer age = persona != null ? persona.age() : null;

        double riskScore = computeRiskScore(monthlyIncome, debtRatio, age);
        String segment = segmentFromRisk(riskScore);
        List<String> recommendations = recommendProducts(segment, request.productPreferences());

        int topLevelFields = originalPayload != null && originalPayload.isObject() ? originalPayload.size() : 0;
        boolean vipFlag = originalPayload != null && originalPayload.path("metadata").path("vip").asBoolean(false);

        return new ProfileEvaluationResponse(
                persona != null ? persona.customerId() : null,
                persona != null ? persona.fullName() : null,
                segment,
                riskScore,
                vipFlag,
                recommendations,
                topLevelFields
        );
    }

    private double computeRiskScore(double monthlyIncome, double debtRatio, Integer age) {
        double normalizedIncome = monthlyIncome <= 0 ? 1.0 : Math.min(monthlyIncome / 10000.0, 1.0);
        double normalizedDebt = Math.max(0.0, Math.min(debtRatio, 1.0));
        double normalizedAge = age == null ? 0.5 : Math.max(0.0, Math.min(age / 100.0, 1.0));

        double risk = (0.65 * normalizedDebt) + (0.25 * (1.0 - normalizedIncome)) + (0.10 * (1.0 - normalizedAge));
        return Math.round(risk * 1000.0) / 1000.0;
    }

    private String segmentFromRisk(double riskScore) {
        if (riskScore < 0.35) {
            return "LOW_RISK";
        }
        if (riskScore < 0.65) {
            return "MEDIUM_RISK";
        }
        return "HIGH_RISK";
    }

    private List<String> recommendProducts(String segment, List<ProductPreference> preferences) {
        List<String> recommendations = new ArrayList<>();
        if (preferences != null) {
            preferences.stream()
                    .map(ProductPreference::productType)
                    .filter(type -> type != null && !type.isBlank())
                    .limit(3)
                    .forEach(recommendations::add);
        }

        if (recommendations.isEmpty()) {
            if ("LOW_RISK".equals(segment)) {
                recommendations = List.of("MORTGAGE_REFINANCE", "PREMIUM_CARD");
            }
            else if ("MEDIUM_RISK".equals(segment)) {
                recommendations = List.of("PERSONAL_LOAN", "STANDARD_CARD");
            }
            else {
                recommendations = List.of("SECURED_CARD", "DEBT_CONSOLIDATION");
            }
        }

        return recommendations;
    }

    public record ProfileEvaluationRequest(
            Persona persona,
            FinancialProfile financialProfile,
            List<ProductPreference> productPreferences,
            Map<String, Object> metadata
    ) {}

    public record Persona(
            String customerId,
            String fullName,
            Integer age,
            Contact contact,
            List<Document> documents
    ) {}

    public record Contact(String email, String phone) {}

    public record Document(String type, String number, String country) {}

    public record FinancialProfile(Double monthlyIncome, Double debtRatio, Boolean hasLatePayments) {}

    public record ProductPreference(String productType, Integer priority, Map<String, Object> constraints) {}

    public record ProfileEvaluationResponse(
            String customerId,
            String customerName,
            String segment,
            double riskScore,
            boolean vip,
            List<String> recommendedProducts,
            int payloadTopLevelFields
    ) {}
}
