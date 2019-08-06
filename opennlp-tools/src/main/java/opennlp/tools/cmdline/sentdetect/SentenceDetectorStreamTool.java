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

package opennlp.tools.cmdline.sentdetect;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParagraphStream;
import opennlp.tools.util.PlainTextByTimeStream;
import opennlp.tools.util.StringUtil;

/**
 * A sentence detector which uses a maxent model to predict the sentences.
 */
public final class SentenceDetectorStreamTool extends BasicCmdLineTool {

  private int maxIdleMillis = 6000;

  private int readOffset = 0;

  private long idleMillis;

  private boolean stripNewline = false;

  public String getShortDescription() {
    return "learnable sentence detector for streaming content";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() 
           + " [--strip-newline] [--max-idle <millis>] [--read-offset <millis>] model > sentences";
  }

  /**
   * Perform sentence detection the input stream.
   *
   * A newline will be treated as a paragraph boundary.
   */
  public void run(String[] args) {
    boolean validParams = false;

    String modelFile = null;

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--strip-newline":
          stripNewline = true;

          break;
        case "--max-idle":
          if (++i < args.length) {
            try {
              maxIdleMillis = +Integer.parseInt(args[i]);
            } catch (NumberFormatException e) {
              i = args.length;
              validParams = false;
            }
          } else {
            i = args.length;
            validParams = false;
          }

          break;
        case "--read-offset":
          if (++i < args.length) {
            try {
              readOffset = +Integer.parseInt(args[i]);
            } catch (NumberFormatException e) {
              i = args.length;
              validParams = false;
            }
          } else {
            i = args.length;
            validParams = false;
          }

          break;
        default:
          if (modelFile == null) {
            modelFile = args[i];
            validParams = true;
          } else {
            i = args.length;
            validParams = false;
          }
      }
    }

    if (!validParams) {
      System.out.println(getHelp());
    } else {
      SentenceModel model = new SentenceModelLoader().load(new File(modelFile));

      SentenceDetectorME sdetector = new SentenceDetectorME(model);

      PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "sent");
      perfMon.start();

      try {
        idleMillis = System.currentTimeMillis();

        PlainTextByTimeStream textStream = new PlainTextByTimeStream(
            new SystemInputStreamFactory(), SystemInputStreamFactory.encoding());

        if (readOffset > 0) {
          textStream.setMillisOffset(readOffset);
        }

        ObjectStream<String> paraStream = new ParagraphStream(textStream);

        String para = new String();
        String remainder = null;
        while ((para != null 
               && (para = ((ParagraphStream) paraStream).readImmediately(remainder == null)) != null)
               || remainder != null) {
          String input = (remainder != null ? remainder : "") + (para != null ? para : "");

          if (!input.equals("")) {
            String trailingWhitespace = new String();

            {
              char whitespace;
              for (int len = input.length(), i = 0; 
                   len - 1 - i >= 0 && StringUtil.isWhitespace(whitespace = input.charAt(len - 1 - i)); i++) {
                trailingWhitespace = String.valueOf(whitespace).concat(trailingWhitespace);
              }
            }

            String[] sents = sdetector.sentDetect(input);

//            System.out.println(new String("sents: ") + Arrays.toString(sents));

            if (sents.length > 0) {
              boolean force_remainder = false;

              if (para != null && para.equals("")) {
                if (System.currentTimeMillis() - idleMillis > maxIdleMillis) {
                  force_remainder = true;
                }
              } else {
                idleMillis = System.currentTimeMillis();
              }

              if (force_remainder || para == null) {
                if (stripNewline) {
                  for (String sentence : sents) {
                    System.out.println(sentence.replace("\r", " ").replace("\n", " "));
                  }
                } else {
                  for (String sentence : sents) {
                    System.out.println(sentence);
                  }
                }

                perfMon.incrementCounter(sents.length);    
  
                remainder = null;
              } else {
                if (stripNewline) {
                  for (int i = 0; i < sents.length - 1; i++) {
                    System.out.println(sents[i].replace("\r", " ").replace("\n", " "));
                  }
                } else {
                  for (int i = 0; i < sents.length - 1; i++) {
                    System.out.println(sents[i]);
                  }
                }
         
                perfMon.incrementCounter(sents.length - 1);
  
                remainder = sents[sents.length - 1].concat(trailingWhitespace);
              }

//            System.out.println();
            }
          }
        }
      }
      catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }

      perfMon.stopAndPrintFinalResult();
    }
  }
}
