package com.example.myapplication;

import android.content.Context;
import android.se.omapi.Channel;
import android.se.omapi.Reader;
import android.se.omapi.SEService;
import android.se.omapi.SEService.OnConnectedListener;
import android.se.omapi.Session;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

public class OMAPI {
    private static OMAPI mOM = null;

    private SEService seService;
    private final long SERVICE_CONNECTION_TIME_OUT = 3000;
    private final Object serviceMutex = new Object();
    private Timer connectionTimer;
    private ServiceConnectionTimerTask mTimerTask = new ServiceConnectionTimerTask();
    private boolean connected = false;

    private final static String UICC_READER_PREFIX = "SIM";
    private String mReaderName = "";
    private Reader mReader = null;
    private Session mSession = null;
    private Channel mChannel = null;

    private CallBackLog mLog = null;

    class ServiceConnectionTimerTask extends TimerTask {
        @Override
        public void run() {
            synchronized (serviceMutex) {
                serviceMutex.notifyAll();
            }
        }
    }

    class SynchronousExecutor implements Executor {
        public void execute(Runnable r) {
            r.run();
        }
    }

    public OMAPI(Context context, CallBackLog log)
    {
        mLog = log;
        prepareOM(context);
    }

    public static OMAPI getInstance(Context context, CallBackLog log) {
        if ((null == log) || (null == context))
        {
            return  null;
        }
        if (null == mOM) {
            mOM = new OMAPI(context, log);
        }
        return mOM;
    }

    private void prepareOM(Context context)
    {
        seService = new SEService(context, new SynchronousExecutor(), mListener);
        connectionTimer = new Timer();
        connectionTimer.schedule(mTimerTask, SERVICE_CONNECTION_TIME_OUT);
    }

    private void waitForConnection() throws TimeoutException {
        synchronized (serviceMutex) {
            if (!connected) {
                try {
                    serviceMutex.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (!connected) {
                throw new TimeoutException(
                        "Service could not be connected after "
                                + SERVICE_CONNECTION_TIME_OUT + " ms");
            }
            if (connectionTimer != null) {
                connectionTimer.cancel();
            }
        }
    }

    private final OnConnectedListener mListener = new OnConnectedListener() {
        public void onConnected() {
            synchronized (serviceMutex) {
                connected = true;
                serviceMutex.notify();
            }
        }
    };

    public String listReaders()
    {
        String names = "";
        Reader[] readers = seService.getReaders();
        for (Reader reader : readers)
        {
            names = names + reader.getName();
            names = names + "\r\n";
        }
        return names;
    }

    public boolean setReaderName(String readerName)
    {
        try
        {
            waitForConnection();
            Reader[] readers = seService.getReaders();
            for (Reader reader : readers)
            {
                if (0 == reader.getName().compareToIgnoreCase(readerName))
                {
                    mReader = reader;
                    return  true;
                }
            }
        }catch (Exception e)
        {
            // do nothing
        }
        return false;
    }

    public Reader getReader()
    {

        if (null != mReader)
        {
            return mReader;
        }
        // 如果没有设定Reader名称，用SIM开头的第一个
        try
        {
            waitForConnection();
            Reader[] readers = seService.getReaders();
            for (Reader reader : readers)
            {
                if (reader.getName().startsWith(UICC_READER_PREFIX))
                {
                    mReader = reader;
                    break;
                }
            }
        }catch (Exception e)
        {
            return null;
        }
        return  mReader;
    }

    boolean openLogicalChannel(byte[] aid)
    {
        if (null == getReader())
        {
            mLog.log("Failed to get reader.");
            return false;
        }

        if (false == mReader.isSecureElementPresent())
        {
            mLog.log("mReader.isSecureElementPresent()");
            return false;
        }

        // 先清理
        cleanUp();

        try
        {
            mSession = mReader.openSession();
            if (null == mSession)
            {
                mLog.log("Failed to openSession");
                return false;
            }

            mChannel = mSession.openLogicalChannel(aid);
            if (null == mChannel)
            {
                mLog.log("Failed to openLogicalChannel");
                return false;
            }

        }catch (Exception e)
        {
            mLog.log(e.getMessage());
            return  false;
        }
        mLog.log("OK to OpenLogicalChannel");
        return true;
    }

    public void cleanUp()
    {
        if (null != mChannel)
        {
            try
            {
                mChannel.close();
            }catch (Exception e)
            {
                // do nothing
            }
            mChannel = null;
        }

        if (null != mSession)
        {
            try
            {
                mSession.close();
            }catch (Exception e)
            {
                // do nothing
            }
            mSession = null;
        }
    }

    public final static byte[] Str2Bytes(String str)
    {
        int pos = 0;
        byte tmpB;
        if(null == str)
        {
            return null;
        }

        byte[] bytes = new byte[str.length() / 2];
        // 清零
        for (byte b: bytes)
        {
            b = (byte) 0x00;
        }

        char[] chars = str.toCharArray();
        tmpB = 0;
        for (char c : chars)
        {
            boolean bMatch = false;
            byte b = (byte) 0;
            if (('a' <= c) && (c <= 'f'))
            {
                b = (byte)(10 + (c - 'A'));
                bMatch = true;
            }

            if (('A' <= c) && (c <= 'F'))
            {
                b = (byte)(10 + (c - 'A'));
                bMatch = true;
            }

            if (('0' <= c) && (c <= '9'))
            {
                b = (byte)(c - '0');
                bMatch = true;
            }

            if (false == bMatch)
            {
                // 出错了，应该抛异常
                return null;
            }
            if (0 == (pos % 2))
            {
                // 高位
                tmpB = (byte)(b << 4);
            }
            else
            {
                tmpB = (byte) (tmpB | b);
                // 赋值到byte[]
                bytes[pos / 2] = tmpB;
                tmpB = (byte) 0;
            }
            pos++;
        }

        return bytes;
    }

    public byte[] SendAPDU(byte[] apdu) throws Exception
    {
        byte[] response;
        if(false == mChannel.isOpen())
        {
            return null;
        }

        if (true == mSession.isClosed())
        {
            return null;
        }

        response = mChannel.transmit(apdu);

        return  response;
    }

    public final static String Bytes2Str(byte[] bytes)
    {
        if (null == bytes)
        {
            return null;
        }

        StringBuffer strBuf = new StringBuffer();
        boolean isMatch = false;
        for (byte b:bytes) {
            // 高位
            byte tmpb = (byte) ((byte)0x0F & (byte)(b >> 4));
            isMatch = false;
            if ((0 <= tmpb) && (tmpb <= 9)) {
                strBuf.append((char) ('0' + tmpb));
                isMatch = true;
            }
            if ((0x0A <= tmpb) && (tmpb <= 0x0F)) {
                strBuf.append((char) ('A' + (tmpb - 0x0A)));
                isMatch = true;
            }
            if (false == isMatch)
            {
                return null;
            }

            // 低位
            tmpb = (byte) (b & (byte) 0x0F);
            isMatch = false;
            if ((0 <= tmpb) && (tmpb <= 9)) {
                strBuf.append((char) ('0' + tmpb));
                isMatch = true;
            }
            if ((0x0A <= tmpb) && (tmpb <= 0x0F)) {
                strBuf.append((char) ('A' + (tmpb - 0x0A)));
                isMatch = true;
            }
            if (false == isMatch)
            {
                return null;
            }
        }
        return strBuf.toString();
    }
}
