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
package org.apache.pinot.plugin.stream.kinesis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.Map;
import org.apache.pinot.spi.stream.Checkpoint;
import org.apache.pinot.spi.stream.StreamPartitionMsgOffset;
import org.apache.pinot.spi.utils.JsonUtils;


public class KinesisCheckpoint implements StreamPartitionMsgOffset {
  private Map<String, String> _shardToStartSequenceMap;

  public KinesisCheckpoint(Map<String, String> shardToStartSequenceMap) {
    _shardToStartSequenceMap = shardToStartSequenceMap;
  }

  public KinesisCheckpoint(String checkpointStr)
      throws IOException {
    _shardToStartSequenceMap = JsonUtils.stringToObject(checkpointStr, new TypeReference<Map<String, String>>() {
    });
  }

  public Map<String, String> getShardToStartSequenceMap() {
    return _shardToStartSequenceMap;
  }

  @Override
  public String serialize() {
    try {
      return JsonUtils.objectToString(_shardToStartSequenceMap);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException();
    }
  }

  @Override
  public KinesisCheckpoint deserialize(String blob) {
    try {
      return new KinesisCheckpoint(blob);
    } catch (IOException e) {
      throw new IllegalStateException();
    }
  }

  @Override
  public int compareTo(Object o) {
    return this._shardToStartSequenceMap.values().iterator().next().compareTo(((KinesisCheckpoint) o)._shardToStartSequenceMap.values().iterator().next());
  }
}
