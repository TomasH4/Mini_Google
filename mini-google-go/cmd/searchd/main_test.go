package main

import (
	"bytes"
	"context"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"minigoogle/internal/index"
)

func TestCommands(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "corpus.tsv")
	if err := os.WriteFile(path, []byte("1\tEjemplo\tjava concurrencia\n"), 0600); err != nil {
		t.Fatal(err)
	}
	for _, tc := range []struct {
		command string
		text    string
		want    string
	}{
		{"search", "java", "[1] Ejemplo"},
		{"suggest", "conc", "concurrencia\t1\t0"},
		{"fuzzy", "concurrensia", "concurrencia\t1\t1"},
	} {
		var out bytes.Buffer
		args := []string{tc.command, "--corpus", path, "--", tc.text}
		if err := run(ctx, args, strings.NewReader(""), &out, io.Discard); err != nil {
			t.Fatal(err)
		}
		if !strings.Contains(out.String(), tc.want) {
			t.Errorf("%s = %q; want %q", tc.command, out.String(), tc.want)
		}
	}
	var out, diagnostics bytes.Buffer
	in := strings.NewReader("(java\njava\n\n")
	if err := run(ctx, []string{"interactive", "--corpus", path}, in, &out, &diagnostics); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out.String(), "[1] Ejemplo") || !strings.Contains(diagnostics.String(), "missing closing parenthesis") {
		t.Fatalf("interactive output = %q, diagnostics = %q", out.String(), diagnostics.String())
	}
}

func BenchmarkConsoleQuery(b *testing.B) {
	ctx := context.Background()
	idx, err := index.BuildTSV(ctx, []byte("1\tTitulo\tjava scala\n2\tOtro\tscala concurrente"))
	if err != nil {
		b.Fatal(err)
	}
	b.ReportAllocs()
	b.ResetTimer()
	for range b.N {
		if err := console(ctx, idx, index.SearchOptions{}, strings.NewReader("scala\n\n"), io.Discard, io.Discard); err != nil {
			b.Fatal(err)
		}
	}
}
