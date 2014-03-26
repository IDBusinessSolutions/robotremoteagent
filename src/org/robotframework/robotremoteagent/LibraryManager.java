package org.robotframework.robotremoteagent;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

import org.robotframework.javalib.library.AnnotationLibrary ;
import org.robotframework.javalib.library.KeywordDocumentationRepository;
import org.robotframework.javalib.library.RobotJavaLibrary;
import org.robotframework.javalib.util.StdStreamRedirecter;

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

public class LibraryManager {
	
	private static LibraryManager instance = new LibraryManager();
	
    private final StdStreamRedirecter streamRedirecter;
    private Instrumentation inst;
    private Map<String, RobotJavaLibrary> libraries;
    private Map<String, String> allKeywords;
	
	private LibraryManager()  {
    	this.streamRedirecter = new StdStreamRedirecter();
		libraries = new HashMap<String, RobotJavaLibrary>();
		allKeywords = new HashMap<String, String>();
		AnnotationLibrary newLibrary = new RemoteAgentLibrary();
		String libraryName = "RemoteAgentLibrary";
		libraries.put(libraryName, newLibrary);
		registerKeywords(libraryName, newLibrary);
	}

	public void setInstumentation(Instrumentation inst) {
		this.inst = inst;
	}
	
	public static LibraryManager getInstance() {
        return instance;
	}
	
	
	public void loadJar(String jarPath){	
    	Method appendMethod;
		try {
			appendMethod = inst.getClass()
			.getMethod("appendToSystemClassLoaderSearch", JarFile.class);
	    	JarFile newJarFile = new JarFile(jarPath);
	    	appendMethod.invoke(inst, newJarFile);
	    	System.err.println("Loaded JAR "+jarPath);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}

	private String normaliseKeyword(String keyword){
		String normalisedKeyword = "";
		// Get rid of spaces and underscores 
		normalisedKeyword = keyword.replace(" ", "");
		normalisedKeyword = normalisedKeyword.replace("_", "");
		normalisedKeyword = normalisedKeyword.toLowerCase();
		return normalisedKeyword;
	}
	
	
	private RobotJavaLibrary getLibraryForKeyword(String keyword){
		String normalisedKeyword = normaliseKeyword(keyword);
		String libraryName = allKeywords.get(normalisedKeyword);
		if(libraryName==null)
			throw new IllegalArgumentException(String.format("No library with keyword %s loaded.",keyword));
		return libraries.get(libraryName); 
	}
	
	public boolean isLibraryAlreadyLoaded(final String libraryName) {
		return libraries.containsKey(libraryName);
	}
	
	public void takeLibraryIntoUse(final String libraryName)  {
		RobotJavaLibrary newLibrary;
		try {
			System.err.println("Taking Library into use "+libraryName);
			newLibrary = (RobotJavaLibrary)Class.forName(libraryName).newInstance();
			libraries.put(libraryName, newLibrary);
			registerKeywords(libraryName, newLibrary);
			System.err.println("Registered Library "+libraryName);
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void registerKeywords(final String libraryName, RobotJavaLibrary newLibrary) {
		String allNewKeywords[] = newLibrary.getKeywordNames();
		for(String newKeyword: allNewKeywords) {
			String normalisedKeyword = normaliseKeyword(newKeyword);
			if(!allKeywords.containsKey(normalisedKeyword))
				allKeywords.put(normalisedKeyword, libraryName);
		}
	}
	
	
    public String[] getKeywordNames() {
    	return allKeywords.keySet().toArray(new String[0]);
    }
    
    public String[] getKeywordArguments(String keywordName) {
    	if(keywordName.equalsIgnoreCase("takeLibraryIntoUse"))
    		return new String[]{"library_path","library_name"};
    	return ((KeywordDocumentationRepository) getLibraryForKeyword(keywordName)).getKeywordArguments(keywordName);
    }
    
    public String getKeywordDocumentation(String keywordName) {
    	return ((KeywordDocumentationRepository) getLibraryForKeyword(keywordName)).getKeywordDocumentation(keywordName);
    }

    public String getStdOut() {
    	return streamRedirecter.getStdOutAsString();
    }
    
    public String getStdErr() {
    	return streamRedirecter.getStdErrAsString();
    }
    
    
    public Object runKeyword(final String keywordName, final Object[] requestParameters) {
		AccessController.doPrivileged( new PrivilegedAction<Void>() {
		public Void run() {
	        streamRedirecter.redirectStdStreams();
			return null; }});
        try {
        	return getLibraryForKeyword(keywordName).runKeyword(keywordName, requestParameters);       	
        } finally {
        	AccessController.doPrivileged( new PrivilegedAction<Void>() {
        		public Void run() {
        			streamRedirecter.resetStdStreams();
        			return null; }});
        }
    }

    
}
