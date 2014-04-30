robotremoteagent
================

A tool for controlling Java applications from a Robot Framework test.

Build pre-requisites
--------------------

The Java part of the tool needs to be built using Maven with the Python 2.7 part requiring Python to be installed.

In order to work in a WebStart environment, the JAR injected into the application being tested needs to be signed. This is included in the pom.xml, but relies keystore information from your maven settings.xml in the .m2 directory.

The settings.xml file should contain the following sections:

	  <profiles>
      <profile>
        <id>keystoreConfig</id>
        <properties>
          <keystore.alias>YOUR ALIAS</keystore.alias>
		      <keystore.path>YOUR PATH</keystore.path>
		      <keystore.password>SECRET</keystore.password>
		      <keystore.type>YOUR TYPE</keystore.type>
        </properties>
      </profile>
    </profiles>
    <activeProfiles>
      <activeProfile>keystoreConfig</activeProfile>
    </activeProfiles>

Once that is configured you should be able to build the agent by using

mvn clean package

python build.py

in the robotremoteagent directory on your machine.

Once the build is complete, the package subdirectory contains a pip installable package of the agent.

