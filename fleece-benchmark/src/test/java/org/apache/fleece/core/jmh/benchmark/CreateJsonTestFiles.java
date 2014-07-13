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
package org.apache.fleece.core.jmh.benchmark;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;

public class CreateJsonTestFiles {

    public static void main(final String[] args) throws Exception {

        create();
    }

    public static void create() throws Exception {

        final String path = "./target/test-classes/bench";
        final File dir = new File(path).getAbsoluteFile();
        dir.mkdirs();

        System.out.println("Create files to " + dir.getAbsolutePath());

        createUnicodeString(path + "/generated_benchmark_test_file_unicodes.txt");
        create(path, 1);
        create(path, 3);
        create(path, 10);
        create(path, 30);
        create(path, 100);
        create(path, 1000);
        create(path, 100000);
        //create(path, 1000000);
    }

    public static File createUnicodeString(final String path) throws Exception {

        final StringBuilder sb = new StringBuilder();

        for (char c = 0; c < '\u0a14'; c++) {
            sb.append(c);
        }

        for (int i = 0; i < 8; i++) {
            sb.append(sb);
        }

        final File file = new File(path);
        FileUtils.write(file, sb.toString(), Charset.forName("UTF-8"));
        return file;
    }

    public static File create(final String path, final int count) throws Exception {

        if (count < 0 || path == null || path.length() == 0) {
            throw new IllegalArgumentException();
        }

        final File json = new File(path + "/" + "generated_benchmark_test_file_" + count + "kb.json");
        final FileWriterWithEncoding sb = new FileWriterWithEncoding(json, StandardCharsets.UTF_8);

        sb.append("{\n");

        for (int i = 0; i < count; i++) {

            sb.append("\t\"special-" + i + "\":" + "\"" + "\\\\f\\n\\r\\t\\uffff\udbff\udfff" + "\",\n");
            sb.append("\t\"unicode-\\u0000- " + i + "\":\"\\u5656\udbff\udfff\",\n");
            sb.append("\t\"\uffffbigdecimal" + i + "\":7817265.00000111,\n");
            sb.append("\t\"bigdecimal-2-" + i + "\":127655512123456.761009E-123,\n");
            sb.append("\t\"string-" + i + "\":\"lorem ipsum, ÄÖÜäöü.-,<!$%&/()9876543XXddddJJJJJJhhhhhhhh\udbff\udfff\",\n");
            sb.append("\t\t\t\t\"int" + i + "\":[1,-4543,112,0,1,10,100,87,34112, true, false, null],\n");
            sb.append("\t\"\uffffints" + i + "\":0,\n");
            sb.append("\t\"\ufffffalse" + i + "\":false,\n");
            sb.append("\t\"\uffffnil" + i + "\":false,\n");
            sb.append("\t\"\uffffn" + i + "\":      null                ,\n");
            sb.append("\t\"obj" + i + "\":\n");

            sb.append("\t\t{\n");

            sb.append("\t\t\t\"special-" + i + "\":" + "\"" + "\\\\f\\n\\r\\t\\uffff" + "\",\n");
            sb.append("\t\t\t\"unicode-\\u0000- " + i + "\":\"\\u5656\",\n");
            sb.append(" \"bigdecimal" + i + "\":7817265.00000111,\n");
            sb.append("\t\t\t\"bigdecimal-2-" + i + "\":127655512123456.761009E-123,\n");
            sb.append("\t\t\t\"string-" + i + "\":\"lorem ipsum, ÄÖÜäöü.-,<!$%&/()9876543XXddddJJJJJJhhhhhhhh\",\n");
            sb.append("\t\t\t\t\"int" + i + "\":[1,-4543,112,0,1,10,100,87,34112, true, false, null],\n");
            sb.append("\t\t\t\"ints" + i + "\":0,\n");
            sb.append("\t\t\t\"false" + i + "\":false,\n");
            sb.append("\t\t\t\"nil" + i + "\":false,\n");
            sb.append("\t\t\t\"obj" + i + "\":      null                ,\n");
            sb.append("\t\t\t\"obj" + i + "\":\n");
            sb.append("\t\t\t\t[    true, \"normal string ascii only normal string ascii only\"    ,\n");

            sb.append("\t\t\t\t{\n");

            sb.append("\t\t\t\t\"special-" + i + "\":" + "\"" + "\\\\f\\n\\r\\t\\uffff" + "\",\n");
            sb.append("\t\t\t\t\"unicode-\\u0000- " + i + "\":\"\\u5656\",\n");
            sb.append("\t\t\t\t\"bigdecimal" + i + "\":7817265.00000111,\n");
            sb.append("\t\t\t\t\"bigdecimal-2-" + i + "\":127655512123456.761009E-123,\n");
            sb.append("\t\t\t\t\"string-" + i + "\":\"lorem ipsum, ÄÖÜäöü.-,<!$%&/()9876543XXddddJJJJJJhhhhhhhh\",\n");
            sb.append("\t\t\t\t\"int" + i + "\":[1,-4543,112,0,1,10,100,87,34112, true, false, null],\n");
            sb.append("\t\t\t\t\"ints" + i + "\":0,\n");
            sb.append("\t\t\t\t\"false" + i + "\":false,\n");
            sb.append("\t\t\t\t\"nil" + i + "\":false,\n");
            sb.append("\t\t\t\t\"obj" + i + "\":      null                \n");
            sb.append("\t\t\t\t\n}\n");

            sb.append("\t\t\t]\n");

            sb.append("\t\t\n}\n\n\n\n                 \t\r                                                      ");

            if (i < count - 1) {
                sb.append(",\n");
            } else {
                sb.append("\n");
            }
        }

        sb.append("\n}\n");

        sb.close();

        return json;

    }

}