/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.vfs2.provider.jar;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.Capability;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VfsLog;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileSystem;
import org.apache.commons.vfs2.provider.UriParser;
import org.apache.commons.vfs2.provider.zip.ZipFileSystemConfigBuilder;

/**
 * A read-only file system for Jar files.
 */
public class JarFileSystem extends AbstractFileSystem {

    private static final Log LOG = LogFactory.getLog(JarFileSystem.class);

    private final File file;
    private final Charset charset;
    private JarFile jarFile;

    /**
     * Cache doesn't need to be synchronized since it is read-only.
     */
    private final Map<FileName, FileObject> cache = new HashMap<>();

    private Attributes attributes;

    protected JarFileSystem(final AbstractFileName rootName, final FileObject parentLayer,
            final FileSystemOptions fileSystemOptions) throws FileSystemException {
        super(rootName, parentLayer, fileSystemOptions);

        // Make a local copy of the file
        file = parentLayer.getFileSystem().replicateFile(parentLayer, Selectors.SELECT_SELF);
        this.charset = ZipFileSystemConfigBuilder.getInstance().getCharset(fileSystemOptions);

        // Open the jar file
        if (!file.exists()) {
            // Don't need to do anything
            jarFile = null;
            return;
        }
    }

    /**
     * @since 2.7.0
     */
    @Override
    public void init() throws FileSystemException {
        super.init();

        try {
            // Build the index
            final List<JarFileObject> strongRef = new ArrayList<>(getJarFile().size());
            final Enumeration<JarEntry> entries = getJarFile().entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                final AbstractFileName name = (AbstractFileName) getFileSystemManager().resolveName(getRootName(),
                        UriParser.encode(entry.getName()));

                // Create the file
                JarFileObject fileObj;
                if (entry.isDirectory() && getFileFromCache(name) != null) {
                    fileObj = (JarFileObject) getFileFromCache(name);
                    fileObj.setJarEntry(entry);
                    continue;
                }

                fileObj = createJarFileObject(name, entry);
                putFileToCache(fileObj);
                strongRef.add(fileObj);
                fileObj.holdObject(strongRef);

                // Make sure all ancestors exist
                // TODO - create these on demand
                JarFileObject parent;
                for (AbstractFileName parentName = (AbstractFileName) name.getParent();
                     parentName != null;
                     fileObj = parent, parentName = (AbstractFileName) parentName.getParent()) {
                    // Locate the parent
                    parent = (JarFileObject) getFileFromCache(parentName);
                    if (parent == null) {
                        parent = createJarFileObject(parentName, null);
                        putFileToCache(parent);
                        strongRef.add(parent);
                        parent.holdObject(strongRef);
                    }

                    // Attach child to parent
                    parent.attachChild(fileObj.getName());
                }
            }
        } finally {
            closeCommunicationLink();
        }
    }

    /**
     * @since 2.7.0
     */
    protected JarFile getJarFile() throws FileSystemException {
        if (jarFile == null && this.file.exists()) {
            this.jarFile = createJarFile(this.file);
        }

        return jarFile;
    }

    /**
     * @since 2.7.0
     */
    protected JarFile createJarFile(final File file) throws FileSystemException {
        try {
            return new JarFile(file);
        } catch (final IOException ioe) {
            throw new FileSystemException("vfs.provider.jar/open-jar-file.error", file, ioe);
        }
    }

    /**
     * @since 2.7.0
     */
    protected JarFileObject createJarFileObject(final AbstractFileName name, final JarEntry entry)
            throws FileSystemException {
        return new JarFileObject(name, entry, this, true);
    }

    /**
     * @since 2.7.0
     */
    @Override
    protected void doCloseCommunicationLink() {
        // Release the jar file
        try {
            if (jarFile != null) {
                jarFile.close();
                jarFile = null;
            }
        } catch (final IOException e) {
            VfsLog.warn(getLogger(), LOG, "vfs.provider.jar/close-jar-file.error :" + file, e);
        }
    }

    /**
     * Returns the capabilities of this file system.
     * @since 2.7.0
     */
    @Override
    protected void addCapabilities(final Collection<Capability> caps) {
        caps.addAll(JarFileProvider.capabilities);
    }

    /**
     * Creates a file object.
     * @since 2.7.0
     */
    @Override
    protected FileObject createFile(final AbstractFileName name) throws FileSystemException {
        // This is only called for files which do not exist in the Jar file
        return new JarFileObject(name, null, this, false);
    }

    /**
     * Adds a file object to the cache.
     * @since 2.7.0
     */
    @Override
    protected void putFileToCache(final FileObject file) {
        cache.put(file.getName(), file);
    }

    /**
     * @since 2.7.0
     */
    protected Charset getCharset() {
        return charset;
    }

    /**
     * Returns a cached file.
     * @since 2.7.0
     */
    @Override
    protected FileObject getFileFromCache(final FileName name) {
        return cache.get(name);
    }

    /**
     * remove a cached file.
     * @since 2.7.0
     */
    @Override
    protected void removeFileFromCache(final FileName name) {
        cache.remove(name);
    }

    /**
    * @since 2.7.0
    */
    @Override
    public String toString() {
        return super.toString() + " for " + file;
    }


    Attributes getAttributes() throws IOException {
        if (attributes == null) {
            final Manifest man = getJarFile().getManifest();
            if (man == null) {
                attributes = new Attributes(1);
            } else {
                attributes = man.getMainAttributes();
                if (attributes == null) {
                    attributes = new Attributes(1);
                }
            }
        }

        return attributes;
    }

    Object getAttribute(final Name attrName) throws FileSystemException {
        try {
            final Attributes attr = getAttributes();
            final String value = attr.getValue(attrName);
            return value;
        } catch (final IOException ioe) {
            throw new FileSystemException(attrName.toString(), ioe);
        }
    }

    Name lookupName(final String attrName) {
        if (Name.CLASS_PATH.toString().equals(attrName)) {
            return Name.CLASS_PATH;
        } else if (Name.CONTENT_TYPE.toString().equals(attrName)) {
            return Name.CONTENT_TYPE;
        } else if (Name.EXTENSION_INSTALLATION.toString().equals(attrName)) {
            return Name.EXTENSION_INSTALLATION;
        } else if (Name.EXTENSION_LIST.toString().equals(attrName)) {
            return Name.EXTENSION_LIST;
        } else if (Name.EXTENSION_NAME.toString().equals(attrName)) {
            return Name.EXTENSION_NAME;
        } else if (Name.IMPLEMENTATION_TITLE.toString().equals(attrName)) {
            return Name.IMPLEMENTATION_TITLE;
        } else if (Name.IMPLEMENTATION_URL.toString().equals(attrName)) {
            return Name.IMPLEMENTATION_URL;
        } else if (Name.IMPLEMENTATION_VENDOR.toString().equals(attrName)) {
            return Name.IMPLEMENTATION_VENDOR;
        } else if (Name.IMPLEMENTATION_VENDOR_ID.toString().equals(attrName)) {
            return Name.IMPLEMENTATION_VENDOR_ID;
        } else if (Name.IMPLEMENTATION_VERSION.toString().equals(attrName)) {
            return Name.IMPLEMENTATION_VENDOR;
        } else if (Name.MAIN_CLASS.toString().equals(attrName)) {
            return Name.MAIN_CLASS;
        } else if (Name.MANIFEST_VERSION.toString().equals(attrName)) {
            return Name.MANIFEST_VERSION;
        } else if (Name.SEALED.toString().equals(attrName)) {
            return Name.SEALED;
        } else if (Name.SIGNATURE_VERSION.toString().equals(attrName)) {
            return Name.SIGNATURE_VERSION;
        } else if (Name.SPECIFICATION_TITLE.toString().equals(attrName)) {
            return Name.SPECIFICATION_TITLE;
        } else if (Name.SPECIFICATION_VENDOR.toString().equals(attrName)) {
            return Name.SPECIFICATION_VENDOR;
        } else if (Name.SPECIFICATION_VERSION.toString().equals(attrName)) {
            return Name.SPECIFICATION_VERSION;
        } else {
            return new Name(attrName);
        }
    }

    /**
     * Retrives the attribute with the specified name. The default implementation simply throws an exception.
     *
     * @param attrName The attiribute's name.
     * @return The value of the attribute.
     * @throws FileSystemException if an error occurs.
     */
    @Override
    public Object getAttribute(final String attrName) throws FileSystemException {
        final Name name = lookupName(attrName);
        return getAttribute(name);
    }

}
