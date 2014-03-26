package org.robotframework.robotremoteagent;

import java.util.ArrayList;

import org.robotframework.javalib.library.AnnotationLibrary;

import java.util.Collection;
import java.util.Collections;

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

public class RemoteAgentLibrary extends AnnotationLibrary {
    public static final String ROBOT_LIBRARY_SCOPE = "GLOBAL";
    public static RemoteAgentLibrary instance;
    private final AnnotationLibrary annotationLibrary = new AnnotationLibrary(
            "org/robotframework/robotremoteagent/keyword/*.class");
    private static final String LIBRARY_DOCUMENTATION = "Library for interfacing RobotFramework with Java applications.\n\n";
    public RemoteAgentLibrary() {
        this(Collections.<String> emptyList());
    }

    protected RemoteAgentLibrary(final String keywordPattern) {
        this(new ArrayList<String>() {
            {
                add(keywordPattern);
            }
        });
    }

    protected RemoteAgentLibrary(Collection<String> keywordPatterns) {
        addKeywordPatterns(keywordPatterns);
        instance = this;
    }

    private void addKeywordPatterns(Collection<String> keywordPatterns) {
        for (String pattern : keywordPatterns) {
            annotationLibrary.addKeywordPattern(pattern);
        }
    }
    
    @Override
    public Object runKeyword(String keywordName, Object[] args) {
        return annotationLibrary.runKeyword(keywordName, toStrings(args));
    }

    @Override
    public String[] getKeywordArguments(String keywordName) {
        return annotationLibrary.getKeywordArguments(keywordName);
    }

    @Override
    public String getKeywordDocumentation(String keywordName) {
        if (keywordName.equals("__intro__"))
            return LIBRARY_DOCUMENTATION;
        return annotationLibrary.getKeywordDocumentation(keywordName);
    }

    @Override
    public String[] getKeywordNames() {
        return annotationLibrary.getKeywordNames();
    }

    private Object[] toStrings(Object[] args) {
        Object[] newArgs = new Object[args.length];
        for (int i = 0; i < newArgs.length; i++) {
            if (args[i].getClass().isArray()) {
                newArgs[i] = args[i];
            } else {
                newArgs[i] = args[i].toString();
            }
        }
        return newArgs;
    }

}
