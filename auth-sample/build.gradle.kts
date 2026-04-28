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

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
}

android {
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/example.keystore")
            storePassword = "example"
            keyAlias = "example_alias"
            keyPassword = "example"
        }
    }

    defaultConfig {
        applicationId = "com.spotify.sdk.android.authentication.sample"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["redirectSchemeName"] = "spotify-sdk"
        manifestPlaceholders["redirectHostName"] = "auth"
        manifestPlaceholders["redirectPathPattern"] = ".*"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Specify which auth-lib flavor to use
        missingDimensionStrategy("auth", "auth")
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"))
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

    namespace = "com.spotify.sdk.android.authentication.sample"
}

dependencies {
    implementation(project(":auth-lib"))
//    implementation("com.spotify.android:auth:3.1.0")

    implementation("androidx.browser:browser:1.4.0")
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("com.google.android.material:material:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
}
