Description
-----------
This module contains an Alfresco v3.1+ "timed deployment" feature, which can
be configured to deploy the latest snapshot in the staging sandbox of any Web
Project on a regular schedule.

Please don't hesitate to contact the authors if you'd like to contribute!


Author
------
Peter Monks (reverse sknompATocserflaDOTmoc)


Release History
---------------
v0.3 released 2010-05-08
     First public release.


Pre-requisites
--------------
Alfresco 3.1 or better for the timed deployment job (tested on Alfresco
Enterprise 3.1SP2)


Installation
------------
1. Copy the provided AMP file into the ${ALFRESCO_HOME}/amps directory
2. Run the ${ALFRESCO_HOME}/apply_amps[.sh|.bat] script to install the AMP
   into your Alfresco instance


Configuration
-------------
Create a file in ${ALFRESCO_HOME}/tomcat/shared/classes/alfresco/extension
with a file name of "custom-timed-deployment-context.xml" and the following
contents:

	<?xml version='1.0' encoding='UTF-8'?>
	<beans xmlns="http://www.springframework.org/schema/beans"
	       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	       xmlns:lang="http://www.springframework.org/schema/lang"
	       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.0.xsd http://www.springframework.org/schema/lang http://www.springframework.org/schema/lang/spring-lang-2.0.xsd">

	  <!--
	   *
	   * To configure one or more timed deployment jobs, replace [webProjectDNSName] with a non-whitespace symbolic name for your Web Project
	   * (any value is allowed, but for simplicity the DNS name of your Web Project is recommended), then replace the other configuration
	   * properties (defined in the "jobDataAsMap" property) as follows:
	   *
	   *  - transactionService:     the reference to the Alfresco transaction service. Shouldn't normally be modified.
	   *  - timedDeploymentService: the reference to the underlying timed deployment service. Shouldn't normally be modified.
	   *  - webProjectNodeRef:      the node ref of the Web Project that this job will operate against.
	   *                            You can find this out by looking at the properties of the Web Project DM Space in the Explorer UI.
	   *  - webProjectDNSName:      the DNS name of the Web Project that this job will operate against.
	   *
	   * Note that only webProjectNodeRef OR webProjectDNSName should be provided, never both.
	   *
	   * The frequency of the job (ie. how often it runs) can be configured via the cronExpression property.  The allowed values for this
	   * property are described in more detail at http://www.opensymphony.com/quartz/wikidocs/CronTriggers%20Tutorial.html.
	   *
	   * Finally, you may configure multiple instances of this bean (with different ids) if you wish to configure timed deployment for multiple Web
	   * Projects.  In this case you may also configure different schedules for each of those Web Projects.
	   *
	  -->
	  <bean id="wcmTimedDeploymentTrigger.[webProjectDNSName]" class="org.alfresco.util.CronTriggerBean">
	    <property name="jobDetail">
	      <bean class="org.springframework.scheduling.quartz.JobDetailBean">
	        <property name="jobClass" value="org.alfresco.extension.timeddeployment.quartzjobs.TimedDeploymentJob" />
	        <property name="jobDataAsMap">
	          <map>
	            <entry key="transactionService">
	              <ref bean="transactionService" />
	            </entry>
	            <entry key="webProjectDeploymentService">
	              <ref bean="extension.webProjectDeploymentService" />
	            </entry>
	<!--            <entry key="webProjectNodeRef" value="[webProjectNodeRef]" /> -->
	<!-- For example:
	            <entry key="webProjectNodeRef" value="workspace://SpacesStore/da81cf94-ec37-4418-9ca1-74c1b94d5661" />
	-->
	            <entry key="webProjectDNSName" value="[webProjectDNSName]" />
	<!-- For example:
	            <entry key="webProjectDNSName" value="test" />
	-->
	          </map>
	        </property>
	      </bean>
	    </property>
	    <property name="scheduler" ref="schedulerFactory" />
	    <!-- trigger every 10 minutes - change as desired -->
	    <property name="cronExpression" value="0 0/10 * * * ?" />
	  </bean>

    </beans>

Once you've updated the file with the NodeRef or DNS name of the Web
Project you wish to deploy, restart Alfresco then monitor the deployment
reports to confirm the configuration.



Usage
-----
There is no usage for this functionality - when configured correctly it will
be automatically invoked on the schedule that has been configured.


Compiling the Package from Source
---------------------------------
1. Checkout the source code:
   svn checkout http://alfresco-wcm-deployment.googlecode.com/svn/trunk/ alfresco-wcm-deployment-read-only

2. Change into the checkout directory and build the AMP file using Maven2:
   mvn clean package

NOTE:
* currently you will need to manually edit the pom.xml file in order to point
  Maven to either the Community Artifact repository (sponsored by
  SourceSense, one of Alfresco's European SI partners), or to a Maven
  repository you've created that contains the Alfresco Enterprise artifacts.
  Alfresco is working on providing a Maven repository for the Enterprise
  artifacts to Enterprise subscribers, but that work is (at the time of
  writing) still in progress.


Outstanding Issues / Enhancements
---------------------------------
See http://code.google.com/p/alfresco-wcm-deployment/issues/list
