package com.sky.job.plugin.script;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;

/** A Windows Job retains descendants even after their parent script exits. */
final class WindowsProcessJob implements AutoCloseable {
    interface Kernel extends StdCallLibrary {
        Pointer CreateJobObjectW(Pointer attributes,Pointer name);
        boolean SetInformationJobObject(Pointer job,int type,Pointer information,int length);
        Pointer OpenProcess(int access,boolean inherit,int pid);
        boolean AssignProcessToJobObject(Pointer job,Pointer process);
        boolean TerminateJobObject(Pointer job,int exitCode);
        boolean CloseHandle(Pointer handle);
    }
    private final Kernel api;
    private final Pointer job;
    private boolean closed;
    private WindowsProcessJob(Kernel api,Pointer job){this.api=api;this.job=job;}
    static WindowsProcessJob attach(Process process) {
        if(!ScriptProcesses.isWindows())return null;
        Kernel api=Native.load("kernel32",Kernel.class);
        Pointer job=api.CreateJobObjectW(null,null);
        if(job==null)throw new IllegalStateException("Cannot create Windows process job");
        try {
            // JOBOBJECT_EXTENDED_LIMIT_INFORMATION: LimitFlags follows two LARGE_INTEGERs.
            int size=Native.POINTER_SIZE==8?144:112;
            try(Memory limits=new Memory(size)) {
                limits.clear();limits.setInt(16,0x2000); // JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
                if(!api.SetInformationJobObject(job,9,limits,size))throw new IllegalStateException("Cannot configure Windows process job");
            }
            Pointer child=api.OpenProcess(0x0100|0x0001,false,(int)process.pid()); // SET_QUOTA | TERMINATE
            if(child==null)throw new IllegalStateException("Cannot open script Runner process");
            try {if(!api.AssignProcessToJobObject(job,child))throw new IllegalStateException("Cannot attach Runner to Windows process job");}
            finally {api.CloseHandle(child);}
            return new WindowsProcessJob(api,job);
        } catch(RuntimeException e){api.CloseHandle(job);throw e;}
    }
    public synchronized void close() {
        if(closed)return;closed=true;
        try {api.TerminateJobObject(job,1);}finally {api.CloseHandle(job);}
    }
}
