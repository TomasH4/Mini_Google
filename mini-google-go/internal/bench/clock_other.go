//go:build !windows

package bench

func platformClock() (clock, error) { return monotonicClock(), nil }
