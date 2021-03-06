/*
 * MIT License
 *
 * Copyright (c) 2019 Choko (choko@curioswitch.org)
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
package org.curioswitch.gradle.helpers.platform;

import java.nio.file.Path;

/** Utilities for working with file paths. */
public final class PathUtil {

  /**
   * Returns a {@link String} representation of the {@link Path} that can be included in a bash
   * invocation.
   */
  public static String toBashString(Path path) {
    var helper = new PlatformHelper();
    if (helper.getOs() != OperatingSystem.WINDOWS) {
      return path.toString();
    } else {
      return "$(cygpath '" + path.toString() + "')";
    }
  }

  /**
   * Returns a {@link String} representation of the {@link String} that can be included in a bash
   * invocation. The {@code path} is often an interpolation.
   */
  public static String toBashString(String path) {
    var helper = new PlatformHelper();
    if (helper.getOs() != OperatingSystem.WINDOWS) {
      return path;
    } else {
      return "$(cygpath " + path + ")";
    }
  }

  /**
   * Returns the name appended with a platform specific exe extension. This currently just adds .exe
   * to the name on Windows.
   */
  public static String getExeName(String name) {
    var helper = new PlatformHelper();
    if (helper.getOs() == OperatingSystem.WINDOWS) {
      return name + ".exe";
    } else {
      return name;
    }
  }

  private PathUtil() {}
}
