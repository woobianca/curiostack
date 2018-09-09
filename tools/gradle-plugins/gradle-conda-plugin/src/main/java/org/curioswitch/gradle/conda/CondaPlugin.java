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

package org.curioswitch.gradle.conda;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import org.curioswitch.gradle.conda.tasks.InstallCondaPackagesTask;
import org.curioswitch.gradle.helpers.platform.OperatingSystem;
import org.curioswitch.gradle.helpers.platform.PlatformHelper;
import org.curioswitch.gradle.tooldownloader.ToolDownloaderPlugin;
import org.curioswitch.gradle.tooldownloader.tasks.DownloadToolTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class CondaPlugin implements Plugin<Project> {

  private static final Splitter CONDA_VERSION_SPLITTER = Splitter.on('-');

  private NamedDomainObjectContainer<ModifiableCondaExtension> condas;

  @Override
  public void apply(Project project) {
    checkState(
        project.getParent() == null,
        "gradle-conda-plugin can only be applied to the root project.");

    condas =
        project.container(
            ModifiableCondaExtension.class,
            name -> CondaExtension.create(name, project.getObjects()));
    project.getExtensions().add("conda", condas);

    condas.configureEach(conda -> addCondaTasks(project, conda));
  }

  public NamedDomainObjectContainer<ModifiableCondaExtension> getCondas() {
    return condas;
  }

  private static void addCondaTasks(Project project, CondaExtension conda) {
    var operatingSystem = new PlatformHelper().getOs();
    project
        .getPlugins()
        .withType(
            ToolDownloaderPlugin.class,
            plugin -> {
              plugin.registerToolIfAbsent(
                  conda.getName(),
                  tool -> {
                    var artifact =
                        conda
                            .getVersion()
                            .map(
                                fullVersion ->
                                    CONDA_VERSION_SPLITTER.splitToList(fullVersion).get(0));
                    var version =
                        conda
                            .getVersion()
                            .map(
                                fullVersion ->
                                    Iterables.getLast(
                                        CONDA_VERSION_SPLITTER.splitToList(fullVersion)));
                    tool.getArtifact().set(artifact);
                    tool.getVersion().set(version);
                    tool.getBaseUrl().set("https://repo.continuum.io/miniconda/");
                    tool.getArtifactPattern().set("[artifact]-[revision]-[classifier].[ext]");

                    if (operatingSystem == OperatingSystem.WINDOWS) {
                      tool.getPathSubDirs().addAll("", "Scripts");
                    } else {
                      tool.getPathSubDirs().add("bin");
                    }

                    var osClassifiers = tool.getOsClassifiers();
                    osClassifiers.getLinux().set("Linux-x86_64");
                    osClassifiers.getMac().set("MacOSX-x86_64");
                    osClassifiers.getWindows().set("Windows-x86_64");

                    var osExtensions = tool.getOsExtensions();
                    osExtensions.getLinux().set("sh");
                    osExtensions.getMac().set("sh");
                    osExtensions.getWindows().set("exe");
                  });
              var capitalized =
                  Ascii.toUpperCase(conda.getName().charAt(0)) + conda.getName().substring(1);
              var download =
                  project
                      .getTasks()
                      .withType(DownloadToolTask.class)
                      .named("toolsDownload" + capitalized);
              download.configure(
                  t ->
                      t.setArchiveExtractAction(
                          archive -> {
                            archive.setExecutable(true);
                            var toolDir = plugin.toolManager().getToolDir(conda.getName());
                            if (operatingSystem == OperatingSystem.WINDOWS) {
                              project.exec(
                                  exec -> {
                                    exec.executable(archive);
                                    exec.args(
                                        "/S",
                                        "/InstallationType=JustMe",
                                        "/AddToPath=0",
                                        "/RegisterPython=0",
                                        "/NoRegistry=1",
                                        "/D=" + toolDir.toAbsolutePath().toString());
                                  });
                            } else {
                              project.exec(
                                  exec -> {
                                    exec.executable(archive);
                                    exec.args("-b", "-p", toolDir.toAbsolutePath().toString());
                                  });
                            }
                          }));
              project
                  .getTasks()
                  .register(
                      "condaInstallPackages" + capitalized,
                      InstallCondaPackagesTask.class,
                      conda,
                      plugin.toolManager())
                  .configure(t -> t.dependsOn(download));
            });
  }
}