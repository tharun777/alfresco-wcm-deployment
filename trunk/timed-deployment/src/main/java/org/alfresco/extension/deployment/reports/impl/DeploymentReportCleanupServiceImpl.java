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
 * http://www.alfresco.com/legal/licensing
 */

package org.alfresco.extension.deployment.reports.impl;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.alfresco.model.WCMAppModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.RegexQNamePattern;

import org.alfresco.extension.WebProjectHelper;
import org.alfresco.extension.deployment.reports.DeploymentReportCleanupService;


/**
 * This class TODO
 *
 * @author Peter Monks (pmonks@alfresco.com)
 *
 */
public class DeploymentReportCleanupServiceImpl
    implements DeploymentReportCleanupService
{
    private final Log log = LogFactory.getLog(DeploymentReportCleanupServiceImpl.class);
    
    private final static String EMPTY_DEPLOYMENT_REPORT_CONTENT        = "START default\r\nEND default\r\n";
    private final static int    DEFAULT_MAX_REPORTS_TO_PRUNE_PER_BATCH = 100;

    
    private final ServiceRegistry  serviceRegistry;
    private final WebProjectHelper webProjectHelper;
    private final NodeService      nodeService;
    
    private int maxReportsToPrunePerBatch = DEFAULT_MAX_REPORTS_TO_PRUNE_PER_BATCH;
    
    
    
    public DeploymentReportCleanupServiceImpl(final ServiceRegistry  serviceRegistry,
                                              final WebProjectHelper webProjectHelper)
    {
        this.serviceRegistry  = serviceRegistry;
        this.webProjectHelper = webProjectHelper;
        this.nodeService      = serviceRegistry.getNodeService();
    }
            

    /**
     * @see org.alfresco.extension.deployment.reports.DeploymentReportCleanupService#setMaxReportsToPrunePerBatch(int)
     */
    @Override
    public void setMaxReportsToPrunePerBatch(int maxReportsToPrunePerBatch)
    {
        this.maxReportsToPrunePerBatch = maxReportsToPrunePerBatch <= 0 ? DEFAULT_MAX_REPORTS_TO_PRUNE_PER_BATCH : maxReportsToPrunePerBatch;
    }


    /**
     * @see org.alfresco.extension.deployment.reports.DeploymentReportCleanupService#deleteNullDeploymentReports(java.lang.String)
     */
    @Override
    public void deleteNullDeploymentReports(final String webProjectDnsName)
    {
        deleteNullDeploymentReports(webProjectHelper.findWebProjectByDNSName(webProjectDnsName));
    }


    /**
     * @see org.alfresco.extension.deployment.reports.DeploymentReportCleanupService#deleteNullDeploymentReports(org.alfresco.service.cmr.repository.NodeRef)
     */
    @Override
    public void deleteNullDeploymentReports(final NodeRef webProjectNodeRef)
    {
        log.debug("Finding and pruning 'null' deployment reports for " + webProjectNodeRef.toString());
        
        int numberPruned = 0;
        
        if (webProjectNodeRef != null)
        {
            // Iterate through all of the Web Project's deployment attempts
            List<ChildAssociationRef> deploymentAttemptRefs = nodeService.getChildAssocs(webProjectNodeRef, WCMAppModel.ASSOC_DEPLOYMENTATTEMPT, WCMAppModel.TYPE_DEPLOYMENTATTEMPT);
            
            for (ChildAssociationRef deploymentAttemptRef: deploymentAttemptRefs)
            {
                NodeRef deploymentAttemptNodeRef = deploymentAttemptRef.getChildRef();
                
                if (isNullDeploymentAttempt(deploymentAttemptNodeRef))
                {
                    log.debug("Found 'null' deployment attempt (" + deploymentAttemptNodeRef.toString() + "), deleting it.");
                    nodeService.deleteNode(deploymentAttemptNodeRef);
                    numberPruned++;
                    
                    if (numberPruned >= maxReportsToPrunePerBatch)
                    {
                        log.info("Hit limit on number of reports to prune per batch (" + maxReportsToPrunePerBatch + ").  Terminating pruning early.");
                        break;
                    }
                }
            }
        }

        if (numberPruned > 0)
        {
            log.info("Pruned " + numberPruned + " 'null' deployment reports.");
        }
        else
        {
            log.debug("Pruned 0 'null' deployment reports.");
        }
    }
    
    
    private boolean isNullDeploymentAttempt(final NodeRef deploymentAttemptNodeRef)
    {
        boolean result = false;
        
        if (deploymentAttemptNodeRef != null)
        {
            int nullDeploymentReportCount = 0;
            
            // Note: validate that there are as many deployment report sub-objects as there are deployment targets.  If not it may indicate that 
            // deployment hasn't finished yet (in which case we definitely do not want to whack this deployment attempt!).
            List<String>              deploymentServers    = (List<String>)nodeService.getProperty(deploymentAttemptNodeRef, WCMAppModel.PROP_DEPLOYATTEMPTSERVERS);
            List<ChildAssociationRef> deploymentReportRefs = nodeService.getChildAssocs(deploymentAttemptNodeRef, WCMAppModel.ASSOC_DEPLOYMENTREPORTS, RegexQNamePattern.MATCH_ALL);

            if (deploymentServers != null &&
                deploymentServers.size() > 0 &&
                deploymentReportRefs != null)
            {
                if (deploymentServers.size() == deploymentReportRefs.size())
                {
                    for (ChildAssociationRef deploymentReportRef : deploymentReportRefs)
                    {
                        NodeRef deploymentReportNodeRef = deploymentReportRef.getChildRef();
                        
                        if (isNullDeploymentReport(deploymentReportNodeRef))
                        {
                            nullDeploymentReportCount++;
                        }
                    }
                    
                    result = nullDeploymentReportCount > 0 && nullDeploymentReportCount >= deploymentReportRefs.size();
                }
            }
        }

        return(result);
    }
    
    
    private boolean isNullDeploymentReport(final NodeRef deploymentReportNodeRef)
    {
        boolean result = false;
        
        if (deploymentReportNodeRef != null)
        {
            // Only successful deployment reports can be "null"
            Boolean successful = (Boolean)nodeService.getProperty(deploymentReportNodeRef, WCMAppModel.PROP_DEPLOYSUCCESSFUL);
            
            if (successful)
            {
                // In addition, only those that didn't do anything can be "null"
                ContentReader reader = serviceRegistry.getFileFolderService().getReader(deploymentReportNodeRef);
                String content = reader.getContentString(EMPTY_DEPLOYMENT_REPORT_CONTENT.length() + 16);  // Only bother getting as much of the content as we need, with 16 bytes of buffer

                if (content != null)
                {
                    result = EMPTY_DEPLOYMENT_REPORT_CONTENT.equals(content);
                }
            }
        }

        return(result);
            
    }
    
    
    /**
     * Helpful utility method for debugging when strings that appear equal aren't.
     * @param array
     * @return
     */
    private String charArrayToString(final char[] array)
    {
        StringBuffer result = null;
        
        if (array != null)
        {
            result = new StringBuffer(array.length * 2 + 2);
            result.append('[');
            
            for (int i = 0; i < array.length; i++)
            {
                if (i > 0)
                {
                    result.append(',');
                }
                
                result.append(Character.getNumericValue(array[i]));
            }
            
            result.append(']');
        }
        
        return (result == null ? null : result.toString());
    }
    
}
