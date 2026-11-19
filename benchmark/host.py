import ctypes
import os
import platform
from pathlib import Path

def machine():
    if os.name == "nt":
        import winreg
        class Memory(ctypes.Structure):
            _fields_ = [("length", ctypes.c_uint32), ("load", ctypes.c_uint32),
                        *[(name, ctypes.c_uint64) for name in
                          "total free page freepage virtual freevirtual extended".split()]]
        mem = Memory()
        mem.length = ctypes.sizeof(mem)
        if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(mem)):
            raise ctypes.WinError()
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"HARDWARE\DESCRIPTION\System\CentralProcessor\0") as key:
            cpu = winreg.QueryValueEx(key, "ProcessorNameString")[0].strip()
        total = mem.total
    else:
        info = Path("/proc/cpuinfo").read_text()
        cpu = next((line.split(":", 1)[1].strip() for line in info.splitlines()
                    if line.startswith("model name")), platform.machine())
        total = os.sysconf("SC_PHYS_PAGES") * os.sysconf("SC_PAGE_SIZE")
    return dict(os=platform.platform(), cpu_model=cpu, cores=os.cpu_count(), ram_total_gb=total / 2**30)

def peak_mb(process):
    if os.name == "nt":
        class Counters(ctypes.Structure):
            _fields_ = [("cb", ctypes.c_uint32), ("faults", ctypes.c_uint32),
                        *[(name, ctypes.c_size_t) for name in
                          "peak working pagedmax paged nonpagedmax nonpaged pagefile pagefilemax".split()]]
        counters = Counters()
        counters.cb = ctypes.sizeof(counters)
        if not ctypes.windll.psapi.GetProcessMemoryInfo(ctypes.c_void_p(int(process._handle)),
                                                       ctypes.byref(counters), counters.cb):
            raise ctypes.WinError()
        return counters.peak / 2**20
    status = Path(f"/proc/{process.pid}/status").read_text()
    return int(next(row.split()[1] for row in status.splitlines() if row.startswith("VmHWM:"))) / 1024

