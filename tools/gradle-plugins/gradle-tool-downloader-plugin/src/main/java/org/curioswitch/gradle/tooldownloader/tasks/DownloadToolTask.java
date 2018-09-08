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

package org.curioswitch.gradle.tooldownloader.tasks;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.curioswitch.gradle.helpers.platform.PlatformHelper;
import org.curioswitch.gradle.tooldownloader.DownloadedToolManager;
import org.curioswitch.gradle.tooldownloader.ModifiableOsValues;
import org.curioswitch.gradle.tooldownloader.ToolDownloaderExtension;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyPatternRepositoryLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public class DownloadToolTask extends DefaultTask {

  private final Property<String> name;
  private final Property<String> version;
  private final Property<String> baseUrl;
  private final Property<String> artifactPattern;
  private final Property<ModifiableOsValues> osClassifiers;
  private final Property<ModifiableOsValues> osExtensions;

  private final PlatformHelper platformHelper;
  private final DownloadedToolManager toolManager;

  @Inject
  public DownloadToolTask(
      ToolDownloaderExtension config,
      PlatformHelper platformHelper,
      DownloadedToolManager toolManager) {
    this.platformHelper = platformHelper;
    this.toolManager = toolManager;

    setGroup("Tools");

    var objects = getProject().getObjects();
    name = objects.property(String.class);
    version = objects.property(String.class);
    baseUrl = objects.property(String.class);
    artifactPattern = objects.property(String.class);
    osClassifiers = objects.property(ModifiableOsValues.class);
    osExtensions = objects.property(ModifiableOsValues.class);

    name.set(config.getName());
    version.set(config.getVersion());
    baseUrl.set(config.getBaseUrl());
    artifactPattern.set(config.getArtifactPattern());
    osClassifiers.set(config.getOsClassifiers());
    osExtensions.set(config.getOsExtensions());
  }

  @Input
  public String getDependency() {
    var operatingSystem = platformHelper.getOs();
    return "org.curioswitch.curiostack.downloaded_tools:"
        + name.get()
        + ":"
        + version.get()
        + ":"
        + osClassifiers.get().getValue(operatingSystem)
        + "@"
        + osExtensions.get().getValue(operatingSystem);
  }

  @OutputDirectory
  public Path getToolDir() {
    return toolManager.getToolDir(name.get());
  }

  @TaskAction
  void exec() {
    checkNotNull(baseUrl.get(), "baseUrl must be set.");
    checkNotNull(artifactPattern.get(), "artifactPattern must be set");

    var currentRepositories = getProject().getRepositories();
    setRepository();

    File archive = resolveAndFetchArchive(getProject().getDependencies().create(getDependency()));
    unpackArchive(archive);

    restoreRepositories(currentRepositories);
  }

  private void setRepository() {
    var repositories = getProject().getRepositories();
    repositories.clear();
    repositories.ivy(
        repo -> {
          repo.setUrl(baseUrl.get());
          repo.layout(
              "pattern",
              (IvyPatternRepositoryLayout layout) -> layout.artifact(artifactPattern.get()));
        });
  }

  private File resolveAndFetchArchive(Dependency dependency) {
    var configuration = getProject().getConfigurations().detachedConfiguration(dependency);
    configuration.setTransitive(false);
    return configuration.resolve().iterator().next();
  }

  private void unpackArchive(File archive) {
    var project = getProject();
    project.copy(
        copy -> {
          if (archive.getName().endsWith(".zip")) {
            copy.from(project.zipTree(archive));
          } else if (archive.getName().contains(".tar.")) {
            copy.from(project.tarTree(archive));
          }
          copy.into(toolManager.getToolDir(name.get()));
        });
  }

  private void restoreRepositories(List<ArtifactRepository> repositoriesBackup) {
    var repositories = getProject().getRepositories();
    repositories.clear();
    repositories.addAll(repositoriesBackup);
  }
}
