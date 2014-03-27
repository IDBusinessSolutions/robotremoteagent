import setup
import shutil
import os
import glob
import fnmatch
import robot.libdoc as libdoc

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

#name and package dir
library_name = 'RobotRemoteAgent'
packages_dir = os.path.abspath(os.path.join(os.path.dirname(__file__),'package'))
if not os.path.exists(packages_dir):
    os.makedirs(packages_dir)

# Make sure no old jar files of xml files are in the lib directory
for file_name in os.listdir(os.path.join(os.path.dirname(__file__),'src-python',library_name,'lib')):
    if fnmatch.fnmatch(file_name, library_name+'-*.jar') or fnmatch.fnmatch(file_name, library_name+'-*.xml'):
        os.remove(os.path.join(os.path.dirname(__file__),'src-python',library_name,'lib',file_name))



# Copy Java library
java_target_dir = os.path.join(os.path.dirname(__file__),'target')
for file in os.listdir(java_target_dir):
    if fnmatch.fnmatch(file, 'robotremoteagent-*.jar'):
        jar_file = file
jar_full_path = os.path.join(java_target_dir,jar_file)
shutil.copy(jar_full_path, os.path.join(os.path.dirname(__file__),'src-python',library_name,'lib'))

#version
execfile(os.path.join(os.path.dirname(__file__), 'src-python', library_name, 'version.py'))

#generate documentation from latest code
if not os.path.exists('doc'):
    os.makedirs('doc')
libdoc.libdoc('src-python/{0}'.format(library_name), 'doc/{0}.html'.format(library_name), version=VERSION)

#generate the package
setup.run_setup(VERSION, ['sdist'])

#copy package to packages directory
doc_files = glob.iglob(os.path.join(os.path.dirname(__file__), 'dist/*.*'))
for file in doc_files:
    if os.path.isfile(file):
        shutil.copy(file, packages_dir)
        
#tidy up build folders
shutil.rmtree('dist')
shutil.rmtree('doc')
egg_info_folders = glob.iglob(os.path.join(os.path.dirname(__file__), 'src-python/*.egg-info'))
for folder in egg_info_folders:
    shutil.rmtree(folder)
    
#build confirmation message
print "Build successful"