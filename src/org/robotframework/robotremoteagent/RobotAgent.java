package org.robotframework.robotremoteagent;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

/*
 * Copyright 2013-2014 ID Business Solutions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class RobotAgent {
	
	static Thread someThread = null;
	static Instrumentation instrumentationInstance = null;
	static ClassFileTransformer transformer = null;
	
	public static void StartRobotRemoteAgentServer(ClassLoader classLoader) {
		// Only do this once
		if(someThread==null) {
		    @SuppressWarnings("rawtypes")
			Class runnableClass;
			try {
				instrumentationInstance.removeTransformer(transformer);
				runnableClass = classLoader.loadClass("org.robotframework.robotremoteagent.RobotRemotAgentServer");
			    someThread = new Thread((Runnable) runnableClass.newInstance());
			    someThread.setContextClassLoader(classLoader);
			    someThread.setDaemon(true);
			    someThread.start();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}    	
	  }
	
	
	public static void StartRobotRemoteAgentServer() {
		// Only do this once
		if(someThread==null) {
			someThread = new Thread(new RobotRemotAgentServer());
			someThread.setDaemon(true);
			someThread.start();
		}    	
	  }

	private static boolean isRunningJavaWebStart() {
	    boolean hasJNLP = false;
	    try {
	      Class.forName("javax.jnlp.ServiceManager");
	      hasJNLP = true;
	    } catch (ClassNotFoundException ex) {
	      hasJNLP = false;
	    }
	    return hasJNLP;
	}
	
	
	public static void premain(String agentArguments, final Instrumentation inst) throws Exception {
		
		instrumentationInstance = inst;
		if(isRunningJavaWebStart()) {
			transformer = new ClassListenerTranformer(agentArguments);
			inst.addTransformer(transformer);
		} else {
			StartRobotRemoteAgentServer();
		}
		
		
   }

}
