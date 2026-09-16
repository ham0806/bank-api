package dev.hambacon.bank.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "dev.hambacon.bank", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("dev.hambacon.bank.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "dev.hambacon.bank.application..",
                    "dev.hambacon.bank.adapter..",
                    "dev.hambacon.bank.config..",
                    "org.springframework..",
                    "org.jooq.."
            )
            .because("ドメイン層は外側の技術詳細に依存しない");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage("dev.hambacon.bank.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "dev.hambacon.bank.adapter..",
                    "org.jooq..",
                    "org.springframework.web.."
            )
            .because("ユースケースはAdapterやWeb、jOOQに依存しない");

    @ArchTest
    static final ArchRule webAdapterDependsOnInputPortsNotServices = noClasses()
            .that().resideInAPackage("dev.hambacon.bank.adapter.in.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "dev.hambacon.bank.application.service..",
                    "dev.hambacon.bank.application.port.out..",
                    "dev.hambacon.bank.adapter.out..",
                    "org.jooq.."
            )
            .because("Web Adapterは入力ポートとドメイン、アプリケーション例外だけに依存する");

    @ArchTest
    static final ArchRule schedulerDependsOnInputPortsNotOutputPorts = noClasses()
            .that().resideInAPackage("dev.hambacon.bank.adapter.in.scheduler..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "dev.hambacon.bank.application.service..",
                    "dev.hambacon.bank.application.port.out..",
                    "dev.hambacon.bank.adapter.out..",
                    "org.jooq.."
            )
            .because("スケジューラAdapterは入力ポートだけを呼び、Outbox処理の手順はユースケースに置く");

    @ArchTest
    static final ArchRule layersFollowDependencyRule = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("dev.hambacon.bank.domain..")
            .layer("Application").definedBy("dev.hambacon.bank.application..")
            .layer("Adapter").definedBy("dev.hambacon.bank.adapter..")
            .whereLayer("Domain").mayNotAccessAnyLayer()
            .whereLayer("Application").mayOnlyAccessLayers("Domain")
            .whereLayer("Adapter").mayOnlyAccessLayers("Application", "Domain")
            .because("依存の向きは Adapter -> Application -> Domain に固定する");
}
