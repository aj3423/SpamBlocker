package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"sync"

	"github.com/fatih/color"
	"github.com/google/generative-ai-go/genai"
	"github.com/panjf2000/ants/v2"
	"google.golang.org/api/option"
)

var nameMap = map[string]string{
	`de`:     `German`,
	`es`:     `Spanish`,
	`fr`:     `French`,
	`gal`:    `Galician`,
	`it`:     `Italian`,
	`ja`:     `Japanese`,
	`pt-rBR`: `Brazilian Portuguese`,
	`ru`:     `Russian`,
	`tr`:     `Turkish`,
	`uk`:     `Ukrainian`,
	`zh`:     `Chinese`,
}

// -------- flags
var thread int

var lang_str string

var filter_str string

var del string

var move string

var to string

var only string

var short bool

// -------- flags end

const ENGLISH = ""

var RES_DIR string

var wg sync.WaitGroup

var pool *ants.Pool

var LANGUAGES []string // [de, es, fr, ...]

func check_lang_param() []string {
	if len(lang_str) == 0 {
		panic("must specify language")
	}
	var languages []string
	if lang_str == "all" {
		languages = LANGUAGES
	} else {
		languages = strings.Split(lang_str, ",")
	}
	for _, lang := range languages {
		if !slices.Contains(LANGUAGES, lang) {
			panic("language " + lang + " not supported yet")
		}
	}
	return languages
}

func assert(e error) {
	if e != nil {
		panic(e)
	}
}

func read_file(fullpath string) string {
	s, err := os.ReadFile(fullpath)
	if err != nil {
		return ""
	}
	return string(s)
}

func read_file_lines(fullpath string) []string {
	return strings.Split(read_file(fullpath), "\n")
}

func write_file(fullpath string, data string) error {
	return os.WriteFile(fullpath, []byte(data), 0666)
}

func read_xml(lang string, xml_fn string) string {
	src_file := lang_xmls_dir(lang) + "/" + xml_fn

	content := read_file(src_file)
	content = strings.ReplaceAll(content, `\'`, `'`)
	content = strings.ReplaceAll(content, `\"`, `"`)
	return strings.TrimSpace(content)
}

func lang_xmls_dir(lang string) string {
	if lang == "" {
		return RES_DIR + "/values"
	} else {
		return RES_DIR + "/values-" + lang
	}
}

func write_xml(lang string, xml_fn string, content string) error {
	escaped := strings.ReplaceAll(content, "'", "\\'")
	dir := lang_xmls_dir(lang)
	os.MkdirAll(dir, 0666)
	return write_file(dir+"/"+xml_fn, escaped)
}

func clear_lang_xmls(lang string) {
	dir := lang_xmls_dir(lang)

	fmt.Printf("clearing: %s\n\n", dir)
	os.RemoveAll(dir)
	os.Mkdir(dir, os.ModePerm)
}

func translate_text(lang string, content_to_translate string) (string, error) {

	if content_to_translate == "" {
		return "", nil
	}

	GeminiToken := os.Getenv("GeminiToken")

	var use_short string
	if short {
		use_short = "Use extream short translation. "
	} else {
		use_short = "If the content contains tags <short> and </short>, always translate the text in between with extream short translations. "
	}
	prompt := fmt.Sprintf(
		"Translate the following xml content to language \"%s\"(\"%s\"), it's about an app that blocks spam calls. "+
			"The word 'number' means phone number, 'spam' means spam calls, don't translate it to spam email. "+
			"If the content contains tags <translate> and </translate>, always translate the text in between. "+
			"If the content contains tags <no_translate> and </no_translate>, keep it as it is. "+
			use_short+
			"show me the raw result only:\n"+
			"%s",
		lang, nameMap[lang], content_to_translate)

	ctx := context.Background()

	client, err := genai.NewClient(ctx, option.WithAPIKey(GeminiToken))
	if err != nil {
		return "", err
	}
	defer client.Close()

	model := client.GenerativeModel("gemini-1.5-flash")

	// max is 8192 for gemini-v1.5-flash (8192 by default)
	// but actually it's only 2048...
	// model.SetMaxOutputTokens(8192)

	resp, err := model.GenerateContent(ctx, genai.Text(prompt))
	if err != nil {
		return "", err
	}

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
			return "", errors.New("no c.Content returned")
		}
		for _, p := range c.Content.Parts {
			fmt.Fprintf(sb, "%v", p)
		}
	}

	ret := sb.String()

	// skip the first line: <?xml version="1.0" encoding="utf-8"?>
	if strings.HasPrefix(ret, "<?xml version=") {
		index := strings.IndexByte(ret, '\n')
		ret = ret[index+1:]
	}
	if strings.HasPrefix(ret, "```xml") {
		lines := split_lines(ret)
		ret = join_lines(lines[1 : len(lines)-2]) // the last line must be ```
	}

	if !strings.HasPrefix(ret, "<resources>") {
		color.HiMagenta(ret)
		return "", Retryable(errors.New("malformed result"))
	}
	return ret, nil
}

// Iterate through all xml files for a particular language
func walk_lang_xmls(lang string, operation func(string) error) {

	filepath.Walk(
		lang_xmls_dir(lang),

		func(path string, fi os.FileInfo, err error) error {
			if err != nil {
				return err
			}
			if fi.IsDir() || !strings.HasPrefix(fi.Name(), "strings_") {
				return nil
			}

			if filter_str != "" {
				if !strings.Contains(fi.Name(), filter_str) {
					return nil
				}
			}

			wg.Add(1)
			pool.Submit(func() {
				e := Retry(5, func(attempt int) error {
					return operation(fi.Name())
				})

				if e != nil {
					color.HiWhite("translate failed: %s, error: %s", color.HiRedString(fi.Name()), e.Error())
				}

				wg.Done()
			})
			return nil
		})
}

func split_lines(content string) []string {
	return strings.Split(content, "\n")
}

func join_lines(lines []string) string {
	return strings.Join(lines, "\n")
}

func remove_lines(slice []string, start, end int) []string {
	s1 := slice[0:start]
	s2 := slice[end:]
	return slices.Concat(s1, s2)
}

func translate_1_xml(lang string, xml_fn string) error {
	english := read_xml(ENGLISH, xml_fn)
	fmt.Printf("translating: %s -> %s\n", xml_fn, lang)
	translated, e := translate_text(lang, english)

	if IsRetryable(e) {
		color.HiWhite("retry %s, error: %s", color.HiYellowString(xml_fn), e.Error())
	}
	if e == nil {
		color.HiWhite("done %s %s", lang, color.HiGreenString(xml_fn))
		if only == "" { // replace entire xml
			write_xml(lang, xml_fn, translated)
		} else { // only replace the specific tag
			v_only := strings.Split(only, ",")
			for _, target := range v_only {
				origin_lines := split_lines(read_xml(lang, xml_fn))
				translated_lines := split_lines(translated)
				found1, start1, end1, _ := extract_tag(origin_lines, target)
				found2, _, _, matched_translated_lines := extract_tag(translated_lines, target)
				if !found2 {
					panic(fmt.Sprintf("tag: <%s> not found in translated result: <%s>, xml: <%s>", target, lang, xml_fn))
				}

				// if tag already exists in xml, replace it, otherwise, append as last tag.
				if found1 {
					// replace the tag content with the `matched_translated_lines`
					new_lines := insert_lines_at(
						remove_lines(origin_lines, start1, end1),
						start1,
						matched_translated_lines,
					)
					write_xml(lang, xml_fn, join_lines(new_lines))
				} else {
					new_lines := insert_lines_at(
						origin_lines,
						len(origin_lines)-1,
						matched_translated_lines,
					)
					write_xml(lang, xml_fn, join_lines(new_lines))
				}
			}
		}
	}
	return e
}

func lang_translator(target_lang string) func(string) error {
	return func(xml_fn string) error {

		e := translate_1_xml(target_lang, xml_fn)

		if e != nil {
			color.HiRed("translate %s, %s, error: %s",
				color.HiWhiteString(target_lang), xml_fn, e.Error())
		}
		return e
	}
}

// return:
//
//	found or not, start line, end line, tag lines
//
// 3 types of tag:
//
//	<string> </string>
//	<plurals> </plurals>
//	<string-array> </string-array>
func extract_tag(lines []string, tag string) (bool, int, int, []string) {
	start := -1
	end := -1
	var close_tag string

	for i := 0; i < len(lines); i++ {
		if strings.Contains(lines[i], `name="`+tag+`"`) {
			start = i
			if strings.Contains(lines[i], "<string name=") {
				close_tag = "</string>"
			} else if strings.Contains(lines[i], "<plurals name=") {
				close_tag = "</plurals>"
			} else if strings.Contains(lines[i], "<string-array name=") {
				close_tag = "</string-array>"
			} else {
				panic(fmt.Sprintf("unknown tag: %s", lines[i]))
			}
		}
		if start != -1 { // look for end
			if strings.Contains(lines[i], close_tag) {
				end = i + 1
				return true, start, end, lines[start:end]
			}
		}
	}
	return false, start, end, nil
}

func insert_lines_at(dest []string, at int, to_insert []string) []string {
	return slices.Concat(
		slices.Concat(dest[:at], to_insert),
		dest[at:],
	)
}

// -move recent_apps -to strings_2.xml
func move_tag(tag string, lang string, to_xml string) func(string) error {
	return func(xml_fn string) error {

		src_lines := split_lines(read_xml(lang, xml_fn))
		found, start, end, matched_lines := extract_tag(src_lines, tag)
		if found {
			// 1. add to dest xml
			dest_lines := split_lines(read_xml(lang, to_xml))
			penultimate := len(dest_lines) - 1
			// insert the matched_lines at the line before last line
			new_lines := insert_lines_at(dest_lines, penultimate, matched_lines)

			write_xml(lang, to_xml, join_lines(new_lines))

			// 2. remove from source xml
			cleared := append(src_lines[:start], src_lines[end:]...)
			write_xml(lang, xml_fn, join_lines(cleared))
		}

		return nil
	}
}

// -del recent_apps
func delete_tag(tag string, lang string, _ string) func(string) error {
	return func(xml_fn string) error {

		src_lines := split_lines(read_xml(lang, xml_fn))
		found, start, end, _ := extract_tag(src_lines, tag)
		if found {
			fmt.Printf("%s  %s\n", lang, xml_fn)
			cleared := append(src_lines[:start], src_lines[end:]...)
			write_xml(lang, xml_fn, join_lines(cleared))
		}

		return nil
	}
}

func setup() {
	for key := range nameMap {
		LANGUAGES = append(LANGUAGES, key)
	}

	cwd, _ := os.Getwd()
	RES_DIR = cwd + "/../app/src/main/res"

	flag.StringVar(&lang_str, "lang", "", fmt.Sprintf("Required, available languages: %v", LANGUAGES))
	flag.StringVar(&filter_str, "filter", "", "-filter _12.xml")
	flag.StringVar(&del, "del", "", "-del tag")
	flag.StringVar(&move, "move", "", "-move tag -to strings_1.xml")
	flag.StringVar(&to, "to", "", "")
	flag.BoolVar(&short, "short", false, "force short, usually used together with -only")
	flag.StringVar(&only, "only", "", "-only tag")
	flag.IntVar(&thread, "thread", 3, "")
	flag.Parse()

	wg = sync.WaitGroup{}
	pool, _ = ants.NewPool(thread)

}

func main() {
	setup()

	if del != "" {
		languages := append(LANGUAGES, ENGLISH)
		for _, lang := range languages {
			walk_lang_xmls(lang, delete_tag(del, lang, to))
		}
	} else if move != "" { // -move recent_apps -to strings_2.xml
		if to == "" {
			color.HiRed("usage: go run . -move %s -to strings_x.xml", move)
			return
		}
		languages := append(LANGUAGES, ENGLISH)
		for _, lang := range languages {
			walk_lang_xmls(lang, move_tag(move, lang, to))
		}
	} else { // translate
		languages := check_lang_param()
		for _, target_lang := range languages {
			// if filter_str == "" {
			// clear_lang_xmls(lang)
			// }
			walk_lang_xmls(ENGLISH, lang_translator(target_lang))
		}
	}

	wg.Wait()
}
