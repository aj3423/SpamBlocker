package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"strings"

	"github.com/google/generative-ai-go/genai"
	"google.golang.org/api/option"
)

var RES_DIR string

func init() {
	cwd, _ := os.Getwd()
	RES_DIR = cwd + "/../app/src/main/res"
}

func check(e error) {
	if e != nil {
		panic(e)
	}
}

func read_file(fn string) string {
	s, err := os.ReadFile(fn)
	if err != nil {
		return ""
	}
	return string(s)
}

func write_file(fn string, data string) error {
	return os.WriteFile(fn, []byte(data), 0666)
}

func translate_1_file(lang string, fn string) error {

	src_file := fmt.Sprintf("%s/values/%s", RES_DIR, fn)
	dest_file := fmt.Sprintf("%s/values-%s/%s", RES_DIR, lang, fn)

	fmt.Printf("processing: %s\n", src_file)

	to_translate := read_file(src_file)

	GeminiToken := os.Getenv("GeminiToken")

	text := fmt.Sprintf(
		"Translate the following xml content to %s, better use short words, leave the XML tags unmodified, show me the result only \n%s",
		lang, to_translate)

	ctx := context.Background()

	client, err := genai.NewClient(ctx, option.WithAPIKey(GeminiToken))
	check(err)
	defer client.Close()

	model := client.GenerativeModel("gemini-pro")
	// model.SetMaxOutputTokens(8192) // max is 8192 for gemini-pro v1.0 (8192 by default)
	resp, err := model.GenerateContent(ctx, genai.Text(text))
	check(err)

	fmt.Println("  - ",
		"TotalTokenCount", resp.UsageMetadata.TotalTokenCount,
		"PromptTokenCount", resp.UsageMetadata.PromptTokenCount,
		"CandidatesTokenCount", resp.UsageMetadata.CandidatesTokenCount,
	)
	fmt.Println()

	if resp.UsageMetadata.CandidatesTokenCount >= 2048 {
		panic("CandidatesTokenCount reached 2048, preferably < 1800, split the xml")
	}

	sb := &strings.Builder{}

	for _, c := range resp.Candidates {
		if c.Content == nil {
			fmt.Println(c)
			return errors.New("no c.Content returned")
		}
		for _, p := range c.Content.Parts {
			fmt.Fprintf(sb, "%v", p)
		}
	}

	// French has lots of '
	cleared := strings.ReplaceAll(sb.String(), "'", "\\'")
	write_file(dest_file, cleared)
	return nil
}

func translate_lang(lang string) error {
	path := RES_DIR + "/values-" + lang

	// the 3rd commandline argument for regenerating particular xml
	var filter *string
	if len(os.Args) > 2 {
		filter = &os.Args[2]
	}

	if filter == nil {
		fmt.Printf("clearing: %s\n\n", path)
		os.RemoveAll(path)
		os.Mkdir(path, os.ModePerm)
	}

	e := filepath.Walk(
		RES_DIR+"/values",

		func(path string, fi os.FileInfo, err error) error {
			if err != nil {
				return err
			}
			if fi.IsDir() || !strings.HasPrefix(fi.Name(), "strings_") {
				return nil
			}
			if strings.Contains(fi.Name(), "no_translate") {
				return nil
			}

			if filter != nil {
				if !strings.Contains(fi.Name(), *filter) {
					return nil
				}
			}

			translate_1_file(lang, fi.Name())
			return nil
		})

	return e
}

var langs = []string{
	`fr`, `ru`, `zh`,
	// `de`,  `ja`, `ko`, `vi`, `zh-rTW`,
}

func usage() {
	fmt.Println("usage:")
	fmt.Println("go run . language [filter]")
	fmt.Println("supported languages are:")
	fmt.Println(langs)
}

func main() {
	if len(os.Args) < 2 {
		usage()
		return
	}

	lang := os.Args[1]
	if !slices.Contains(langs, lang) {
		usage()
		return
	}
	e := translate_lang(lang)
	check(e)

}
