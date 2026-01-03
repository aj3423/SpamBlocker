package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
)

type ChatCompletion struct {
	Choices []Choice `json:"choices"`
}

type Choice struct {
	Message Message `json:"message"`
}

type Message struct {
	Content string `json:"content"`
}

func parseGroqResponse(body []byte) (string, error) {
	var resp ChatCompletion

	if err := json.Unmarshal(body, &resp); err != nil {
		return "", fmt.Errorf("failed to unmarshal response: %w", err)
	}

	// Groq always returns at least one choice (n=1 is enforced)
	if len(resp.Choices) == 0 {
		return "", fmt.Errorf("no choices returned")
	}

	content := resp.Choices[0].Message.Content

	return content, nil
}

func groq(model, prompt string) (string, error) {

	url := "https://api.groq.com/openai/v1/chat/completions"

	jsonPayload := fmt.Sprintf(`{
	 "model": "%s",
	 "temperature": 1,
	 "max_completion_tokens": 8192,
	 "top_p": 1,
	 "stream": false,
	 "reasoning_effort": "medium",
	 "stop": null,
	 "n": 1,
	 "messages": [
	   {
		 "role": "user",
		 "content": "%s"
	   }
	 ]
    }`, model, strings.Trim(strconv.Quote(prompt), "\""))

	// fmt.Println("payload:")
	// fmt.Println(jsonPayload)

	req, e := http.NewRequest("POST", url, bytes.NewBuffer([]byte(jsonPayload)))
	if e != nil {
		return "", e
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+os.Getenv("GROQ_API_KEY"))

	client := &http.Client{}
	resp, e := client.Do(req)
	if e != nil {
		return "", e
	}
	defer resp.Body.Close()
	body, e := io.ReadAll(resp.Body)
	if e != nil {
		return "", e
	}
	// fmt.Println("body: ")
	// fmt.Println(string(body))

	content, err := parseGroqResponse(body)
	if err != nil {
		panic(err)
	}

	return strings.TrimSpace(content), nil

}
