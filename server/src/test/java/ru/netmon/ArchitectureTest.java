package ru.netmon;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import com.tngtech.archunit.core.domain.*; import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.*; import com.tngtech.archunit.lang.*;
import java.nio.file.*; import java.util.*; import java.util.stream.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
@AnalyzeClasses(packages = "ru.netmon", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
  static String module(String pkg) {
    if (!pkg.startsWith("ru.netmon.")) return null;
    String[] p = pkg.substring("ru.netmon.".length()).split("\\.");
    if (p[0].equals("check") && p.length > 1) return "check." + p[1];
    return p[0];
  }
  static final ArchCondition<JavaClass> ONLY_ALLOWED = new ArchCondition<>("depend only on contract, common and own module") {
    public void check(JavaClass c, ConditionEvents ev) {
      String own = module(c.getPackageName());
      for (Dependency d : c.getDirectDependenciesFromSelf()) {
        JavaClass t = d.getTargetClass().getBaseComponentType();
        if (!t.getPackageName().equals("ru.netmon") && !t.getPackageName().startsWith("ru.netmon.")) continue;
        String m = module(t.getPackageName());
        if (!"contract".equals(m) && !"common".equals(m) && !Objects.equals(own, m)) ev.add(SimpleConditionEvent.violated(d, d.getDescription()));
      }
    }
  };
  @ArchTest static final ArchRule modules = classes().that().resideInAPackage("ru.netmon.*..").should(ONLY_ALLOWED);
  @ArchTest static final ArchRule contract = classes().that().resideInAPackage("ru.netmon.contract..").should().onlyDependOnClassesThat().resideInAnyPackage("java..", "ru.netmon.contract..");
  @ArchTest static final ArchRule controllers = methods().that().areDeclaredInClassesThat().areMetaAnnotatedWith(org.springframework.stereotype.Controller.class)
      .and().areMetaAnnotatedWith(org.springframework.web.bind.annotation.RequestMapping.class)
      .should().beAnnotatedWith(org.springframework.security.access.prepost.PreAuthorize.class).allowEmptyShould(true);
  @ArchTest static final ArchRule controllerPlace = classes().that().areMetaAnnotatedWith(org.springframework.stereotype.Controller.class).should().resideInAPackage("ru.netmon.*..").andShould().resideOutsideOfPackages("ru.netmon.contract..", "ru.netmon.common..").allowEmptyShould(true);
  @Test void noOid() throws Exception {
    try (Stream<Path> s = Files.walk(Path.of("src/main"))) {
      List<Path> bad = s.filter(Files::isRegularFile).filter(p -> !p.startsWith(Path.of("src/main/resources/seed")))
        .filter(p -> { try { return new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.ISO_8859_1).contains("1.3.6.1"); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); } }).toList();
      assertThat(bad).isEmpty();
    }
  }
}
