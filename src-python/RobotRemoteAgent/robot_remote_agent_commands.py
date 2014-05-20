from robot.libraries.Remote import Remote
from robot.libraries.BuiltIn import BuiltIn
import robot.version
from robot.api import logger
from robot import utils
import datetime
import time
import os
import tempfile
import subprocess
import fnmatch
import sys
import pickle, json, csv, shutil
import socket

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

#
# From http://code.activestate.com/recipes/576642-persistent-dict-with-multiple-standard-file-format/
#
class PersistentDict(dict):
    """ Persistent dictionary with an API compatible with shelve and anydbm.

    The dict is kept in memory, so the dictionary operations run as fast as
    a regular dictionary.7

    Write to disk is delayed until close or sync (similar to gdbm's fast mode).

    Input file format is automatically discovered.
    Output file format is selectable between pickle, json, and csv.
    All three serialization formats are backed by fast C implementations.

    """

    def __init__(self, filename, flag='c', mode=None, format='pickle', *args, **kwds):
        self.flag = flag                    # r=readonly, c=create, or n=new
        self.mode = mode                    # None or an octal triple like 0644
        self.format = format                # 'csv', 'json', or 'pickle'
        self.filename = filename
        if flag != 'n' and os.access(filename, os.R_OK):
            fileobj = open(filename, 'rb' if format == 'pickle' else 'r')
            with fileobj:
                self.load(fileobj)
        dict.__init__(self, *args, **kwds)

    def sync(self):
        """Write dict to disk"""
        if self.flag == 'r':
            return
        filename = self.filename
        tempname = filename + '.tmp'
        fileobj = open(tempname, 'wb' if self.format == 'pickle' else 'w')
        try:
            self.dump(fileobj)
        except Exception:
            os.remove(tempname)
            raise
        finally:
            fileobj.close()
        shutil.move(tempname, self.filename)    # atomic commit
        if self.mode is not None:
            os.chmod(self.filename, self.mode)

    def close(self):
        self.sync()

    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        self.close()

    def dump(self, fileobj):
        if self.format == 'csv':
            csv.writer(fileobj).writerows(self.items())
        elif self.format == 'json':
            json.dump(self, fileobj, separators=(',', ':'))
        elif self.format == 'pickle':
            pickle.dump(dict(self), fileobj, 2)
        else:
            raise NotImplementedError('Unknown format: ' + repr(self.format))

    def load(self, fileobj):
        # try formats from most restrictive to least restrictive
        for loader in (pickle.load, json.load, csv.reader):
            fileobj.seek(0)
            try:
                return self.update(loader(fileobj))
            except Exception:
                pass
        raise ValueError('File not in a supported format')


# Static Methods
def _get_port_file_path():
    return _get_path_to_file('launched.txt')


def _get_connected_file_path():
    return _get_path_to_file('connected.txt')


def _get_path_to_file(file_name):
    robot_dir = _get_robot_dir()
    remote_agent_directory = os.path.join(robot_dir, "robotremoteagent")
    if not os.path.exists(remote_agent_directory):
        os.makedirs(remote_agent_directory)
    return os.path.join(remote_agent_directory, file_name)


def _get_temp_file(alias):
    return open(os.path.join(tempfile.gettempdir(), 'remote_%s_%s.txt' % (alias, int(time.time()))), 'w')


def _purge_port_file():
    try:
        os.remove(_get_port_file_path())
    except OSError:
        pass


def _register_alias_port(alias, port):
    with PersistentDict(_get_connected_file_path(), 'c', format='json') as connection_database:
        connection_database[alias] = port


def _get_robot_dir():
    if sys.platform == 'win32':
        return os.path.join(os.environ["APPDATA"], "RobotFramework")
    return os.path.join(os.environ["HOME"], ".robotframework")


def _start(alias, command):
    # close_fds enables RIDE to shut down without the application being tested shutting down as well
    subprocess.Popen(command, shell=True, close_fds=True)


def _get_java_tool_options():
    if 'JAVA_TOOL_OPTIONS' in os.environ:
        return os.environ['JAVA_TOOL_OPTIONS']
    return ''



class RobotRemoteAgentCommands(object):
    _port = None
    _take_screenshot_on_failure = True

    def __init__(self):
        self._port = None
        self._all_gateway_ports = PersistentDict(_get_connected_file_path(), 'c', format='json')

    def start_java_application(self, alias, command, timeout="3 Minutes", webstart_main_class_regexp=None):
        """Launches a Java application, setting the environment variables needed for Java instrumentation.
        The application just launched becomes the current application.
        \n\n
        *Arguments*\n
        _alias_\n
        Alias for switching back to the application.\n\n
        _command_\n
        Command to launch the application.\n\n
         _timeout_\n
         (optional) timeout for attempting to get port file (default: 3 Minutes).\n\n
         _webstart_main_class_regexp_\n
         (optional) If the robot remote agent detects that it is running in a webstart application it waits until a
         class matching this regular expression is loaded before launching the agent. This ensures that the agent is launched
         with the correct class loader.\n\n
        *Return value*\n
        None\n\n
        *Precondition*\n
        None\n\n
        *Example*
        | Start Java Application | App1 | java -jar my_application.jar | timeout="10 Minutes" | webstart_main_class_regexp=com.kensingtonspace.client.webstart.WebProcess |
        | Start Java Application | App1 | java -jar my_application.jar |  |  |
        | Take Libraries Into Use | c:/libraries/MyLibrary.jar | MyLibrary |  |  |
        """
        _purge_port_file()
        self._run_command_with_java_tool_options(alias, command, webstart_main_class_regexp)
        self.connect_to_java_application(alias, timeout)

    def _run_command_with_java_tool_options(self, alias, command, webstart_main_class_regexp):
        orig_java_tool_options = _get_java_tool_options()
        tool_options = self.get_java_agent_option(webstart_main_class_regexp)
        logger.info('Settings JAVA_TOOL_OPTIONS={0}'.format(tool_options))
        os.environ['JAVA_TOOL_OPTIONS'] = tool_options
        _start(alias, command)
        os.environ['JAVA_TOOL_OPTIONS'] = orig_java_tool_options

    @staticmethod
    def get_java_agent_option(webstart_main_class_regexp=None):
        """Returns the javaagent command line option that injects the instrumentation Jar into a Java application.\n
        This can be useful if the application to be tested is to be launched indirectly without using the _Start Java
        Application_ keyword.\n\n
        *Arguments*\n
         _webstart_main_class_regexp_\n
         (optional) Adds an argument to the commend that for Webstart applications. If the robot remote agent detects
         that it is running in a Webstart application it waits until a class matching this regular expression is loaded
         before launching the agent. This ensures that the agent is launched
         with the correct class loader.\n\n
        None\n\n
        *Return value*\n
        A string that need to be assigned to the JAVA_TOOL_OPTIONS environment variable.\n\n
        *Precondition*\n
        None\n\n
        *Example*
        | ${option}= | Get java agent option |  
        """
        library_dir = os.path.realpath(os.path.join(os.path.dirname(__file__), "lib"))
        for file in os.listdir(library_dir):
            if fnmatch.fnmatch(file, 'robotremoteagent-*.jar'):
                jar_file = file
        jar_full_path = os.path.join(library_dir, jar_file)
        java_agent_option =  '-javaagent:"{0}"'.format(jar_full_path)
        if not webstart_main_class_regexp is None:
            java_agent_option += '='+webstart_main_class_regexp
        return java_agent_option


    def switch_to_java_application(self, alias):
        """Switches the focus back to an previously connected, running Java application.
        Any actions from this point in the script will be directed to the different application.\n\n
        *Arguments*\n
        _alias_\n
        Alias for switching back to the application.\n\n
        *Return value*\n
        None\n\n
        *Precondition*\n
        None\n\n
        *Example*
        | Switch to Java Application | App1 |
        """
        if self._all_gateway_ports.has_key(alias):
            self._port = self._all_gateway_ports[alias]
        else:
            raise RuntimeError("Unknown application alias {0}.".format(alias))

    def java_application_is_closed(self, alias):
        """Informs the system, that a connected Java application should be closed and can't be connected to anymore.
        This removes the application from the list of applications that can be connected to.\n\n
        *Arguments*\n
        _alias_\n
        Alias for the application that has closed.\n\n
        *Return value*\n
        None\n\n
        *Precondition*\n
        None\n\n
        *Example*
        | Java Application is Closed | App1 |
        """
        if self._all_gateway_ports.has_key(alias):
            del self._all_gateway_ports[alias]
            self._all_gateway_ports.sync()
        else:
            raise RuntimeError("Unknown application alias {0}.".format(alias))

    def _check_current_java_application_is_connected(self):
        return self._check_java_application_is_available_on_port(self._port)

    def _check_current_java_application_is_disconnected(self):
        return not self._check_java_application_is_available_on_port(self._port)

    def _check_java_application_is_available_on_port(self, port):
        remote_url = "http://127.0.0.1:{0}".format(port)
        try:
            remote_java_app = Remote(remote_url)
            # Drop timeout on connection attempt to 3 seconds
            socket.setdefaulttimeout(3)
            # Call method on server to check if there is actually a server there. Only try onec though
            remote_java_app.get_keyword_names(attempts=1)
            # Set timeout back to default
            socket.setdefaulttimeout(None)
        except:
            return False
        return True

    def wait_for_current_java_application_to_close(self, timeout="3 Minutes"):
        """Waits for the currently connected Java application to shut down.\n\n
        *Arguments*\n
        _timeout_\n
        (optional) maximum wait time (default: 3 Minutes).\n\n
        *Return value*\n
        None\n\n
        *Precondition*\n
        None\n\n
        *Example*
        | Wait for current Java Application to Close |  |
        | Wait for current Java Application to Close  | timeout=5 Minutes |
        """
        start_time = datetime.datetime.now()
        timeout_in_seconds = utils.timestr_to_secs(timeout)
        has_timed_out = False
        while self._check_java_application_is_available_on_port(self._port) and not has_timed_out:
            if (datetime.datetime.now() - start_time).seconds > timeout_in_seconds:
                has_timed_out = True
            else:
                time.sleep(3)

        if has_timed_out:
            raise RuntimeError("Timeout waiting for Java application to close.")
        for alias, port in self._all_gateway_ports.items():
            if port==self._port:
                del self._all_gateway_ports[alias]
        self._port = None
        self._all_gateway_ports.sync()


    def connect_to_java_application(self, alias, timeout="3 Minutes"):
        """Connects to an already running Java application and registers it with the specified alias.\n
        The application just connected to becomes the current application.\n If the connection attempt does not succeed
         immediately, it will retry up to the timeout
        *Warning*\n
        This keyword will only connect to the last launched application.\n\n
        *Arguments*\n
        _alias_\n
        Alias for switching back to the application.\n\n
        _timeout_\n
        (optional) timeout for attempting to get a successful connection (default: 3 Minutes).\n\n
        *Return value*\n
        None\n\n
        *Precondition*\n
        None\n\n
        *Example*
        | Connect to Java Application | App1 |  |
        | Connect to Java Application | App1 | timeout=5 Minutes |
        """
        port_file_path = _get_port_file_path()
        attempt = 0
        port = None
        start_time = datetime.datetime.now()
        timeout_in_seconds = utils.timestr_to_secs(timeout)
        has_timed_out = False
        while port is None and not has_timed_out:
            try:
                attempt += 1
                with open(port_file_path, 'r') as port_file:
                    port = int(port_file.readline())
                if not self._check_java_application_is_available_on_port(port):
                    port = None
            # IOError happens if the file hasn't been written yet, ValueError is when the file has been created,
            # but hasn't got any text inside
            except (IOError, ValueError):
                # Just wait
                time.sleep(3)
            if (datetime.datetime.now() - start_time).seconds > timeout_in_seconds:
                has_timed_out = True

        if has_timed_out:
            raise RuntimeError("Timeout connecting to java application.")
        else:
            logger.info("Connected to java application on port {0}.".format(port))
        self._port = port
        self._all_gateway_ports[alias] = port
        self._all_gateway_ports.sync()

    def _check_active_app(self):
        if self._port is None:
            raise RuntimeError("No application selected")

    def _run_keyword_in_java_application(self, name, args, **kwargs):
        remote_url = "http://127.0.0.1:{0}".format(self._port)
        remote_java_app = Remote(remote_url)
        try:
            # Convert all the arguments to strings
            string_arguments = []
            for argument in args:
                string_arguments.append(str(argument))

            # The interface of the Remote library changes in robot framework 2.8.3 to take an additional dictionary for keyword arguments
            if robot.version.get_version() >= '2.8.3':
                return_value = remote_java_app.run_keyword(name, string_arguments, kwargs)
            else:
                return_value = remote_java_app.run_keyword(name, string_arguments)
        except Exception as inst:
            # Take a screenshot if, we need to
            if self._take_screenshot_on_failure:
                output_directory = BuiltIn().replace_variables('${OUTPUTDIR}')
                screenshot_file_name = 'screenshot_java_0000.png'
                index = 0
                while os.path.exists(os.path.join(output_directory, screenshot_file_name)):
                    index += 1
                    screenshot_file_name = 'screenshot_java_{0:04d}.png'.format(index)
                # Use the remote library directly to avoid infinite loops
                # The interface of the Remote library changes in robot framework 2.8.3 to take an additional dictionary for keyword arguments
                if robot.version.get_version() >= '2.8.3':
                    remote_java_app.run_keyword("takeScreenshot", [os.path.join(output_directory, screenshot_file_name)], {})
                else:
                    remote_java_app.run_keyword("takeScreenshot", [os.path.join(output_directory, screenshot_file_name)])
                # Log link to screenshot in ancjhor to make the screenshot clickable for a bigger version
                logger.info('<a href="{0}"><img  src="{0}" width="600px" /></a>'.format(
                    screenshot_file_name.replace("\\", "/")), html=True)
            # Raise exception back to robot framework
            raise
        return return_value

    def take_library_into_use_in_java_application(self, library_path, library_name):
        """Ensures the library jar specified by library_path is loaded into the current application and loads it into 
        use in the current test script. If the library is already loaded this keyword has no effect, but can be safely 
        used again.\n\n
        *Arguments*\n
        _library_path_\n
        Full path to the jar file with the library.\n\n
        _library_name_\n
        Name of the library.\n\n
        *Return value*\n
        None\n\n
        *Precondition*\n
        None\n\n
        *Example*
        | Start Java Application | App1 | java -jar my_application.jar | 
        | Take Libraries Into Use in Java Application | c:/libraries/MyLibrary.jar | MyLibrary |
        """
        self._check_active_app()
        self._run_keyword_in_java_application("takeLibraryIntoUse", [library_path, library_name])

    def load_non_keyword_jar_into_java_application(self, jar_path):
        """Loads a jar not containing any keywords into the classloader of the remote java application.\n\n
        *Arguments*\n
        _jar_path_\n
        Full path to the jar file.\n\n
        *Return value*\n
        None\n\n
        *Precondition*\n
        None\n\n
        *Example*
        | Load non keyword jar into java application | c:/libraries/MyJar.jar |
        """
        self._check_active_app()
        self._run_keyword_in_java_application("loadNonKeywordJar", [jar_path])

    def stop_remote_agent_in_java_application(self):
        """Stops the remote agent in the  java application.\n\n
        *Arguments*\n
        None\n\n
        *Return value*\n
        None\n\n
        *Precondition*\n
        None\n\n
        *Example*
        | Load non keyword jar into java application | c:/libraries/MyJar.jar |
        """
        self._check_active_app()
        self._run_keyword_in_java_application("stopRemoteAgent")

    def disable_taking_screenshots_on_failure(self):
        """Disables the taking of screenshots on keyword failures.\n
        This is useful when keyword failures are expected (eg during synchronisation) to eliminate meaningless
        screenshots in the output directory.\n\n
        *Arguments*\n
        None\n\n
        *Return value*\n
        None\n\n
        *Precondition*\n
        None\n\n
        *Example*
        | Disable Taking Screenshots On Failure |
        """
        self._take_screenshot_on_failure = False

    def enable_taking_screenshots_on_failure(self):
        """Enables the taking of screenshots on keyword failures.\n\n
        *Arguments*\n
        None\n\n
        *Return value*\n
        None\n\n
        *Precondition*\n
        None\n\n
        *Example*
        | Enable Taking Screenshots On Failure |
        """
        self._take_screenshot_on_failure = True
