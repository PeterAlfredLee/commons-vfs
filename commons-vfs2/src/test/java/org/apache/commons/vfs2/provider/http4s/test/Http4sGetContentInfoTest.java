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
package org.apache.commons.vfs2.provider.http4s.test;

import org.apache.commons.vfs2.FileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.http.test.HttpGetContentInfoTest;
import org.apache.commons.vfs2.provider.http4.Http4FileSystemConfigBuilder;

/**
 * Tests VFS-427 NPE on HttpFileObject.getContent().getContentInfo()
 *
 * @since 2.1
 */
public class Http4sGetContentInfoTest extends HttpGetContentInfoTest {

    @Override
    public String getTestUri() {
        return "http4s://www.apache.org/licenses/LICENSE-2.0.txt";
    }

    @Override
    public FileSystemConfigBuilder getConfigBuilder() {
        return Http4FileSystemConfigBuilder.getInstance();
    }
}
