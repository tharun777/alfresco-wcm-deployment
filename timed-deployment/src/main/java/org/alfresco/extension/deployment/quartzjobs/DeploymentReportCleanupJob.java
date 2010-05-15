/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have received a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */

package org.alfresco.extension.deployment.quartzjobs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.CronTriggerBean;

import org.alfresco.extension.deployment.reports.DeploymentReportCleanupService;


/**
 * Quartz job that cleans up all "null" deployment reports for a given Web Project.
 * 
 * In order to function, one (or more) instances of this class need to be configured in a custom Spring *-context.xml file in alfresco/extension.
 * Configuration is as follows:
 * 
 * <code>
 *  &lt;bean id="wcmDeploymentReportCleanupTrigger.[webProjectDNSName]" class="org.alfresco.util.CronTriggerBean"&gt;
 *    &lt;property name="jobDetail"&gt;
 *      &lt;bean class="org.springframework.scheduling.quartz.JobDetailBean"&gt;
 *        &lt;property name="jobClass" value="org.alfresco.extension.deployment.quartzjobs.DeploymentReportCleanupJob" /&gt;
 *        &lt;property name="jobDataAsMap"&gt;
 *          &lt;map&gt;
 *            &lt;entry key="transactionService"&gt;
 *              &lt;ref bean="transactionService" /&gt;
 *            &lt;/entry&gt;
 *            &lt;entry key="webProjectDeploymentReportCleanupService"&gt;
 *              &lt;ref bean="extension.webProjectDeploymentReportCleanupService" /&gt;
 *            &lt;/entry&gt;
 *            &lt;entry key="webProjectNodeRef" value="[webProjectNodeRef]" /&gt;  &lt;!-- Node Ref of Web Project DM Space --&gt;
 *            &lt;!-- OR --&gt;
 *            &lt;entry key="webProjectDNSName" value="[webProjectDNSName]" /&gt;  &lt;!-- DNS Name of Web Project --&gt;
 *          &lt;/map&gt;
 *        &lt;/property&gt;
 *      &lt;/bean&gt;
 *    &lt;/property&gt;
 *    &lt;property name="scheduler" ref="schedulerFactory" /&gt;
      &lt;!-- trigger every 10 minutes --&gt;
      &lt;property name="cronExpression" value="0 0/10 * * * ?" /&gt;
 *  &lt;/bean&gt;
 *  </code>
 *  
 *  The configuration properties for this bean (defined in the "jobDataAsMap" property) are as follows: 
 *  <ul>
 *    <li><code>transactionService</code>: the reference to the Alfresco transaction service. Shouldn't normally be modified.</li>
 *    <li><code>webProjectDeploymentReportCleanupService</code>: the reference to the underlying deployment report cleanup service. Shouldn't normally be modified.</li>
 *    <li><code>webProjectNodeRef</code>: the node ref of the Web Project that this job will operate against.
 *        You can find this out by looking at the properties of the Web Project in the Explorer UI.</li>
 *    <li><code>webProjectDNSName</code>: the DNS name of the Web Project that this job will operate against.</li>
 *  </ul>
 *  
 *  Note that only webProjectNodeRef OR webProjectDNSName should be provided.
 *  
 *  The frequency of the job (ie. how often it runs) can be configured via the <code>cronExpression</code> property.  This is described in more detail in
 *  the <a href="http://www.opensymphony.com/quartz/wikidocs/CronTriggers%20Tutorial.html">Quartz documentation</a>.
 *   
 *  Finally, you may configure multiple instances of this bean (with different ids) if you wish to configure deployment report cleanup for multiple Web
 *  Projects.  In this case you may also configure different schedules for each of those Web Projects.
 *
 * @author Peter Monks (peter.monks@alfresco.com)
 * @version $Id$
 */
public class DeploymentReportCleanupJob
    extends CronTriggerBean
    implements Job
{
    private static Log log = LogFactory.getLog(DeploymentReportCleanupJob.class);
    
    private final static String JOB_DATA_PARAMETER_NAME_TRANSACTION_SERVICE               = "transactionService";
    private final static String JOB_DATA_PARAMETER_NAME_DEPLOYMENT_REPORT_CLEANUP_SERVICE = "webProjectDeploymentReportCleanupService";
    private final static String JOB_DATA_PARAMETER_NAME_WEB_PROJECT_NODE_REF              = "webProjectNodeRef";
    private final static String JOB_DATA_PARAMETER_NAME_WEB_PROJECT_DNS_NAME              = "webProjectDNSName";
    
    private boolean                        initialised                    = false;
    private TransactionService             transactionService             = null;
    private DeploymentReportCleanupService deploymentReportCleanupService = null;
    private NodeRef                        webProjectNodeRef              = null;
    private String                         webProjectDNSName              = null;
    

    /**
     * @see org.quartz.Job#execute(org.quartz.JobExecutionContext)
     */
    public void execute(final JobExecutionContext context)
        throws JobExecutionException
    {
        // Weird initialisation logic due to lack of dependency injection in Quartz jobs.
        synchronized(this)
        {
            if (!initialised)
            {
                initialiseConfiguration(context);
                initialised = true;
            }
        }
        
        // Run the deployment report cleanup logic as a system user and within a retrying transaction.
        AuthenticationUtil.RunAsWork<Object> runAsDeploymentReportCleanupWork = new AuthenticationUtil.RunAsWork<Object>()
        {
            public Object doWork()
                throws Exception
            {
                RetryingTransactionCallback<Object> retryingDeploymentReportCleanupWork = new RetryingTransactionCallback<Object>()
                {
                    public Object execute()
                        throws Exception
                    {
                        if (webProjectNodeRef != null)
                        {
                            deploymentReportCleanupService.deleteNullDeploymentReports(webProjectNodeRef);
                        }
                        else
                        {
                            deploymentReportCleanupService.deleteNullDeploymentReports(webProjectDNSName);
                        }
                        
                        return null;
                     }
                };
                return(transactionService.getRetryingTransactionHelper().doInTransaction(retryingDeploymentReportCleanupWork));
            }
        };
        
        try
        {
            AuthenticationUtil.runAs(runAsDeploymentReportCleanupWork, AuthenticationUtil.SYSTEM_USER_NAME);
        }
        catch (Exception e)
        {
            // Log the error and swallow it - better we handle it than Quartz
            log.error("Unexpected error while executing deployment report cleanup job: " + e.getMessage(), e);
        }
    }
    
    
    /**
     * Private method that initialises all of our state from the JobExecutionContext.
     * 
     * @param context The JobExecutionContext containing the configuration data <i>(must not be null)</i>.
     */
    private void initialiseConfiguration(final JobExecutionContext context)
    
    {
        // PRECONDITIONS
        assert context != null : "context must not be null.";
        
        // Body
        
        // Pull state from the job configuration data.
        JobDataMap jobData                           = context.getJobDetail().getJobDataMap();
        Object     transactionServiceObj             = jobData.get(JOB_DATA_PARAMETER_NAME_TRANSACTION_SERVICE);
        Object     deploymentReportCleanupServiceObj = jobData.get(JOB_DATA_PARAMETER_NAME_DEPLOYMENT_REPORT_CLEANUP_SERVICE);
        Object     webProjectNodeRefObj              = jobData.get(JOB_DATA_PARAMETER_NAME_WEB_PROJECT_NODE_REF);
        Object     webProjectDNSNameObj              = jobData.get(JOB_DATA_PARAMETER_NAME_WEB_PROJECT_DNS_NAME);

        
        // Service Registry
        if (transactionServiceObj == null ||
            !(transactionServiceObj instanceof TransactionService))
        {
            throw new AlfrescoRuntimeException(JOB_DATA_PARAMETER_NAME_TRANSACTION_SERVICE + " must be provided and must be a reference to an instance of org.alfresco.service.transaction.TransactionService.");
        }
        
        this.transactionService = (TransactionService)transactionServiceObj;
        
        
        // Timed Deployment Service
        if (deploymentReportCleanupServiceObj == null ||
            !(deploymentReportCleanupServiceObj instanceof DeploymentReportCleanupService))
        {
            throw new AlfrescoRuntimeException(JOB_DATA_PARAMETER_NAME_DEPLOYMENT_REPORT_CLEANUP_SERVICE + " must be provided and must be a reference to an instance of org.alfresco.extension.deployment.reports.DeploymentReportCleanupService.");
        }
        
        this.deploymentReportCleanupService = (DeploymentReportCleanupService)deploymentReportCleanupServiceObj;

        
        // Web Project, either by NodeRef or DNS Name
        if (webProjectNodeRefObj == null &&
            webProjectDNSNameObj == null)
        {
            throw new AlfrescoRuntimeException("Either " + JOB_DATA_PARAMETER_NAME_WEB_PROJECT_NODE_REF + " or " +
                                               JOB_DATA_PARAMETER_NAME_WEB_PROJECT_DNS_NAME + " must be provided.");
        }
        
        if (webProjectNodeRefObj != null)
        {
            this.webProjectNodeRef = new NodeRef((String)webProjectNodeRefObj);
        }
        
        if (webProjectDNSNameObj != null)
        {
            this.webProjectDNSName = (String)webProjectDNSNameObj;
        }
    }
    
}
