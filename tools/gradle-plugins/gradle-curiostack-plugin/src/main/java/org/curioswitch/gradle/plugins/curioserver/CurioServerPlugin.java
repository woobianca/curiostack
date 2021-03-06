/*
 * MIT License
 *
 * Copyright (c) 2017 Choko (choko@curioswitch.org)
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

package org.curioswitch.gradle.plugins.curioserver;

import com.google.cloud.tools.jib.gradle.BuildImageTask;
import com.google.cloud.tools.jib.gradle.JibExtension;
import com.google.cloud.tools.jib.gradle.JibPlugin;
import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.gorylenko.GitPropertiesPlugin;
import org.curioswitch.gradle.plugins.curioserver.tasks.NativeImageTask;
import org.curioswitch.gradle.plugins.gcloud.tasks.KubectlTask;
import org.curioswitch.gradle.tooldownloader.DownloadedToolManager;
import org.curioswitch.gradle.tooldownloader.util.DownloadToolUtil;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.ApplicationPluginConvention;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.jvm.tasks.Jar;

/**
 * A simple {@link Plugin} to reduce boilerplate when defining server projects. Contains common
 * logic for building and deploying executables.
 */
public class CurioServerPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    project.getRootProject().getPlugins().apply(CurioServerSetupPlugin.class);

    project.getPlugins().apply(ApplicationPlugin.class);
    project.getPlugins().apply(GitPropertiesPlugin.class);
    project.getPlugins().apply(JibPlugin.class);
    project
        .getExtensions()
        .create(ImmutableDeploymentExtension.NAME, DeploymentExtension.class, project);

    project.getNormalization().getRuntimeClasspath().ignore("git.properties");

    // We don't use distributions so don't build them. Users can still reenable in afterEvaluate if
    // they really need it.
    project.getTasks().named("distTar").configure(t -> t.setEnabled(false));
    project.getTasks().named("distZip").configure(t -> t.setEnabled(false));

    var jib = project.getExtensions().getByType(JibExtension.class);

    var jibTask = project.getTasks().named("jib");
    var patchAlpha = project.getTasks().register("patchAlpha", KubectlTask.class);
    patchAlpha.configure(t -> t.mustRunAfter(jibTask));
    if (System.getenv("CI_MASTER") != null) {
      project.getTasks().named("build").configure(t -> t.dependsOn(jibTask, patchAlpha));
    }

    project
        .getTasks()
        .withType(
            BuildImageTask.class,
            t -> {
              t.dependsOn(project.getTasks().getByName(BasePlugin.ASSEMBLE_TASK_NAME));
              t.dependsOn(project.getRootProject().getTasks().getByName("gcloudSetup"));
            });

    jib.container(
        container -> {
          container.setFormat(ImageFormat.Docker);
          container.setPorts(ImmutableList.of("8080"));
        });

    jib.getFrom().setImage("curiostack/java-cloud-runner");
    jib.getTo()
        .setCredHelper(
            DownloadedToolManager.get(project)
                .getBinDir("gcloud")
                .resolve("docker-credential-gcr")
                .toString());

    var jar = project.getTasks().withType(Jar.class).named("jar");
    var nativeImage =
        project
            .getTasks()
            .register(
                "nativeImage",
                NativeImageTask.class,
                t -> {
                  t.getClasspath()
                      .from(
                          project
                              .getConfigurations()
                              .named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
                  t.getJarFile().set(jar.get().getOutputs().getFiles().getSingleFile());

                  t.dependsOn(jar, DownloadToolUtil.getSetupTask(project, "graalvm"));
                });

    project.afterEvaluate(
        p -> {
          ImmutableDeploymentExtension config =
              project.getExtensions().getByType(DeploymentExtension.class);

          String archivesBaseName =
              project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName();

          var appPluginConvention =
              project.getConvention().getPlugin(ApplicationPluginConvention.class);
          appPluginConvention.setApplicationName(archivesBaseName);

          nativeImage.configure(t -> t.getOutputName().set(archivesBaseName));

          jib.getTo().setImage(config.imagePrefix() + config.baseName());

          String releaseBranch =
              (String)
                  project
                      .getRootProject()
                      .getExtensions()
                      .getByType(ExtraPropertiesExtension.class)
                      .getProperties()
                      .get("curiostack.releaseBranch");

          jib.getTo()
              .setTags(ImmutableSet.of(releaseBranch != null ? releaseBranch : config.imageTag()));
          jib.container(
              container -> container.setMainClass(appPluginConvention.getMainClassName()));

          String revisionId =
              (String) project.getRootProject().findProperty("curiostack.revisionId");
          if (revisionId != null) {
            jib.getTo().setTags(ImmutableSet.of(config.imageTag(), revisionId));
            var alpha = config.getTypes().getByName("alpha");
            patchAlpha.configure(
                t -> {
                  t.setArgs(
                      ImmutableList.of(
                          "--namespace=" + alpha.namespace(),
                          "patch",
                          "deployment/" + alpha.deploymentName(),
                          "-p",
                          "{\"spec\": "
                              + "{\"template\": {\"metadata\": {\"labels\": {\"revision\": \""
                              + revisionId
                              + "\" }}}}}"));
                  t.setIgnoreExitValue(true);
                });
          }
        });
  }
}
