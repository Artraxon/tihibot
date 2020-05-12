import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "de.rtrx.a"
version = "0.1"

plugins {
    application
    maven
    id("com.github.johnrengelman.shadow") version("5.1.0")
    id ("com.bmuschko.docker-remote-api") version("5.2.0")
    id("com.bmuschko.docker-java-application") version "5.2.0"
    kotlin("jvm") version "1.3.72"

}

repositories {
    jcenter()

    maven {
        name = "GithubPackages"
        url = uri("https://maven.pkg.github.com/Artraxon/unexbot")
        credentials {
            credentials {
                val GITHUB_USER: String by project
                val GITHUB_TOKEN: String by project

                username = GITHUB_USER
                password = GITHUB_TOKEN
            }

        }
    }
}


dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(kotlin("stdlib"))
    implementation("de.rtrx.a:unexBot:2.2.6")
    implementation(kotlin("stdlib-jdk8"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.3.1")

    implementation("dev.misfitlabs.kotlinguice4:kotlin-guice:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0")
    implementation( "net.dean.jraw:JRAW:1.1.0")
    implementation( "org.postgresql:postgresql:42.2.8")
    implementation( "com.google.code.gson:gson:2.8.5")
    implementation(group= "org.slf4j", name= "slf4j-api", version= "1.7.28")
    implementation(group= "org.slf4j", name= "slf4j-jdk14", version= "1.7.28")

    implementation(group="com.google.inject", name= "guice", version = "4.2.3")
    implementation(group="com.google.inject.extensions", name= "guice-assistedinject", version = "3.0")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation( "org.mockito:mockito-inline:2.13.0")



    implementation("io.github.microutils:kotlin-logging:1.5.9")
    implementation("com.uchuhimo:konf-core:0.20.0")
    implementation("com.uchuhimo:konf-yaml:0.20.0")
    implementation("org.yaml:snakeyaml:1.25")


}

tasks.test {
    useJUnitPlatform()
}
application {
    mainClassName = "de.rtrx.a.MainKt"
    applicationDefaultJvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}