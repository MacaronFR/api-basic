import org.jreleaser.model.Active

plugins {
	id("maven-publish")
	kotlin("jvm") version "2.1.21"
	id("org.jreleaser") version "1.20.0"
}

group = "fr.imacaron"
version = "1.1.0"

repositories {
	mavenCentral()
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			groupId = project.group.toString()
			artifactId = project.name

			from(components["java"])

			pom {
				name = project.name
				description = "IMacaron basics for API development in Kotlin using Ktor"
				url = "https://github.com/MacaronFR/api-basic"
				inceptionYear = "2025"
				developers {
					developer {
						id = "MacaronFR"
						name = "MacaronFR"
						url = "https:/github.com/MacaronFR"
						roles = mutableSetOf("developer")
						timezone = "Europe/Paris"
					}
				}
				licenses {
					license {
						name = "Apache-2.0"
						url = "https://spdx.org/licenses/Apache-2.0.html"
					}
				}
				scm {
					connection = "scm:git:git://github.com/MacaronFR/api-basic.git"
					developerConnection = "scm:git:ssh://github.com/MacaronFR/api-basic.git"
					url = "https://github.com/MacaronFR/api-basic"
				}
			}
		}
	}

	repositories {
		maven {
			url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
		}
	}
}

jreleaser {
	project {
		authors = listOf("IMacaron")
		license = "Apache-2.0"
		inceptionYear = "2025"
		name = "api-basic"

		signing {
			active = Active.ALWAYS
			armored = true
			verify = true
		}
		deploy {
			maven {
				mavenCentral {
					create("sonatype") {
						active = Active.ALWAYS
						url = "https://central.sonatype.com/api/v1/publisher"
						stagingRepository("build/staging-deploy")
					}
				}
			}
		}
	}
}

val ktorVersion = "3.1.2"
val exposedVersion = "0.61.0"

dependencies {
	implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")

	implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
	implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
	implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
}

tasks.test {
	useJUnitPlatform()
}
kotlin {
	jvmToolchain(21)
}

java {
	withSourcesJar()
	withJavadocJar()
}