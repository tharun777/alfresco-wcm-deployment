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

package org.alfresco.extension.wcmdeployment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.alfresco.deployment.DeploymentTarget;
import org.alfresco.deployment.FileDescriptor;
import org.alfresco.deployment.FileType;
import org.alfresco.deployment.impl.DeploymentException;
import org.alfresco.util.GUID;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;



/**
 * ####TODO: Add description
 *
 * @author Peter Monks (peter.monks@alfresco.com)
 * @version $Id$
 */
public class NaiveFilesystemDeploymentTarget
    implements DeploymentTarget
{
    private final static Log log = LogFactory.getLog(NaiveFilesystemDeploymentTarget.class);

    private final static String DEFAULT_BASE_DIRECTORY     = "./naivetarget/";
    private final static String DEFAULT_METADATA_DIRECTORY = "./naivemetadata/";
    private final static String VERSION_FILENAME           = "version.txt";
    
    private File baseDirectory     = null;
    private File metadataDirectory = null;
    
    private ConcurrentMap<String, Map<String, Object>> deployments = null;
    
    
    
    public void init()
    {
        log.trace("NaiveFilesystemDeploymentTarget.init()");
        
        if (baseDirectory == null)
        {
            baseDirectory = new File(DEFAULT_BASE_DIRECTORY);
            mkdirs(baseDirectory);
        }
        
        if (metadataDirectory == null)
        {
            metadataDirectory = new File(DEFAULT_METADATA_DIRECTORY);
            mkdirs(metadataDirectory);
        }
        
        deployments = new ConcurrentHashMap<String, Map<String, Object>>();
    }
    
    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#begin(java.lang.String, java.lang.String, int, java.lang.String, java.lang.String)
     */
    public String begin(final String target,
                        final String storeName,
                        final int    version,
                        final String user,
                        final char[] password)
    {
        log.trace("NaiveFilesystemDeploymentTarget.begin(" + target + ", " + storeName + ", " + version + ")");
        String result = GUID.generate();
        
        File targetDirectory = new File(baseDirectory, target);
        File storeDirectory  = new File(targetDirectory, storeName);
        mkdirs(storeDirectory);
        
        File targetMetaDirectory = new File(metadataDirectory, target);
        File storeMetaDirectory  = new File(targetMetaDirectory, storeName);
        mkdirs(storeMetaDirectory);
        
        Map<String, Object> deploymentState = new HashMap<String, Object>();
        
        deploymentState.put("target",        target);
        deploymentState.put("store",         storeName);
        deploymentState.put("version",       Integer.valueOf(version));
        deploymentState.put("directory",     storeDirectory);
        deploymentState.put("metaDirectory", storeMetaDirectory);

        if (deployments.putIfAbsent(result, deploymentState) != null)
        {
            throw new IllegalStateException("A deployment to this target is already in progress.");
        }
        
        return(result);
    }
    
    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#prepare(java.lang.String)
     */
    public void prepare(final String ticket)
        throws DeploymentException
    {
        log.trace("NaiveFilesystemDeploymentTarget.prepare(" + ticket + ")");
        
        // Note: MongoDB isn't transactional, so this is a NO-OP
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
        log.trace("NaiveFilesystemDeploymentTarget.createDirectory(" + ticket + ", " + path + ")");
        
        File newDirectory  = new File(getBaseDirectory(ticket), path);
        mkdirs(newDirectory);
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#delete(java.lang.String, java.lang.String)
     */
    public void delete(final String ticket, final String path)
        throws DeploymentException
    {
        log.trace("NaiveFilesystemDeploymentTarget.delete(" + ticket + ", " + path + ")");
        
        File pathFile = new File(getBaseDirectory(ticket), path);
        
        if (pathFile.exists())
        {
            if (!pathFile.delete())
            {
                throw new DeploymentException("Unable to delete path '" + getPath(pathFile) + "'.");
            }
        }
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#getCurrentVersion(java.lang.String, java.lang.String)
     */
    public int getCurrentVersion(final String target, final String storeName)
    {
        log.trace("NaiveFilesystemDeploymentTarget.getCurrentVersion(" + target + ", " + storeName + ")");
        int result = 0;

        File targetMetaDirectory = new File(metadataDirectory,   target);
        File storeMetaDirectory  = new File(targetMetaDirectory, storeName);
        File versionFile         = new File(storeMetaDirectory,  VERSION_FILENAME);
        
        if (versionFile.exists())
        {
            CharBuffer buffer = CharBuffer.allocate(16);
            Reader     reader = null;
            
            try
            {
                reader = new InputStreamReader(new FileInputStream(versionFile));
                reader.read(buffer);
                
                result = Integer.valueOf(buffer.toString().trim());
            }
            catch (final IOException ioe)
            {
                log.warn("Unable to retrieve version infomation from '" + getPath(versionFile) + "'.", ioe);
            }
            catch (final NumberFormatException nfe)
            {
                log.warn("Unable to parse version number '" + buffer.toString() + "'.", nfe);
            }
            finally
            {
                if (reader != null)
                {
                    try
                    {
                        reader.close();
                    }
                    catch (IOException ioe)
                    {
                        // *Gulp* - if this occurs there's not a lot else we can do but swallow it...
                    }
                }
            }
        }
        
        return(result);
    }
    
    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#getListing(java.lang.String, java.lang.String)
     */
    public List<FileDescriptor> getListing(final String ticket, final String parentPath)
        throws DeploymentException
    {
        log.trace("NaiveFilesystemDeploymentTarget.getListing(" + ticket + ", " + parentPath + ")");
        
        List<FileDescriptor> result          = new ArrayList<FileDescriptor>();
        
        File parentPathFile = new File(getBaseDirectory(ticket), parentPath);
        
        if (parentPathFile.exists())
        {
            File[] children = parentPathFile.listFiles();
            
            for (File child : children)
            {
                result.add(new FileDescriptor(child.getPath(), child.isDirectory() ? FileType.DIR : FileType.FILE, null));  // Note: use of file GUID here is bogus!!
            }
        }
        
        return(result);
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
                             final Map<String, Serializable> props)
        throws DeploymentException
    {
        log.trace("NaiveFilesystemDeploymentTarget.send(" + ticket + ", " + path + ")");
        
        OutputStream result = null;
        
        File outputFile = new File(getBaseDirectory(ticket), path);
        
        try
        {
            if (!outputFile.exists())
            {
                if (!outputFile.createNewFile())
                {
                    throw new DeploymentException("Unable to create file '" + getPath(outputFile) + "'.");
                }
            }
            
            //####TEST!!!!
            result = new BufferedOutputStream(new NoopOutputStream());
            //result = new BufferedOutputStream(new FileOutputStream(outputFile));
        }
        catch (IOException ioe)
        {
            throw new DeploymentException("I/O error opening file '" + getPath(outputFile) + "' for write.", ioe);
        }
        
        return(result);
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
        log.trace("NaiveFilesystemDeploymentTarget.updateDirectory()");

        // NO-OP - we're ignoring directory aspects and properties in this DeploymentTarget
    }

    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#commit(java.lang.String)
     */
    public void commit(final String ticket)
    {
        log.trace("NaiveFilesystemDeploymentTarget.commit(" + ticket + ")");
        
        // Update the version number then clear out the transient deployment state
        if (deployments.containsKey(ticket))   // WARNING WARNING WARNING: Not thread safe (non-atomic test-then-set)
        {
            setVersion(getMetaDirectory(ticket), getVersion(ticket));
            deployments.remove(ticket);
        }
    }


    /**
     * Filesystem isn't transactional, so this is effectively a NO-OP.
     * 
     * @see org.alfresco.deployment.DeploymentTarget#abort(java.lang.String)
     */
    public void abort(final String ticket)
    {
        log.trace("NaiveFilesystemDeploymentTarget.abort(" + ticket + ")");
        
        // Clear out the transient deployment state
        if (deployments.containsKey(ticket))   // WARNING WARNING WARNING: Not thread safe (non-atomic test-then-set)
        {
            deployments.remove(ticket);
        }
    }
    
    
    /**
     * @param baseDirectory the baseDirectory to set
     */
    public void setBaseDirectory(final File baseDirectory)
    {
        this.baseDirectory = baseDirectory;
    }


    /**
     * @param metadataDirectory the metadataDirectory to set
     */
    public void setMetadataDirectory(final File metadataDirectory)
    {
        this.metadataDirectory = metadataDirectory;
    }

    

    
    private void setVersion(final File metaDirectory, final int version)
    {
        Writer writer      = null;
        File   versionFile = new File(metaDirectory, VERSION_FILENAME);
        
        // Create the version file if it doesn't already exist
        if (!versionFile.exists())
        {
            try
            {
                versionFile.createNewFile();
            }
            catch (IOException ioe)
            {
                // Checked exceptions suxors
                throw new DeploymentException("Unable to create version file.", ioe);
            }
        }
        
        // Write the version number to it
        try
        {
            writer = new OutputStreamWriter(new FileOutputStream(versionFile));
            writer.write(version);
            writer.flush();
        }
        catch (IOException ioe)
        {
            log.error("Unable to record version " + version + " in '" + getPath(versionFile) + "'.", ioe);
        }
        finally
        {
            if (writer != null)
            {
                try
                {
                    writer.close();
                }
                catch (IOException ioe)
                {
                    // *Gulp* - if this occurs there's not a lot else we can do but swallow it...
                }
            }
        }
    }

    
    private void mkdirs(final File newDirectory)
    {
        if (!newDirectory.exists())
        {
            if (!newDirectory.mkdirs())
            {
                throw new DeploymentException("Unable to create non-existent directory '" + getPath(newDirectory) + "'.");
            }
        }
    }
    
    private String getPath(final File file)
    {
        String result = null;
        
        try
        {
            result = file.getCanonicalPath();
        }
        catch (IOException ioe)
        {
            result = file.getAbsolutePath();
        }
        
        return(result);
    }
    
    
    private File getBaseDirectory(final String ticket)
    {
        return((File)deployments.get(ticket).get("directory"));
    }
    
    
    private File getMetaDirectory(final String ticket)
    {
        return((File)deployments.get(ticket).get("metaDirectory"));
    }
    
    private int getVersion(final String ticket)
    {
        return((Integer)deployments.get(ticket).get("version"));
    }
    
}
