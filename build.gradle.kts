plugins {
    `java-library`
    `maven-publish`
}

group = "fr.traodev"
version = "0.1.0-SNAPSHOT"
description = "Lightweight Discord API library for Java 21"

repositories {
    mavenCentral()
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("JDisc")
                description.set(project.description)
                url.set("https://github.com/traodev/jdisc")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                scm {
                    url.set("https://github.com/traodev/jdisc")
                    connection.set("scm:git:https://github.com/traodev/jdisc.git")
                }
            }
        }
    }
}
