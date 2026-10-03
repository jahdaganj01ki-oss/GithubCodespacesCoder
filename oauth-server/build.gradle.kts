plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.gitcodera.oauth.MainKt")
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.h2database:h2:2.3.232")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}

tasks.test {
    useJUnitPlatform()
}
