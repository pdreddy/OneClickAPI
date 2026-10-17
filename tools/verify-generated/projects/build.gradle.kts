plugins {
    id("io.spring.dependency-management") version "1.1.7" apply false
}
subprojects {
    val boot2 = name.contains("boot2")
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    repositories { mavenCentral() }
    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().imports {
        mavenBom(if (boot2) "org.springframework.boot:spring-boot-dependencies:2.7.18" else "org.springframework.boot:spring-boot-dependencies:3.5.0")
    }
    the<JavaPluginExtension>().toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    val mybatis = name.contains("mybatis")
    dependencies {
        "implementation"("org.springframework.boot:spring-boot-starter-web")
        "implementation"("org.springframework.boot:spring-boot-starter-validation")
        "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
        if (mybatis) "implementation"(if (boot2) "org.mybatis.spring.boot:mybatis-spring-boot-starter:2.3.2" else "org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.4")
        "implementation"(if (boot2) "org.springdoc:springdoc-openapi-ui:1.8.0" else "org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
        "implementation"("org.liquibase:liquibase-core")
        "implementation"("org.mapstruct:mapstruct:1.6.3")
        "annotationProcessor"("org.mapstruct:mapstruct-processor:1.6.3")
        "compileOnly"("org.projectlombok:lombok:1.18.38")
        "annotationProcessor"("org.projectlombok:lombok:1.18.38")
        "annotationProcessor"("org.projectlombok:lombok-mapstruct-binding:0.2.0")
        "testCompileOnly"("org.projectlombok:lombok:1.18.38")
        "testAnnotationProcessor"("org.projectlombok:lombok:1.18.38")
        "runtimeOnly"("com.h2database:h2")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test> { useJUnitPlatform(); testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
}
