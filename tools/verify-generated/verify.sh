#!/bin/bash
# Validates what the generator produces, without IntelliJ:
#  1. compiles the plugin core, runs its unit tests and writes every sample scenario (PlanDumpTest) to build/generated
#  2. adds a Spring Boot application class and a "context loads" test to each scenario
#  3. compiles each scenario against real Spring Boot jars and runs its generated tests (no -parameters flag, like a
#     plain Maven build)
# Usage: tools/verify-generated/verify.sh   (needs JDK 21 and access to Maven Central)
set -u
here="$(cd "$(dirname "$0")" && pwd)"
gradlew="$here/../../gradlew"
gen="$here/build/generated"
rm -rf "$gen"

"$gradlew" -p "$here/core" cleanTest test -q || { echo "plugin core tests FAILED"; exit 1; }
echo "plugin core tests passed"

for d in "$gen"/*/; do
  mkdir -p "$d/src/main/java/com/acme/shop" "$d/src/test/java/com/acme/shop" "$d/src/main/resources"
  [ -f "$d/src/main/java/com/acme/shop/ShopApplication.java" ] || printf 'package com.acme.shop;\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n@SpringBootApplication\npublic class ShopApplication { public static void main(String[] a) { SpringApplication.run(ShopApplication.class, a); } }\n' > "$d/src/main/java/com/acme/shop/ShopApplication.java"
  printf 'package com.acme.shop;\nimport org.junit.jupiter.api.Test;\nimport org.springframework.boot.test.context.SpringBootTest;\n@SpringBootTest\nclass ContextLoadsTest { @Test void contextLoads() {} }\n' > "$d/src/test/java/com/acme/shop/ContextLoadsTest.java"
  printf 'spring.liquibase.enabled=false\nspring.jpa.hibernate.ddl-auto=create-drop\nmybatis.mapper-locations=classpath:mybatis/mappers/*.xml\n' > "$d/src/main/resources/application.properties"
done

# The JaCoCo edit the plugin makes to a build file must give a coverage report in a real build.
for tool in gradle-groovy gradle-kotlin maven; do
  p="$here/build/jacoco-builds/$tool"
  if [ "$tool" = maven ]; then
    (cd "$p" && mvn -q -B test > build.log 2>&1); report="$p/target/site/jacoco/index.html"
  else
    "$gradlew" -p "$p" test -q > "$p/build.log" 2>&1; report="$p/build/reports/jacoco/test/html/index.html"
  fi
  if [ -f "$report" ]; then echo "JaCoCo edit, $tool: report written"; else echo "JaCoCo edit, $tool: NO REPORT (see $p/build.log)"; status_jacoco=1; fi
done

"$gradlew" -p "$here/projects" test --continue -q --max-workers=2 > "$here/build/projects.log" 2>&1
status=0
for d in "$gen"/*/; do
  r="$d/build/test-results/test"
  tests=$(cat "$r"/*.xml 2>/dev/null | grep -o ' tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s+0}')
  failed=$(cat "$r"/*.xml 2>/dev/null | grep -o 'failures="[0-9]*"\|errors="[0-9]*"' | awk -F'"' '{s+=$2} END {print s+0}')
  if [ "$tests" = 0 ] || [ "$failed" != 0 ]; then status=1; mark=FAIL; else mark=ok; fi
  csv="$d/build/reports/jacoco/test/jacocoTestReport.csv"
  cov=$(awk -F, 'NR>1 {lm+=$8; lc+=$9; bm+=$6; bc+=$7} END {if (lm+lc>0) printf "lines %5.1f%%  branches %5.1f%%", 100*lc/(lm+lc), (bm+bc>0 ? 100*bc/(bm+bc) : 100)}' "$csv" 2>/dev/null)
  printf '%-45s %4s tests %3s failed  %-4s  %s\n' "$(basename "$d")" "$tests" "$failed" "$mark" "$cov"
done
echo "Coverage counts only generated main classes; per-class HTML: build/generated/<scenario>/build/reports/jacoco/test/html/index.html"
[ $status = 0 ] || echo "Some scenarios failed; compiler errors are in $here/build/projects.log, test reports in build/generated/<scenario>/build/reports"
[ "${status_jacoco:-0}" = 0 ] || status=1
exit $status
