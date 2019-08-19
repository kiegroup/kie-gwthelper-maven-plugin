RELEASE HOW-TO
==============

To keep the deploy process as smoothest as possible

1) keep "version" as SNAPSHOT" (release plugin will take care of actual release version)
2) issue mvn clean install release:prepare -Pjboss-release (it will ask about tags/version/whatever)
3) issue mvn release:perform -Pjboss-release (it will updated version on the pom to point to next one, push modifications, and actually deploy the release)

At this point, there should be

1) inside github, master' pom.xml with the next development (SNAPSHOT) version
2) inside Nexus, the "release" and the "SNAPSHOT" (next development) versions

From now on, just follow documentation at [https://developer.jboss.org/wiki/MavenDeployingARelease]() "Closing the Staging Respository"


