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

package org.alfresco.extension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.alfresco.model.WCMAppModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;


/**
 * This class provides some handy helper methods for dealing with Web Projects.
 *
 * @author Peter Monks (pmonks@alfresco.com)
 *
 */
public class WebProjectHelper
{
    private final Log log = LogFactory.getLog(WebProjectHelper.class);
    
    private final ServiceRegistry serviceRegistry;
    private final Repository      repository;
    
    
    public WebProjectHelper(final ServiceRegistry serviceRegistry,
                            final Repository      repository)
    {
        this.serviceRegistry = serviceRegistry;
        this.repository      = repository;
    }
    
    
    /**
     * Finds a Web Project by its DNS name.
     * 
     * @param dnsName The DNS name of the Web Project <i>(must not be null, empty or blank)</i>.
     * @return The NodeRef of the Web Project's DM space <i>(will be null if no Web Projects with the given DNS name are found)</i>.
     */
    public NodeRef findWebProjectByDNSName(final String dnsName)
    {
        NodeRef result = null;
        
        if (dnsName != null)
        {
            // Step 1: find the "Web Projects" space
            NodeRef  companyHome      = null;
            FileInfo fi               = null;
            NodeRef  webProjectsSpace = null;
            
            try
            {
                companyHome = repository.getCompanyHome();
            }
            catch (NullPointerException npe)
            {
                // Repository.getCompanyHome() seems to throw intermittent NPEs when invoked during the bootstrap process, so catch, log and ignore
                log.warn("Unable to find Company Home. This can sometimes happen during the bootstrap process, but if it occurs repeatedly post-bootstrap, please report this to the author of this extension.");
            }
            
            if (companyHome != null)
            {
                try
                {
                    fi = serviceRegistry.getFileFolderService().resolveNamePath(companyHome, Arrays.asList("Web Projects"));
                }
                catch (FileNotFoundException fnfe)
                {
                    fi = null;
                }
                
                if (fi != null)
                {
                    // Step 2: list its children one-by-one, until we either run out or find one where wca:avmstore == dnsName
                    webProjectsSpace = fi.getNodeRef();
                    
                    List<FileInfo> webProjects = serviceRegistry.getFileFolderService().list(webProjectsSpace);
                    
                    for (FileInfo fi2 : webProjects)
                    {
                        NodeRef webProject = fi2.getNodeRef();
                        
                        if (dnsName.equals(serviceRegistry.getNodeService().getProperty(webProject, WCMAppModel.PROP_AVMSTORE)))
                        {
                            result = webProject;
                            break;
                        }
                    }
                }
            }
        }
        
        return(result);
    }
    
    
    /**
     * Returns the store id of the staging sandbox for the given Web Project.
     * 
     * @param webProjectRef The NodeRef representing the DM space of the Web Project <i>(must not be null)</i>.
     * @return The store id of the staging sandbox <i>(may be null)</i>.
     */
    public String getStagingStoreId(final NodeRef webProjectRef)
    {
        String result = null;
        
        if (webProjectRef != null)
        {
            result = (String)serviceRegistry.getNodeService().getProperty(webProjectRef, WCMAppModel.PROP_AVMSTORE);
        }
        
        return(result);
    }
    
    
    /**
     * Returns all live deployment targets for the given Web Project.
     * 
     * @param webProjectRef The NodeRef representing the DM space of the Web Project <i>(must not be null)</i>.
     * @return The list of deployment targets <i>(may be null)</i>.
     */
    public String[] getAllLiveDeploymentTargets(final NodeRef webProjectRef)
    {
        String[] result = null;
        
        if (webProjectRef != null)
        {
            Set<QName> searchTypeQNames = new HashSet<QName>(1);
            searchTypeQNames.add(WCMAppModel.ASSOC_DEPLOYMENTSERVER);
            
            List<ChildAssociationRef> childAssocRefs = serviceRegistry.getNodeService().getChildAssocs(webProjectRef, searchTypeQNames);
            List<String>              tmpResult      = new ArrayList<String>(childAssocRefs.size());
            
            for (ChildAssociationRef assocRef : childAssocRefs)
            {
                NodeRef childNode  = assocRef.getChildRef();
                String  targetType = (String)serviceRegistry.getNodeService().getProperty(childNode, WCMAppModel.PROP_DEPLOYSERVERTYPE);
                
                if (WCMAppModel.CONSTRAINT_LIVESERVER.equals(targetType))
                {
                    tmpResult.add(assocRef.getChildRef().toString());
                }
            }
            
            result = new String[tmpResult.size()];
            int i = 0;
            
            for (String childNodeRefStr : tmpResult)
            {
                result[i] = childNodeRefStr;
                i++;
            }
        }
        
        return(result);
    }

    
    /**
     * WARNING!  Copied from org.alfresco.repo.avm.util.AVMUtil (which was only added in 3.1SP2 or thereabouts).
     */
    private static final char AVM_STORE_SEPARATOR_CHAR = ':';
    private static final char AVM_PATH_SEPARATOR_CHAR  = '/';
    public String buildAVMPath(final String storeName, final String storeRelativePath)
    {
        // note: assumes storeRelativePath is not null and does not contain ':', although will add leading slash (if missing)
        StringBuilder builder = new StringBuilder();
        builder.append(storeName).append(AVM_STORE_SEPARATOR_CHAR);
        if ((storeRelativePath.length() == 0) || (storeRelativePath.charAt(0) != AVM_PATH_SEPARATOR_CHAR))
        {
            builder.append(AVM_PATH_SEPARATOR_CHAR);
        }
        builder.append(storeRelativePath);
        return builder.toString();
    }
    
}
