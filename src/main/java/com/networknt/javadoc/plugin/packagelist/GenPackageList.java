package com.networknt.javadoc.plugin.packagelist;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaPackage;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mojo(name="gen-package-list")
public class GenPackageList extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    public void execute() throws MojoExecutionException, MojoFailureException {
        String sourceDir = mavenProject.getModel().getBuild().getSourceDirectory();
        String targetDir = mavenProject.getModel().getBuild().getDirectory();
        if (mavenProject.getModel().getParent() != null) {
            try {
                JavaProjectBuilder javaProjectBuilder = new JavaProjectBuilder();
                javaProjectBuilder.addSourceTree(new File(sourceDir));
                Collection<JavaPackage> packages = javaProjectBuilder.getPackages();
                if (packages != null && packages.size() > 0) {
                    List<String> packageNames = new ArrayList<>();
                    for (JavaPackage javaPackage : packages) {
                        packageNames.add(javaPackage.getName());
                    }
                    new File(targetDir.concat("/apidocs")).mkdirs();
                    FileUtils.fileWrite(targetDir.concat("/apidocs/package-list"), String.join("\n", packageNames));
                    getLog().info("Created package-list");
                } else {
                    getLog().info("Skipping package-list since no packages were found.");
                }
            } catch (Exception e) {
                getLog().warn("Failed to generate package-list, as a result javadoc links may not work..");
            }
        }
    }
}
