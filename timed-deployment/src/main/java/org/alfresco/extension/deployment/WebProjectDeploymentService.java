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

package org.alfresco.extension.deployment;

import org.alfresco.service.cmr.repository.NodeRef;


/**
 * This interface defines an abstraction for deploying Web Projects.
 *
 * @author Peter Monks (pmonks@alfresco.com)
 *
 */
public interface WebProjectDeploymentService
{
    /**
     * Deploys the latest version (snapshot) of the given Web Project's staging sandbox to all live deployment targets.
     *  
     * @param webProjectDNSName The DNS Name of the Web Project <i>(must not be null, empty or blank)</i>.
     */
    void deploy(final String webProjectDNSName);


    /**
     * Deploys the given version (snapshot) of the given Web Project's staging sandbox to all live deployment targets.
     *  
     * @param webProjectDNSName The DNS Name of the Web Project <i>(must not be null, empty or blank)</i>.
     * @param versionToDeploy The version to deploy <i>(&lt;= 0 means deploy the latest version)</i>.
     */
    void deploy(final String webProjectDNSName, int versionToDeploy);
    
    
    /**
     * Deploys the latest version (snapshot) of the given Web Project's staging sandbox to all live deployment targets.
     *  
     * @param webProjectRef The Node Ref of the DM space representing the Web Project <i>(must not be null)</i>.
     */
    void deploy(final NodeRef webProjectRef);


    /**
     * Deploys the given version (snapshot) of the given Web Project's staging sandbox to all live deployment targets.
     *  
     * @param webProjectRef   The Node Ref of the DM space representing the Web Project <i>(must not be null)</i>.
     * @param versionToDeploy The version to deploy <i>(&lt;= 0 means deploy the latest version)</i>.
     */
    void deploy(final NodeRef webProjectRef, int versionToDeploy);
}
