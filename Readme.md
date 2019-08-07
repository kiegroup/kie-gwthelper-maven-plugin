KIE GWT Helper Maven Plugin
=======================

Scope of this plugin is to include arbitrary directories (in whatever location of local filesystem) to the runtime sources of a GWT project,
so that GWT' Super Dev Mode will listen for changes also in those sources, eventually recompiling them when they change.

The main difference with the maven-dependency addSources goal is that this plugin recursively scan a given directory to find
GWT modules, eventually filtering them based on  **includes/excludes** patterns (optional).

GWT modules are individuated if the directory contains a **src/main/resources/()/().gwt.xml** file.

**includes/excludes** patterns are simple **contains** evaluation on the (full) **gwt.xml** file name; i.e. they are matched if that name **contains** those pattern, **case-sensitive**.

**includes/excludes are mutually exclusive!** If both are provided, the plugin will throw an exception.

Moreover, it requires a single parameter to define the folder to scan, so that these parameter may be included as **property** in private user settings,
to allow different configuration on different machine.

 Available parameters:

    excludes
      Comma-separated pattern to match to exclude modules. Does not use regex,
      but simple string

    includes
      Comma-separated list of patterns to match to include modules. Does not use
      regex, but simple string

    rootDirectories
      Comma-separated list of absolute/relative paths of directory to scan.
      Required: Yes



Here's an example of a valid configuration:

    <plugin>
          <groupId>org.kie</groupId>
          <artifactId>kie-gwthelper-maven-plugin</artifactId>
          <version>1.1</version>
          <executions>
            <execution>
                <id>add-source</id>
                <phase>generate-sources</phase>
                <goals>
                    <goal>add-source</goal>
                </goals>
                <configuration>
                    <excludes>API,Mock</excludes> <!-- will exclude all GWT module whose configuration file name contains API or Mock -->
                    <rootDirectories>../common-screens,/home/user/Developing/git/parent-big/external-widgets</rootDirectories> <!-- will search inside those two directories -->
                </configuration>
             </execution>
          </executions>
    </plugin>

Inheritance goal
----------------

Scope of this goal is to print out the "inheritance" tree of GWT module.

For each "parent" GWT module, it will print out inherited ones with the artifacts that provides them, or it will raise a WARNING if no provider artifact is found.
Analysis may be done recursively to see the whole inheritance tree, or may be reduced to only the first level.

  Available parameters:

    failOnWarning (Default: false)
      Whether to fail the build if an inheritance warning is found.
      User property: failOnWarning

    fileName (Default: inheritance.xml)
      Generated file name

    fileOutput (Default: false)
      Whether to write output to file
      User property: fileOutput

    verbose (Default: false)
      Whether to have a verbose output
      User property: verbose


