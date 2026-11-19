package main

import (
	"bufio"
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"os/signal"
	"runtime"
	"strings"

	"minigoogle/internal/bench"
	"minigoogle/internal/index"
	"minigoogle/internal/trie"
)

const defaultCorpus = "../dataset/corpus.tsv"
const defaultQueries = "../dataset/queries.txt"

const usage = `Mini-Google en Go
  searchd interactive [--corpus ruta] [--fuzzy 2]
  searchd search [--corpus ruta] [--fuzzy 2] -- consulta
  searchd suggest [--corpus ruta] [--limit 10] -- prefijo
  searchd fuzzy [--corpus ruta] [--limit 10] [--distance 2] -- término
  searchd bench [--corpus ruta] [--queries ruta] [--workers n] [--warmup n] [--out reporte.json] [--dump resultados.tsv]
`

func main() {
	ctx := context.Background()
	// The console keeps standard Ctrl+C behavior while blocked in a terminal read.
	if len(os.Args) > 1 && os.Args[1] != "interactive" {
		var stop context.CancelFunc
		ctx, stop = signal.NotifyContext(ctx, os.Interrupt)
		defer stop()
	}
	if err := run(ctx, os.Args[1:], os.Stdin, os.Stdout, os.Stderr); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run(ctx context.Context, args []string, in io.Reader, out, diagnostics io.Writer) error {
	command := "interactive"
	if len(args) != 0 {
		command, args = args[0], args[1:]
	}
	switch command {
	case "-h", "--help", "help":
		_, err := io.WriteString(out, usage)
		return err
	case "bench":
		return runBench(ctx, args, out, diagnostics)
	case "search", "interactive", "suggest", "fuzzy":
		return runSearch(ctx, command, args, in, out, diagnostics)
	default:
		return fmt.Errorf("unknown command %q\n%s", command, usage)
	}
}

func runSearch(ctx context.Context, command string, args []string, in io.Reader, out, diagnostics io.Writer) error {
	flags := flag.NewFlagSet(command, flag.ContinueOnError)
	flags.SetOutput(diagnostics)
	corpus := flags.String("corpus", defaultCorpus, "corpus TSV")
	var opts index.SearchOptions
	limit, distance := 10, trie.MaxDistance
	switch command {
	case "interactive", "search":
		flags.IntVar(&opts.FuzzyDistance, "fuzzy", 0, "edit distance for missing terms (0 disables correction)")
	case "suggest", "fuzzy":
		flags.IntVar(&limit, "limit", limit, "maximum suggestions")
		if command == "fuzzy" {
			flags.IntVar(&distance, "distance", distance, "maximum edit distance")
		}
	}
	if err := flags.Parse(args); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return nil
		}
		return err
	}
	if opts.FuzzyDistance < 0 || opts.FuzzyDistance > trie.MaxDistance || distance < 0 || distance > trie.MaxDistance {
		return fmt.Errorf("edit distance must be between 0 and %d", trie.MaxDistance)
	}
	if limit <= 0 {
		return fmt.Errorf("suggestion limit must be positive")
	}
	text := strings.Join(flags.Args(), " ")
	if command == "interactive" && flags.NArg() != 0 {
		return fmt.Errorf("interactive does not accept positional arguments")
	}
	if (command == "search" || command == "fuzzy") && strings.TrimSpace(text) == "" {
		return fmt.Errorf("%s requires text after the flags", command)
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	data, err := os.ReadFile(*corpus)
	if err != nil {
		return fmt.Errorf("read corpus: %w", err)
	}
	idx, err := index.BuildTSV(ctx, data)
	if err != nil {
		return fmt.Errorf("build index: %w", err)
	}
	switch command {
	case "interactive":
		return console(ctx, idx, opts, in, out, diagnostics)
	case "search":
		matches, err := idx.Search(ctx, text, opts)
		if err != nil {
			return err
		}
		return writeMatches(out, matches)
	case "suggest":
		suggestions, err := idx.Suggest(ctx, text, limit)
		if err != nil {
			return err
		}
		return writeSuggestions(out, suggestions)
	case "fuzzy":
		suggestions, err := idx.Fuzzy(ctx, text, distance, limit)
		if err != nil {
			return err
		}
		return writeSuggestions(out, suggestions)
	default:
		return fmt.Errorf("unsupported search command %q", command)
	}
}

func console(ctx context.Context, idx *index.Index, opts index.SearchOptions, in io.Reader, out, diagnostics io.Writer) error {
	if _, err := fmt.Fprintf(out, "Mini-Google · %d documentos. Línea vacía para salir.\n", idx.Stats().Documents); err != nil {
		return err
	}
	lines := bufio.NewScanner(in)
	for {
		if err := ctx.Err(); err != nil {
			return err
		}
		if _, err := fmt.Fprint(out, "query> "); err != nil {
			return err
		}
		if !lines.Scan() {
			return lines.Err()
		}
		text := strings.TrimSpace(lines.Text())
		if text == "" {
			return nil
		}
		matches, err := idx.Search(ctx, text, opts)
		if err != nil {
			fmt.Fprintln(diagnostics, err)
			continue
		}
		if err := writeMatches(out, matches); err != nil {
			return err
		}
	}
}

func writeMatches(out io.Writer, matches index.Matches) error {
	if _, err := fmt.Fprintf(out, "%d coincidencias\n", matches.Total); err != nil {
		return err
	}
	for _, result := range matches.Top {
		if _, err := fmt.Fprintf(out, "[%d] %s (BM25 %.6f)\n", result.DocID, result.Title, result.Score); err != nil {
			return err
		}
	}
	return nil
}

func writeSuggestions(out io.Writer, suggestions []trie.Suggestion) error {
	for _, suggestion := range suggestions {
		if _, err := fmt.Fprintf(out, "%s\t%d\t%d\n", suggestion.Term, suggestion.DocumentFrequency, suggestion.Distance); err != nil {
			return err
		}
	}
	return nil
}

func runBench(ctx context.Context, args []string, out, diagnostics io.Writer) error {
	flags := flag.NewFlagSet("bench", flag.ContinueOnError)
	flags.SetOutput(diagnostics)
	var cfg bench.Config
	flags.StringVar(&cfg.CorpusPath, "corpus", defaultCorpus, "corpus TSV")
	flags.StringVar(&cfg.QueriesPath, "queries", defaultQueries, "query file")
	flags.IntVar(&cfg.Workers, "workers", runtime.GOMAXPROCS(0), "concurrent worker count")
	flags.IntVar(&cfg.Warmup, "warmup", 50, "queries to run before measurement")
	reportPath := flags.String("out", "", "write JSON report to this path")
	dumpPath := flags.String("dump", "", "write a TSV with the ranked results of each query")
	if err := flags.Parse(args); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return nil
		}
		return err
	}
	if flags.NArg() != 0 {
		return fmt.Errorf("bench accepts only flags")
	}
	result, err := bench.Measure(ctx, cfg)
	if err != nil {
		return err
	}
	if *dumpPath != "" {
		if err := writeFile(*dumpPath, func(w io.Writer) error { return result.WriteDump(ctx, w) }); err != nil {
			return err
		}
	}
	if *reportPath != "" {
		if err := writeFile(*reportPath, result.Report.WriteJSON); err != nil {
			return err
		}
	}
	return result.Report.WriteJSON(out)
}

func writeFile(path string, write func(io.Writer) error) error {
	file, err := os.Create(path)
	if err != nil {
		return fmt.Errorf("create %q: %w", path, err)
	}
	writeErr := write(file)
	if err := errors.Join(writeErr, file.Close()); err != nil {
		return fmt.Errorf("write %q: %w", path, err)
	}
	return nil
}
