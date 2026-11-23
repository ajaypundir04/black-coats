package com.blackcoat.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.blackcoat.api", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureTests {

    /**
     * Requirement: ArchUnit rules keep module boundaries clean.
     * 'chat' may call 'rag', but 'rag' must NEVER depend on or call 'chat'.
     */
    @ArchTest
    static final ArchRule rag_should_not_depend_on_chat =
        noClasses().that().resideInAPackage("..rag..")
            .should().dependOnClassesThat().resideInAPackage("..chat..");

    /**
     * Requirement: 'llm' module is low-level and must not depend on higher-level 'rag' or 'chat'.
     */
    @ArchTest
    static final ArchRule llm_should_not_depend_on_rag_or_chat =
        noClasses().that().resideInAPackage("..llm..")
            .should().dependOnClassesThat().resideInAnyPackage("..rag..", "..chat..");
}
