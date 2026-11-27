package com.smartteach;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.repository.Repository;

/**
 * The backend is split by feature (classroom, quiz, assignment...). Each feature owns its tables:
 * other features read and change them through its services, never through its repositories.
 */
class ArchitectureTest {

    private static final String ROOT = "com.smartteach";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    /** Top-level packages under com.smartteach, e.g. "quiz"; read from the code so a new one is checked too. */
    static Stream<String> modules() {
        return CLASSES.stream()
                .map(JavaClass::getPackageName)
                .filter(name -> name.startsWith(ROOT + "."))
                .map(name -> name.substring(ROOT.length() + 1).split("\\.")[0])
                .distinct()
                .sorted();
    }

    @ParameterizedTest
    @MethodSource("modules")
    void repositoriesAreOnlyUsedInsideTheirModule(String module) {
        noClasses().that().resideOutsideOfPackage(ROOT + "." + module + "..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + "." + module + ".repository..")
                .because("a module changes another module's data only through that module's services")
                .check(CLASSES);
    }

    @ParameterizedTest
    @MethodSource("modules")
    void controllersAreOnlyUsedInsideTheirModule(String module) {
        noClasses().that().resideOutsideOfPackage(ROOT + "." + module + "..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + "." + module + ".controller..")
                .check(CLASSES);
    }

    @Test
    void controllersGoThroughServices() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .because("owner checks and transactions live in the service layer")
                .check(CLASSES);
    }

    @Test
    void commonDoesNotDependOnFeatures() {
        noClasses().that().resideInAPackage(ROOT + ".common..")
                .should().dependOnClassesThat().resideInAnyPackage(modules()
                        .filter(module -> !module.equals("common"))
                        .map(module -> ROOT + "." + module + "..")
                        .toArray(String[]::new))
                .because("common is the only code shared by every feature")
                .check(CLASSES);
    }

    @Test
    void modulesHaveNoCycles() {
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(CLASSES);
    }
}
