
 /**
  ***************************************
  * Tier Performance Metics Monitor Plugin
  ***************************************
  * Author: Daniel Pohanka (Dynatrace)
  * Version: 2.0.1
  * Created: 12/21/2015
  * Modified: 7/24/2017
  *
  * This plugin retrieves the response, execution, and CPU time per trier in the transaction flow dashlet.
  * For information, please visit https://github.com/dynaTrace/Dynatrace-Tier-Time-Monitor
  **/ 

package com.dynatrace.monitor;

import com.dynatrace.diagnostics.pdk.*;
import java.util.logging.Logger;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
//import javax.xml.xpath.XPathVariableResolver;
import javax.xml.namespace.QName;

import java.util.*; 

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;

import org.apache.http.client.ClientProtocolException;
import java.io.IOException;

public class TierTimeMonitor implements Monitor {

	private static final Logger log = Logger.getLogger(TierTimeMonitor.class.getName());
	
	// measure constants
	private static final String METRIC_GROUP_1 = "Tier Time";
	private static final String METRIC_GROUP_2 = "Tier Count";
	private static final String MSR_ResponseTime = "Response Time";
	private static final String MSR_ExecTime = "Execution Time";
	private static final String MSR_ExecCPUTime = "Execution CPU Time";
	private static final String MSR_TransactionCount = "Transaction Count";
	private static final String MSR_TransactionCountRate = "Transaction Count Rate";

	//variables
	private Collection<MonitorMeasure>  measures  = null;
	private URLConnection connection;
	private String urlprotocol;
	private int urlport;
	private String dynaTraceURL;
	private String username;
	private String password;
	private String responseTimeMeasure;
	private String execTimeMeasure;
	private String execCPUTimeMeasure;
	private String transactionCountMeasure;
	private MonitorMeasure dynamicMeasure;
	private NodeList xpathNodeList;	
	private String splitOption;

	/**
	 * Initializes the Plugin. 
	 * 
	 * If the returned status is null or the status code is a
	 * non-success code then {@link Plugin#teardown() teardown()} will be called
	 * next.
	 *
	 * Resources like sockets or files can be opened in this method.
	 * @param env
	 *            the configured <tt>MonitorEnvironment</tt> for this Plugin;
	 *            contains subscribed measures, but <b>measurements will be
	 *            discarded</b>
	 * 
	 * @return a Status object that describes the result of the method call
	 */
	@Override
	public Status setup(MonitorEnvironment env) throws Exception {
		
		log.finer("*****BEGIN PLUGIN LOGGING*****");
		log.finer("Entering setup method");
		log.finer("Entering variables from plugin.xml");
		
		urlprotocol = env.getConfigString("protocol");
		urlport = env.getConfigLong("httpPort").intValue();
		
		username = env.getConfigString("username");
		password = env.getConfigPassword("password");
		
		if (username.equals("") || password.equals("")){
			log.severe("username and password are required");
				return new Status(Status.StatusCode.ErrorInternal);
		}
		
		log.finer("URL Protocol: " + urlprotocol);
		log.finer("URL Port: " + urlport);
		log.finer("Username: " + username);
		
		//set up measures
		String aggregation = env.getConfigString("aggregation");
		responseTimeMeasure = "response_" + aggregation;
		execTimeMeasure = "exec_" + aggregation;
		execCPUTimeMeasure = "exec_cpu_" + aggregation;
		transactionCountMeasure = "remoting_count"; // new code for count
	
		//determine splitting (Agent Name / Agent Group / Technology)
		splitOption = env.getConfigString("splitChoice");
		log.fine("split results by: " + splitOption);
		if (splitOption.equals("Agent Group")){
			splitOption = "group";}
		else if (splitOption.equals("Agent Name")){
			splitOption = "name";}
		else if (splitOption.equals("Technology")){
			splitOption = "technology";}
		log.finer("splitOption: " + splitOption);
		
		//Create Report Url
		dynaTraceURL = "/rest/management/reports/create/TransactionFlow?type=XML&format=XML+Export";
		if (env.getConfigString("systemProfileFilter").equals("")){
			log.severe("System Profile Filter entry is required");
				return new Status(Status.StatusCode.ErrorInternal);
		}
		dynaTraceURL = dynaTraceURL + "&source=live:" + env.getConfigString("systemProfileFilter");
		String timeframe = env.getConfigString("timeframeFilter").replaceAll(" ",":").toUpperCase();
		dynaTraceURL = dynaTraceURL +  "&filter=tf:OffsetTimeframe?" + timeframe;
		
		if (env.getConfigBoolean("filterBoolean")==true){
			if(env.getConfigBoolean("agentFilterBoolean")==true){
				String agentGroupFilter;
				String agentPatternFilter;
				if (env.getConfigString("agentFilter").equals("Agent Group") && !(agentGroupFilter = env.getConfigString("agentGroupFilter")).equals("")){
				dynaTraceURL = dynaTraceURL + "&filter=ag:AgentGroups?" + agentGroupFilter;
				}
				else if (env.getConfigString("agentFilter").equals("Agent Pattern") && !(agentPatternFilter = env.getConfigString("agentPatternFilter")).equals("")){
					dynaTraceURL = dynaTraceURL + "&filter=ag:AgentsByPattern?" + agentPatternFilter + "@" + env.getConfigString("agentPatternMatchType");
				}
			}
			if(env.getConfigBoolean("btBoolean")==true){
				String btFilter;
				if (!(btFilter = env.getConfigString("btFilter")).equals("")){	
					dynaTraceURL = dynaTraceURL + "&filter=bt:" + btFilter;}
			}
		}
		
		log.finer("Report URL: " + dynaTraceURL);
		log.finer("Exiting setup method");
		
		return new Status(Status.StatusCode.Success);
	}

	/**
	 * Executes the Monitor Plugin to retrieve subscribed measures and store
	 * measurements.
	 *
	 * 
	 * This method is called at the scheduled intervals. If the Plugin execution
	 * takes longer than the schedule interval, subsequent calls to
	 * {@link #execute(MonitorEnvironment)} will be skipped until this method
	 * returns. After the execution duration exceeds the schedule timeout,
	 * {@link TaskEnvironment#isStopped()} will return true. In this
	 * case execution should be stopped as soon as possible. If the Plugin
	 * ignores {@link TaskEnvironment#isStopped()} or fails to stop execution in
	 * a reasonable timeframe, the execution thread will be stopped ungracefully
	 * which might lead to resource leaks!
	 *
	 * @param env
	 *            a MonitorEnvironment object that contains the
	 *            Plugin configuration and subscribed measures. These
	*            MonitorMeasures can be used to store measurements.
	 * @return a Status object that describes the result of the method call
	 */
	@Override
	public Status execute(MonitorEnvironment env) throws Exception {
				
		log.finer("Entering execute method");
		
		log.finer("Entering URL Setup");
		URL overviewurl = new URL(urlprotocol, env.getHost().getAddress(), urlport, dynaTraceURL);		
		log.fine("Executing URL: " + overviewurl.toString());
		
		try {
			
			//login to dynatrace server
			log.finer("Entering username/password setup");
			String userpass = username + ":" + password;
			String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());
			
			disableCertificateValidation();
				
			//URL to grab XML file
			log.finer("Entering XML file grab");
			connection = overviewurl.openConnection();
			connection.setRequestProperty("Authorization", basicAuth);
			connection.setConnectTimeout(50000);
			
			InputStream responseIS = connection.getInputStream();	
			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = xmlFactory.newDocumentBuilder();
			Document xmlDoc = docBuilder.parse(responseIS);
			XPathFactory xpathFact = XPathFactory.newInstance();
			XPath xpath = xpathFact.newXPath();
												
			//used to store each unique tier and its values
			Set<String> uniqueTierSet = new HashSet<String>();
			xpathNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/admdashlet/adm/agent", xmlDoc, XPathConstants.NODESET);
			log.finer("number of unique tiers = " + xpathNodeList.getLength());
			
			//count number of unique rows
			if (xpathNodeList.getLength() >= 1)
			{
				for (int i = 0; i < xpathNodeList.getLength(); ++i){
					log.finer("tier noder list iteration: " + i);
					String tempString = xpathNodeList.item(i).getAttributes().getNamedItem(splitOption).toString();
					log.finer("tempString orig: " + tempString);
					String changedTempString = tempString.replaceAll("\"","").replace(splitOption + "=","");
					log.finer("tempString chg: " + changedTempString);
					uniqueTierSet.add(changedTempString);
				}
			}	
					
			log.finer("number of unique rows = " + uniqueTierSet.size());
			String[] tempStringArray = uniqueTierSet.toArray(new String[0]);
										
			//loop through array of unique tiers
			for (int j = 0; j < uniqueTierSet.size(); ++j){
				log.finer("Splitting for tempString: " + tempStringArray[j]);
				dynamicMetric(env, xpath, tempStringArray[j], xmlDoc);
			}
			
		} catch (ClientProtocolException e) {
			log.severe("ClientProtocolException: " + e);
			return new Status(Status.StatusCode.ErrorInternal);

		} catch (IOException e) {
			log.severe("IOException: " + e);
			return new Status(Status.StatusCode.ErrorInternal);

		} catch (Exception e){
			log.severe("Exception: " + e);
			return new Status(Status.StatusCode.ErrorInternal);
		}
		
		log.finer("Exiting execute method");
		log.finer("*****END PLUGIN LOGGING*****");
		
		return new Status(Status.StatusCode.Success);
	}
	
	/**
	 * Converts an attribute map from a NamedNodeMap objejt to a String value.
	 *
	 * @param 	env
	 *          	a MonitorEnvironment object that contains the
	 *            	Plugin configuration and subscribed measures. These
	*            	MonitorMeasures can be used to store measurements.
	 *			xpath
	 *          	a XPath object that contains syntax for defining parts 
	 *				of an XML document. xpath uses path expressions to navigate in XML documents. 
	 *				xpath contains a library of standard functions. 
	 *			tempStringMeasure
	 *          	a String that contains the to be evaluated splitting
	 *			xmlDoc
	 *          	a document objectobject that contains the parsed xml response
	 */
	private void dynamicMetric(MonitorEnvironment env, XPath xpath, String tempStringMeasure, Document xmlDoc) throws XPathExpressionException, NullPointerException {
		
		log.finer("Entering dynamicMetrics method");
		
		//assign string as a variable to use in xPath evaluate statement
		MapVariableResolver vr = new MapVariableResolver();
		vr.setVariable("myVar", tempStringMeasure);
		xpath.setXPathVariableResolver(vr);
		log.fine("myVar: " + vr.resolveVariable( new QName ("myVar")));
		NodeList tierNodeList = null;
		NodeList countNodeList = null;
			
		if (splitOption.equals("name")){
			log.finer("dynamicMetric splitOption = name");
			tierNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/admdashlet/adm/agent[contains(@name, $myVar)]", xmlDoc, XPathConstants.NODESET);
			countNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/admdashlet/adm_links/agent[contains(@to, $myVar)]", xmlDoc, XPathConstants.NODESET); //new for count
		}
		else if (splitOption.equals("group")){
			log.finer("dynamicMetric splitOption = group");
			tierNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/admdashlet/adm/agent[contains(@group, $myVar)]", xmlDoc, XPathConstants.NODESET);
		}
		
		else if (splitOption.equals("technology")){
			log.finer("dynamicMetric splitOption = technology");
			tierNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/admdashlet/adm/agent[contains(@technology, $myVar)]", xmlDoc, XPathConstants.NODESET);
		}
		
		log.finer("Size tierNodeList: " + tierNodeList.getLength());
				
		//Response Time
		if ((measures = env.getMonitorMeasures(METRIC_GROUP_1, MSR_ResponseTime)) != null && !measures.isEmpty()) {					
			log.fine("**************Measure Response Time*****************");
			assignMeasureValue(env, tempStringMeasure, calculateMapAvg(getResultMap(tierNodeList, responseTimeMeasure, "name")));
		}
		
		
		//Exec Time		
		if ((measures = env.getMonitorMeasures(METRIC_GROUP_1, MSR_ExecTime)) != null && !measures.isEmpty()) {
			log.fine("**************Measure Execution Time*****************");
			assignMeasureValue(env, tempStringMeasure, calculateMapAvg(getResultMap(tierNodeList, execTimeMeasure, "name")));
		}
		
		
		//Exec CPU Time
		if ((measures = env.getMonitorMeasures(METRIC_GROUP_1, MSR_ExecCPUTime)) != null && !measures.isEmpty()) {
			log.fine("**************Measure Execution CPU Time*****************");
			assignMeasureValue(env, tempStringMeasure, calculateMapAvg(getResultMap(tierNodeList, execCPUTimeMeasure, "name")));
		}
		
		
		//Transaction Count
		try{
			if ((measures = env.getMonitorMeasures(METRIC_GROUP_2, MSR_TransactionCount)) != null && !measures.isEmpty()) {
				
				log.fine("*******************Measure TransactionCount*****************");
				
				HashMap<String, Double> transactionCountMap = getResultMap(countNodeList, transactionCountMeasure, "to");
				log.fine("transactionCountMap size= " + transactionCountMap.size());
				
				for(Map.Entry<String, Double> m:transactionCountMap.entrySet()){  
					log.fine("Map Entry: " + m.getKey() + " " + m.getValue());
					assignMeasureValue(env, m.getKey(), m.getValue());				
				}	
	  
			} 
		} 
		catch (NullPointerException e){
			log.severe("Exception: " + e);
			log.severe("Transaction Count can only be calculated with Agent Name splitting. Please adjust your monior configuration.");
		}
		
		//Transaction Count Rate
		try {
			if ((measures = env.getMonitorMeasures(METRIC_GROUP_2, MSR_TransactionCountRate)) != null && !measures.isEmpty()) {
				
				log.fine("*******************Measure TransactionCount Rate*****************");
				
				HashMap<String, Double> transactionCountRateMap = getResultMap(countNodeList, transactionCountMeasure, "to");
				log.finer("transactionCountRateMap size= " + transactionCountRateMap.size());
				
				log.fine("Calculating Sum");
				double transactionCountSum = calculateSum(transactionCountRateMap);			
				log.fine("count Sum: " + transactionCountSum);
				
				for(Map.Entry<String,Double> m:transactionCountRateMap.entrySet()){  
					log.fine("Map Entry: " + m.getKey() + " " + m.getValue());
					double transactionCountRate = m.getValue()/transactionCountSum;
					transactionCountRate = Math.round(transactionCountRate * 10000d) / 10000d;
					transactionCountRate = transactionCountRate*100;
					log.fine("transactionCountRate = " + transactionCountRate + "%");
					assignMeasureValue(env, m.getKey(), transactionCountRate);				
				}

			}
		} 
		catch (NullPointerException e){
			log.severe("Exception: " + e);
			log.severe("Transaction Count Rate can only be calculated with Agent Name splitting. Please adjust your monior configuration.");
		}
		
		log.finer("Exiting dynamicMetricsCollector method");	
	}
	
	private void assignMeasureValue(MonitorEnvironment env, String measureSplitName, double resultValue) {
		
		log.finer("Assigning Measure Value");
		
		String comparisonString = measureSplitName;
		String[] parts = measureSplitName.split("@");
		String [] parts2 = parts[0].split("\\[");
		comparisonString = parts2[0];
		
		for (MonitorMeasure measure : measures){			
			log.info(measure.getMeasureName() + " for " + measureSplitName + " = " + resultValue);
			log.fine("Measure Name: " + measure.getMeasureName());
			log.fine("Measure Split Name: " + measureSplitName);
			log.fine("Measure Comparison String: " + comparisonString);
			log.fine("Parameter: " + measure.getParameter("Tier Filter"));
			
			if (measure.getParameter("Tier Filter").equals("none")){			
				dynamicMeasure = env.createDynamicMeasure(measure, splitOption, measureSplitName);
				dynamicMeasure.setValue(resultValue);
			}
			else if (measure.getParameter("Tier Filter").equals(comparisonString)){
				dynamicMeasure = env.createDynamicMeasure(measure, splitOption, measureSplitName);
				dynamicMeasure.setValue(resultValue);
			}			
		}
	}
	
	/**
	 * Shuts the Plugin down and frees resources.
	 * 
	 * The Plugin methods >setup, execute and teardown are called on 
	 * different threads, but they are called sequentially. This means that 
	 * the execution of these methods does not overlap, they are executed 
	 * one after the other.
	 *
	 * Failed means that either an unhandled exception is thrown or the status
	 * returned by the method contains a non-success code.
	 *
	 * All by the Plugin allocated resources should be freed in this method.
	 * @see Monitor#setup(MonitorEnvironment)
	 */	@Override
	public void teardown(MonitorEnvironment env) throws Exception {
		
		// Releasing variables
		urlprotocol = null;
		username = null;
		password = null;
		responseTimeMeasure = null;
		execTimeMeasure = null;
		execCPUTimeMeasure = null;
		transactionCountMeasure = null;
		dynaTraceURL = null;
		measures = null;
	}	
	
	public static void disableCertificateValidation() {
		
		log.finer("Entering disableCertificateValidation method");  
		
		// Create a trust manager that does not validate certificate chains
		  TrustManager[] trustAllCerts = new TrustManager[] { 
		    new X509TrustManager() {
		      public X509Certificate[] getAcceptedIssuers() { 
		        return new X509Certificate[0]; 
		      }
		      public void checkClientTrusted(X509Certificate[] certs, String authType) {}
		      public void checkServerTrusted(X509Certificate[] certs, String authType) {}
		  }};

		  // Ignore differences between given hostname and certificate hostname
			HostnameVerifier hv = new HostnameVerifier() {
		    public boolean verify(String hostname, SSLSession session) { return true; }
		  };

		  // Install the all-trusting trust manager
		  try {
		    SSLContext sc = SSLContext.getInstance("SSL");
		    sc.init(null, trustAllCerts, new SecureRandom());
		    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		    HttpsURLConnection.setDefaultHostnameVerifier(hv);
		  } catch (Exception e) {}
		  
		  log.finer("Leaving disableCertificateValidation method");
	}
	
	/**
	 * Converts an attribute map from a NamedNodeMap objejt to a String value.
	 *
	 * @param attributes
	 *            a NamedNodeMap object that contains the attributes
	 *            of a Node
	 * @return a String object that contains all Node attributes
	 */
	private String getAttributesAsString(NamedNodeMap attributes) {
		StringBuilder sb = new StringBuilder("\n");
		for (int j = 0; j < attributes.getLength(); j++) {
			sb.append("\t- ").append(attributes.item(j).getNodeName()).append(": ").append(attributes.item(j).getNodeValue()).append("\n");
		}
		return sb.toString();
	}
	
	private HashMap<String, Double> getResultMap(NodeList measureList, String measureName, String agentNameField) {
		
		log.finer("Entering getResultList Method");		
		
		double resultMeasure = 0;
		HashMap<String, Double> resultMap = new HashMap<String, Double>();  
			
		for (int i = 0; i < measureList.getLength(); ++i){
			log.finer("measureList iteration = " + i);
			String attributesAsString = getAttributesAsString(measureList.item(i).getAttributes());
			log.finer("NodeName " + measureList.item(i).getNodeName() + " " + attributesAsString);
			
			if (attributesAsString.contains(measureName)){
				String tempString = measureList.item(i).getAttributes().getNamedItem(measureName).toString();
				log.finer("tempString: " + tempString);
				String stringAsDouble = tempString.replaceAll("\"","").replaceAll(measureName + "=","");
				resultMeasure = Double.parseDouble(stringAsDouble);
				log.finer("resultMeasure: " + resultMeasure);
				
				String tempAgentName = measureList.item(i).getAttributes().getNamedItem(agentNameField).toString();
				log.finer("tempAgentName: " + tempAgentName);
				tempAgentName = tempAgentName.replaceAll("\"","").replaceAll((agentNameField + "="),"");
				log.finer("Agent Name: " + tempAgentName);
				
				resultMap.put(tempAgentName,resultMeasure);
			}
			resultMeasure = 0;
		}
		
		return resultMap;
	}
	
	private double calculateMapAvg(HashMap<String, Double> valuesMap) {
		
		log.fine("Calculating Average");
		
		double averageTime = 0;
		int i = 0;
		
		
		for(Map.Entry<String,Double> value:valuesMap.entrySet()){  
			log.fine("Map Entry: " + value.getKey() + " " + value.getValue());
			averageTime = averageTime + value.getValue();
			i++;
		}		
			
		if (i >= 1){
			averageTime = averageTime / i;
		}
		
		log.fine("Average = " + averageTime);
		
		return averageTime;
	}
	
	private double calculateSum(HashMap<String, Double>  valuesMap) {
		
		log.fine("Calculating Sum");
		double sumValue = 0;		
		
		for(Map.Entry<String,Double> value:valuesMap.entrySet()){  
			log.fine("Map Entry: " + value.getKey() + " " + value.getValue());
			sumValue = sumValue +  value.getValue();			
		}
		
		log.fine("Sum: " + sumValue);
		
		return sumValue;
	}
}