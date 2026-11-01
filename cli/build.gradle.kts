plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // javax.smartcardio lives in the java.smartcardio module, which is
        // not part of the default compiler module set.
        freeCompilerArgs.add("-Xadd-modules=java.smartcardio")
    }
}

application {
    mainClass.set("eu.colega.tacho.cli.ReadCardKt")
    applicationDefaultJvmArgs = listOf("--add-modules", "java.smartcardio")
}

dependencies {
    implementation(project(":core"))
}
