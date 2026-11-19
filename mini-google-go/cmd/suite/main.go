package main

import (
	"context"
	"fmt"
	"minigoogle/internal/bench"
	"os"
	"os/signal"
)

func main() {
	if len(os.Args) != 5 {
		fmt.Fprintln(os.Stderr, "suite parity|measure corpus queries writes")
		os.Exit(2)
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()
	if err := bench.RunSuite(ctx, os.Args[1], os.Args[2], os.Args[3], os.Args[4], os.Stdin, os.Stdout); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
