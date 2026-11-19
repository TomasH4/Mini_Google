package index

import (
	"bufio"
	"bytes"
	"context"
	"fmt"
	"strconv"
	"strings"
	"unicode/utf8"
)

// BuildTSV parses an in-memory UTF-8 TSV and constructs the index. The input
// belongs to the caller, so disk I/O can be measured independently.
func BuildTSV(ctx context.Context, data []byte) (*Index, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	docs := make([]Document, 0, bytes.Count(data, []byte{'\n'}))
	reader := bufio.NewReader(bytes.NewReader(data))
	for line := 1; ; line++ {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		raw, err := reader.ReadString('\n')
		if len(raw) > 0 {
			raw = strings.TrimSuffix(strings.TrimSuffix(raw, "\n"), "\r")
			if raw != "" {
				doc, parseErr := parseRow(raw)
				if parseErr != nil {
					return nil, fmt.Errorf("TSV line %d: %w", line, parseErr)
				}
				docs = append(docs, doc)
			}
		}
		if err != nil {
			// The reader wraps a byte slice, so its only read error is EOF.
			break
		}
	}
	return Build(ctx, docs)
}

func parseRow(line string) (Document, error) {
	if !utf8.ValidString(line) {
		return Document{}, fmt.Errorf("invalid UTF-8")
	}
	rawID, rest, first := strings.Cut(line, "\t")
	title, body, second := strings.Cut(rest, "\t")
	if !first || !second || strings.ContainsRune(body, '\t') {
		return Document{}, fmt.Errorf("expected ID, title and body separated by tabs")
	}
	id, err := strconv.Atoi(strings.TrimSpace(rawID))
	if err != nil {
		return Document{}, fmt.Errorf("invalid document ID: %w", err)
	}
	title = strings.TrimSpace(title)
	if id <= 0 || title == "" {
		return Document{}, fmt.Errorf("expected a positive ID and a title")
	}
	return Document{ID: id, Title: title, Body: body}, nil
}
