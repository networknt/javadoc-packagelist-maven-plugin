package com.networknt.javadoc.plugin.packagelist;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaModule;
import com.thoughtworks.qdox.model.JavaModuleDescriptor;
import com.thoughtworks.qdox.model.JavaModuleDescriptor.JavaExports;
import com.thoughtworks.qdox.model.JavaPackage;

import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Mojo(name="gen-package-list")
public class GenPackageList extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    public void execute() throws MojoExecutionException, MojoFailureException {
        String sourceDir = mavenProject.getModel().getBuild().getSourceDirectory();
        String targetDir = mavenProject.getModel().getBuild().getDirectory();
        if (!"pom".equals(mavenProject.getPackaging())) {
            // Obtain maven-javadoc-plugin configuration.
            List<Pattern> excludeREList; // Excluded packages (regular expressions).
            String strDestDir = null; // Optional destination directory.
            try {
                Xpp3Dom configDom = getJavadocPluginConfiguration();
                excludeREList = getExcludedRegExpList(configDom);
                Xpp3Dom destDirDom = configDom.getChild("destDir");
                if (destDirDom != null) {
                    strDestDir = destDirDom.getValue();
                    // It is not documented whether getValue() could return null or not.
                    if (strDestDir != null && strDestDir.length() == 0) {
                        strDestDir = null;
                    }
                }
            } catch (Exception e) {
                excludeREList = null;
                getLog().warn(
                        "Could not obtain the configuration from the maven-javadoc-plugin.");
            }
            try {
                File fSourceDir = new File(sourceDir);
                // First, try to obtain exported packages from module declaration.
                List<String> packageNames = getExportedPackages(fSourceDir);
                if (packageNames.isEmpty()) {
                    // Not a modular project.
                    JavaProjectBuilder javaProjectBuilder = new JavaProjectBuilder();
                    javaProjectBuilder.addSourceTree(fSourceDir);
                    Collection<JavaPackage> packages = javaProjectBuilder.getPackages();
                    if (packages != null && packages.size() > 0) {
                        for (JavaPackage javaPackage : packages) {
                            packageNames.add(javaPackage.getName());
                        }
                    }
                }
                // Remove excluded packages.
                Iterator<String> it = packageNames.iterator();
                while (it.hasNext()) {
                    String pkgName = it.next();
                    if (excludeREList != null && excludeMatch(excludeREList, pkgName)) {
                        it.remove();
                    }
                }
                // If we got the packages, write the list.
                if (!packageNames.isEmpty()) {
                    File destDir;
                    if (strDestDir == null) {
                        destDir = new File(targetDir, "apidocs");
                    } else {
                        destDir = new File(strDestDir);
                    }
                    destDir.mkdirs();
                    FileUtils.fileWrite(new File(destDir, "package-list"), String.join("\n", packageNames));
                    getLog().info("Created package-list");
                } else {
                    getLog().info("Skipping package-list since no packages were found.");
                }
            } catch (Exception e) {
                getLog().warn("Failed to generate package-list, as a result javadoc links may not work..");
            }
        }
    }

    /**
     * Obtains a list of excluded regexp patterns from the
     * {@code excludePackageNames} configuration of the
     * {@code maven-javadoc-plugin}.
     * 
     * @param configDom the configuration of the {@code maven-javadoc-plugin}.
     * @return the list of excluded regexp patterns, or {@code null} if there are no
     *         excluded packages.
     */
    private List<Pattern> getExcludedRegExpList(Xpp3Dom configDom) {
        List<Pattern> excludeREList = null;
        Xpp3Dom epnDom = configDom.getChild("excludePackageNames");
        if (epnDom != null) {
            String excludePackageNames = epnDom.getValue();
            if (excludePackageNames != null && excludePackageNames.length() != 0) {
                String[] excludedList = excludePackageNames.split("[,:;]");
                for (String excludeStr : excludedList) {
                    String reStr = convertExcludeToRegExp(excludeStr);
                    try {
                        Pattern excludeRE = Pattern.compile(reStr);
                        if (excludeREList == null) {
                            excludeREList = new ArrayList<Pattern>();
                        }
                        excludeREList.add(excludeRE);
                    } catch (PatternSyntaxException e) {
                        getLog().warn("Unable to process exclusion " + excludeStr + '.');
                    }
                }
            }
        }
        return excludeREList;
    }

    /**
     * Obtains the configuration of the {@code maven-javadoc-plugin}.
     * 
     * @return the configuration, or {@code null} if it could not be found.
     */
    private Xpp3Dom getJavadocPluginConfiguration() {
        Build build = mavenProject.getModel().getBuild();
        List<Plugin> plugins;
        if (build != null && (plugins = build.getPlugins()) != null) {
            for (Plugin plugin : plugins) {
                String groupId = plugin.getGroupId();
                String artifactId = plugin.getArtifactId();
                if ("maven-javadoc-plugin".equals(artifactId) && "org.apache.maven.plugins".equals(groupId)) {
                    return (Xpp3Dom) plugin.getConfiguration();
                }
            }
        }
        return null;
    }

    /**
     * Attempts to convert a package exclusion expression to a <i>regexp</i>.
     * <p>
     * It may not be fully compatible with the <a href=
     * "https://maven.apache.org/plugins/maven-javadoc-plugin/javadoc-mojo.html#excludePackageNames">behavior
     * of <code>excludePackageNames</code></a>.
     * </p>
     * 
     * @param excludeStr the excluded package expression from
     *                   {@code excludePackageNames}.
     * @return a string containing a compatible <i>regexp</i>.
     */
    private String convertExcludeToRegExp(String excludeStr) {
        if (excludeStr.charAt(0) == '*') {
            if (excludeStr.length() > 1) {
                excludeStr = excludeStr.substring(1);
            } else {
                return ".*";
            }
        } else {
            excludeStr = '^' + excludeStr;
        }
        excludeStr = excludeStr.replace(".", "\\.");
        int elen = excludeStr.length();
        if (excludeStr.charAt(elen - 1) == '*') {
            excludeStr = excludeStr.subSequence(0, elen) + ".*";
        } else {
            excludeStr = excludeStr + "\\.?.*";
        }
        return excludeStr;
    }

    private boolean excludeMatch(List<Pattern> excludeREList, String pkgName) {
        for (Pattern excludeRE : excludeREList) {
            Matcher matcher = excludeRE.matcher(pkgName);
            if (matcher.matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the packages exported by a module.
     * <p>
     * In case this is a modular project, obtain the list of exported packages.
     * </p>
     * 
     * @param sourceDir the source directory.
     * @return the list of exported packages.
     */
    private List<String> getExportedPackages(File sourceDir) {
        List<String> packageNames = new ArrayList<>();
        try {
            JavaProjectBuilder javaProjectBuilder = new JavaProjectBuilder();
            javaProjectBuilder.addSourceFolder(sourceDir);
            Collection<JavaModule> modules = javaProjectBuilder.getModules();
            if (modules != null && modules.size() != 0) {
                for (JavaModule module : modules) {
                    JavaModuleDescriptor descriptor = module.getDescriptor();
                    if (descriptor != null) {
                        Collection<JavaExports> exports = descriptor.getExports();
                        // With the current implementation, 'exports' cannot be null,
                        // but this is not documented so no effective API contract.
                        if (exports != null) {
                            for (JavaExports export : exports) {
                                packageNames.add(export.getSource().getName());
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // That's probably a problem parsing module-info, so we log the exception at debug level.
            getLog().warn("Found a problem processing the module-info file in " + sourceDir.getPath());
            getLog().debug(e);
        }
        return packageNames;
    }

}
