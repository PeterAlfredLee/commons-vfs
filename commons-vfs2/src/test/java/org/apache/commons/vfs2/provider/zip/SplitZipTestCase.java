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
package org.apache.commons.vfs2.provider.zip;

import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * Test for read split zip file.
 * These test is copy and modify from commons-compress
 * @since 2.7.0
 */
public class SplitZipTestCase {

    static File getFile(final String path) throws IOException {
        final URL url = SplitZipTestCase.class.getClassLoader().getResource(path);
        if (url == null) {
            throw new FileNotFoundException("couldn't find " + path);
        }
        URI uri = null;
        try {
            uri = url.toURI();
        } catch (URISyntaxException ex) {
            throw new IOException(ex);
        }
        return new File(uri);
    }

    @Test
    public void testFileLiesAcrossSplitZipSegmentsCreatedByZip() throws Exception {
        File lastZipFile = getFile("test-data/read-split-zip-tests/split_zip_created_by_zip/split_zip_created_by_zip.zip");
        FileSystemManager manager = VFS.getManager();

        // check file_to_compare_1
        File fileToCompare = getFile("test-data/read-split-zip-tests/split_zip_created_by_zip/file_to_compare_1");
        String zipFileUri = "zip:file:"+lastZipFile.getAbsolutePath()+"!/"
                +"commons-compress/src/main/java/org/apache/commons/compress/archivers/dump/UnsupportedCompressionAlgorithmException.java";
        assertFileEqualsToContent(fileToCompare, manager.resolveFile(zipFileUri).getContent());

        // check file_to_compare_2
        fileToCompare = getFile("test-data/read-split-zip-tests/split_zip_created_by_zip/file_to_compare_2");
        zipFileUri = "zip:file:"+lastZipFile.getAbsolutePath()+"!/"
                +"commons-compress/src/main/java/org/apache/commons/compress/compressors/deflate/DeflateParameters.java";
        assertFileEqualsToContent(fileToCompare, manager.resolveFile(zipFileUri).getContent());
    }

    @Test
    public void testFileLiesAcrossSplitZipSegmentsCreatedByZipOfZip64() throws Exception {
        File lastZipFile = getFile("test-data/read-split-zip-tests/split_zip_created_by_zip/split_zip_created_by_zip_zip64.zip");
        FileSystemManager manager = VFS.getManager();

        // check file_to_compare_1
        File fileToCompare = getFile("test-data/read-split-zip-tests/split_zip_created_by_zip/file_to_compare_1");
        String zipFileUri = "zip:file:"+lastZipFile.getAbsolutePath()+"!/"
                +"commons-compress/src/main/java/org/apache/commons/compress/archivers/dump/UnsupportedCompressionAlgorithmException.java";
        assertFileEqualsToContent(fileToCompare, manager.resolveFile(zipFileUri).getContent());

        // check file_to_compare_2
        fileToCompare = getFile("test-data/read-split-zip-tests/split_zip_created_by_zip/file_to_compare_2");
        zipFileUri = "zip:file:"+lastZipFile.getAbsolutePath()+"!/"
                +"commons-compress/src/main/java/org/apache/commons/compress/compressors/deflate/DeflateParameters.java";
        assertFileEqualsToContent(fileToCompare, manager.resolveFile(zipFileUri).getContent());
    }

    @Test
    public void testFileLiesAcrossSplitZipSegmentsCreatedByWinrar() throws Exception {
        File lastZipFile = getFile("test-data/read-split-zip-tests/split_zip_created_by_winrar/split_zip_created_by_winrar.zip");
        FileSystemManager manager = VFS.getManager();

        // check file_to_compare_1
        File fileToCompare = getFile("test-data/read-split-zip-tests/split_zip_created_by_winrar/file_to_compare_1");
        String zipFileUri = "zip:file:"+lastZipFile.getAbsolutePath()+"!/"
                +"commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipArchiveInputStream.java";
        assertFileEqualsToContent(fileToCompare, manager.resolveFile(zipFileUri).getContent());
    }

    private void assertFileEqualsToContent(File fileToCompare, FileContent fileContent) throws IOException {
        byte[] buffer = new byte[10240];
        File tempFile = File.createTempFile("temp","txt");
        tempFile.deleteOnExit();
        OutputStream outputStream = new FileOutputStream(tempFile);
        InputStream inputStream = fileContent.getInputStream();
        int readLen;
        while((readLen = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, readLen);
        }

        outputStream.close();
        inputStream.close();

        assertFileEqualIgnoreEndOfLine(fileToCompare, tempFile);
    }

    private void assertFileEqualIgnoreEndOfLine(File file1, File file2) throws IOException {
        List<String> linesOfFile1 = Files.readAllLines(Paths.get(file1.getCanonicalPath()), StandardCharsets.UTF_8);
        List<String> linesOfFile2 = Files.readAllLines(Paths.get(file2.getCanonicalPath()), StandardCharsets.UTF_8);

        if(linesOfFile1.size() != linesOfFile2.size()) {
            fail("files not equal : " + file1.getName() + " , " + file2.getName());
        }

        String tempLineInFile1;
        String tempLineInFile2;
        for (int i = 0;i < linesOfFile1.size();i++) {
            tempLineInFile1 = linesOfFile1.get(i).replaceAll("\r\n", "\n");
            tempLineInFile2 = linesOfFile2.get(i).replaceAll("\r\n", "\n");
            Assert.assertEquals(tempLineInFile1, tempLineInFile2);
        }
    }

}