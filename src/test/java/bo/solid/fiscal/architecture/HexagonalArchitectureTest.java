package bo.solid.fiscal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "bo.solid.fiscal", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "javax..", "java.sql..", "com.fasterxml..", "..infrastructure..",
                    "..application..", "..interfaces..");

    @ArchTest
    static final ArchRule application_does_not_know_adapters = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "..interfaces..", "org.springframework.web..", "org.springframework.jdbc..",
                    "java.sql..", "jakarta.servlet..", "org.springframework.security..", "org.springframework.kafka..",
                    "com.fasterxml..", "org.apache.kafka..");

    @ArchTest
    static final ArchRule interfaces_do_not_touch_infrastructure = noClasses().that().resideInAPackage("..interfaces..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule infrastructure_does_not_depend_on_interfaces = noClasses().that()
            .resideInAPackage("..infrastructure..").should().dependOnClassesThat().resideInAPackage("..interfaces..");

    @ArchTest
    static final ArchRule no_soap_or_sql_in_controllers = noClasses().that().resideInAPackage("..interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "org.springframework.jdbc..", "jakarta.xml.ws..");
}
