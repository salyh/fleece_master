/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fleece.core;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;

public class JsonGeneratorFactoryImpl implements JsonGeneratorFactory, Serializable {
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    private final Map<String, Object> internalConfig = new HashMap<String, Object>();
    private static final String[] SUPPORTED_CONFIG_KEYS = new String[] {
        
        JsonGenerator.PRETTY_PRINTING
        
    };
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<String, String>();
    private final boolean pretty;

    public JsonGeneratorFactoryImpl(final Map<String, ?> config) {
        
          if(config != null) {
          
              for (String configKey : SUPPORTED_CONFIG_KEYS) {
                  if(config.containsKey(configKey)) {
                      internalConfig.put(configKey, config.get(configKey));
                  }
              }
          } 

          if(internalConfig.containsKey(JsonGenerator.PRETTY_PRINTING)) {
              this.pretty = Boolean.TRUE.equals(internalConfig.get(JsonGenerator.PRETTY_PRINTING)) || "true".equals(internalConfig.get(JsonGenerator.PRETTY_PRINTING));
          } else {
              this.pretty = false;
          }
    }

    @Override
    public JsonGenerator createGenerator(final Writer writer) {
        return newJsonGeneratorImpl(writer);
    }

    private JsonGenerator newJsonGeneratorImpl(final Writer writer) {
        if (pretty) {
            return new JsonPrettyGeneratorImpl(writer, cache);
        }
        return new JsonGeneratorImpl(writer, cache);
    }

    @Override
    public JsonGenerator createGenerator(final OutputStream out) {
        return createGenerator(new OutputStreamWriter(out, UTF8_CHARSET));
    }

    @Override
    public JsonGenerator createGenerator(final OutputStream out, final Charset charset) {
        return createGenerator(new OutputStreamWriter(out, charset));
    }

    @Override
    public Map<String, ?> getConfigInUse() {
        return Collections.unmodifiableMap(internalConfig);
    }
}
