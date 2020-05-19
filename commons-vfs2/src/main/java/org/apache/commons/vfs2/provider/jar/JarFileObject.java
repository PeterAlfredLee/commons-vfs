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

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileObject;


/**
 * A file in a Jar file system.
 */
public class JarFileObject extends AbstractFileObject<JarFileSystem> {

    /** The JarEntry. */
    protected JarEntry entry;
    private final HashSet<String> children = new HashSet<>();
    private FileType type;

    private final JarFileSystem fs;

    private Attributes attributes;

    protected JarFileObject(final AbstractFileName name, final JarEntry entry, final JarFileSystem fs,
            final boolean jarExists) throws FileSystemException {
        super(name, fs);
        setJarEntry(entry);
        if (!jarExists) {
            type = FileType.IMAGINARY;
        }
        if (entry != null) {
			// For Java 9 and up: Force the certificates to be read and cached now. This avoids an
			// IllegalStateException in java.util.jar.JarFile.isMultiRelease() when it tries
			// to read the certificates and the file is closed.
            entry.getCertificates();
        }
        this.fs = fs;

        try {
            getAttributes(); // early get the attributes as the zip file might be closed
        } catch (final IOException e) {
            throw new FileSystemException(e);
        }
    }

    /**
     * Sets the details for this file object.
     *
     * @param entry JAR information related to this file.
     * @since 2.7.0
     */
    protected void setJarEntry(final JarEntry entry) {
        if (this.entry != null) {
            return;
        }

        if (entry == null || entry.isDirectory()) {
            type = FileType.FOLDER;
        } else {
            type = FileType.FILE;
        }

        this.entry = entry;
    }

    /**
     * Attaches a child.
     * <p>
     * TODO: Shouldn't this method have package-only visibility? Cannot change this without breaking binary
     * compatibility.
     * </p>
     *
     * @param childName The name of the child.
     * @since 2.7.0
     */
    public void attachChild(final FileName childName) {
        children.add(childName.getBaseName());
    }

    /**
     * Determines if this file can be written to.
     *
     * @return {@code true} if this file is writable, {@code false} if not.
     * @throws FileSystemException if an error occurs.
     * @since 2.7.0
     */
    @Override
    public boolean isWriteable() throws FileSystemException {
        return false;
    }

    /**
     * Returns the file's type.
     * @since 2.7.0
     */
    @Override
    protected FileType doGetType() {
        return type;
    }

    /**
     * Lists the children of the file.
     * @since 2.7.0
     */
    @Override
    protected String[] doListChildren() {
        try {
            if (!getType().hasChildren()) {
                return null;
            }
        } catch (final FileSystemException e) {
            // should not happen as the type has already been cached.
            throw new RuntimeException(e);
        }

        return children.toArray(new String[children.size()]);
    }

    /**
     * Returns the size of the file content (in bytes). Is only called if {@link #doGetType} returns
     * {@link FileType#FILE}.
     * @since 2.7.0
     */
    @Override
    protected long doGetContentSize() {
        return entry.getSize();
    }

    /**
     * Returns the last modified time of this file.
     * @since 2.7.0
     */
    @Override
    protected long doGetLastModifiedTime() throws Exception {
        return entry.getTime();
    }

    /**
     * Creates an input stream to read the file content from. Is only called if {@link #doGetType} returns
     * {@link FileType#FILE}. The input stream returned by this method is guaranteed to be closed before this method is
     * called again.
     * @since 2.7.0
     */
    @Override
    protected InputStream doGetInputStream(final int bufferSize) throws Exception {
        // VFS-210: zip allows to gather an input stream even from a directory and will
        // return -1 on the first read. getType should not be expensive and keeps the tests
        // running
        if (!getType().hasContent()) {
            throw new FileSystemException("vfs.provider/read-not-file.error", getName());
        }

        return getAbstractFileSystem().getJarFile().getInputStream(entry);
    }

    /**
    * @since 2.7.0
    */
    @Override
    protected void doAttach() throws Exception {
        getAbstractFileSystem().getJarFile();
    }

    /**
     * @since 2.7.0
     */
    @Override
    protected void doDetach() throws Exception {
        final JarFileSystem afs = getAbstractFileSystem();
        if (!afs.isOpen()) {
            afs.close();
        }
    }

    /**
     * Returns the Jar manifest.
     */
    Manifest getManifest() throws IOException {
        if (fs.getJarFile() == null) {
            return null;
        }

        return fs.getJarFile().getManifest();
    }

    /**
     * Returns the attributes of this file.
     */
    Attributes getAttributes() throws IOException {
        if (attributes == null) {
            if (entry == null) {
                attributes = new Attributes(1);
            } else {
                attributes = ((JarEntry) entry).getAttributes();
                if (attributes == null) {
                    attributes = new Attributes(1);
                }
            }
        }

        return attributes;
    }

    /**
     * Returns the value of an attribute.
     */
    @Override
    protected Map<String, Object> doGetAttributes() throws Exception {
        final Map<String, Object> attrs = new HashMap<>();

        // Add the file system's attributes first
        final JarFileSystem fs = (JarFileSystem) getFileSystem();
        addAll(fs.getAttributes(), attrs);

        // Add this file's attributes
        addAll(getAttributes(), attrs);

        return attrs;
    }

    /**
     * Adds the source attributes to the destination map.
     */
    private void addAll(final Attributes src, final Map<String, Object> dest) {
        for (final Entry<Object, Object> entry : src.entrySet()) {
            // final String name = entry.getKey().toString().toLowerCase();
            final String name = entry.getKey().toString();
            dest.put(name, entry.getValue());
        }
    }

    /**
     * Return the certificates of this JarEntry.
     */
    @Override
    protected Certificate[] doGetCertificates() {
        if (entry == null) {
            return null;
        }

        return ((JarEntry) entry).getCertificates();
    }
}
