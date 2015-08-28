package com.dji.dev.util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import android.app.ActivityManager;
import android.content.Context;

public class MemInfo {
    // get available mem
    public static long getmem_UNUSED(Context mContext)
    {
        long MEM_UNUSED;
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        MEM_UNUSED = mi.availMem / (1024*1024);  //Mb
        return MEM_UNUSED;
    }
    //get total mem
    public static long getmem_TOLAL()
    {
        long mTotal;
        //read from /proc/meminfo(adb shell:cat /proc/meminfo)
        String path = "/proc/meminfo";
        String content = null;
        BufferedReader br = null;
        try
        {
            br = new BufferedReader(new FileReader(path), 8);
            String line;
            if((line = br.readLine()) != null)
            {
                content = line;
            }
        }
        catch(FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (br != null)
            {
                try
                {
                    br.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        // beginIndex
        int begin = content.indexOf(':');
        // endIndex
        int end = content.indexOf('k');
        content = content.substring(begin + 1, end).trim();
        mTotal = Integer.parseInt(content);
        mTotal=mTotal/1024;  //Mb
        return mTotal;
    }
}
