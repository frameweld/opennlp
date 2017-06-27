/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Reads a plain text file and return each line as a <code>String</code> object.
 */
public class PlainTextByTimeStream implements ObjectStream<String> {
  private int millisOffset = 2000;
  private int bufferSize = 2000;

  private long lastRead = System.currentTimeMillis();

  private boolean unread = true;

  private boolean blockInput = false;

  private final FileChannel channel;
  private final String encoding;

  private InputStreamFactory inputStreamFactory;

  private BufferedReader in;

  public PlainTextByTimeStream(InputStreamFactory inputStreamFactory,
                               String charsetName) throws IOException {
    this(inputStreamFactory, Charset.forName(charsetName));
  }

  public PlainTextByTimeStream(InputStreamFactory inputStreamFactory,
                               Charset charset) throws IOException {
    this.inputStreamFactory = inputStreamFactory;
    this.channel = null;
    this.encoding = charset.name();

    reset();
  }

  public boolean setBlockingInput() {
    return blockInput = true;
  }

  public int getMillisOffset() {
    return millisOffset;
  }

  public int setMillisOffset(int offset) {
    return millisOffset = offset;
  }

  public String read() throws IOException {
    long adjust = System.currentTimeMillis() - lastRead;

    if ((!unread) && adjust < millisOffset) {
      try {
        Thread.sleep(millisOffset - adjust);
      } catch (InterruptedException e) {
        //this is fine
      }
    }

    char[] buf = new char[bufferSize];

    String input = new String();

    int read_return = 0;

    while (((blockInput && !(blockInput = false)) || unread || in.ready())
           && (read_return = in.read(buf, 0, bufferSize)) > 0 && !(unread && (unread = false))) {
      lastRead = System.currentTimeMillis();

      input = input.concat(new String(Arrays.copyOf(buf, read_return)));

      if (read_return < bufferSize) {
        break;
      }

      buf = new char[bufferSize];
    }

    if (input.equals("") && read_return == -1) {
      return null;
    }

    return input;
  }

  public void reset() throws IOException {

    if (inputStreamFactory != null) {
      in = new BufferedReader(new InputStreamReader(inputStreamFactory.createInputStream(),
          encoding));
    } else if (channel == null) {
      in.reset();
    } else {
      channel.position(0);
      in = new BufferedReader(Channels.newReader(channel, encoding));
    }
  }

  public void close() throws IOException {
    if (in != null && channel == null) {
      in.close();
    } else if (channel != null) {
      channel.close();
    }
  }
}
