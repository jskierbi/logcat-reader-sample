package com.jskierbi.acra_test;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import org.acra.ACRA;
import org.acra.ACRAConstants;
import org.acra.collector.CollectorUtil;
import org.acra.config.ACRAConfiguration;
import org.acra.util.BoundedLinkedList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

class LogCatCollector {

  /**
   * Default number of latest lines kept from the logcat output.
   */
  private static final int DEFAULT_TAIL_COUNT = 100;

  /**
   * Executes the logcat command with arguments taken from
   * {@link ReportsCrashes#logcatArguments()}
   *
   * @param config     AcraConfig to use when collecting logcat.
   * @param bufferName The name of the buffer to be read: "main" (default), "radio" or "events".
   * @return A {@link String} containing the latest lines of the output.
   * Default is 100 lines, use "-t", "300" in
   * {@link ReportsCrashes#logcatArguments()} if you want 300 lines.
   * You should be aware that increasing this value causes a longer
   * report generation time and a bigger footprint on the device data
   * plan consumption.
   */
  public String collectLogCat(@NonNull ACRAConfiguration config, @Nullable String bufferName) {
    final int myPid = android.os.Process.myPid();
    String myPidStr = null;
    if (config.logcatFilterByPid() && myPid > 0) {
      myPidStr = Integer.toString(myPid) + "):";
    }

    final List<String> commandLine = new ArrayList<String>();
    commandLine.add("logcat");
    if (bufferName != null) {
      commandLine.add("-b");
      commandLine.add(bufferName);
    }

    // "-t n" argument has been introduced in FroYo (API level 8). For
    // devices with lower API level, we will have to emulate its job.
    final int tailCount;
    final List<String> logcatArgumentsList = new ArrayList<String>(Arrays.asList(config.logcatArguments()));

    final int tailIndex = logcatArgumentsList.indexOf("-t");
    if (tailIndex > -1 && tailIndex < logcatArgumentsList.size()) {
      tailCount = Integer.parseInt(logcatArgumentsList.get(tailIndex + 1));
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
        logcatArgumentsList.remove(tailIndex + 1);
        logcatArgumentsList.remove(tailIndex);
        logcatArgumentsList.add("-d");
      }
    } else {
      tailCount = -1;
    }

    final LinkedList<String> logcatBuf = new BoundedLinkedList<String>(tailCount > 0 ? tailCount
        : DEFAULT_TAIL_COUNT);
    commandLine.addAll(logcatArgumentsList);

    BufferedReader bufferedReader = null;

    try {
      final Process process = Runtime.getRuntime().exec(commandLine.toArray(new String[commandLine.size()]));
      bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()), ACRAConstants.DEFAULT_BUFFER_SIZE_IN_BYTES);

      if (ACRA.DEV_LOGGING) ACRA.log.d("ACRA", "Retrieving logcat output...");

      // Dump stderr to null
      new Thread(new Runnable() {
        public void run() {
          try {
            InputStream stderr = process.getErrorStream();
            byte[] dummy = new byte[ACRAConstants.DEFAULT_BUFFER_SIZE_IN_BYTES];
            //noinspection StatementWithEmptyBody
            while (stderr.read(dummy) >= 0)
              ;
          } catch (IOException ignored) {
          }
        }
      }).start();

      while (true) {
        final String line = bufferedReader.readLine();
        if (line == null) {
          break;
        }
        if (myPidStr == null || line.contains(myPidStr)) {
          logcatBuf.add(line + "\n");
        }
      }

    } catch (IOException e) {
      ACRA.log.e("ACRA", "LogCatCollector.collectLogCat could not retrieve data.", e);
    } finally {
      CollectorUtil.safeClose(bufferedReader);
    }

    return logcatBuf.toString();
  }
}