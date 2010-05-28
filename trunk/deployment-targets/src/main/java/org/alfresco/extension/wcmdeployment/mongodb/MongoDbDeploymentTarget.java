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

package org.alfresco.extension.wcmdeployment.mongodb;

import java.io.OutputStream;
import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.alfresco.deployment.DeploymentTarget;
import org.alfresco.deployment.FileDescriptor;
import org.alfresco.deployment.FileType;
import org.alfresco.deployment.impl.DeploymentException;
import org.alfresco.util.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;


/**
 * ####TODO: Add description
 *
 * @author Peter Monks (peter.monks@alfresco.com)
 * @version $Id$
 */
public class MongoDbDeploymentTarget
    implements DeploymentTarget
{
    private final static Log log = LogFactory.getLog(MongoDbDeploymentTarget.class);
    
    private final static String DEFAULT_MONGO_DB_HOSTNAME = "localhost";
    private final static int    DEFAULT_MONGO_DB_PORT     = 27017;
    
    private String hostname = DEFAULT_MONGO_DB_HOSTNAME;
    private int    port     = DEFAULT_MONGO_DB_PORT;
    
    private ConcurrentMap<String, DB> deployments = new ConcurrentHashMap<String, DB>();

    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#begin(java.lang.String, java.lang.String, int, java.lang.String, java.lang.String)
     */
    public String begin(final String target,
                        final String storeName,
                        final int    version,
                        final String user,
                        final char[] password)
    {
        log.trace("MongoDbDeploymentTarget.begin()");
        String result = buildDeploymentKey(target, storeName);
        
        try
        {
            Mongo mongo    = new Mongo(hostname, port);
            DB    database = mongo.getDB(target);        // We use the target name as the Mongo database name
            
            if (user != null && user.trim().length() > 0)
            {
                if (!database.authenticate(user, password))
                {
                    throw new RuntimeException("Unable to authenticate with MongoDB server.");
                }
            }

            if (deployments.putIfAbsent(result, database) != null)
            {
                throw new IllegalStateException("A deployment to this target is already in progress.");
            }
            
            database.requestStart();
        }
        catch (UnknownHostException uhe)
        {
            // Tra-lala-lala I hate checked exceptions...
            throw new DeploymentException("Unable to connect to MongoDB server at: " + hostname + ":" + String.valueOf(port), uhe);
        }
        
        return(result);
    }
    
    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#prepare(java.lang.String)
     */
    public void prepare(final String ticket)
        throws DeploymentException
    {
        log.trace("MongoDbDeploymentTarget.prepare()");
        
        // Note: MongoDB isn't transactional, so this is a NO-OP
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#createDirectory(java.lang.String, java.lang.String, java.lang.String, java.util.Set, java.util.Map)
     */
    public void createDirectory(final String ticket, final String path, final String guid, final Set<String>aspects, final Map<String, Serializable> properties)
        throws DeploymentException
    {
        log.trace("MongoDbDeploymentTarget.createDirectory()");
        
        // NO-OP - we don't need the concept of directories in MongoDB
        //####NOTE: may need to be stored anyway (as some kind of placeholder document), to ensure getListing works as authoring expects
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#delete(java.lang.String, java.lang.String)
     */
    public void delete(final String ticket, final String path)
        throws DeploymentException
    {
        log.trace("MongoDbDeploymentTarget.delete()");
        
        DBCollection collection = getCollection(ticket);
        DBObject     document   = findByPath(collection, path);
        
        if (document != null)
        {
            collection.remove(document);
        }
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#getCurrentVersion(java.lang.String, java.lang.String)
     */
    public int getCurrentVersion(final String target, final String storeName)
    {
        log.trace("MongoDbDeploymentTarget.getCurrentVersion()");
        int result = 0;
        
        DB database = deployments.get(buildDeploymentKey(target, storeName));
        
        if (database != null)
        {
            DBCollection collection        = database.getCollection("deploymentSystem");
            DBObject     currentVersionDoc = collection.findOne("currentVersion");
            
            if (currentVersionDoc != null)
            {
                if (currentVersionDoc.containsField("currentVersion"))
                {
                    Object currentVersion = currentVersionDoc.get("currentVersion");
                    
                    if (currentVersion != null)
                    {
                        try
                        {
                            result = Integer.valueOf(String.valueOf(currentVersion));
                        }
                        catch (NumberFormatException nfe)
                        {
                            log.warn("Unable to parse version " + String.valueOf(currentVersion) + " from current version document.  Ignoring and resetting version to 0.");
                            result = 0;
                            currentVersionDoc.put("currentVersion", result);
                            collection.save(currentVersionDoc);
                        }
                    }
                }
                else
                {
                    // Shouldn't happen, but just in case...
                    log.warn("Current version document was missing currentVersion field.  Resetting version to 0.");
                    currentVersionDoc.put("currentVersion", result);
                    collection.save(currentVersionDoc);
                }
            }
            else
            {
                // No current version doc (presumably because we've never deployed to this MongoDB before), so create a new one
                log.info("No current version document found.  Creating at version 0.");
                currentVersionDoc = new BasicDBObject();
                currentVersionDoc.put("currentVersion", result);
                collection.save(currentVersionDoc);
            }
        }
        else
        {
            throw new DeploymentException("Invalid state: could not retrieve database object using key " + buildDeploymentKey(target, storeName));
        }
        
        return(result);
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#getListing(java.lang.String, java.lang.String)
     */
    public List<FileDescriptor> getListing(final String ticket, final String parentPath)
        throws DeploymentException
    {
        log.trace("MongoDbDeploymentTarget.getListing()");
        
        List<FileDescriptor> result          = new ArrayList<FileDescriptor>();
        DBCollection         collection      = getCollection(ticket);
        DBObject             parentPathQuery = new BasicDBObject();
        DBCursor             cursor          = null; 
        
        collection.ensureIndex("parentPath");   // Make sure we index parentPath, so that listings are efficient
        parentPathQuery.put("parentPath", new BasicDBObject("$eq", parentPath));
        cursor = collection.find(parentPathQuery);
        
        while (cursor.hasNext())
        {
            DBObject document = cursor.next();
            result.add(new FileDescriptor((String)document.get("path"), FileType.FILE, (String)document.get("_id")));
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
        log.trace("MongoDbDeploymentTarget.send()");
        
        OutputStream result = null;
        
        if (mimeType.equals("text/xml") ||
            mimeType.equals("application/xml") ||
            mimeType.endsWith("+xml"))
        {
            DBCollection collection = getCollection(ticket);
            DBObject     document   = new BasicDBObject();
            
            document.put("_id",        guid);
            document.put("path",       path);
            document.put("parentPath", getParentPath(path));
            document.put("filename",   getFileName(path));
            document.put("mimeType",   mimeType);
            
            result = new XmlToBsonMappingOutputStream(collection, document);
        }
        
        return(result);
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#updateDirectory(java.lang.String, java.lang.String, java.lang.String, java.util.Set, java.util.Map)
     */
    public void updateDirectory(final String ticket, final String path, final String guid, final Set<String> aspects, final Map<String, Serializable> properties)
        throws DeploymentException
    {
        log.trace("MongoDbDeploymentTarget.updateDirectory()");

        // NO-OP - we don't need the concept of directories in MongoDB
    }

    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#commit(java.lang.String)
     */
    public void commit(final String ticket)
    {
        log.trace("MongoDbDeploymentTarget.commit()");

        // Note: MongoDB isn't transactional, so this is effectively a NO-OP
        deployments.get(ticket).requestDone();
        deployments.remove(ticket);
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#abort(java.lang.String)
     */
    public void abort(final String ticket)
    {
        log.trace("MongoDbDeploymentTarget.abort()");
        
        // Note: MongoDB isn't transactional, so this is effectively a NO-OP
        deployments.get(ticket).requestDone();
        deployments.remove(ticket);
    }


    /**
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname)
    {
        log.trace("MongoDbDeploymentTarget.setHostname()");
        this.hostname = hostname;
    }


    /**
     * @param port the port to set
     */
    public void setPort(int port)
    {
        log.trace("MongoDbDeploymentTarget.setPort()");
        this.port = port;
    }


    /**
     * Retrieves the collection for the given ticket.
     * 
     * @param ticket The ticket <i>(must not be null, empty or blank)</i>.
     * @return The collection (if any) for that ticket <i>(may be null)</i>.
     */
    private DBCollection getCollection(final String ticket)
    {
        log.trace("MongoDbDeploymentTarget.getCollection()");
        
        // PRECONDITIONS
        assert ticket != null             : "ticket must not be null";
        assert ticket.trim().length() > 0 : "ticket must not be empty or blank.";
        
        // Body
        String       collectionName = ticket.split("\\/")[1];
        DBCollection result         = deployments.get(ticket).getCollection(collectionName);   // We use the storeName as the MongoDB collection name
        
        return(result);
    }
    
    
    private String buildDeploymentKey(final String target, final String storeName)
    {
        return(target + "/" + storeName);
    }
    
    
    private DBObject findByPath(final DBCollection collection, final String path)
    {
        DBObject result = null;
        
        if (collection != null && path != null)
        {
            DBObject pathQuery = new BasicDBObject();
            
            collection.ensureIndex("path");   // Make sure we index path, so that listings are efficient
            pathQuery.put("path", new BasicDBObject("$eq", path));
            result = collection.findOne(pathQuery);
        }
        
        return(result);
    }
    
    
    private String getParentPath(final String path)
    {
        return(path.substring(0, path.lastIndexOf('/')));
    }
    
    
    private String getFileName(final String path)
    {
        return(path.substring(path.lastIndexOf('/')));
    }
    
}
