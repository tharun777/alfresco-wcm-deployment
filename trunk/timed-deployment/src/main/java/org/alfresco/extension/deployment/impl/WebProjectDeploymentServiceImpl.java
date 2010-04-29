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

package org.alfresco.extension.deployment.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.alfresco.model.WCMAppModel;
import org.alfresco.repo.avm.AVMNodeConverter;
import org.alfresco.repo.avm.actions.AVMDeployWebsiteAction;
//import org.alfresco.repo.avm.util.AVMUtil;   // 3.1SP2+ only
import org.alfresco.repo.domain.PropertyValue;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.avm.AVMService;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.GUID;
import org.alfresco.wcm.sandbox.SandboxConstants;
import org.alfresco.config.JNDIConstants;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.extension.deployment.WebProjectDeploymentService;


/**
 * This class implements the deployment logic. 
 *
 * @author Peter Monks (pmonks@alfresco.com)
 *
 */
public class WebProjectDeploymentServiceImpl
    implements WebProjectDeploymentService
{
    private final Log logger = LogFactory.getLog(WebProjectDeploymentServiceImpl.class);
    
    private final ActionService     actionService;
    private final NodeService       unprotectedNodeService;
    private final PermissionService unprotectedPermissionService;
    private final AVMService        avmService;
    
    private boolean updateTestServer = true;   // Not quite sure what the point of this is, but don't want to strip it out
    
    
    public WebProjectDeploymentServiceImpl(final ActionService     actionService,
                                           final NodeService       unprotectedNodeService,
                                           final PermissionService unprotectedPermissionService,
                                           final AVMService        avmService)
    {
        this.actionService                = actionService;
        this.unprotectedNodeService       = unprotectedNodeService;
        this.unprotectedPermissionService = unprotectedPermissionService;
        this.avmService                   = avmService;
    }
    

    
    
    /**
     * @see org.alfresco.extension.deployment.WebProjectDeploymentService#deploy(org.alfresco.service.cmr.repository.NodeRef)
     */
    @Override
    public void deploy(final NodeRef webProjectRef)
    {
        deploy(webProjectRef, -1);
    }
    
    
    /**
     * @see org.alfresco.extension.deployment.WebProjectDeploymentService#deploy(org.alfresco.service.cmr.repository.NodeRef, int)
     */
    @Override
    public void deploy(NodeRef webProjectRef, int versionToDeploy)
    {
        String stagingSandboxStoreId = getStagingStoreId(webProjectRef);
        
        if (versionToDeploy <= 0)
        {
            versionToDeploy = avmService.getLatestSnapshotID(stagingSandboxStoreId);
        }
        
        deploy(webProjectRef,
               stagingSandboxStoreId,
               WCMAppModel.CONSTRAINT_LIVESERVER,
               versionToDeploy,
               getAllLiveDeploymentTargets(webProjectRef));
    }




    // This method is lifted almost verbatim from org.alfresco.web.bean.wcm.DeployWebsiteDialog (which is useless to us since it's tightly coupled to the Web Client).
    private void deploy(final NodeRef webProjectRef, final String store, final String deployMode, final int versionToDeploy, final String[] deployTo)
    {
        if (logger.isDebugEnabled())
            logger.debug("Requesting deployment of: " + webProjectRef.toString() + ", version " + versionToDeploy + " to servers: " + arrayToString(deployTo));
        
        // WARNING: the following 2 lines are NOT lifted verbatim from the class mentioned above
        String  storeRoot  = buildAVMPath(store, JNDIConstants.DIR_DEFAULT_WWW_APPBASE);
        NodeRef websiteRef = AVMNodeConverter.ToNodeRef(versionToDeploy, storeRoot);
         
        if (deployTo != null && deployTo.length > 0)
        {
           List<String> selectedDeployToNames = new ArrayList<String>();
           
           // create a deploymentattempt node to represent this deployment
           String attemptId = GUID.generate();
           Map<QName, Serializable> props = new HashMap<QName, Serializable>(8, 1.0f);
           props.put(WCMAppModel.PROP_DEPLOYATTEMPTID, attemptId);
           props.put(WCMAppModel.PROP_DEPLOYATTEMPTTYPE, deployMode);
           props.put(WCMAppModel.PROP_DEPLOYATTEMPTSTORE, store);
           props.put(WCMAppModel.PROP_DEPLOYATTEMPTVERSION, versionToDeploy);
           props.put(WCMAppModel.PROP_DEPLOYATTEMPTTIME, new Date());
           NodeRef attempt = unprotectedNodeService.createNode(webProjectRef, 
                 WCMAppModel.ASSOC_DEPLOYMENTATTEMPT, WCMAppModel.ASSOC_DEPLOYMENTATTEMPT, 
                 WCMAppModel.TYPE_DEPLOYMENTATTEMPT, props).getChildRef();
           
           // allow anyone to add child nodes to the deploymentattempt node
           unprotectedPermissionService.setPermission(attempt, PermissionService.ALL_AUTHORITIES, 
                    PermissionService.ADD_CHILDREN, true);
           
           // execute a deploy action for each of the selected remote servers asynchronously
           for (String targetServer : deployTo)
           {
              if (targetServer.length() > 0)
              {
                 NodeRef serverRef = new NodeRef(targetServer);
                 if (unprotectedNodeService.exists(serverRef))
                 {
                    // get all properties of the target server
                    Map<QName, Serializable> serverProps = unprotectedNodeService.getProperties(serverRef);
                    
                    String url = (String)serverProps.get(WCMAppModel.PROP_DEPLOYSERVERURL);
                    String serverUri = AVMDeployWebsiteAction.calculateServerUri(serverProps);
                    String serverName = (String)serverProps.get(WCMAppModel.PROP_DEPLOYSERVERNAME);
                    if (serverName == null || serverName.length() == 0)
                    {
                       serverName = serverUri;
                    }
                    
                    // if this is a test server deployment we need to allocate the
                    // test server to the current sandbox so it can re-use it and
                    // more importantly, no one else can. Before doing that however,
                    // we need to make sure no one else has taken the server since
                    // we selected it.
                    if (WCMAppModel.CONSTRAINT_TESTSERVER.equals(deployMode) &&
                        this.updateTestServer == false)
                    {
                       String allocatedTo = (String)serverProps.get(WCMAppModel.PROP_DEPLOYSERVERALLOCATEDTO);
                       if (allocatedTo != null)
                       {
                          throw new AlfrescoRuntimeException("testserver.taken", new Object[] {serverName});
                       }
                       else
                       {
                          unprotectedNodeService.setProperty(serverRef, WCMAppModel.PROP_DEPLOYSERVERALLOCATEDTO, 
                                   store);
                       }
                    }
                    
                    if (logger.isDebugEnabled())
                       logger.debug("Issuing deployment request for: " + serverName);
                    
                    // remember the servers deployed to
                    selectedDeployToNames.add(serverName);
                    
                    // create and execute the action asynchronously
                    Map<String, Serializable> args = new HashMap<String, Serializable>(1, 1.0f);
                    args.put(AVMDeployWebsiteAction.PARAM_WEBPROJECT, webProjectRef);
                    args.put(AVMDeployWebsiteAction.PARAM_SERVER, serverRef);
                    args.put(AVMDeployWebsiteAction.PARAM_ATTEMPT, attempt);
                    Action action = actionService.createAction(AVMDeployWebsiteAction.NAME, args);
                    actionService.executeAction(action, websiteRef, false, true);
                 }
                 else if (logger.isWarnEnabled())
                 {
                    logger.warn("target server '" + targetServer + "' was ignored as it no longer exists!");
                 }
              }
           }
           
           // now we know the list of selected servers set the property on the attempt node
           unprotectedNodeService.setProperty(attempt, WCMAppModel.PROP_DEPLOYATTEMPTSERVERS, 
                    (Serializable)selectedDeployToNames);
           
           // set the deploymentattempid property on the store this deployment was for
           avmService.deleteStoreProperty(store, SandboxConstants.PROP_LAST_DEPLOYMENT_ID);
           avmService.setStoreProperty(store, SandboxConstants.PROP_LAST_DEPLOYMENT_ID, 
                    new PropertyValue(DataTypeDefinition.TEXT, attemptId));
        }
        else
        {
           if (logger.isWarnEnabled())
              logger.warn("Deployment of '" + websiteRef.toString() + "' skipped as no servers were selected");
        }
    }
    
    
    /**
     * @param updateTestServer the updateTestServer to set
     */
    public void setUpdateTestServer(final boolean updateTestServer)
    {
        this.updateTestServer = updateTestServer;
    }


    private String getStagingStoreId(final NodeRef webProjectRef)
    {
        String result = null;
        
        if (webProjectRef != null)
        {
            result = (String)unprotectedNodeService.getProperty(webProjectRef, WCMAppModel.PROP_AVMSTORE);
        }
        
        return(result);
    }
    
    
    private String[] getAllLiveDeploymentTargets(final NodeRef webProjectRef)
    {
        String[] result = null;
        
        if (webProjectRef != null)
        {
            Set<QName> searchTypeQNames = new HashSet<QName>(1);
            searchTypeQNames.add(WCMAppModel.ASSOC_DEPLOYMENTSERVER);
            
            List<ChildAssociationRef> childAssocRefs = unprotectedNodeService.getChildAssocs(webProjectRef, searchTypeQNames);
            List<String>              tmpResult      = new ArrayList<String>(childAssocRefs.size());
            
            for (ChildAssociationRef assocRef : childAssocRefs)
            {
                NodeRef childNode  = assocRef.getChildRef();
                String  targetType = (String)unprotectedNodeService.getProperty(childNode, WCMAppModel.PROP_DEPLOYSERVERTYPE);
                
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
    
    
    //####TODO: pretty sure this exists in commons-lang somewhere - need to look for it
    private String arrayToString(final String[] values)
    {
        String result = null;
        
        if (values != null)
        {
            StringBuffer temp = new StringBuffer();
            
            for (int i = 0; i < values.length; i++)
            {
                if (i > 0)
                {
                    temp.append(", ");
                }
                
                temp.append(values[i]);
            }
            
            result = temp.toString();
        }
        
        return(result);
    }
    
    
    /**
     * WARNING!  Copied from org.alfresco.repo.avm.util.AVMUtil (which was only added in 3.1SP2 or thereabouts).
     */
    private static final char AVM_STORE_SEPARATOR_CHAR = ':';
    private static final char AVM_PATH_SEPARATOR_CHAR  = '/';
    private String buildAVMPath(String storeName, String storeRelativePath)
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
