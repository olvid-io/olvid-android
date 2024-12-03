/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.olvid.engine;


import java.util.UUID;

public class Logger {
    public static final int DEBUG = 0;
    public static final int INFO = 1;
    public static final int WARNING = 2;
    public static final int ERROR = 3;
    public static final int NONE = 10;

    private static int outputLogLevel = NONE;

    private static LogOutputter outputter = null;

    public static void setOutputter(LogOutputter outputter) {
        Logger.outputter = outputter;
    }

    private static void log(int logLevel, String message) {
        if (logLevel >= outputLogLevel) {
            if (outputter == null) {
                System.out.println(message);
            } else {
                switch (logLevel) {
                    case Logger.DEBUG:
                        outputter.d("Logger", message);
                        break;
                    case Logger.INFO:
                        outputter.i("Logger", message);
                        break;
                    case Logger.WARNING:
                        outputter.w("Logger", message);
                        break;
                    case Logger.ERROR:
                        outputter.e("Logger", message);
                        break;
                }
            }
        }
    }

    public static void setOutputLogLevel(int outputLogLevel) {
        Logger.outputLogLevel = outputLogLevel;
    }

    public static void d(String message) {
        log(DEBUG, message);
    }
    public static void i(String message) {
        log(INFO, message);
    }
    public static void w(String message) {
        log(WARNING, message);
    }
    public static void e(String message) {
        log(ERROR, message);
    }
    public static void e(String message, Exception e) {
        log(ERROR, message + "( " + e.toString() + ")");
        x(e);
    }
    public static void x(Throwable throwable) {
        if (WARNING >= outputLogLevel) {
            outputter.x("Logger", throwable);
        }
    }

    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String toHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] fromHexString(String hex) {
        int len = hex.length();
        byte[] data = new byte[len/2];
        for(int i = 0; i < len; i+=2){
            data[i/2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }


    public static String getUuidString(UUID uuid) {
        if (uuid == null) {
            return "";
        }
        return (digits(uuid.getMostSignificantBits() >> 32, 8) + "-" +
                digits(uuid.getMostSignificantBits() >> 16, 4) + "-" +
                digits(uuid.getMostSignificantBits(), 4) + "-" +
                digits(uuid.getLeastSignificantBits() >> 48, 4) + "-" +
                digits(uuid.getLeastSignificantBits(), 12));
    }

    private static String digits(long val, int digits) {
        long hi = 1L << (digits * 4);
        return Long.toHexString(hi | (val & (hi - 1))).substring(1);
    }

    public interface LogOutputter {
        void d(String tag, String message);
        void i(String tag, String message);
        void w(String tag, String message);
        void e(String tag, String message);
        void x(String tag, Throwable throwable);
    }
}
