/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.grigio.common.keyValueStore;

import org.apache.pinot.grigio.common.messages.KeyCoordinatorMessageContext;
import org.apache.pinot.spi.utils.retry.RetryPolicies;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class RocksDBKeyValueStoreTable implements KeyValueStoreTable<ByteArrayWrapper, KeyCoordinatorMessageContext> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RocksDBKeyValueStoreTable.class);
  private final String _path;
  private final Options _options;
  private final ReadOptions _readOptions;
  private final WriteOptions _writeOptions;
  private final RocksDB _db;

  public RocksDBKeyValueStoreTable(String path, Options options, ReadOptions readOptions, WriteOptions writeOptions)
      throws IOException {
    _path = path;
    _options = options;
    _readOptions = readOptions;
    _writeOptions = writeOptions;
    try {
      _db = RocksDB.open(path);
    } catch (RocksDBException e) {
      throw new IOException("failed to open rocksdb db", e);
    }
  }

  @Override
  public Map<ByteArrayWrapper, KeyCoordinatorMessageContext> multiGet(List<ByteArrayWrapper> keys) throws IOException {
    try {
      List<byte[]> byteKeys = keys.stream().map(ByteArrayWrapper::getData).collect(Collectors.toList());
      RocksDBBatchReader batchReader = new RocksDBBatchReader(_db, byteKeys);
      RetryPolicies.fixedDelayRetryPolicy(RocksDBBatchReader.MAX_RETRY_ATTEMPTS, RocksDBBatchReader.RETRY_WAIT_MS).attempt(batchReader);
      Map<byte[], byte[]> rocksdbResult = batchReader.getResult();
      Map<ByteArrayWrapper, KeyCoordinatorMessageContext> result = new HashMap<>(rocksdbResult.size());
      for (Map.Entry<byte[], byte[]> entry : rocksdbResult.entrySet()) {
        Optional<KeyCoordinatorMessageContext> value = KeyCoordinatorMessageContext.fromBytes(entry.getValue());
        if (!value.isPresent()) {
          LOGGER.warn("failed to parse value in kv for key {} and value {}", entry.getKey(), entry.getValue());
        } else {
          result.put(new ByteArrayWrapper(entry.getKey()), value.get());
        }
      }
      return result;
    } catch (Exception e) {
      throw new IOException("failed to get keys from rocksdb " + _path, e);
    }
  }

  @Override
  public void multiPut(Map<ByteArrayWrapper, KeyCoordinatorMessageContext> keyValuePairs) throws IOException {
    if (keyValuePairs.size() == 0) {
      return;
    }
    final WriteBatch batch = new WriteBatch();
    try {
      for (Map.Entry<ByteArrayWrapper, KeyCoordinatorMessageContext> entry: keyValuePairs.entrySet()) {
        batch.put(entry.getKey().getData(), entry.getValue().toBytes());
      }
      RocksDBBatchWriter batchWriter = new RocksDBBatchWriter(_db, _writeOptions, batch);
      RetryPolicies.fixedDelayRetryPolicy(RocksDBBatchWriter.MAX_RETRY_ATTEMPTS, RocksDBBatchWriter.RETRY_WAIT_MS).attempt(batchWriter);
    } catch (Exception e) {
      throw new IOException("failed to put data to rocksdb table " + _path, e);
    }

  }

  @Override
  public void deleteTable() throws IOException {
    String backupPath = _path + ".bak";
    Files.delete(Paths.get(backupPath));
    Files.move(Paths.get(_path), Paths.get(backupPath), StandardCopyOption.COPY_ATTRIBUTES);
  }
}