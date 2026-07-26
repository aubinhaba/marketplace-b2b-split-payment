package com.aubin.payment.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("Hexagonal architecture — ArchUnit guards — payment-service")
class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.aubin.payment");
    }

    @Test
    @DisplayName("domain/ has no dependency on Spring, JPA or AWS")
    void domain_must_not_depend_on_frameworks() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "software.amazon.."
                )
                .because("ADR-001: domain is framework-free. Enforced in CI.")
                .check(classes);
    }

    @Test
    @DisplayName("domain/ does not reference infrastructure/")
    void domain_must_not_depend_on_infrastructure() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("ADR-001: dependency flow is infrastructure → application → domain, never reversed.")
                .check(classes);
    }

    @Test
    @DisplayName("application/ does not reference infrastructure/")
    void application_must_not_depend_on_infrastructure() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("ADR-001: application layer depends only on abstract ports, never on concrete adapters.")
                .check(classes);
    }

    @Test
    @DisplayName("no api/ package — controllers belong in infrastructure.adapter.in.rest")
    void no_api_package_at_root() {
        noClasses()
                .should().resideInAPackage("com.aubin.payment.api..")
                .because("Hexagonal convention: REST controllers are inbound adapters under infrastructure.adapter.in.rest.")
                .check(classes);
    }

    @Test
    @DisplayName("only the Stripe adapter imports the Stripe SDK")
    void only_the_psp_adapter_may_depend_on_stripe() {
        noClasses()
                .that().resideOutsideOfPackages(
                        "com.aubin.payment.infrastructure.adapter.out.psp..",
                        "com.aubin.payment.infrastructure.config..")
                .should().dependOnClassesThat().resideInAPackage("com.stripe..")
                .because("The PSP vocabulary stops at the adapter, so swapping Stripe out never reaches the domain.")
                .check(classes);
    }

    @Test
    @DisplayName("outbound adapters reside under infrastructure.adapter.out")
    void outbound_adapters_live_under_adapter_out() {
        classes()
                .that().haveSimpleNameEndingWith("Adapter")
                .and().resideInAPackage("..infrastructure..")
                .should().resideInAPackage("..infrastructure.adapter.out..")
                .because("Keeps persistence, cache and PSP adapters from drifting back out of adapter/out.")
                .check(classes);
    }
}
