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

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.UnknownHostException;
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
import org.alfresco.extension.wcmdeployment.NoopOutputStream;
import org.alfresco.util.GUID;
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
    
    private boolean authenticate = false;
    private String  hostname     = DEFAULT_MONGO_DB_HOSTNAME;
    private int     port         = DEFAULT_MONGO_DB_PORT;
    
    private Mongo                                      mongo       = null;
    private ConcurrentMap<String, Map<String, Object>> deployments = null;

    
    
    public void init()
    {
        log.trace("MongoDbDeploymentTarget.init()");
        
        try
        {
            mongo = new Mongo(hostname, port);
        }
        catch (UnknownHostException uhe)
        {
            // Tra-lala-lala I hate checked exceptions...
            throw new DeploymentException("Unable to connect to MongoDB server at: " + hostname + ":" + String.valueOf(port), uhe);
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
        log.trace("MongoDbDeploymentTarget.begin(" + target + ", " + storeName + ", " + version + ")");
        String result = GUID.generate();
        
        DB database = mongo.getDB(storeName);        // We use the target name as the Mongo database name
        
        if (authenticate && user != null && user.trim().length() > 0)
        {
            if (!database.authenticate(user, password))
            {
                throw new RuntimeException("Unable to authenticate with MongoDB database '" + target + "'.");
            }
        }
        
        Map<String, Object> deploymentState = new HashMap<String, Object>();
        
        deploymentState.put("target",   target);
        deploymentState.put("store",    storeName);
        deploymentState.put("version",  Integer.valueOf(version));
        deploymentState.put("database", database);

        if (deployments.putIfAbsent(result, deploymentState) != null)
        {
            throw new IllegalStateException("A deployment to this target is already in progress.");
        }
        
        database.requestStart();
        
        return(result);
    }
    
    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#prepare(java.lang.String)
     */
    public void prepare(final String ticket)
        throws DeploymentException
    {
        log.trace("MongoDbDeploymentTarget.prepare(" + ticket + ")");
        
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
        log.trace("MongoDbDeploymentTarget.createDirectory(" + ticket + ", " + path + ")");
        
        //####TODO!!!!
        //####NOTE: may need to be stored anyway (as some kind of placeholder document), to ensure getListing works as authoring expects
        // NO-OP - we don't need the concept of directories in MongoDB
    }


    /**
     * @see org.alfresco.deployment.DeploymentTarget#delete(java.lang.String, java.lang.String)
     */
    public void delete(final String ticket, final String path)
        throws DeploymentException
    {
        log.trace("MongoDbDeploymentTarget.delete(" + ticket + ", " + path + ")");
        
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
        log.trace("MongoDbDeploymentTarget.getCurrentVersion(" + target + ", " + storeName + ")");
        int result = 0;
        
        DB       database          = mongo.getDB(storeName);
        DBObject currentVersionDoc = findOrCreateVersionDoc(database);
        Object   currentVersion    = currentVersionDoc.get(target);
        
        try
        {
            result = Integer.valueOf(String.valueOf(currentVersion));
        }
        catch (NumberFormatException nfe)
        {
            log.warn("Unable to parse version '" + String.valueOf(currentVersion) + "' from current version document.  Ignoring and resetting version to 0.");
            result = 0;
            setVersion(database, result);
        }
        
        return(result);
    }
    
    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#getListing(java.lang.String, java.lang.String)
     */
    public List<FileDescriptor> getListing(final String ticket, final String parentPath)
        throws DeploymentException
    {
        log.trace("MongoDbDeploymentTarget.getListing(" + ticket + ", " + parentPath + ")");
        
        List<FileDescriptor> result          = new ArrayList<FileDescriptor>();
        DBCollection         collection      = getCollection(ticket);
        DBObject             parentPathQuery = new BasicDBObject();
        DBCursor             cursor          = null; 
        
        collection.ensureIndex("parentPath");   // Make sure we index parentPath, so that listings are efficient
        parentPathQuery.put("parentPath", parentPath);
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
        log.trace("MongoDbDeploymentTarget.send(" + ticket + ", " + path + ")");
        
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

            // We use a BufferedOutputStream here since using the XmlToBsonMappingOutputStream results in "read end dead" IOExceptions. ####TODO: Get to the bottom of this...
            result = new BufferedOutputStream(new XmlToBsonMappingOutputStream(collection, document));
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
        log.trace("MongoDbDeploymentTarget.updateDirectory()");

        //####TODO!!!!
        // NO-OP - we don't need the concept of directories in MongoDB
    }

    
    /**
     * @see org.alfresco.deployment.DeploymentTarget#commit(java.lang.String)
     */
    public void commit(final String ticket)
    {
        log.trace("MongoDbDeploymentTarget.commit(" + ticket + ")");
        
        // Update the version number then clear out the transient deployment state
        DB database = getDatabase(ticket);
        
        setVersion(database, getVersion(ticket));
        database.requestDone();
        deployments.remove(ticket);
    }


    /**
     * MongoDB isn't transactional, so this is effectively a NO-OP.
     * 
     * @see org.alfresco.deployment.DeploymentTarget#abort(java.lang.String)
     */
    public void abort(final String ticket)
    {
        log.trace("MongoDbDeploymentTarget.abort(" + ticket + ")");
        
        // Clear out the transient deployment state
        if (deployments.containsKey(ticket))   // WARNING WARNING WARNING: Not thread safe
        {
            getDatabase(ticket).requestDone();
            deployments.remove(ticket);
        }
    }


    /**
     * @param authenticate the authenticate to set
     */
    public void setAuthenticate(final boolean authenticate)
    {
        log.trace("MongoDbDeploymentTarget.setAuthenticate(" + authenticate + ")");
        this.authenticate = authenticate;
    }


    /**
     * @param hostname the hostname to set
     */
    public void setHostname(final String hostname)
    {
        log.trace("MongoDbDeploymentTarget.setHostname(" + hostname + ")");
        this.hostname = hostname;
    }


    /**
     * @param port the port to set
     */
    public void setPort(final int port)
    {
        log.trace("MongoDbDeploymentTarget.setPort(" + port + ")");
        this.port = port;
    }
    
    
    
    /**
     * Retrieves the collection for the given ticket.
     * ####TODO: Refactor to use a collection per source document root element
     * 
     * @param ticket The ticket <i>(must not be null, empty or blank)</i>.
     * @return The collection (if any) for that ticket <i>(may be null)</i>.
     */
    private DBCollection getCollection(final String ticket)
    {
        log.trace("MongoDbDeploymentTarget.getCollection(" + ticket + ")");
        
        return(getDatabase(ticket).getCollection("deployedData"));
    }
    
    private DB getDatabase(final String ticket)
    {
        return((DB)deployments.get(ticket).get("database"));
    }
    
    
    private String getTarget(final String ticket)
    {
        return((String)deployments.get(ticket).get("target"));
    }
    
    
    private String getStore(final String ticket)
    {
        return((String)deployments.get(ticket).get("store"));
    }


    private int getVersion(final String ticket)
    {
        return((Integer)deployments.get(ticket).get("version"));
    }
    
    
    private DBObject findByPath(final DBCollection collection, final String path)
    {
        DBObject result = null;
        
        if (collection != null && path != null)
        {
            DBObject pathQuery = new BasicDBObject();
            
            collection.ensureIndex("path");   // Make sure we index path, so that listings are efficient
            pathQuery.put("path", path);
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
    
    
    private DBObject findVersionDoc(final DB database)
    {
        DBObject result = null;
        
        if (database != null)
        {
            DBCollection collection = database.getCollection("deploymentSystem");
            result = collection.findOne("version");
        }
        
        return(result);
    }
    
    
    private DBObject createVersionDoc(final DB database)
    {
        DBObject result = null;
        
        if (database != null)
        {
            DBCollection collection = database.getCollection("deploymentSystem");
            result = new BasicDBObject();
            result.put("_id", "version");
            result.put("version", 0);
            collection.save(result);
        }
        
        return(result);
    }
    
    
    private DBObject findOrCreateVersionDoc(final DB database)
    {
        DBObject result = findVersionDoc(database);
        
        if (result == null)
        {
            result = createVersionDoc(database);
        }
        
        if (result != null)
        {
            if (!result.containsField("version"))
            {
                // Shouldn't happen, but just in case...
                log.warn("Version document was missing 'version' field.  Resetting to 0.");
                result.put("version", 0);
                setVersion(database, 0);
            }
        }
        else
        {
            // Shouldn't happen, but just in case...
            throw new IllegalStateException("Unable to find or create version document.");
        }
        
        return(result);
    }
    
    
    private void setVersion(final DB database, final int version)
    {
        if (database != null)
        {
            DBCollection collection = database.getCollection("deploymentSystem");
            DBObject     query      = new BasicDBObject();
            DBObject     statement  = new BasicDBObject();
            
            query.put("_id", "version");
            statement.put("$set", new BasicDBObject("version", version));
            collection.update(query, statement);
        }
    }
    
    
}
