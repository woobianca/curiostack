/*
 * MIT License
 *
 * Copyright (c) 2018 Choko (choko@curioswitch.org)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

plugins {
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    compile(project(":common:curio-helpers"))
    compile(project(":tools:gradle-plugins:gradle-helpers"))

    compile("com.google.gradle:osdetector-gradle-plugin")
    compile("com.google.guava:guava")

    annotationProcessor("org.immutables:value")
    compileOnly("org.immutables:value-annotations")
}

gradlePlugin {
    plugins {
        register("protobuf") {
            id = "org.curioswitch.gradle-protobuf-plugin"
            implementationClass = "org.curioswitch.gradle.protobuf.ProtobufPlugin"
        }
    }
}

publishing {
    publications {
        register("maven", MavenPublication::class) {
            pom {
                name.set("Gradle Protobuf Plugin")
                description.set("Gradle plugin to compile proto files into generated code.")
                url.set("https://github.com/curioswitch/curiostack/tree/master/tools/" +
                        "gradle-plugins/gradle-protobuf-plugin")
            }
        }
    }
}
