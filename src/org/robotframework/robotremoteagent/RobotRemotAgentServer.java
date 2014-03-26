package org.robotframework.robotremoteagent;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

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

public class RobotRemotAgentServer implements Runnable {

	private static boolean stopAgent = false;
	private static LibraryManager robotLibraryManager = null;

	public RobotRemotAgentServer() {
		  robotLibraryManager = LibraryManager.getInstance();
		  robotLibraryManager.setInstumentation(RobotAgent.instrumentationInstance);
	}
	
	@Override
	public void run() {
    	AccessController.doPrivileged( new PrivilegedAction<Void>() {
    		@SuppressWarnings("resource")
			public Void run() {
    			  ServerSocket serversocket = null;
    			    try {
    			      //make a ServerSocket and bind it to given port,
    			      serversocket = new ServerSocket(0);
    			      // Have a 100ms timeout on all the blocking operations
    			      serversocket.setSoTimeout(100);
    			      writePortToLaunchedFile(serversocket.getLocalPort());
    			    }
    			    catch (Exception e) { //catch any errors and print errors to gui
    			    	System.err.println(e.getMessage());
    			    	e.printStackTrace();
    			    }
    			    while (!stopAgent) {
    			        try {
    			          //this call waits/blocks until someone connects to the port we
    			          //are listening to
    			          Socket connectionsocket = serversocket.accept();
    			          //Read the http request from the client from the socket interface
    			          //into a buffer.
    			          InputStream input = connectionsocket.getInputStream();
    			          //Prepare a outputstream from us to the client,
    			          //this will be used sending back our response
    			          //(header + requested file) to the client.
    			          OutputStream output = connectionsocket.getOutputStream();

    			          //as the name suggest this method handles the http request, see further down.
    			          //abstraction rules
    			          http_handler(input, output);
    			        }
    			        catch (SocketTimeoutException e) { 
    			        	// Timeouts are expected.
    			        }
    			        catch (Exception e) { //catch any errors, and print them
    			        	e.printStackTrace();
    			        }
    			      } //go back in loop, wait for next request
					return null;
    			
    		}});
	}

	private static void createDir(String path) {
	    File dir = new File(path);
		if (!dir.exists()) {
	        if (!dir.mkdir()) {
	            throw new RuntimeException("Could not create directory " + path);
	        }
	    }
	}

	private static Node createObjectNode(Document replyDocument, Object keywordReturn) {
		Node scalarNode = replyDocument.createElement("value");
		if(keywordReturn instanceof String) {
			String keywordReturnString = (String)keywordReturn;
			scalarNode.appendChild(replyDocument.createElement("string")).
				appendChild(replyDocument.createTextNode(keywordReturnString));		    		
		}
		else if(keywordReturn instanceof Boolean) {
			Text textNode;
			if((Boolean)keywordReturn==true) 
				textNode = replyDocument.createTextNode("1");
			else
				textNode = replyDocument.createTextNode("0");
			scalarNode.appendChild(replyDocument.createElement("boolean")).appendChild(textNode);		    		
		}
		else if((keywordReturn instanceof Date)) {
			SimpleDateFormat dateformat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss'Z'");
			scalarNode.appendChild(replyDocument.createElement("dateTime.iso8601")).
				appendChild(replyDocument.createTextNode(dateformat.format(keywordReturn)));		    		
		}
		else if((keywordReturn instanceof Float) || (keywordReturn instanceof Double)) {
			scalarNode.appendChild(replyDocument.createElement("double")).
				appendChild(replyDocument.createTextNode(keywordReturn.toString()));		    		
		}
		else if((keywordReturn instanceof Integer) || (keywordReturn instanceof Long) || (keywordReturn instanceof Byte)) {
			scalarNode.appendChild(replyDocument.createElement("int")).
				appendChild(replyDocument.createTextNode(keywordReturn.toString()));		    		
		}
		else if((keywordReturn.getClass().isArray())) {
			Node dataElement = scalarNode.appendChild(replyDocument.createElement("array")).
			appendChild(replyDocument.createElement("data"));
			for(int i=0; i<((Object[])keywordReturn).length; i++)
			{
				dataElement.appendChild(createObjectNode(replyDocument, ((Object[])keywordReturn)[i]));
			}				
			
		}
		else {
			String keywordReturnString = (String)keywordReturn.toString();
			scalarNode.appendChild(replyDocument.createElement("string")).
				appendChild(replyDocument.createTextNode(keywordReturnString));		    		
		}
		return scalarNode;
	}

	private static String getHttpHeader(long payloadLength) {
	    String header = "HTTP/1.0 200 OK\r\n";
	
	    header += "Connection: close\r\n"; //we can't handle persistent connections
	    header += "Content-Type: text/xml\r\n";
	    header += "Content-Length: "+payloadLength+"\r\n";
	    header += "Server: RobotRemoteAgent v1\r\n"; //server name
	    
	    header += "\r\n"; //this marks the end of the httpheader
	    return header;
	  }

	private static String getMethodNameFromRequest (Document XMLRPCrequest) {
		  Node methodNameNode = XMLRPCrequest.getElementsByTagName("methodName").item(0);
		  return methodNameNode.getTextContent();
	  }

	private static String getPathToFile(String file_name) {
	    String robot = getRobotDir();
	    createDir(robot);
	    String remoteapplications = join(robot, "robotremoteagent");
	    createDir(remoteapplications);
	    return join(remoteapplications, file_name);
	}

	private static Object[] getRequestParameters (Document XMLRPCrequest) {
		  NodeList allParameters = XMLRPCrequest.getElementsByTagName("param");
		  List<Object> objects = new ArrayList<Object>();
		  for(int i=0; i<allParameters.getLength(); i++){
			  Node paramChild = allParameters.item(i).getFirstChild();
			  // Skip across any arbitrary whitespace within the node 
			  while(paramChild.getNodeType()!=Node.ELEMENT_NODE)
				  paramChild = paramChild.getNextSibling();
			  if(paramChild.getNodeName().contentEquals("value")) {
				  Node singleValueParameter = paramChild.getFirstChild();
				  // Skip across any arbitrary whitespace within the node 
				  while(singleValueParameter.getNodeType()!=Node.ELEMENT_NODE)
					  singleValueParameter = singleValueParameter.getNextSibling();
				  if(singleValueParameter.getNodeName().contentEquals("string"))
					  objects.add(new String(singleValueParameter.getTextContent()));
				  if(singleValueParameter.getNodeName().contentEquals("array")) {
					  List<Object> subArray = new ArrayList<Object>();
					  Node dataNode = singleValueParameter.getFirstChild();
					  // Skip across any arbitrary whitespace within the node 
					  while(dataNode.getNodeType()!=Node.ELEMENT_NODE)
						  dataNode = dataNode.getNextSibling();
					  if(dataNode.getNodeName().contentEquals("data")){
						  NodeList valueNodes = dataNode.getChildNodes();
						  for(int childNodeIndex=0; childNodeIndex<valueNodes.getLength(); childNodeIndex++) {
							  if(valueNodes.item(childNodeIndex).getNodeType()==Node.ELEMENT_NODE){
								  Node valueNode = valueNodes.item(childNodeIndex);
								  if(valueNode.getNodeName().contentEquals("value")){
									  Node singleValue = valueNode.getFirstChild();
									  // Skip across any arbitrary whitespace within the node 
									  while(singleValue.getNodeType()!=Node.ELEMENT_NODE)
										  singleValue = singleValue.getNextSibling();
									  if(singleValue.getNodeName().contentEquals("string"))
										  subArray.add(new String(singleValue.getTextContent()));
								  }
							  }
						  }
					  }
					 objects.add(subArray.toArray());
				  }
			  }
		  }
		  return objects.toArray();
	  }

	private static String getRobotDir() {
		if (System.getProperty("os.name").startsWith("Windows"))
	        return join(System.getenv("APPDATA"), "RobotFramework");
		return join(System.getenv("HOME"), ".robotframework");
	}

	private static void http_handler(InputStream inputBuffer, OutputStream outputStream) {
	    try {
	    	DocumentBuilderFactory dbFactory =DocumentBuilderFactory.newInstance("com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl",null);
	    	DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	    	byte[] buffer = new byte[128];
	    	String requestString = ""; 
	    	int bytesRead = 0;
	    	// Wait for data
	    	while (inputBuffer.available()==0){
	    		Thread.sleep(10);
	    	}
	
	    	while (inputBuffer.available()>0){
	          bytesRead = inputBuffer.read(buffer);
	    	  for (int i = 0; i < bytesRead; i++){
	    		  requestString += (char)buffer[i];
	    	  }
	    	}
	    	// Trim header
	    	String requestPayload = requestString.substring(requestString.indexOf("\r\n\r\n")+4);
	    	Document request = null;
	    	try{
	    		request = dBuilder.parse(new ByteArrayInputStream(requestPayload.getBytes()));
	    	} catch (Exception ex) {
		    	System.err.println(ex.getMessage());		    		
	    	}
	    	request.getDocumentElement().normalize();
	    	String methodName = getMethodNameFromRequest(request);
	    	Object[] requestParameters = getRequestParameters(request); 
	    	Document replyDocument = dbFactory.newDocumentBuilder().newDocument();
	    	if(methodName.equalsIgnoreCase("run_keyword")){
	    		String keywordName = (String) requestParameters[0];
	    		String keywordResult = "FAIL";
	    		String keywordOutput = "";
	    		Object keywordReturn = null;
	    		String errorString = "";
	    		String traceBack = "";
	    		try {
	    			keywordReturn = robotLibraryManager.runKeyword(keywordName, (Object[]) requestParameters[1]);
	    			keywordResult = "PASS";	
	    			keywordOutput = robotLibraryManager.getStdErr();
	    			keywordOutput += robotLibraryManager.getStdOut();
	    		} catch (Throwable ex) {
	    			keywordResult = "FAIL";
	    			keywordOutput = robotLibraryManager.getStdErr();
	    			keywordOutput += robotLibraryManager.getStdOut();
	    			errorString = ex.getMessage();
	    			StringWriter stringWriter = new StringWriter();
	    			PrintWriter printWriter = new PrintWriter(stringWriter);
	    			ex.printStackTrace(printWriter);
	    			traceBack = stringWriter.toString();
	    		}
	    		if(keywordReturn==null)
	    			keywordReturn = "";
	    		if(errorString==null)
	    			errorString = "";
	    		Node methodResponseParams = replyDocument.createElement("methodResponse").appendChild(replyDocument.createElement("params"));
	    		Node responseStruct = methodResponseParams.appendChild(replyDocument.createElement("param")).
	    				appendChild(replyDocument.createElement("struct"));
	    		Node memberNode = responseStruct.appendChild(replyDocument.createElement("member"));
	    		memberNode.appendChild(replyDocument.createElement("name")).appendChild(replyDocument.createTextNode("status"));
	    		memberNode.appendChild(replyDocument.createElement("value")).appendChild(replyDocument.createElement("string")).
						appendChild(replyDocument.createTextNode(keywordResult));
	    		memberNode = responseStruct.appendChild(replyDocument.createElement("member"));
	    		memberNode.appendChild(replyDocument.createElement("name")).appendChild(replyDocument.createTextNode("output"));
	    		memberNode.appendChild(replyDocument.createElement("value")).appendChild(replyDocument.createElement("string")).
					appendChild(replyDocument.createTextNode(keywordOutput));
				memberNode = responseStruct.appendChild(replyDocument.createElement("member"));
				memberNode.appendChild(replyDocument.createElement("name")).appendChild(replyDocument.createTextNode("return"));
				memberNode = responseStruct.appendChild(replyDocument.createElement("member"));
				memberNode.appendChild(createObjectNode(replyDocument, keywordReturn));
				memberNode = responseStruct.appendChild(replyDocument.createElement("member"));
				memberNode.appendChild(replyDocument.createElement("name")).appendChild(replyDocument.createTextNode("error"));
				memberNode.appendChild(replyDocument.createElement("value")).appendChild(replyDocument.createElement("string")).
					appendChild(replyDocument.createTextNode(errorString));		    		
				memberNode = responseStruct.appendChild(replyDocument.createElement("member"));
				memberNode.appendChild(replyDocument.createElement("name")).appendChild(replyDocument.createTextNode("stacktrace"));
				memberNode.appendChild(replyDocument.createElement("value")).appendChild(replyDocument.createElement("string")).
					appendChild(replyDocument.createTextNode(traceBack));		    		
	    		replyDocument.appendChild(methodResponseParams);
	    	}
	    	else if (methodName.equalsIgnoreCase("get_keyword_names")) {
	    		String[] allKeywords = robotLibraryManager.getKeywordNames();
	    		Node methodResponseParams = replyDocument.createElement("methodResponse").appendChild(replyDocument.createElement("params"));
	    		Node array = methodResponseParams.appendChild(replyDocument.createElement("param")).
	    				appendChild(replyDocument.createElement("value")).
	    				appendChild(replyDocument.createElement("array")).
	    				appendChild(replyDocument.createElement("data"));
	    		for(String keyword: allKeywords)
	    			array.appendChild(replyDocument.createElement("value")).appendChild(replyDocument.createElement("string")).appendChild(replyDocument.createTextNode(keyword));
	    		replyDocument.appendChild(methodResponseParams);
	    	}
	    	else if (methodName.equalsIgnoreCase("get_keyword_arguments")) {
	    		String keywordName = (String) requestParameters[0];
	    		String[] allArguments = robotLibraryManager.getKeywordArguments(keywordName);
	    		Node methodResponseParams = replyDocument.createElement("methodResponse").appendChild(replyDocument.createElement("params"));
	    		Node array = methodResponseParams.appendChild(replyDocument.createElement("param")).
	    				appendChild(replyDocument.createElement("value")).
	    				appendChild(replyDocument.createElement("array")).
	    				appendChild(replyDocument.createElement("data"));
	    		for(String argument: allArguments)
	    			array.appendChild(replyDocument.createElement("value")).appendChild(replyDocument.createElement("string")).appendChild(replyDocument.createTextNode(argument));
	    		replyDocument.appendChild(methodResponseParams);
	    	}
	    	else if (methodName.equalsIgnoreCase("get_keyword_documentation")) {
	    		String keywordName = (String) requestParameters[0];
	    		String keywordDocumentation = robotLibraryManager.getKeywordDocumentation(keywordName);
	    		Node methodResponseParams = replyDocument.createElement("methodResponse").appendChild(replyDocument.createElement("params"));
	    		methodResponseParams.appendChild(replyDocument.createElement("param")).
	    				appendChild(replyDocument.createElement("value")).
	    				appendChild(replyDocument.createElement("string")).
	    				appendChild(replyDocument.createTextNode(keywordDocumentation));
	    		replyDocument.appendChild(methodResponseParams);
	    	}
	    	else {
	    		String[] allKeywords = robotLibraryManager.getKeywordNames();
	    		Node methodResponseParams = replyDocument.createElement("methodResponse").appendChild(replyDocument.createElement("params"));
	    		Node array = methodResponseParams.appendChild(replyDocument.createElement("param")).
	    				appendChild(replyDocument.createElement("value")).
	    				appendChild(replyDocument.createElement("array")).
	    				appendChild(replyDocument.createElement("data"));
	    		for(String keyword: allKeywords)
	    			array.appendChild(replyDocument.createElement("value")).appendChild(replyDocument.createElement("string")).appendChild(replyDocument.createTextNode(keyword));
	    		replyDocument.appendChild(methodResponseParams);
	    	}
	    	Transformer transformer = TransformerFactory.newInstance().newTransformer();
	    	transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
	    	StreamResult result = new StreamResult(new StringWriter());
	    	DOMSource source = new DOMSource(replyDocument);
	    	transformer.transform(source, result);
	    	String replyString = result.getWriter().toString();
	    	byte[] rawReplyBody = replyString.getBytes("UTF-8");
	    	String stringHttpHeader = getHttpHeader(rawReplyBody.length);
	    	byte[] rawHeader = stringHttpHeader.getBytes("UTF-8");
	    	outputStream.write(rawHeader);
	    	outputStream.write(rawReplyBody);
	    	outputStream.close();
	    }
	
	    catch (Exception e) {
	    	e.printStackTrace();
	    }
	
	  }

	private static String join(String item1, String item2) {
		return item1 + File.separator + item2;
	}

	public static void stopRobotAgent() {
		System.err.println("Stopping agent");
		stopAgent = true;
	}

	private static void writePortToLaunchedFile(int port)
	{
		try {
		    PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(getPathToFile("launched.txt"), false)));
		    out.println(port);
		    out.close();
		} catch (IOException e) {
		    //oh noes!
		}
	}

}
