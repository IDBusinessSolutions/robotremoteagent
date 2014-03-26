package org.robotframework.robotremoteagent.keyword;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.robotframework.javalib.annotation.ArgumentNames;
import org.robotframework.javalib.annotation.RobotKeyword;
import org.robotframework.javalib.annotation.RobotKeywords;
import org.robotframework.robotremoteagent.LibraryManager;
import org.robotframework.robotremoteagent.RobotAgent;
import org.robotframework.robotremoteagent.RobotRemotAgentServer;

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

@RobotKeywords
public class keywords {
		
    @RobotKeyword("Loads a library into the current application being tested.\n\n")
    @ArgumentNames({"libraryPath","libraryName"})
    public void takeLibraryIntoUse(String libraryPath, String libraryName) {
    	LibraryManager javaLibraryManager = LibraryManager.getInstance();
    	// Don't do anything if the library is already loaded
    	if(javaLibraryManager.isLibraryAlreadyLoaded(libraryName)==false) {
        	javaLibraryManager.loadJar(libraryPath);
        	javaLibraryManager.takeLibraryIntoUse(libraryName);
    		
    	}
    }

    @RobotKeyword("Loads a java JAR into the current application being tested.\n\n")
    @ArgumentNames({"jarPath"})
    public void loadNonKeywordJar(String jarPath) {
    	LibraryManager javaLibraryManager = LibraryManager.getInstance();
    	javaLibraryManager.loadJar(jarPath);
    }
    
    
    @RobotKeyword("Stops the remote agent.\n\n")
    public void stopRemoteAgent() {
    	RobotRemotAgentServer.stopRobotAgent();
    }

    @RobotKeyword("Takes a screeshots of the whole screen and writes it to the file.\n\n If the file alreadye exists, it is overwritten.\n")
    @ArgumentNames({"screenshotFileName"})
	public void takeScreenshot (String screenshotFileName) throws AWTException, IOException {
		File screenshotFile = new File(screenshotFileName);
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screens = ge.getScreenDevices();
	
		Rectangle allScreenBounds = new Rectangle();
		int maxXCoordinate = Integer.MIN_VALUE;
		int maxYCoordinate = Integer.MIN_VALUE;
		for (GraphicsDevice screen : screens) {
			Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();	
			// Make sure that we can handle extra screens on the left and to the top of the primary screen
			allScreenBounds.x = Math.min(allScreenBounds.x, screenBounds.x);			  
			allScreenBounds.y = Math.min(allScreenBounds.y, screenBounds.y);			  
	
			// Calculate maximum screen coordinates
			maxYCoordinate = Math.max(maxYCoordinate, (screenBounds.y+screenBounds.height));
			maxXCoordinate = Math.max(maxXCoordinate, (screenBounds.x+screenBounds.width));
		}
		allScreenBounds.width = maxXCoordinate-allScreenBounds.x;
		allScreenBounds.height = maxYCoordinate-allScreenBounds.y;
	  
		Robot robot;
		robot = new Robot();
		BufferedImage screenShot = robot.createScreenCapture(allScreenBounds);
		ImageIO.write(screenShot, "png", screenshotFile);
	  }

    

    
}
