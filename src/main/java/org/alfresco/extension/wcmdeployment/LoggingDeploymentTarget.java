/*
 * Copyright (C) 2005-2009 Alfresco Software Limited.
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

package org.alfresco.extension.wcmdeployment;

import java.io.OutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alfresco.deployment.DeploymentTarget;
import org.alfresco.deployment.FileDescriptor;
import org.alfresco.deployment.impl.DeploymentException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Simple DeploymentTarget that simply logs all calls made to it.
 *
 * @author Peter Monks (peter.monks@alfresco.com)
 * @version $Id$
 */
public class LoggingDeploymentTarget
    implements DeploymentTarget
{
    private final static Log log = LogFactory.getLog(LoggingDeploymentTarget.class);
    

    /**
     * @see org.alfresco.deployment.DeploymentTarget#begin(java.lang.String, java.lang.String, int, java.lang.String, java.lang.String)
     */
    public String begin(final String target,
                        final String storeName,
                        final int    version,
                        final String user,
                        final String password)    // This may change.  See https://issues.alfresco.com/jira/browse/ETHREEOH-3612
    {
        log.info("LoggingDeploymentTarget.begin(" + target + ", " + storeName + ", " + String.valueOf(version) + ", " + user + ", " + password + ")");
        
        return(target + "/" + storeName + "/" + String.valueOf(version));
    }
    
    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#prepare(java.lang.String)
     */
    public void prepare(final String ticket)
        throws DeploymentException
    {
        log.info("LoggingDeploymentTarget.prepare(" + ticket + ")");
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#createDirectory(java.lang.String, java.lang.String, java.lang.String, java.util.Set, java.util.Map)
     */
    public void createDirectory(final String                    ticket,
                                final String                    path,
                                final String                    guid,
                                final Set<String>               aspects,
                                final Map<String, Serializable> properties)
        throws DeploymentException
    {
        log.info("LoggingDeploymentTarget.createDirectory(" + ticket + ", " + path + ", " + guid + ", aspects (TODO), properties (TODO))");
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#delete(java.lang.String, java.lang.String)
     */
    public void delete(final String ticket,
                       final String path)
        throws DeploymentException
    {
        log.info("LoggingDeploymentTarget.delete(" + ticket + ", " + path + ")");
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#getCurrentVersion(java.lang.String, java.lang.String)
     */
    public int getCurrentVersion(final String target,
                                 final String storeName)
    {
        log.info("LoggingDeploymentTarget.getCurrentVersion(" + target + ", " + storeName + ")");
        return(-1);
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#getListing(java.lang.String, java.lang.String)
     */
    public List<FileDescriptor> getListing(final String ticket,
                                           final String path)
        throws DeploymentException
    {
        log.info("LoggingDeploymentTarget.getListing(" + ticket + ", " + path + ")");
        return(null);
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#send(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.util.Set, java.util.Map)
     */
    public OutputStream send(final String                    ticket,
                             final String                    path,
                             final String                    guid,
                             final String                    encoding,
                             final String                    mimeType,
                             final Set<String>               aspects,
                             final Map<String, Serializable> properties)
        throws DeploymentException
    {
        log.info("LoggingDeploymentTarget.send(" + ticket + ", " + path + ", " + guid + ", " + encoding + ", " + mimeType + ", aspects (TODO), properties (TODO))");
        return(null);
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#updateDirectory(java.lang.String, java.lang.String, java.lang.String, java.util.Set, java.util.Map)
     */
    public void updateDirectory(final String                    ticket,
                                final String                    path,
                                final String                    guid,
                                final Set<String>               aspects,
                                final Map<String, Serializable> properties)
        throws DeploymentException
    {
        log.info("LoggingDeploymentTarget.updateDirectory(" + ticket + ", " + path + ", " + guid + ", aspects (TODO), properties (TODO))");
    }

    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#commit(java.lang.String)
     */
    public void commit(final String ticket)
    {
        log.info("LoggingDeploymentTarget.commit(" + ticket + ")");
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#abort(java.lang.String)
     */
    public void abort(final String ticket)
    {
        log.info("LoggingDeploymentTarget.abort(" + ticket + ")");
    }
    
}
