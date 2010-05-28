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

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mongodb.DBCollection;
import com.mongodb.DBObject;

/**
 * This class TODO
 *
 * @author Peter Monks (pmonks@alfresco.com)
 *
 */
public class XmlToBsonMappingOutputStream
    extends OutputStream
{
    private final static Log log = LogFactory.getLog(XmlToBsonMappingOutputStream.class);
    
    private final DBCollection collection;
    private final DBObject     document;
    
    
    
    public XmlToBsonMappingOutputStream(final DBCollection collection,
                                        final DBObject     document)
    {
        this.collection = collection;
        this.document   = document;
    }
    

    /**
     * @see java.io.OutputStream#write(int)
     */
    @Override
    public void write(final int b)
        throws IOException
    {
        //####TODO: implement this
    }
    

    /* (non-Javadoc)
     * @see java.io.OutputStream#close()
     */
    @Override
    public void close()
        throws IOException
    {
        log.trace("XmlToBsonMappingOutputStream.close()");
        collection.save(document);
    }

}
