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

package org.alfresco.extension.deployment.reports;

import org.alfresco.service.cmr.repository.NodeRef;


/**
 * This interface defines an abstraction for cleaning up deployment reports in a Web Project.
 *
 * @author Peter Monks (pmonks@alfresco.com)
 *
 */
public interface DeploymentReportCleanupService
{
    /**
     * @param maxReportsToPrunePerBatch The maximum number of reports to prune in a single batch (execution of the timed job).
     */
    void setMaxReportsToPrunePerBatch(final int maxReportsToPrunePerBatch);
    
    
    /**
     * Deletes all "null" deployment reports for the given Web Project.  A "null" deployment report is one where nothing happened (ie. all targets were already up to date).
     *  
     * @param webProjectDNSName The DNS Name of the Web Project <i>(must not be null, empty or blank)</i>.
     */
    void deleteNullDeploymentReports(final String webProjectDnsName);
    
    
    /**
     * Deletes all "null" deployment reports for the given Web Project.  A "null" deployment report is one where nothing happened (ie. all targets were already up to date).
     *  
     * @param webProjectRef The Node Ref of the DM space representing the Web Project <i>(must not be null)</i>.
     */
    void deleteNullDeploymentReports(final NodeRef webProjectNodeRef);
}
