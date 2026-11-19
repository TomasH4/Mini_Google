package bench

import "time"

type clock struct {
	source    string
	now       func() int64
	frequency int64
}

func (c clock) elapsed(start int64) time.Duration {
	ticks := c.now() - start
	return time.Duration(float64(ticks) * float64(time.Second) / float64(c.frequency))
}

func monotonicClock() clock {
	origin := time.Now()
	return clock{
		source:    "go-monotonic",
		now:       func() int64 { return time.Since(origin).Nanoseconds() },
		frequency: int64(time.Second),
	}
}
