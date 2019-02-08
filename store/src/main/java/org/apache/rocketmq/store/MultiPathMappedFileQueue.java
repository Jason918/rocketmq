/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.store;


import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.store.config.MessageStoreConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultiPathMappedFileQueue extends MappedFileQueue {

    private final MessageStoreConfig config;

    public MultiPathMappedFileQueue(MessageStoreConfig messageStoreConfig, int mappedFileSize,
        AllocateMappedFileService allocateMappedFileService) {
        super(messageStoreConfig.getStorePathCommitLog(), mappedFileSize, allocateMappedFileService);
        this.config = messageStoreConfig;
    }


    @Override
    public boolean load() {
        List<File> files = new ArrayList<>();
        for (String path : config.getCommitLogStorePaths()) {
            File dir = new File(path);
            File[] ls = dir.listFiles();
            if (ls != null) {
                Collections.addAll(files, ls);
            }
        }
        if (config.getReadOnlyCommitLogStorePaths() != null) {
            for (String path : config.getReadOnlyCommitLogStorePaths()) {
                File dir = new File(path);
                File[] ls = dir.listFiles();
                if (ls != null) {
                    Collections.addAll(files, ls);
                }
            }
        }

        return doLoad(files);
    }

    @Override
    protected MappedFile tryCreateMappedFile(long createOffset) {
        long fileIdx = createOffset / this.mappedFileSize;
        List<String> pathList = config.getCommitLogStorePaths();
        String nextFilePath = pathList.get((int) (fileIdx % pathList.size())) + File.separator
                + UtilAll.offset2FileName(createOffset);
        String nextNextFilePath = pathList.get((int) ((fileIdx + 1) % pathList.size())) + File.separator
                + UtilAll.offset2FileName(createOffset + this.mappedFileSize);
        return doCreateMappedFile(nextFilePath, nextNextFilePath);
    }

    @Override
    public void destroy() {
        for (MappedFile mf : this.mappedFiles) {
            mf.destroy(1000 * 3);
        }
        this.mappedFiles.clear();
        this.flushedWhere = 0;

        if (config.getCommitLogStorePaths() != null) {
            for (String path : config.getCommitLogStorePaths()) {
                File file = new File(path);
                if (file.isDirectory()) {
                    file.delete();
                }
            }
        }
        if (config.getReadOnlyCommitLogStorePaths() != null) {
            for (String path : config.getReadOnlyCommitLogStorePaths()) {
                File file = new File(path);
                if (file.isDirectory()) {
                    file.delete();
                }
            }
        }
    }
}
