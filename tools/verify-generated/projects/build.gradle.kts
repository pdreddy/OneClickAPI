plugins {
    id("io.spring.dependency-management") version "1.1.7" apply false
}
subprojects {
    val boot2 = name.contains("boot2")
    val boot4 = name.contains("boot4")
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = "io.spring.dependency-management")
    repositories { mavenCentral() }
    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:" + when { boot2 -> "2.7.18"; boot4 -> "4.1.1"; else -> "3.5.0" })
    }
    the<JavaPluginExtension>().toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    val mybatis = name.contains("mybatis")
    dependencies {
        // Same artifacts and versions DependencyAdvisor recommends for each Boot generation.
        "implementation"(if (boot4) "org.springframework.boot:spring-boot-starter-webmvc" else "org.springframework.boot:spring-boot-starter-web")
        "implementation"("org.springframework.boot:spring-boot-starter-validation")
        "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
        if (mybatis) "implementation"("org.mybatis.spring.boot:mybatis-spring-boot-starter:" + when { boot2 -> "2.3.2"; boot4 -> "4.0.1"; else -> "3.0.5" })
        "implementation"(when { boot2 -> "org.springdoc:springdoc-openapi-ui:1.8.0"; boot4 -> "org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1"; else -> "org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13" })
        if (boot4) "testImplementation"("org.springframework.boot:spring-boot-starter-webmvc-test")
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
    the<JacocoPluginExtension>().toolVersion = "0.8.14"
    tasks.named<Test>("test") { finalizedBy("jacocoTestReport") }
    tasks.named<JacocoReport>("jacocoTestReport") {
        reports { csv.required.set(true); html.required.set(true) }
        // Only what the generator wrote counts: the harness's own application class and the sample entities do not.
        classDirectories.setFrom(files(classDirectories.files.map { fileTree(it) { exclude("com/acme/shop/ShopApplication*", "com/acme/shop/model/**", "com/acme/shop/pojo/**") } }))
    }
    tasks.withType<Test> { useJUnitPlatform(); testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
}
