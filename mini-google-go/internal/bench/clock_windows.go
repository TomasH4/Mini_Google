package bench

import (
	"fmt"
	"syscall"
	"unsafe"
)

// The Go runtime clock can record zero for short queries on Windows.
// QPC provides a process-wide monotonic counter with higher resolution.
func platformClock() (clock, error) {
	kernel := syscall.NewLazyDLL("kernel32.dll")
	counter := kernel.NewProc("QueryPerformanceCounter")
	if err := counter.Find(); err != nil {
		return clock{}, fmt.Errorf("load QueryPerformanceCounter: %w", err)
	}
	queryFrequency := kernel.NewProc("QueryPerformanceFrequency")
	if err := queryFrequency.Find(); err != nil {
		return clock{}, fmt.Errorf("load QueryPerformanceFrequency: %w", err)
	}
	var frequency int64
	ok, _, err := queryFrequency.Call(uintptr(unsafe.Pointer(&frequency)))
	if ok == 0 || frequency <= 0 {
		return clock{}, fmt.Errorf("read performance counter frequency: %w", err)
	}
	return clock{
		source: "windows-qpc",
		now: func() int64 {
			var ticks int64
			// On supported Windows versions QPC always succeeds with a valid pointer.
			counter.Call(uintptr(unsafe.Pointer(&ticks)))
			return ticks
		},
		frequency: frequency,
	}, nil
}
