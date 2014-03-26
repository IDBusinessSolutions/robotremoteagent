import sys
from setuptools import setup
from os.path import join, dirname

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

def run_setup(version, scriptargs):
    setup(
      name             = 'robotframework-RobotRemoteAgent',
      version          = version,
      description      = 'Robot Framework keywords to allow the automation of Java applications.',
      author           = 'IDBS',
      author_email     = 'testautomation@idbs.com',
      keywords         = 'robotframework testing testautomation java',
      platforms        = 'any',
      package_dir      = {'' : 'src-python'},
      install_requires = ['robotframework'],
      packages = ['RobotRemoteAgent'],      
      include_package_data = True,
      script_args      = scriptargs  
    )

if __name__=="__main__":
    execfile(join(dirname(__file__),'src-python','RobotRemoteAgent', 'version.py'))
    run_setup(VERSION, sys.argv[1:])

