import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import java.util.zip.ZipFile

plugins {}

group = providers.gradleProperty("maven_group").get()
version = providers.gradleProperty("mod_version").get()

val targets = listOf("legacy", "community", "reloaded")
val variants = listOf("multilang", "synapse", "bundled")

subprojects {}
//Because of all the local sources required the gradle build files will be delayed

tasks.named("build") {
    dependsOn(distribution)
}
