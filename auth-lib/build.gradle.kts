/*
 * Copyright (c) 2015-2016 Spotify AB
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Properties
import java.io.FileInputStream
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("checkstyle")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka")
}

group = "com.spotify.android"
version = "4.0.2"

val archivesBaseName = "auth"

android {
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 21
        targetSdk = 35
        buildConfigField("String", "LIB_VERSION_NAME", "\"${project.version}\"")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"))
        }
    }

    @Suppress("UnstableApiUsage")
    flavorDimensions += "auth"
    productFlavors {
        create("store") {
            dimension = "auth"
        }
        create("auth") {
            dimension = "auth"
            isDefault = true
        }
    }

    libraryVariants.configureEach {
        outputs.configureEach {
            // Rename auth-auth-[buildtype].aar to auth-[buildtype].aar
            if (name.startsWith("auth")) {
                (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                    "auth-${buildType.name}.aar"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    lint {
        lintConfig = file("${project.rootDir}/config/lint.xml")
        quiet = false
        warningsAsErrors = false
        textReport = true
        textOutput = file("stdout")
        xmlReport = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    val manifestPlaceholdersForTests = mapOf(
        "redirectSchemeName" to "spotify-sdk",
        "redirectHostName" to "auth",
        "redirectPathPattern" to "/.*"
    )

    namespace = "com.spotify.sdk.android.auth"

    unitTestVariants.configureEach {
        mergedFlavor.manifestPlaceholders.putAll(manifestPlaceholdersForTests)
    }
    testVariants.configureEach {
        mergedFlavor.manifestPlaceholders.putAll(manifestPlaceholdersForTests)
    }
}

val kotlinVersion = rootProject.extra["kotlin_version"] as String

dependencies {
    implementation("androidx.browser:browser:1.5.0")
    api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:2.28.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
}

/*
    Static analysis section
    run: ./gradlew auth-lib:checkstyle auth-lib:findbugs
 */

tasks.register<Checkstyle>("checkstyle") {
    configFile = file("${project.rootDir}/config/checkstyle.xml")
    source("src")
    include("**/*.java")
    exclude("**/gen/**")
    exclude("**/*.kt")
    classpath = files()
}

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

signing {
    sign(publishing.publications)
}

extra["ossrhUsername"] = ""
extra["ossrhPassword"] = ""

fun getSigningVariables() {
    // Try to fetch the values from local.properties, otherwise look in the environment variables
    // More info here: https://central.sonatype.org/publish/requirements/gpg/
    val secretPropsFile = project.rootProject.file("local.properties")
    if (secretPropsFile.exists()) {
        val p = Properties()
        FileInputStream(secretPropsFile).use { p.load(it) }
        p.forEach { name, value ->
            extra[name.toString()] = value
        }
    } else {
        extra["ossrhUsername"] = System.getenv("OSSRH_USERNAME") ?: ""
        extra["ossrhPassword"] = System.getenv("OSSRH_PASSWORD") ?: ""
    }
}

/*
 * Publishing is done through ossrh and can onky be done by people with access.
 * reach out to the foss channel to get access.
 * https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/
 */
afterEvaluate {
    publishing {
        repositories {
            maven {
                getSigningVariables()

                name = "ossrh-staging-api"
                url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                credentials {
                    username = extra["ossrhUsername"].toString()
                    password = extra["ossrhPassword"].toString()
                }
            }
        }

        android.libraryVariants.configureEach {
            if (buildType.name == "debug") return@configureEach

            val variant = this
            val flavored = variant.flavorName != "auth"

            val javaDocDir = if (flavored) {
                "../docs-${variant.flavorName}/"
            } else {
                "../docs/"
            }

            val sourceDirs = variant.sourceSets.flatMap {
                it.javaDirectories + it.resourcesDirectories
            }

            val dokkaTaskName = "${variant.name}Dokka"
            if (tasks.findByName(dokkaTaskName) == null) {
                tasks.register(dokkaTaskName, DokkaTask::class.java) {
                    description = "Generates Dokka documentation for ${variant.name}."

                    // Configure source sets
                    dokkaSourceSets {
                        configureEach {
                            // Include both Kotlin and Java sources
                            val sourceDirs = variant.sourceSets.flatMap {
                                it.javaDirectories
                            }
                            sourceDirs.forEach { sourceDir ->
                                if (sourceDir.exists()) {
                                    this@configureEach.sourceRoots.from(sourceDir)
                                }
                            }

                            // Configure classpath
                            classpath.from(variant.javaCompileProvider.get().classpath)
                            classpath.from(project.files(android.bootClasspath.joinToString(File.pathSeparator)))

                            // External documentation links
                            externalDocumentationLink {
                                url.set(uri("https://docs.oracle.com/javase/8/docs/api/").toURL())
                            }
                            externalDocumentationLink {
                                url.set(uri("https://developer.android.com/reference/").toURL())
                            }

                            // Suppress warnings for undocumented code (optional)
                            suppressInheritedMembers.set(false)
                        }
                    }

                    // Output directory
                    outputDirectory.set(file(javaDocDir))

                    // Fail on warning (set to false during migration)
                    failOnWarning.set(false)
                }
            }

            val dokkaJarTaskName = "${variant.name}DokkaJar"
            if (tasks.findByName(dokkaJarTaskName) == null) {
                tasks.register(dokkaJarTaskName, org.gradle.jvm.tasks.Jar::class.java) {
                    dependsOn(dokkaTaskName)
                    archiveClassifier.set("javadoc")
                    from(file(javaDocDir))
                }
            }

            val sourcesJarTaskName = "${variant.name}SourcesJar"
            if (tasks.findByName(sourcesJarTaskName) == null) {
                tasks.register(sourcesJarTaskName, org.gradle.jvm.tasks.Jar::class.java) {
                    from(sourceDirs)
                    archiveClassifier.set("sources")
                }
            }

            val dokkaJar = tasks.named(dokkaJarTaskName)
            val sourcesJar = tasks.named(sourcesJarTaskName)

            publications {
                create<MavenPublication>("${variant.flavorName}Release") {
                    from(components["${variant.flavorName}Release"])
                    val suffix = if (flavored) "-${variant.flavorName}" else ""
                    artifact(dokkaJar)
                    artifact(sourcesJar)
                    groupId = project.group.toString()
                    version = project.version.toString()
                    artifactId = archivesBaseName + suffix

                    pom {
                        name.set(project.group.toString() + ":" + archivesBaseName + suffix)
                        val descriptionSuffix = if (variant.flavorName == "store") {
                            " with the Play Store Fallback"
                        } else {
                            ""
                        }
                        description.set("Spotify authorization library for Android$descriptionSuffix")
                        configurePom()
                    }
                }
            }
        }
    }
}

fun MavenPom.configurePom() {
    packaging = "aar"
    url.set("https://github.com/spotify/android-auth")

    licenses {
        license {
            name.set("The Apache Software License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }

    scm {
        connection.set("scm:git:https://github.com/spotify/android-auth.git")
        developerConnection.set("scm:git:git@github.com:spotify/android-auth.git")
        url.set("https://github.com/spotify/android-auth")
    }

    developers {
        developer {
            id.set("erikg")
            name.set("Erik Ghonyan")
            email.set("erikg@spotify.com")
        }
    }
}
