import robot_remote_agent_commands
import os

#
# Copyright 2013-2014 ID Business Solutions
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

THIS_DIR = os.path.dirname(os.path.abspath(__file__))
execfile(os.path.join(THIS_DIR, 'version.py'))
__version__ = VERSION

class RobotRemoteAgent(robot_remote_agent_commands.RobotRemoteAgentCommands):
    """This library allows Robot Framework to control Java application by injecting an XML-RPC server into the application.\n
    The library uses the JAVA_TOOL_OPTIONS environment variable to inject a Jar file into an application.\n
    In order to use this application with JavaWS applications, the following needs to be added to the <JAVA_HOME>/lib/security/java.policy file on Windows. The name of the user account running the tests needs to be substituted for *<<CURRENT USER>>*.\n
    grant {\n
  permission java.lang.RuntimePermission "setIO";\n
  permission java.lang.RuntimePermission "accessClassInPackage.sun.instrument";\n
  permission java.lang.RuntimePermission "getenv.APPDATA";\n
  permission java.net.SocketPermission "localhost:1024-", "accept";\n
  permission java.io.FilePermission "<<ALL FILES>>", "read";\n
  permission java.io.FilePermission "C:\\\\Users\\\ *<<CURRENT USER>>* \\\\AppData\\\\Roaming\\\\RobotFramework\\\\robotremoteagent\\\*", "write";\n
  permission java.util.PropertyPermission "jemmy.*", "read, write";\n
  permission java.util.PropertyPermission "drivers.*", "read, write";\n
  permission java.awt.AWTPermission "*";\n
  permission java.util.PropertyPermission "org.robotframework.*", "read, write";\n
  permission java.util.PropertyPermission "java.class.path", "read";\n
  permission java.util.PropertyPermission "co.*", "read";\n
  permission java.lang.RuntimePermission "shutdownHooks";\n
  permission java.lang.RuntimePermission "accessDeclaredMembers";\n
  permission java.lang.reflect.ReflectPermission "suppressAccessChecks";\n
  permission java.util.PropertyPermission "mrj.*", "read";\n
  permission java.lang.RuntimePermission "getClassLoader";\n
};\n
    \n\n
    """
    
    ROBOT_LIBRARY_SCOPE = 'GLOBAL'
    ROBOT_LIBRARY_VERSION = __version__
   
    def __init__(self):
         robot_remote_agent_commands.RobotRemoteAgentCommands.__init__(self)